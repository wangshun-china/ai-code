package com.ws.codecraft.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ws.codecraft.ai.config.ReasoningStreamingChatModelConfig;
import com.ws.codecraft.ai.config.StreamingChatModelConfig;
import com.ws.codecraft.ai.guardrail.PromptSafetyInputGuardrail;
import com.ws.codecraft.ai.tools.*;
import com.ws.codecraft.exception.BusinessException;
import com.ws.codecraft.exception.ErrorCode;
import com.ws.codecraft.model.enums.AiModelEnum;
import com.ws.codecraft.model.enums.CodeGenTypeEnum;
import com.ws.codecraft.service.ChatHistoryService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI 服务创建工厂
 */
@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    @Resource
    private StreamingChatModelConfig streamingChatModelConfig;

    @Resource
    private ReasoningStreamingChatModelConfig reasoningStreamingChatModelConfig;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ToolManager toolManager;

    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，缓存键: {}, 原因: {}", key, cause);
            })
            .build();

    /**
     * 根据 appId 获取服务（为了兼容老逻辑）
     *
     * @param appId
     * @return
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);
    }

    /**
     * 根据 appId 获取服务
     *
     * @param appId       应用 id
     * @param codeGenType 生成类型
     * @return
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        return getAiCodeGeneratorService(appId, codeGenType, null);
    }

    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType, String modelKey) {
        String normalizedModelKey = AiModelEnum.normalize(modelKey);
        String cacheKey = buildCacheKey(appId, codeGenType, normalizedModelKey);
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType, normalizedModelKey));
    }

    /**
     * 创建新的 AI 服务实例
     *
     * @param appId       应用 id
     * @param codeGenType 生成类型
     * @return
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType, String modelKey) {
        log.info("为 appId: {} 创建新的 AI 服务实例, model={}", appId, modelKey);
        ChatModel selectedChatModel = createChatModel(modelKey);
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        // 从数据库中加载对话历史到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        return switch (codeGenType) {
            // Vue 项目生成，使用工具调用和推理模型
            case VUE_PROJECT -> {
                StreamingChatModel reasoningStreamingChatModel = createReasoningStreamingChatModel(modelKey);
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .chatModel(selectedChatModel)
                        .streamingChatModel(reasoningStreamingChatModel)
                        .chatMemoryProvider(memoryId -> chatMemory)
                        .tools(toolManager.getAllTools())
                        // 处理工具调用幻觉问题
                        .hallucinatedToolNameStrategy(toolExecutionRequest ->
                                ToolExecutionResultMessage.from(toolExecutionRequest,
                                        "Error: there is no tool called " + toolExecutionRequest.name())
                        )
                        .maxSequentialToolsInvocations(20)  // 最多连续调用 20 次工具
                        .inputGuardrails(new PromptSafetyInputGuardrail()) // 添加输入护轨
//                        .outputGuardrails(new RetryOutputGuardrail()) // 添加输出护轨，为了流式输出，这里不使用
                        .build();
            }
            // HTML 和 多文件生成，使用流式对话模型
            case HTML, MULTI_FILE -> {
                StreamingChatModel openAiStreamingChatModel = createStreamingChatModel(modelKey);
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .chatModel(selectedChatModel)
                        .streamingChatModel(openAiStreamingChatModel)
                        .chatMemory(chatMemory)
                        .inputGuardrails(new PromptSafetyInputGuardrail()) // 添加输入护轨
//                        .outputGuardrails(new RetryOutputGuardrail()) // 添加输出护轨，为了流式输出，这里不使用
                        .build();
            }
            default ->
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType.getValue());
        };
    }

    /**
     * 创建 AI 代码生成器服务
     *
     * @return
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0);
    }

    /**
     * 构造缓存键
     *
     * @param appId
     * @param codeGenType
     * @return
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType) {
        return buildCacheKey(appId, codeGenType, AiModelEnum.DEFAULT_MODEL_KEY);
    }

    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType, String modelKey) {
        return appId + "_" + codeGenType.getValue() + "_" + AiModelEnum.normalize(modelKey);
    }

    private ChatModel createChatModel(String modelKey) {
        return OpenAiChatModel.builder()
                .apiKey(streamingChatModelConfig.getApiKey())
                .baseUrl(streamingChatModelConfig.getBaseUrl())
                .modelName(AiModelEnum.normalize(modelKey))
                .maxTokens(streamingChatModelConfig.getMaxTokens())
                .temperature(streamingChatModelConfig.getTemperature())
                .logRequests(streamingChatModelConfig.isLogRequests())
                .logResponses(streamingChatModelConfig.isLogResponses())
                .build();
    }

    private StreamingChatModel createStreamingChatModel(String modelKey) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(streamingChatModelConfig.getApiKey())
                .baseUrl(streamingChatModelConfig.getBaseUrl())
                .modelName(AiModelEnum.normalize(modelKey))
                .maxTokens(streamingChatModelConfig.getMaxTokens())
                .temperature(streamingChatModelConfig.getTemperature())
                .logRequests(streamingChatModelConfig.isLogRequests())
                .logResponses(streamingChatModelConfig.isLogResponses())
                .build();
    }

    private StreamingChatModel createReasoningStreamingChatModel(String modelKey) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(reasoningStreamingChatModelConfig.getApiKey())
                .baseUrl(reasoningStreamingChatModelConfig.getBaseUrl())
                .modelName(AiModelEnum.normalize(modelKey))
                .maxTokens(reasoningStreamingChatModelConfig.getMaxTokens())
                .temperature(reasoningStreamingChatModelConfig.getTemperature())
                .logRequests(reasoningStreamingChatModelConfig.getLogRequests())
                .logResponses(reasoningStreamingChatModelConfig.getLogResponses())
                .build();
    }
}
