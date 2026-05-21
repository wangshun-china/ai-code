package com.ws.codecraft.ai;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ws.codecraft.ai.config.ReasoningStreamingChatModelConfig;
import com.ws.codecraft.ai.config.StreamingChatModelConfig;
import com.ws.codecraft.core.AiCallHelper;
import com.ws.codecraft.exception.BusinessException;
import com.ws.codecraft.exception.ErrorCode;
import com.ws.codecraft.model.enums.AiModelEnum;
import com.ws.codecraft.model.enums.AiModelEnum.ModelEndpoint;
import com.ws.codecraft.model.enums.CodeGenTypeEnum;
import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    private static final String VUE_PROJECT_PROMPT = "prompt/codegen-vue-project-system-prompt.txt";
    private static final String PLAN_PROMPT = "prompt/codegen-plan-system-prompt.txt";
    private static final String REVIEW_PROMPT = "prompt/codegen-vue-review-system-prompt.txt";
    private static final int SUMMARY_TOKEN_THRESHOLD = 50000;
    private static final int SUMMARY_KEEP_MESSAGES = 30;
    private static final int CHAT_MEMORY_MAX_MESSAGES = 20;
    private static final String DASHSCOPE_COMPATIBLE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    @Resource
    private StreamingChatModelConfig streamingChatModelConfig;

    @Resource
    private ReasoningStreamingChatModelConfig reasoningStreamingChatModelConfig;

    @Resource
    private SpringAiToolCallbackRegistry toolCallbackRegistry;

    @Resource
    private AiCallHelper callHelper;

    @Resource
    private AiModelFallbackRouter aiModelFallbackRouter;

    @Resource
    private RedisSaver redisSaver;

    @Resource
    private ChatMemoryRepository chatMemoryRepository;

    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) ->
                    log.debug("AI 服务实例被移除，缓存键: {}, 原因: {}", key, cause))
            .build();

    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);
    }

    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        return getAiCodeGeneratorService(appId, codeGenType, null);
    }

    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType, String modelKey) {
        String normalizedModelKey = AiModelEnum.normalize(modelKey);
        String cacheKey = buildCacheKey(appId, codeGenType, normalizedModelKey);
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType, normalizedModelKey));
    }

    public String chatPlain(String message, String modelKey) {
        return chatPlainWithFallback(message, modelKey, null);
    }

    public String chatPlainWithFallback(String message, String modelKey, Consumer<String> modelSelectionHandler) {
        String normalizedModelKey = AiModelEnum.normalize(modelKey);
        return callWithModelFallback(
                aiModelFallbackRouter.resolveCandidates(normalizedModelKey),
                modelSelectionHandler,
                candidate -> chatPlainOnce(message, candidate));
    }

    private String chatPlainOnce(String message, String normalizedModelKey) {
        ChatClient client = createChatClient(normalizedModelKey,
                streamingChatModelConfig.getMaxTokens(), streamingChatModelConfig.getTemperature(), false);
        return callHelper.call(client, message, normalizedModelKey);
    }

    public String chatWithImage(String message, byte[] imageBytes, String mimeType, String fileName, String modelKey) {
        return chatWithImageWithFallback(message, imageBytes, mimeType, fileName, modelKey, null);
    }

    public String chatWithImageWithFallback(String message, byte[] imageBytes, String mimeType, String fileName,
                                            String modelKey, Consumer<String> modelSelectionHandler) {
        String normalizedModelKey = AiModelEnum.normalize(modelKey);
        return callWithModelFallback(
                aiModelFallbackRouter.resolveCandidates(normalizedModelKey),
                modelSelectionHandler,
                candidate -> chatWithImageOnce(message, imageBytes, mimeType, fileName, candidate));
    }

    private String chatWithImageOnce(String message, byte[] imageBytes, String mimeType, String fileName,
                                     String normalizedModelKey) {
        ChatClient client = createChatClient(normalizedModelKey,
                streamingChatModelConfig.getMaxTokens(), streamingChatModelConfig.getTemperature(), false);
        return callHelper.callWithImage(client, message, imageBytes, mimeType, fileName, normalizedModelKey);
    }

    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType, String modelKey) {
        log.info("创建 Spring AI Alibaba AI 服务实例, appId={}, type={}, model={}", appId, codeGenType, modelKey);
        if (codeGenType == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        ChatClient chatClient = createChatClient(modelKey,
                streamingChatModelConfig.getMaxTokens(), streamingChatModelConfig.getTemperature(), false);
        ChatModel reasoningChatModel = buildReasoningChatModel(modelKey);
        List<ToolCallback> toolCallbacks = toolCallbackRegistry.buildAgentToolCallbacks(appId);

        // 阶段1: 规划 Agent — 分析需求，输出项目结构规划
        ReactAgent plannerAgent = ReactAgent.builder()
                .name("planner")
                .model(reasoningChatModel)
                .description("分析用户需求，输出 Vue 项目结构规划")
                .systemPrompt(AiCallHelper.loadPrompt(PLAN_PROMPT))
                .instruction("用户需求：{input}")
                .outputKey("plan")
                .build();

        SummarizationHook summarizationHook = SummarizationHook.builder()
                .model(reasoningChatModel)
                .maxTokensBeforeSummary(SUMMARY_TOKEN_THRESHOLD)
                .messagesToKeep(SUMMARY_KEEP_MESSAGES)
                .build();

        HumanInTheLoopHook humanHook = HumanInTheLoopHook.builder()
                .approvalOn("deleteFile", ToolConfig.builder()
                        .description("删除文件操作需要确认").build())
                .approvalOn("modifyFile", ToolConfig.builder()
                        .description("修改文件操作需要确认").build())
                .build();

        // 阶段2: 编码 Agent — 根据规划执行代码生成
        ReactAgent coderAgent = ReactAgent.builder()
                .name("vue_coder")
                .model(reasoningChatModel)
                .description("Vue3 前端代码生成专家，根据规划生成代码")
                .systemPrompt(AiCallHelper.loadPrompt(VUE_PROJECT_PROMPT))
                .instruction("请根据以下项目规划生成代码：{plan}")
                .tools(toolCallbacks)
                .saver(redisSaver)
                .hooks(List.of(humanHook, summarizationHook))
                .build();

        // 阶段3: 审查 Agent — 检查代码质量并修复问题
        ReactAgent reviewerAgent = ReactAgent.builder()
                .name("reviewer")
                .model(reasoningChatModel)
                .description("Vue3 代码审查专家，检查并修复代码问题")
                .systemPrompt(AiCallHelper.loadPrompt(REVIEW_PROMPT))
                .instruction("请审查刚生成的 Vue 项目代码，检查完整性和质量。项目规划：{plan}")
                .tools(toolCallbacks)
                .saver(redisSaver)
                .hooks(List.of(summarizationHook))
                .build();

        // 编排为顺序流水线：规划 → 编码 → 审查
        SequentialAgent codegenPipeline = SequentialAgent.builder()
                .name("vue_codegen_pipeline")
                .description("Vue 项目生成流水线：规划 → 编码 → 审查")
                .subAgents(List.of(plannerAgent, coderAgent, reviewerAgent))
                .build();

        return switch (codeGenType) {
            case HTML, MULTI_FILE, VUE_PROJECT -> new SpringAiAlibabaCodeGeneratorService(
                    chatClient, codegenPipeline, modelKey,
                    callHelper, toolCallbackRegistry);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "不支持的代码生成类型: " + codeGenType.getValue());
        };
    }

    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0);
    }

    public ChatClient createChatClient(String modelKey) {
        return createChatClient(AiModelEnum.normalize(modelKey),
                streamingChatModelConfig.getMaxTokens(), streamingChatModelConfig.getTemperature(), false);
    }

    public ChatClient createChatClient(String modelKey, Integer maxTokens, Double temperature, boolean streaming) {
        String normalizedKey = AiModelEnum.normalize(modelKey);
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(CHAT_MEMORY_MAX_MESSAGES)
                .build();
        return ChatClient.builder(buildChatModel(normalizedKey, maxTokens, temperature, streaming))
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultOptions(buildOptions(normalizedKey, maxTokens, temperature, streaming))
                .build();
    }

    private ChatModel buildReasoningChatModel(String modelKey) {
        String normalizedKey = AiModelEnum.normalize(modelKey);
        if (AiModelEnum.getEndpoint(normalizedKey) == ModelEndpoint.OPENAI_COMPATIBLE) {
            OpenAiChatOptions options = buildOpenAiOptions(normalizedKey,
                    reasoningStreamingChatModelConfig.getMaxTokens(),
                    reasoningStreamingChatModelConfig.getTemperature());
            options.setInternalToolExecutionEnabled(false);
            return buildOpenAiChatModel(options);
        }
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model(normalizedKey)
                .maxToken(reasoningStreamingChatModelConfig.getMaxTokens())
                .temperature(reasoningStreamingChatModelConfig.getTemperature())
                .incrementalOutput(true)
                .multiModel(AiModelEnum.isMultimodal(normalizedKey))
                .build();
        options.setInternalToolExecutionEnabled(false);
        return DashScopeChatModel.builder()
                .dashScopeApi(buildDashScopeApi(
                        streamingChatModelConfig.getApiKey(), streamingChatModelConfig.getBaseUrl()))
                .defaultOptions(options)
                .build();
    }

    private ChatModel buildChatModel(String modelKey, Integer maxTokens, Double temperature, boolean streaming) {
        if (AiModelEnum.getEndpoint(modelKey) == ModelEndpoint.OPENAI_COMPATIBLE) {
            OpenAiChatOptions options = buildOpenAiOptions(modelKey, maxTokens, temperature);
            options.setInternalToolExecutionEnabled(false);
            return buildOpenAiChatModel(options);
        }
        return DashScopeChatModel.builder()
                .dashScopeApi(buildDashScopeApi(
                        streamingChatModelConfig.getApiKey(), streamingChatModelConfig.getBaseUrl()))
                .build();
    }

    private static DashScopeApi buildDashScopeApi(String apiKey, String baseUrl) {
        DashScopeApi.Builder builder = DashScopeApi.builder().apiKey(apiKey);
        if (StrUtil.isNotBlank(baseUrl) && !baseUrl.contains("compatible-mode")) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    private ChatOptions buildOptions(String modelKey, Integer maxTokens, Double temperature, boolean streaming) {
        if (AiModelEnum.getEndpoint(modelKey) == ModelEndpoint.OPENAI_COMPATIBLE) {
            OpenAiChatOptions options = buildOpenAiOptions(modelKey, maxTokens, temperature);
            options.setInternalToolExecutionEnabled(false);
            return options;
        }
        var builder = DashScopeChatOptions.builder()
                .model(modelKey)
                .incrementalOutput(streaming)
                .multiModel(AiModelEnum.isMultimodal(modelKey));
        if (maxTokens != null) builder.maxToken(maxTokens);
        if (temperature != null) builder.temperature(temperature);
        DashScopeChatOptions options = builder.build();
        options.setInternalToolExecutionEnabled(false);
        return options;
    }

    private OpenAiChatOptions buildOpenAiOptions(String modelKey, Integer maxTokens, Double temperature) {
        var builder = OpenAiChatOptions.builder()
                .model(modelKey);
        if (maxTokens != null) builder.maxTokens(maxTokens);
        if (temperature != null) builder.temperature(temperature);
        return builder.build();
    }

    private OpenAiChatModel buildOpenAiChatModel(OpenAiChatOptions options) {
        OpenAiApi openAiApi = new OpenAiApi(
                resolveCompatibleBaseUrl(streamingChatModelConfig.getBaseUrl()),
                new SimpleApiKey(streamingChatModelConfig.getApiKey()),
                new LinkedMultiValueMap<>(),
                "/chat/completions",
                "/embeddings",
                RestClient.builder(),
                WebClient.builder(),
                RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER
        );
        return new OpenAiChatModel(
                openAiApi,
                options,
                ToolCallingManager.builder().build(),
                RetryTemplate.defaultInstance(),
                ObservationRegistry.NOOP
        );
    }

    private static String resolveCompatibleBaseUrl(String configuredBaseUrl) {
        if (StrUtil.isBlank(configuredBaseUrl)) {
            return DASHSCOPE_COMPATIBLE_BASE_URL;
        }
        if (configuredBaseUrl.contains("compatible-mode")) {
            return configuredBaseUrl;
        }
        return StrUtil.removeSuffix(configuredBaseUrl, "/") + "/compatible-mode/v1";
    }

    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType, String modelKey) {
        return appId + "_" + codeGenType.getValue() + "_" + AiModelEnum.normalize(modelKey);
    }

    private String callWithModelFallback(List<String> candidates,
                                         Consumer<String> modelSelectionHandler,
                                         ModelCall modelCall) {
        RuntimeException lastException = null;
        for (int i = 0; i < candidates.size(); i++) {
            String candidate = candidates.get(i);
            if (modelSelectionHandler != null) modelSelectionHandler.accept(candidate);
            try {
                return modelCall.call(candidate);
            } catch (RuntimeException e) {
                lastException = e;
                if (!aiModelFallbackRouter.isQuotaExceeded(e) || i == candidates.size() - 1) throw e;
                log.warn("AI 模型额度不足，自动切换备用模型: from={}, to={}", candidate, candidates.get(i + 1));
            }
        }
        throw lastException == null ? new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 模型调用失败") : lastException;
    }

    @FunctionalInterface
    private interface ModelCall {
        String call(String modelKey);
    }
}
