package com.ws.codecraft.model.enums;

import lombok.Getter;

/**
 * App lifecycle status.
 */
@Getter
public enum AppStatusEnum {

    DRAFT("draft", "草稿"),
    GENERATING("generating", "生成中"),
    GENERATE_SUCCESS("generate_success", "生成成功"),
    GENERATE_FAILED("generate_failed", "生成失败"),
    BUILDING("building", "构建中"),
    BUILD_SUCCESS("build_success", "构建成功"),
    BUILD_FAILED("build_failed", "构建失败"),
    DEPLOYING("deploying", "部署中"),
    DEPLOY_SUCCESS("deploy_success", "部署成功"),
    DEPLOY_FAILED("deploy_failed", "部署失败"),
    SCREENSHOT_FAILED("screenshot_failed", "截图失败");

    private final String value;
    private final String text;

    AppStatusEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }
}
