package com.ws.codecraft.ai;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ws.codecraft.ai.config.ReasoningStreamingChatModelConfig;
import com.ws.codecraft.ai.config.StreamingChatModelConfig;
import com.ws.codecraft.core.AiCallHelper;
import com.ws.codecraft.exception.BusinessException;
import com.ws.codecraft.exception.ErrorCode;
import com.ws.codecraft.model.ai.AiModelRegistry;
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
    private static final String SCAFFOLD_PROMPT = "prompt/codegen-vue-scaffold-system-prompt.txt";
    private static final String UI_PROMPT = "prompt/codegen-vue-ui-system-prompt.txt";
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
    private UserAiConfigManager userAiConfigManager;

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
        String normalizedModelKey = AiModelRegistry.normalize(modelKey);
        String cacheKey = buildCacheKey(appId, codeGenType, normalizedModelKey);
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType, normalizedModelKey));
    }

    public String chatPlain(String message, String modelKey) {
        return chatPlainWithFallback(message, modelKey, null);
    }

    public String chatPlainWithFallback(String message, String modelKey, Consumer<String> modelSelectionHandler) {
        String normalizedModelKey = AiModelRegistry.normalize(modelKey);
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
        String normalizedModelKey = AiModelRegistry.normalize(modelKey);
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
        List<ToolCallback> readOnlyToolCallbacks = toolCallbackRegistry.buildAgentReadOnlyToolCallbacks(appId);

        SummarizationHook summarizationHook = SummarizationHook.builder()
                .model(reasoningChatModel)
                .maxTokensBeforeSummary(SUMMARY_TOKEN_THRESHOLD)
                .messagesToKeep(SUMMARY_KEEP_MESSAGES)
                .build();

        // 阶段2a: 脚手架编码 Agent — 并行生成配置文件、路由、布局
        ReactAgent scaffoldCoderAgent = ReactAgent.builder()
                .name("scaffold_coder")
                .model(reasoningChatModel)
                .description("Vue3 脚手架专家，并行生成项目配置、路由和布局")
                .systemPrompt(AiCallHelper.loadPrompt(SCAFFOLD_PROMPT))
                .instruction("请根据以下项目规划生成脚手架代码：{plan}")
                .tools(toolCallbacks)
                .saver(redisSaver)
                .hooks(List.of(summarizationHook))
                .build();

        // 阶段2b: UI编码 Agent — 并行生成页面和组件
        ReactAgent uiCoderAgent = ReactAgent.builder()
                .name("ui_coder")
                .model(reasoningChatModel)
                .description("Vue3 UI专家，并行生成页面和组件代码")
                .systemPrompt(AiCallHelper.loadPrompt(UI_PROMPT))
                .instruction("请根据以下项目规划生成页面和组件代码：{plan}")
                .tools(toolCallbacks)
                .saver(redisSaver)
                .hooks(List.of(summarizationHook))
                .build();

        // 阶段2c: 修复编码 Agent — 审查不通过时统一修复
        ReactAgent fixCoderAgent = ReactAgent.builder()
                .name("fix_coder")
                .model(reasoningChatModel)
                .description("Vue3 代码修复专家，根据审查意见修复代码")
                .systemPrompt(AiCallHelper.loadPrompt(VUE_PROJECT_PROMPT))
                .instruction("""
                        请根据审查意见修复代码。
                        项目规划：{plan}

                        审查意见：{review_result}

                        要求：
                        1. 先读取相关文件确认问题。
                        2. 只修复审查意见指出的问题，不要重做整个项目。
                        3. 修复完成后简要说明修改了哪些文件。
                        """)
                .tools(toolCallbacks)
                .saver(redisSaver)
                .hooks(List.of(summarizationHook))
                .build();

        // 阶段3: 审查 Agent — 只读检查代码质量，修复交给 fix_coder
        ReactAgent reviewerAgent = ReactAgent.builder()
                .name("reviewer")
                .model(reasoningChatModel)
                .description("Vue3 代码审查专家，只读检查代码质量并输出审查结论")
                .systemPrompt(AiCallHelper.loadPrompt(REVIEW_PROMPT))
                .instruction("""
                        请审查刚生成的 Vue 项目代码，检查完整性和质量。项目规划：{plan}

                        你只能读取文件和目录，不能修改、删除或重写文件。
                        输出一份简洁的检查项结果，每个检查项一行。
                        如果代码质量合格，请在最后单独输出一行：[REVIEW_PASS]
                        如果发现问题，请列出必须修复的问题和建议，并在最后单独输出一行：[REVIEW_FAIL]
                        不要重复输出同一份检查结果，不要同时输出 [REVIEW_PASS] 和 [REVIEW_FAIL]。
                        """)
                .outputKey("review_result")
                .tools(readOnlyToolCallbacks)
                .saver(redisSaver)
                .hooks(List.of(summarizationHook))
                .build();

        // 使用 StateGraph 编排：编码 → 审查 → (审查不通过则修复后复审)。
        // 正式生成前已经完成方案确认，userMessage 会作为 plan 注入状态，避免再次输出一遍方案 JSON。
        AsyncEdgeAction reviewRouter = state -> {
            Object raw = state.value("review_result", "");
            String result = raw instanceof String s ? s : String.valueOf(raw);
            int lastPass = result.lastIndexOf("[REVIEW_PASS]");
            int lastFail = result.lastIndexOf("[REVIEW_FAIL]");
            if (lastPass >= 0 && lastPass > lastFail) {
                log.info("代码审查通过，进入构建阶段");
                return java.util.concurrent.CompletableFuture.completedFuture("pass");
            }
            if (lastFail >= 0) {
                log.info("代码审查未通过，进入修复节点");
                return java.util.concurrent.CompletableFuture.completedFuture("fail");
            }
            log.warn("代码审查未输出明确结论，按未通过处理");
            return java.util.concurrent.CompletableFuture.completedFuture("fail");
        };

        KeyStrategyFactory keyStrategyFactory = new KeyStrategyFactoryBuilder()
                .addStrategy("input", new ReplaceStrategy())
                .addStrategy("plan", new ReplaceStrategy())
                .addStrategy("review_result", new ReplaceStrategy())
                .build();

        CompiledGraph codegenPipeline;
        try {
            codegenPipeline = new StateGraph("vue_codegen_pipeline", keyStrategyFactory)
                    .addNode("scaffold_coder", scaffoldCoderAgent.asNode(true, true))
                    .addNode("ui_coder", uiCoderAgent.asNode(true, true))
                    .addNode("reviewer", reviewerAgent.asNode(true, true))
                    .addNode("fix_coder", fixCoderAgent.asNode(true, true))
                    .addEdge(StateGraph.START, "scaffold_coder")
                    .addEdge("scaffold_coder", "ui_coder")
                    .addEdge("ui_coder", "reviewer")
                    .addConditionalEdges("reviewer", reviewRouter, java.util.Map.of(
                            "pass", StateGraph.END,
                            "fail", "fix_coder"
                    ))
                    .addEdge("fix_coder", "reviewer")
                    .compile();
            codegenPipeline.setMaxIterations(6);
        } catch (com.alibaba.cloud.ai.graph.exception.GraphStateException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 流水线构建失败: " + e.getMessage());
        }

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
        return createChatClient(AiModelRegistry.normalize(modelKey),
                streamingChatModelConfig.getMaxTokens(), streamingChatModelConfig.getTemperature(), false);
    }

    public ChatClient createChatClient(String modelKey, Integer maxTokens, Double temperature, boolean streaming) {
        String normalizedKey = AiModelRegistry.normalize(modelKey);
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
        String normalizedKey = AiModelRegistry.normalize(modelKey);
        String apiKey = resolveApiKey(normalizedKey);
        String baseUrl = resolveBaseUrl(normalizedKey);
        OpenAiChatOptions options = buildOpenAiOptions(normalizedKey,
                reasoningStreamingChatModelConfig.getMaxTokens(),
                reasoningStreamingChatModelConfig.getTemperature());
        options.setInternalToolExecutionEnabled(false);
        return buildOpenAiChatModel(options, apiKey, baseUrl);
    }

    private ChatModel buildChatModel(String modelKey, Integer maxTokens, Double temperature, boolean streaming) {
        String apiKey = resolveApiKey(modelKey);
        String baseUrl = resolveBaseUrl(modelKey);
        OpenAiChatOptions options = buildOpenAiOptions(modelKey, maxTokens, temperature);
        options.setInternalToolExecutionEnabled(false);
        return buildOpenAiChatModel(options, apiKey, baseUrl);
    }

    private String resolveApiKey(String modelKey) {
        String dynamicKey = AiModelRegistry.getDynamicApiKey(modelKey);
        return StrUtil.isNotBlank(dynamicKey) ? dynamicKey : streamingChatModelConfig.getApiKey();
    }

    private String resolveBaseUrl(String modelKey) {
        String dynamicBaseUrl = AiModelRegistry.getDynamicBaseUrl(modelKey);
        return StrUtil.blankToDefault(dynamicBaseUrl, DASHSCOPE_COMPATIBLE_BASE_URL);
    }

    private ChatOptions buildOptions(String modelKey, Integer maxTokens, Double temperature, boolean streaming) {
        OpenAiChatOptions options = buildOpenAiOptions(modelKey, maxTokens, temperature);
        options.setInternalToolExecutionEnabled(false);
        return options;
    }

    private OpenAiChatOptions buildOpenAiOptions(String modelKey, Integer maxTokens, Double temperature) {
        var builder = OpenAiChatOptions.builder()
                .model(AiModelRegistry.getActualModelName(modelKey));
        if (maxTokens != null) builder.maxTokens(maxTokens);
        if (temperature != null) builder.temperature(temperature);
        return builder.build();
    }

    private OpenAiChatModel buildOpenAiChatModel(OpenAiChatOptions options, String apiKey, String baseUrl) {
        OpenAiApi openAiApi = new OpenAiApi(
                StrUtil.removeSuffix(baseUrl, "/"),
                new SimpleApiKey(apiKey),
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

    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType, String modelKey) {
        return appId + "_" + codeGenType.getValue() + "_" + AiModelRegistry.normalize(modelKey);
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
