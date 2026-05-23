package com.ws.codecraft.ai;

import cn.hutool.core.util.StrUtil;
import com.ws.codecraft.ai.config.ReasoningStreamingChatModelConfig;
import com.ws.codecraft.ai.config.StreamingChatModelConfig;
import com.ws.codecraft.model.ai.AiModelRegistry;
import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.Resource;
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
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 统一创建 OpenAI-compatible ChatClient/ChatModel。
 */
@Component
public class AiChatClientFactory {

    private static final int CHAT_MEMORY_MAX_MESSAGES = 20;
    private static final String DASHSCOPE_COMPATIBLE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    @Resource
    private StreamingChatModelConfig streamingChatModelConfig;

    @Resource
    private ReasoningStreamingChatModelConfig reasoningStreamingChatModelConfig;

    @Resource
    private ChatMemoryRepository chatMemoryRepository;

    public Integer defaultMaxTokens() {
        return streamingChatModelConfig.getMaxTokens();
    }

    public Double defaultTemperature() {
        return streamingChatModelConfig.getTemperature();
    }

    public ChatClient createChatClient(String modelKey) {
        return createChatClient(AiModelRegistry.normalize(modelKey),
                defaultMaxTokens(), defaultTemperature(), false);
    }

    public ChatClient createChatClient(String modelKey, Integer maxTokens, Double temperature, boolean streaming) {
        String normalizedKey = AiModelRegistry.normalize(modelKey);
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(CHAT_MEMORY_MAX_MESSAGES)
                .build();
        return ChatClient.builder(buildChatModel(normalizedKey, maxTokens, temperature))
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultOptions(buildOptions(normalizedKey, maxTokens, temperature, streaming))
                .build();
    }

    public ChatModel buildReasoningChatModel(String modelKey) {
        String normalizedKey = AiModelRegistry.normalize(modelKey);
        String apiKey = resolveApiKey(normalizedKey);
        String baseUrl = resolveBaseUrl(normalizedKey);
        OpenAiChatOptions options = buildOpenAiOptions(normalizedKey,
                reasoningStreamingChatModelConfig.getMaxTokens(),
                reasoningStreamingChatModelConfig.getTemperature());
        options.setInternalToolExecutionEnabled(false);
        return buildOpenAiChatModel(options, apiKey, baseUrl);
    }

    private ChatModel buildChatModel(String modelKey, Integer maxTokens, Double temperature) {
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
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        if (temperature != null) {
            builder.temperature(temperature);
        }
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
}
