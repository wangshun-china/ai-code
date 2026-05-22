package com.ws.codecraft.model.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public enum AiModelEnum {

    QWEN_3_7_MAX("qwen3.7-max-2026-05-20", "Qwen3.7 Max", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_3_6_MAX_PREVIEW("qwen3.6-max-preview", "Qwen3.6 Max Preview", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_3_6_PLUS("qwen3.6-plus", "Qwen3.6 Plus", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_3_6_PLUS_20260402("qwen3.6-plus-2026-04-02", "Qwen3.6 Plus 2026-04-02", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_3_5_PLUS("qwen3.5-plus", "Qwen3.5 Plus", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_3_5_PLUS_20260420("qwen3.5-plus-2026-04-20", "Qwen3.5 Plus 2026-04-20", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_3_6_FLASH("qwen3.6-flash", "Qwen3.6 Flash", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_3_6_FLASH_20260416("qwen3.6-flash-2026-04-16", "Qwen3.6 Flash 2026-04-16", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_3_5_FLASH("qwen3.5-flash", "Qwen3.5 Flash", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_3_5_FLASH_20260223("qwen3.5-flash-2026-02-23", "Qwen3.5 Flash 2026-02-23", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_FLASH_CHARACTER("qwen-flash-character-2026-05-20", "Qwen Flash Character", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_3_6_35B_A3B("qwen3.6-35b-a3b", "Qwen3.6 35B A3B", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_3_5_122B_A10B("qwen3.5-122b-a10b", "Qwen3.5 122B A10B", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_3_5_35B_A3B("qwen3.5-35b-a3b", "Qwen3.5 35B A3B", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_3_6_27B("qwen3.6-27b", "Qwen3.6 27B", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_3_5_27B("qwen3.5-27b", "Qwen3.5 27B", ModelEndpoint.DASHSCOPE_NATIVE, true),
    GLM_5_1("glm-5.1", "GLM 5.1", ModelEndpoint.DASHSCOPE_NATIVE, false),
    GUI_PLUS("gui-plus-2026-02-26", "GUI Plus", ModelEndpoint.DASHSCOPE_NATIVE, false),
    DEEPSEEK_V4_PRO("deepseek-v4-pro", "DeepSeek V4 Pro", ModelEndpoint.OPENAI_COMPATIBLE, false),
    DEEPSEEK_V4_FLASH("deepseek-v4-flash", "DeepSeek V4 Flash", ModelEndpoint.OPENAI_COMPATIBLE, false);

    public static final String DEFAULT_MODEL_KEY = "qwen3.6-plus";

    private final String value;
    private final String text;
    private final ModelEndpoint endpoint;
    private final boolean multimodal;

    private static final Map<String, DynamicModel> DYNAMIC_MODELS = new ConcurrentHashMap<>();

    AiModelEnum(String value, String text, ModelEndpoint endpoint, boolean multimodal) {
        this.value = value;
        this.text = text;
        this.endpoint = endpoint;
        this.multimodal = multimodal;
    }

    public static AiModelEnum getEnumByValue(String value) {
        return Arrays.stream(values())
                .filter(item -> item.value.equals(value))
                .findFirst()
                .orElse(null);
    }

    public static String normalize(String value) {
        if (value != null && DYNAMIC_MODELS.containsKey(value)) return value;
        AiModelEnum modelEnum = getEnumByValue(value);
        return modelEnum == null ? DEFAULT_MODEL_KEY : modelEnum.getValue();
    }

    public static ModelEndpoint getEndpoint(String value) {
        if (value != null) {
            DynamicModel dynamic = DYNAMIC_MODELS.get(value);
            if (dynamic != null) return dynamic.endpoint;
        }
        AiModelEnum modelEnum = getEnumByValue(normalize(value));
        return modelEnum == null ? ModelEndpoint.DASHSCOPE_NATIVE : modelEnum.getEndpoint();
    }

    public static boolean isMultimodal(String value) {
        if (value != null) {
            DynamicModel dynamic = DYNAMIC_MODELS.get(value);
            if (dynamic != null) return dynamic.multimodal;
        }
        AiModelEnum modelEnum = getEnumByValue(normalize(value));
        return modelEnum != null && modelEnum.isMultimodal();
    }

    public static void registerDynamicModel(String modelName, String displayName,
                                             ModelEndpoint endpoint, boolean multimodal,
                                             String apiKey) {
        DYNAMIC_MODELS.put(modelName, new DynamicModel(modelName, displayName, endpoint, multimodal, apiKey));
    }

    public static String getDynamicApiKey(String modelName) {
        DynamicModel dynamic = DYNAMIC_MODELS.get(modelName);
        return dynamic != null ? dynamic.apiKey : null;
    }

    public static boolean isDynamicModel(String modelName) {
        return modelName != null && DYNAMIC_MODELS.containsKey(modelName);
    }

    public static List<DynamicModel> getDynamicModels() {
        return List.copyOf(DYNAMIC_MODELS.values());
    }

    public static void removeDynamicModel(String modelName) {
        DYNAMIC_MODELS.remove(modelName);
    }

    public record DynamicModel(String name, String displayName, ModelEndpoint endpoint,
                               boolean multimodal, String apiKey) {
    }

    public enum ModelEndpoint {
        DASHSCOPE_NATIVE,
        OPENAI_COMPATIBLE
    }
}
