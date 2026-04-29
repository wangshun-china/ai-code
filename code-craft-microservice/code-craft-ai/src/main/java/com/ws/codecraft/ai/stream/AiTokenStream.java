package com.ws.codecraft.ai.stream;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Project-owned streaming contract for AI generation.
 */
public interface AiTokenStream {

    AiTokenStream onPartialResponse(Consumer<String> partialResponseHandler);

    AiTokenStream onToolRequest(BiConsumer<Integer, AiToolCallRequest> toolRequestHandler);

    AiTokenStream onToolExecuted(Consumer<AiToolExecution> toolExecutionHandler);

    AiTokenStream onComplete(Consumer<String> completeResponseHandler);

    AiTokenStream onError(Consumer<Throwable> errorHandler);

    AiTokenStream ignoreErrors();

    void start();
}
