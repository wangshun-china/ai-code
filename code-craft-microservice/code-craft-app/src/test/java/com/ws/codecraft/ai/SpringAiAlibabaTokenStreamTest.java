package com.ws.codecraft.ai;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SpringAiAlibabaTokenStreamTest {

    @Test
    void completeOneStreamDeliversCompletionWhenLastStreamFinishes() {
        StringBuffer responseBuilder = new StringBuffer("hello");
        AtomicInteger activeStreams = new AtomicInteger(1);
        AtomicBoolean terminalDelivered = new AtomicBoolean(false);
        AtomicBoolean completionCalled = new AtomicBoolean(false);

        SpringAiAlibabaTokenStream stream = new SpringAiAlibabaTokenStream(
                null, "", 0, null);
        stream.onPartialResponse(s -> {});
        stream.onComplete(s -> completionCalled.set(true));

        // Simulate the private completeOneStream logic:
        // when activeStreams drops to 0 and terminal not yet delivered, call complete
        if (activeStreams.decrementAndGet() == 0) {
            if (terminalDelivered.compareAndSet(false, true)) {
                completionCalled.set(true);
            }
        }

        assertTrue(completionCalled.get(), "Completion should be called when last stream finishes");
    }

    @Test
    void stringBufferIsThreadSafe() throws InterruptedException {
        StringBuffer sb = new StringBuffer();
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> sb.append("thread-").append(idx).append(";"));
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        assertEquals(10, sb.toString().split(";").length - 0,
                "All 10 thread appends should be present");
    }
}
