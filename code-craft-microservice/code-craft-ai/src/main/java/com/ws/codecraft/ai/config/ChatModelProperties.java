package com.ws.codecraft.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Non-streaming chat model properties used by plan generation and routing-like text tasks.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.chat-model")
public class ChatModelProperties {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private Duration timeout = Duration.ofSeconds(180);

    private Integer maxRetries = 1;

    private boolean logRequests;

    private boolean logResponses;
}
