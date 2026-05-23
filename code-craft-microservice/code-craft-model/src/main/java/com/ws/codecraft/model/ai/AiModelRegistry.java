package com.ws.codecraft.model.ai;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime model-key registry.
 *
 * <p>Model availability comes from ai_model_credential. This class only keeps
 * runtime metadata needed to resolve custom credential model keys.</p>
 */
public final class AiModelRegistry {

    public static final String DEFAULT_MODEL_KEY = "qwen3.6-plus";
    public static final String ENDPOINT_OPENAI_COMPATIBLE = "OPENAI_COMPATIBLE";

    private static final Map<String, DynamicModel> DYNAMIC_MODELS = new ConcurrentHashMap<>();

    private AiModelRegistry() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_MODEL_KEY;
        }
        return value.trim();
    }

    public static void registerDynamicModel(String modelKey,
                                            String displayName,
                                            boolean multimodal,
                                            String apiKey,
                                            String baseUrl,
                                            String actualModelName) {
        DYNAMIC_MODELS.put(modelKey, new DynamicModel(
                modelKey,
                displayName,
                ENDPOINT_OPENAI_COMPATIBLE,
                multimodal,
                apiKey,
                baseUrl,
                actualModelName));
    }

    public static String getDynamicApiKey(String modelKey) {
        DynamicModel dynamic = DYNAMIC_MODELS.get(modelKey);
        return dynamic != null ? dynamic.apiKey : null;
    }

    public static String getDynamicBaseUrl(String modelKey) {
        DynamicModel dynamic = DYNAMIC_MODELS.get(modelKey);
        return dynamic != null ? dynamic.baseUrl : null;
    }

    public static String getActualModelName(String modelKey) {
        DynamicModel dynamic = modelKey == null ? null : DYNAMIC_MODELS.get(modelKey);
        return dynamic != null ? dynamic.actualModelName : normalize(modelKey);
    }

    public static boolean isDynamicModel(String modelKey) {
        return modelKey != null && DYNAMIC_MODELS.containsKey(modelKey);
    }

    public static List<DynamicModel> getDynamicModels() {
        return List.copyOf(DYNAMIC_MODELS.values());
    }

    public static void removeDynamicModelsByPrefix(String prefix) {
        if (prefix == null) {
            return;
        }
        DYNAMIC_MODELS.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public record DynamicModel(String name,
                               String displayName,
                               String endpoint,
                               boolean multimodal,
                               String apiKey,
                               String baseUrl,
                               String actualModelName) {
    }
}
