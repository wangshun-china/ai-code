package com.ws.codecraft.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiModelVO {
    private String value;
    private String text;
    private String endpoint;
    private String baseUrl;
    private boolean multimodal;
    private boolean custom;

    public AiModelVO(String value, String text, String endpoint, boolean multimodal, boolean custom) {
        this(value, text, endpoint, null, multimodal, custom);
    }
}
