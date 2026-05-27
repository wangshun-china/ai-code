package com.ws.codecraft.ai;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;

import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.ws.codecraft.ai.stream.AiTokenStream;
import com.ws.codecraft.ai.stream.AiToolCallRequest;
import com.ws.codecraft.ai.stream.AiToolExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Slf4j
public class SpringAiAlibabaTokenStream implements AiTokenStream {

    private final CompiledGraph compiledGraph;
    private final String userMessage;
    private final long appId;
    private final SpringAiToolCallbackRegistry toolCallbackRegistry;
    private final AtomicBoolean ignoreErrors = new AtomicBoolean(false);

    private Consumer<String> partialResponseHandler;
    private BiConsumer<Integer, AiToolCallRequest> toolRequestHandler;
    private Consumer<AiToolExecution> toolExecutionHandler;
    private Consumer<String> completeResponseHandler;
    private Consumer<Throwable> errorHandler;

    public SpringAiAlibabaTokenStream(CompiledGraph compiledGraph,
                                      String userMessage,
                                      long appId,
                                      SpringAiToolCallbackRegistry toolCallbackRegistry) {
        this.compiledGraph = compiledGraph;
        this.userMessage = userMessage;
        this.appId = appId;
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
    public AiTokenStream onToolExecuted(Consumer<AiToolExecution> toolExecutionHandler) {
        this.toolExecutionHandler = toolExecutionHandler;
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

        toolCallbackRegistry.registerHandlers(appId, toolRequestHandler, toolExecutionHandler);

        RunnableConfig config = RunnableConfig.builder()
                .threadId("app_" + appId)
                .build();

        StringBuilder responseBuilder = new StringBuilder();
        AtomicInteger activeStreams = new AtomicInteger(1);
        AtomicBoolean terminalDelivered = new AtomicBoolean(false);

        Flux<NodeOutput> outputFlux = compiledGraph.stream(Map.<String, Object>of(
                "input", userMessage,
                "plan", userMessage
        ), config);

        Disposable ignored = outputFlux.subscribe(output -> {
                    if (output instanceof InterruptionMetadata interruption) {
                        handleInterruption(config, interruption, responseBuilder, activeStreams, terminalDelivered);
                        return;
                    }
                    if (output instanceof StreamingOutput<?> streamingOutput) {
                        String chunk = extractStreamingText(streamingOutput);
                        if (StrUtil.isNotBlank(chunk)) {
                            responseBuilder.append(chunk);
                            partialResponseHandler.accept(chunk);
                        }
                    }
                }, error -> {
                    boolean firstTerminal = terminalDelivered.compareAndSet(false, true);
                    if (firstTerminal) {
                        toolCallbackRegistry.unregisterHandlers(appId);
                    }
                    if (firstTerminal && !ignoreErrors.get() && errorHandler != null) {
                        errorHandler.accept(error);
                    }
                }, () -> {
                    completeOneStream(responseBuilder, activeStreams, terminalDelivered);
                });
    }

    private static String extractStreamingText(StreamingOutput<?> streamingOutput) {
        Message message = streamingOutput.message();
        return message == null ? "" : StrUtil.blankToDefault(message.getText(), "");
    }

    private static final int MAX_INTERRUPTION_DEPTH = 5;

    private void handleInterruption(RunnableConfig config,
                                    InterruptionMetadata interruption,
                                    StringBuilder responseBuilder,
                                    AtomicInteger activeStreams,
                                    AtomicBoolean terminalDelivered) {
        handleInterruption(config, interruption, responseBuilder, activeStreams, terminalDelivered, 0);
    }

    private void handleInterruption(RunnableConfig config,
                                    InterruptionMetadata interruption,
                                    StringBuilder responseBuilder,
                                    AtomicInteger activeStreams,
                                    AtomicBoolean terminalDelivered,
                                    int depth) {
        if (depth >= MAX_INTERRUPTION_DEPTH) {
            log.warn("Human-in-the-Loop 中断嵌套超过 {} 层，自动放行", MAX_INTERRUPTION_DEPTH);
            return;
        }
        log.info("Human-in-the-Loop 中断: node={}, tools={}", interruption.node(),
                interruption.toolFeedbacks().stream()
                        .map(f -> f.getName() + "=" + f.getResult())
                        .toList());

        InterruptionMetadata approved = approveAll(interruption);
        RunnableConfig resumedConfig = RunnableConfig.builder(config)
                .resume()
                .addHumanFeedback(approved)
                .build();

        activeStreams.incrementAndGet();
        compiledGraph.stream((Map<String, Object>) null, resumedConfig)
            .subscribe(output -> {
                if (output instanceof InterruptionMetadata nextInterruption) {
                    handleInterruption(resumedConfig, nextInterruption, responseBuilder,
                            activeStreams, terminalDelivered, depth + 1);
                    return;
                }
                if (output instanceof StreamingOutput<?> streamingOutput) {
                    String chunk = extractStreamingText(streamingOutput);
                    if (StrUtil.isNotBlank(chunk)) {
                        responseBuilder.append(chunk);
                        partialResponseHandler.accept(chunk);
                    }
                }
            }, error -> {
                boolean firstTerminal = terminalDelivered.compareAndSet(false, true);
                if (firstTerminal) {
                    toolCallbackRegistry.unregisterHandlers(appId);
                }
                if (firstTerminal && !ignoreErrors.get() && errorHandler != null) {
                    errorHandler.accept(error);
                }
            }, () -> {
                completeOneStream(responseBuilder, activeStreams, terminalDelivered);
            });
    }

    private void completeOneStream(StringBuilder responseBuilder,
                                   AtomicInteger activeStreams,
                                   AtomicBoolean terminalDelivered) {
        if (activeStreams.decrementAndGet() != 0) {
            return;
        }
        if (terminalDelivered.compareAndSet(false, true)) {
            toolCallbackRegistry.unregisterHandlers(appId);
            if (completeResponseHandler != null) {
                completeResponseHandler.accept(responseBuilder.toString());
            }
        }
    }

    private InterruptionMetadata approveAll(InterruptionMetadata interruption) {
        InterruptionMetadata.Builder builder = InterruptionMetadata.builder(
                interruption.node(), interruption.state());
        interruption.toolFeedbacks().forEach(toolFeedback ->
                builder.addToolFeedback(InterruptionMetadata.ToolFeedback.builder(toolFeedback)
                        .result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED)
                        .build()));
        return builder.build();
    }
}
