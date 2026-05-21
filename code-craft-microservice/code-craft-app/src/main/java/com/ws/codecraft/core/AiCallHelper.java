package com.ws.codecraft.core;

import cn.hutool.core.util.StrUtil;
import com.ws.codecraft.ai.monitor.AiModelMonitorListener;
import com.ws.codecraft.ai.monitor.AiModelMonitorListener.SpringAiUsageTrace;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Reusable helper that wraps ChatClient calls with monitoring (start/record success/error).
 */
@Component
public class AiCallHelper {

    private final AiModelMonitorListener monitor;

    public AiCallHelper(AiModelMonitorListener monitor) {
        this.monitor = monitor;
    }

    public AiModelMonitorListener monitor() {
        return monitor;
    }

    public String call(ChatClient client, String systemPrompt, String userMessage, String modelName) {
        String promptText = buildPromptText(systemPrompt, userMessage);
        SpringAiUsageTrace trace = monitor.startSpringAiRequest(modelName, promptText);
        try {
            ChatResponse response = client.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .chatResponse();
            String text = extractText(response);
            recordSuccess(trace, response, text);
            return text;
        } catch (RuntimeException e) {
            monitor.recordSpringAiError(trace, e);
            throw e;
        }
    }

    public String call(ChatClient client, String userMessage, String modelName) {
        SpringAiUsageTrace trace = monitor.startSpringAiRequest(modelName, userMessage);
        try {
            ChatResponse response = client.prompt()
                    .user(userMessage)
                    .call()
                    .chatResponse();
            String text = extractText(response);
            recordSuccess(trace, response, text);
            return text;
        } catch (RuntimeException e) {
            monitor.recordSpringAiError(trace, e);
            throw e;
        }
    }

    public String callWithImage(ChatClient client, String text, byte[] imageBytes,
                                 String mimeType, String fileName, String modelName) {
        SpringAiUsageTrace trace = monitor.startSpringAiRequest(modelName, text);
        try {
            ChatResponse response = client.prompt()
                    .user(u -> u
                            .text(text)
                            .media(org.springframework.util.MimeTypeUtils.parseMimeType(
                                    StrUtil.blankToDefault(mimeType, "image/png")),
                                    new org.springframework.core.io.ByteArrayResource(imageBytes) {
                                        @Override
                                        public String getFilename() {
                                            return StrUtil.blankToDefault(fileName, "upload.png");
                                        }
                                    }))
                    .call()
                    .chatResponse();
            String responseText = extractText(response);
            recordSuccess(trace, response, responseText);
            return responseText;
        } catch (RuntimeException e) {
            monitor.recordSpringAiError(trace, e);
            throw e;
        }
    }

    public Flux<String> stream(ChatClient client, String systemPrompt, String userMessage, String modelName) {
        String promptText = buildPromptText(systemPrompt, userMessage);
        AtomicReference<SpringAiUsageTrace> traceRef = new AtomicReference<>();
        AtomicReference<Usage> usageRef = new AtomicReference<>();
        StringBuilder responseBuilder = new StringBuilder();
        return client.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .stream()
                .chatResponse()
                .doOnSubscribe(subscription ->
                        traceRef.set(monitor.startSpringAiRequest(modelName, promptText)))
                .map(response -> {
                    if (response != null && response.getMetadata() != null) {
                        usageRef.set(response.getMetadata().getUsage());
                    }
                    String chunk = extractText(response);
                    responseBuilder.append(StrUtil.blankToDefault(chunk, ""));
                    return chunk;
                })
                .filter(StrUtil::isNotBlank)
                .doOnError(error -> monitor.recordSpringAiError(traceRef.get(), error))
                .doOnComplete(() -> {
                    Usage usage = usageRef.get();
                    monitor.recordSpringAiSuccess(traceRef.get(), responseBuilder.toString(),
                            usage == null ? null : usage.getPromptTokens(),
                            usage == null ? null : usage.getCompletionTokens(),
                            usage == null ? null : usage.getTotalTokens());
                });
    }

    private void recordSuccess(SpringAiUsageTrace trace, ChatResponse response, String responseText) {
        Usage usage = response != null && response.getMetadata() != null
                ? response.getMetadata().getUsage() : null;
        monitor.recordSpringAiSuccess(trace, responseText,
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                usage == null ? null : usage.getTotalTokens());
    }

    private static String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return StrUtil.blankToDefault(response.getResult().getOutput().getText(), "");
    }

    public static String loadPrompt(String resourcePath) {
        try {
            return StreamUtils.copyToString(new ClassPathResource(resourcePath).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取系统提示词失败: " + resourcePath, e);
        }
    }

    private static String buildPromptText(String system, String user) {
        if (StrUtil.isNotBlank(system)) {
            return "system: " + system + "\n\nuser: " + user;
        }
        return "user: " + user;
    }
}