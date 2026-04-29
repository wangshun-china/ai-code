package com.ws.codecraft.ai.model;

import lombok.Data;

/**
 * 多文件代码结果
 */
@Data
public class MultiFileCodeResult {

    /**
     * HTML 代码
     */
    private String htmlCode;

    /**
     * CSS 代码
     */
    private String cssCode;

    /**
     * JS 代码
     */
    private String jsCode;

    /**
     * 描述
     */
    private String description;
}
