package com.ws.codecraft.model.enums;

import lombok.Getter;

/**
 * App version source type.
 */
@Getter
public enum AppVersionSourceTypeEnum {

    CREATE("create", "创建"),
    GENERATE("generate", "代码生成"),
    DEPLOY("deploy", "部署"),
    EDIT("edit", "编辑");

    private final String value;
    private final String text;

    AppVersionSourceTypeEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }
}
