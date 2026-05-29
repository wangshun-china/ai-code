package com.ws.codecraft.ai;

import com.ws.codecraft.model.ai.AiModelRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves fallback model order for quota-exhausted AI requests.
 */
@Component
@Slf4j
public class AiModelFallbackRouter {

    private static final String[] FALLBACK_MODEL_ORDER = {
            "qwen3.6-plus", "qwen-plus", "qwen-turbo"
    };

    public List<String> resolveCandidates(String primaryModelKey) {
        String normalized = AiModelRegistry.normalize(primaryModelKey);
        List<String> candidates = new ArrayList<>();
        candidates.add(normalized);
        for (String fallback : FALLBACK_MODEL_ORDER) {
            if (!fallback.equals(normalized) && !candidates.contains(fallback)) {
                candidates.add(fallback);
            }
        }
        return candidates;
    }

    public boolean isQuotaExceeded(Throwable error) {
        if (error == null) {
            return false;
        }
        String message = collectMessages(error);
        return message.contains("AllocationQuota.FreeTierOnly")
                || message.contains("FreeTierOnly")
                || (message.contains("insufficient_quota"))
                || (message.contains("quota") && message.contains("exceeded"));
    }

    private String collectMessages(Throwable error) {
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null) {
                builder.append(msg).append('\n');
            }
            current = current.getCause();
        }
        return builder.toString().toLowerCase();
    }
}
