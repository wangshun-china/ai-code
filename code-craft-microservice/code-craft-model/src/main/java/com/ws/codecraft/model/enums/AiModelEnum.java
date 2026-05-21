package com.ws.codecraft.model.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * Supported AI model whitelist.
 */
@Getter
public enum AiModelEnum {

    QWEN_3_6_PLUS("qwen3.6-plus", "Qwen3.6 Plus", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_3_6_PLUS_20260402("qwen3.6-plus-2026-04-02", "Qwen3.6 Plus 2026-04-02", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_3_6_MAX_PREVIEW("qwen3.6-max-preview", "Qwen3.6 Max Preview", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_3_6_FLASH("qwen3.6-flash", "Qwen3.6 Flash", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_3_6_35B_A3B("qwen3.6-35b-a3b", "Qwen3.6 35B A3B", ModelEndpoint.DASHSCOPE_NATIVE, true),
    QWEN_3_5_PLUS_20260215("qwen3.5-plus-2026-02-15", "Qwen3.5 Plus 2026-02-15", ModelEndpoint.DASHSCOPE_NATIVE, true),
    KIMI_K2_6("kimi-k2.6", "Kimi K2.6", ModelEndpoint.DASHSCOPE_NATIVE, false),
    KIMI_K2_5("kimi-k2.5", "Kimi K2.5", ModelEndpoint.DASHSCOPE_NATIVE, false),
    MINIMAX_M2_1("MiniMax-M2.1", "MiniMax M2.1", ModelEndpoint.DASHSCOPE_NATIVE, false),
    DEEPSEEK_V4_PRO("deepseek-v4-pro", "DeepSeek V4 Pro", ModelEndpoint.OPENAI_COMPATIBLE, false),
    DEEPSEEK_V4_FLASH("deepseek-v4-flash", "DeepSeek V4 Flash", ModelEndpoint.OPENAI_COMPATIBLE, false);

    public static final String DEFAULT_MODEL_KEY = "qwen3.6-plus";

    private final String value;

    private final String text;

    private final ModelEndpoint endpoint;

    private final boolean multimodal;

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
        AiModelEnum modelEnum = getEnumByValue(value);
        return modelEnum == null ? DEFAULT_MODEL_KEY : modelEnum.getValue();
    }

    public static ModelEndpoint getEndpoint(String value) {
        AiModelEnum modelEnum = getEnumByValue(normalize(value));
        return modelEnum == null ? ModelEndpoint.DASHSCOPE_NATIVE : modelEnum.getEndpoint();
    }

    public static boolean isMultimodal(String value) {
        AiModelEnum modelEnum = getEnumByValue(normalize(value));
        return modelEnum != null && modelEnum.isMultimodal();
    }

    public enum ModelEndpoint {
        DASHSCOPE_NATIVE,
        OPENAI_COMPATIBLE
    }
}
