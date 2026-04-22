package com.ws.codecraft.model.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * Supported AI model whitelist.
 */
@Getter
public enum AiModelEnum {

    QWEN_3_6_PLUS("qwen3.6-plus", "Qwen3.6 Plus"),
    QWEN_3_6_PLUS_20260402("qwen3.6-plus-2026-04-02", "Qwen3.6 Plus 2026-04-02"),
    QWEN_3_6_MAX_PREVIEW("qwen3.6-max-preview", "Qwen3.6 Max Preview"),
    QWEN_3_6_FLASH("qwen3.6-flash", "Qwen3.6 Flash"),
    QWEN_3_6_35B_A3B("qwen3.6-35b-a3b", "Qwen3.6 35B A3B"),
    QWEN_3_5_PLUS_20260215("qwen3.5-plus-2026-02-15", "Qwen3.5 Plus 2026-02-15"),
    KIMI_K2_6("kimi-k2.6", "Kimi K2.6"),
    KIMI_K2_5("kimi-k2.5", "Kimi K2.5"),
    MINIMAX_M2_1("MiniMax-M2.1", "MiniMax M2.1");

    public static final String DEFAULT_MODEL_KEY = "qwen3.6-plus";

    private final String value;

    private final String text;

    AiModelEnum(String value, String text) {
        this.value = value;
        this.text = text;
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
}
