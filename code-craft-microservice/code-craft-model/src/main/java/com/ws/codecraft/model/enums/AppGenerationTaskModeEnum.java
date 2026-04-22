package com.ws.codecraft.model.enums;

import lombok.Getter;

/**
 * AI 任务模式。
 */
@Getter
public enum AppGenerationTaskModeEnum {

    CHAT("chat", "普通聊天"),
    PLAN("plan", "方案生成"),
    GENERATE("generate", "代码生成");

    private final String value;

    private final String text;

    AppGenerationTaskModeEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }
}
