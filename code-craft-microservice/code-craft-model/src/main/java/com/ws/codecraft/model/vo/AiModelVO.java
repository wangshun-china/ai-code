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
    private boolean multimodal;
    private boolean custom;
}
