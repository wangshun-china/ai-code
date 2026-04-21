package com.ws.codecraft.model.enums;

import lombok.Getter;

/**
 * Deploy task status.
 */
@Getter
public enum AppDeployTaskStatusEnum {

    PENDING("pending", "等待中"),
    RUNNING("running", "执行中"),
    SUCCESS("success", "成功"),
    FAILED("failed", "失败");

    private final String value;
    private final String text;

    AppDeployTaskStatusEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }
}
