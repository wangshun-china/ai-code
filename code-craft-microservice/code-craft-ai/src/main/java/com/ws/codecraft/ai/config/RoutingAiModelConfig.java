package com.ws.codecraft.ai.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 智能路由模型配置
 */
@Configuration
@ConfigurationProperties(prefix = "spring-ai-alibaba.open-ai.routing-chat-model")
@Data
public class RoutingAiModelConfig {

    @ToString.Exclude
    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private boolean logRequests;

    private boolean logResponses;

}
