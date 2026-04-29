package com.ws.codecraft.ai;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ws.codecraft.ai.config.ReasoningStreamingChatModelConfig;
import com.ws.codecraft.ai.config.StreamingChatModelConfig;
import com.ws.codecraft.ai.monitor.AiModelMonitorListener;
import com.ws.codecraft.ai.monitor.AiModelMonitorListener.SpringAiUsageTrace;
import com.ws.codecraft.ai.tools.ToolManager;
import com.ws.codecraft.exception.BusinessException;
import com.ws.codecraft.exception.ErrorCode;
import com.ws.codecraft.model.enums.AiModelEnum;
import com.ws.codecraft.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;

import java.time.Duration;

/**
 * AI 服务创建工厂。springaialibaba 分支的模型请求统一走 Spring AI Alibaba
 * DashScopeChatModel，保留原业务接口以降低迁移风险。
 */
@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    @Resource
    private StreamingChatModelConfig streamingChatModelConfig;

    @Resource
    private ReasoningStreamingChatModelConfig reasoningStreamingChatModelConfig;

    @Resource
    private ToolManager toolManager;

    @Resource
    private AiModelMonitorListener aiModelMonitorListener;

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
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(codeGenType, normalizedModelKey));
    }

    /**
     * 普通聊天模型不挂载任何写文件工具，避免聊天误触发代码覆盖。
     */
    public String chatPlain(String message, String modelKey) {
        String normalizedModelKey = AiModelEnum.normalize(modelKey);
        DashScopeChatModel model = createChatModel(normalizedModelKey, streamingChatModelConfig.getApiKey(),
                streamingChatModelConfig.getBaseUrl(), streamingChatModelConfig.getMaxTokens(),
                streamingChatModelConfig.getTemperature(), false);
        DashScopeChatOptions options = createOptions(normalizedModelKey, streamingChatModelConfig.getMaxTokens(),
                streamingChatModelConfig.getTemperature(), false);
        SpringAiUsageTrace trace = aiModelMonitorListener.startSpringAiRequest(normalizedModelKey, message);
        try {
            ChatResponse response = model.call(new Prompt(new UserMessage(message), options));
            String responseText = extractText(response);
            Usage usage = response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
            aiModelMonitorListener.recordSpringAiSuccess(trace, responseText,
                    usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens(),
                    usage == null ? null : usage.getTotalTokens());
            return responseText;
        } catch (RuntimeException e) {
            aiModelMonitorListener.recordSpringAiError(trace, e);
            throw e;
        }
    }

    public String chatWithImage(String message, byte[] imageBytes, String mimeType, String fileName, String modelKey) {
        String normalizedModelKey = AiModelEnum.normalize(modelKey);
        DashScopeChatModel model = createChatModel(normalizedModelKey, streamingChatModelConfig.getApiKey(),
                streamingChatModelConfig.getBaseUrl(), streamingChatModelConfig.getMaxTokens(),
                streamingChatModelConfig.getTemperature(), false);
        DashScopeChatOptions options = createOptions(normalizedModelKey, streamingChatModelConfig.getMaxTokens(),
                streamingChatModelConfig.getTemperature(), false);
        UserMessage userMessage = UserMessage.builder()
                .text(message)
                .media(Media.builder()
                        .mimeType(MimeTypeUtils.parseMimeType(StrUtil.blankToDefault(mimeType, "image/png")))
                        .data(new ByteArrayResource(imageBytes) {
                            @Override
                            public String getFilename() {
                                return StrUtil.blankToDefault(fileName, "upload.png");
                            }
                        })
                        .build())
                .build();
        SpringAiUsageTrace trace = aiModelMonitorListener.startSpringAiRequest(normalizedModelKey, message);
        try {
            ChatResponse response = model.call(new Prompt(userMessage, options));
            String responseText = extractText(response);
            Usage usage = response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
            aiModelMonitorListener.recordSpringAiSuccess(trace, responseText,
                    usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens(),
                    usage == null ? null : usage.getTotalTokens());
            return responseText;
        } catch (RuntimeException e) {
            aiModelMonitorListener.recordSpringAiError(trace, e);
            throw e;
        }
    }

    private AiCodeGeneratorService createAiCodeGeneratorService(CodeGenTypeEnum codeGenType, String modelKey) {
        log.info("创建 Spring AI Alibaba AI 服务实例, type={}, model={}", codeGenType, modelKey);
        if (codeGenType == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        DashScopeChatModel chatModel = createChatModel(modelKey, streamingChatModelConfig.getApiKey(),
                streamingChatModelConfig.getBaseUrl(), streamingChatModelConfig.getMaxTokens(),
                streamingChatModelConfig.getTemperature(), false);
        DashScopeChatOptions chatOptions = createOptions(modelKey, streamingChatModelConfig.getMaxTokens(),
                streamingChatModelConfig.getTemperature(), false);
        DashScopeChatModel reasoningChatModel = createChatModel(modelKey, reasoningStreamingChatModelConfig.getApiKey(),
                reasoningStreamingChatModelConfig.getBaseUrl(), reasoningStreamingChatModelConfig.getMaxTokens(),
                reasoningStreamingChatModelConfig.getTemperature(), true);
        DashScopeChatOptions reasoningOptions = createOptions(modelKey, reasoningStreamingChatModelConfig.getMaxTokens(),
                reasoningStreamingChatModelConfig.getTemperature(), true);
        return switch (codeGenType) {
            case HTML, MULTI_FILE, VUE_PROJECT -> new SpringAiAlibabaCodeGeneratorService(
                    chatModel, reasoningChatModel, chatOptions, reasoningOptions, modelKey,
                    aiModelMonitorListener, toolManager);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "不支持的代码生成类型: " + codeGenType.getValue());
        };
    }

    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0);
    }

    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType, String modelKey) {
        return appId + "_" + codeGenType.getValue() + "_" + AiModelEnum.normalize(modelKey);
    }

    public DashScopeChatModel createChatModel(String modelKey) {
        return createChatModel(AiModelEnum.normalize(modelKey), streamingChatModelConfig.getApiKey(),
                streamingChatModelConfig.getBaseUrl(), streamingChatModelConfig.getMaxTokens(),
                streamingChatModelConfig.getTemperature(), false);
    }

    public DashScopeChatOptions createOptions(String modelKey, Integer maxTokens, Double temperature, boolean streaming) {
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model(AiModelEnum.normalize(modelKey))
                .maxToken(maxTokens)
                .temperature(temperature)
                .incrementalOutput(streaming)
                .build();
        options.setInternalToolExecutionEnabled(false);
        return options;
    }

    private DashScopeChatModel createChatModel(String modelKey, String apiKey, String baseUrl,
                                               Integer maxTokens, Double temperature, boolean streaming) {
        DashScopeApi.Builder apiBuilder = DashScopeApi.builder().apiKey(apiKey);
        if (StrUtil.isNotBlank(baseUrl) && !baseUrl.contains("compatible-mode")) {
            apiBuilder.baseUrl(baseUrl);
        }
        return DashScopeChatModel.builder()
                .dashScopeApi(apiBuilder.build())
                .defaultOptions(createOptions(modelKey, maxTokens, temperature, streaming))
                .build();
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return StrUtil.blankToDefault(response.getResult().getOutput().getText(), "");
    }
}
