package com.ws.codecraft.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 模型凭据配置视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiModelCredentialVO {

    private Long id;

    private String name;

    private String apiKey;

    private String baseUrl;

    private List<String> modelNames;

    private Boolean defaultSelected;

    private Boolean systemDefault;
}
