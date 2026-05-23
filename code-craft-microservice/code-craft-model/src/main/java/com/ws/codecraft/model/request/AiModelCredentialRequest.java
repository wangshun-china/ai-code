package com.ws.codecraft.model.request;

import lombok.Data;

import java.util.List;

/**
 * AI 模型凭据配置请求。
 */
@Data
public class AiModelCredentialRequest {

    private Long id;

    private String name;

    private String apiKey;

    private String baseUrl;

    private List<String> modelNames;
}
