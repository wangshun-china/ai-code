package com.ws.codecraft.ai.model;

import lombok.Data;

/**
 * HTML 代码结果
 */
@Data
public class HtmlCodeResult {

    /**
     * HTML 代码
     */
    private String htmlCode;

    /**
     * 描述
     */
    private String description;
}
