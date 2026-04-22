package com.ws.codecraft.model.enums;

import lombok.Getter;

/**
 * AI 任务状态。
 */
@Getter
public enum AppGenerationTaskStatusEnum {

    PENDING("pending", "等待中"),
    RUNNING("running", "执行中"),
    SUCCESS("success", "成功"),
    FAILED("failed", "失败"),
    CANCELED("canceled", "已取消");

    private final String value;

    private final String text;

    AppGenerationTaskStatusEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }
}
