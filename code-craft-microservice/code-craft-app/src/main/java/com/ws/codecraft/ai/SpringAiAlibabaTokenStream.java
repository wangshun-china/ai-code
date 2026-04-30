package com.ws.codecraft.ai;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.ws.codecraft.ai.monitor.AiModelMonitorListener;
import com.ws.codecraft.ai.monitor.AiModelMonitorListener.SpringAiUsageTrace;
import com.ws.codecraft.ai.stream.AiTokenStream;
import com.ws.codecraft.ai.stream.AiToolCallRequest;
import com.ws.codecraft.ai.stream.AiToolExecution;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.Disposable;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Tool-enabled stream backed by Spring AI Alibaba streaming calls.
 */
public class SpringAiAlibabaTokenStream implements AiTokenStream {

    private final DashScopeChatModel chatModel;
    private final List<Message> messages;
    private final DashScopeChatOptions baseOptions;
    private final long appId;
    private final String modelName;
    private final String promptText;
    private final AiModelMonitorListener aiModelMonitorListener;
    private final SpringAiToolCallbackRegistry toolCallbackRegistry;
    private final AtomicBoolean ignoreErrors = new AtomicBoolean(false);

    private Consumer<String> partialResponseHandler;
    private BiConsumer<Integer, AiToolCallRequest> toolRequestHandler;
    private Consumer<AiToolExecution> toolExecutionHandler;
    private Consumer<String> completeResponseHandler;
    private Consumer<Throwable> errorHandler;

    public SpringAiAlibabaTokenStream(DashScopeChatModel chatModel,
                                      List<Message> messages,
                                      DashScopeChatOptions baseOptions,
                                      long appId,
                                      String modelName,
                                      String promptText,
                                      AiModelMonitorListener aiModelMonitorListener,
                                      SpringAiToolCallbackRegistry toolCallbackRegistry) {
        this.chatModel = chatModel;
        this.messages = messages;
        this.baseOptions = baseOptions;
        this.appId = appId;
        this.modelName = modelName;
        this.promptText = promptText;
        this.aiModelMonitorListener = aiModelMonitorListener;
        this.toolCallbackRegistry = toolCallbackRegistry;
    }

    @Override
    public AiTokenStream onPartialResponse(Consumer<String> partialResponseHandler) {
        this.partialResponseHandler = partialResponseHandler;
        return this;
    }

    @Override
    public AiTokenStream onToolRequest(BiConsumer<Integer, AiToolCallRequest> toolRequestHandler) {
        this.toolRequestHandler = toolRequestHandler;
        return this;
    }

    @Override
    public AiTokenStream onToolExecuted(Consumer<AiToolExecution> toolExecuteHandler) {
        this.toolExecutionHandler = toolExecuteHandler;
        return this;
    }

    @Override
    public AiTokenStream onComplete(Consumer<String> completeResponseHandler) {
        this.completeResponseHandler = completeResponseHandler;
        return this;
    }

    @Override
    public AiTokenStream onError(Consumer<Throwable> errorHandler) {
        this.errorHandler = errorHandler;
        return this;
    }

    @Override
    public AiTokenStream ignoreErrors() {
        this.ignoreErrors.set(true);
        return this;
    }

    @Override
    public void start() {
        if (partialResponseHandler == null) {
            throw new IllegalStateException("onPartialResponse must be configured before start");
        }
        DashScopeChatOptions options = DashScopeChatOptions.fromOptions(baseOptions);
        options.setToolCallbacks(toolCallbackRegistry.buildVueProjectToolCallbacks(appId,
                toolRequestHandler, toolExecutionHandler));
        options.setInternalToolExecutionEnabled(true);
        options.setParallelToolCalls(false);

        AtomicReference<SpringAiUsageTrace> traceRef = new AtomicReference<>();
        AtomicReference<Usage> usageRef = new AtomicReference<>();
        StringBuilder responseBuilder = new StringBuilder();
        Disposable ignored = chatModel.stream(new Prompt(messages, options))
                .doOnSubscribe(subscription ->
                        traceRef.set(aiModelMonitorListener.startSpringAiRequest(modelName, promptText)))
                .subscribe(response -> {
                    if (response != null && response.getMetadata() != null) {
                        usageRef.set(response.getMetadata().getUsage());
                    }
                    String chunk = extractText(response);
                    if (StrUtil.isNotBlank(chunk)) {
                        responseBuilder.append(chunk);
                        partialResponseHandler.accept(chunk);
                    }
                }, error -> {
                    aiModelMonitorListener.recordSpringAiError(traceRef.get(), error);
                    if (!ignoreErrors.get() && errorHandler != null) {
                        errorHandler.accept(error);
                    }
                }, () -> {
                    Usage usage = usageRef.get();
                    aiModelMonitorListener.recordSpringAiSuccess(traceRef.get(), responseBuilder.toString(),
                            usage == null ? null : usage.getPromptTokens(),
                            usage == null ? null : usage.getCompletionTokens(),
                            usage == null ? null : usage.getTotalTokens());
                    if (completeResponseHandler != null) {
                        completeResponseHandler.accept(responseBuilder.toString());
                    }
                });
    }

    private String extractText(org.springframework.ai.chat.model.ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return StrUtil.blankToDefault(response.getResult().getOutput().getText(), "");
    }
}
