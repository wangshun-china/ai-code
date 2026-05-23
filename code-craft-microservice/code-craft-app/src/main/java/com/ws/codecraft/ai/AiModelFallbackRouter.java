package com.ws.codecraft.ai;

import com.ws.codecraft.model.ai.AiModelRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves fallback model order for quota-exhausted AI requests.
 */
@Component
public class AiModelFallbackRouter {

    public List<String> resolveCandidates(String primaryModelKey) {
        return List.of(AiModelRegistry.normalize(primaryModelKey));
    }

    public boolean isQuotaExceeded(Throwable error) {
        String message = collectMessages(error);
        String lowerMessage = message.toLowerCase();
        return message.contains("AllocationQuota.FreeTierOnly")
                || message.contains("FreeTierOnly")
                || lowerMessage.contains("insufficient_quota")
                || lowerMessage.contains("quota")
                || message.contains("403");
    }

    private String collectMessages(Throwable error) {
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            builder.append(current.getClass().getSimpleName()).append(':')
                    .append(current.getMessage()).append('\n');
            current = current.getCause();
        }
        return builder.toString();
    }
}
