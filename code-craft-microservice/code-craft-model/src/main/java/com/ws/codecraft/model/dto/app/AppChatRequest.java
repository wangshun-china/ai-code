package com.ws.codecraft.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 普通应用对话请求。
 */
@Data
public class AppChatRequest implements Serializable {

    private Long appId;

    private String message;

    private static final long serialVersionUID = 1L;
}
