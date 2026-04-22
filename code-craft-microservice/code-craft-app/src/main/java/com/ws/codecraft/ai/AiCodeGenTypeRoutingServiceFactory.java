package com.ws.codecraft.ai;

import com.ws.codecraft.ai.config.RoutingAiModelConfig;
import com.ws.codecraft.model.enums.AiModelEnum;
import com.ws.codecraft.utils.SpringContextUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI代码生成类型路由服务工厂
 *
 * @author ws
 */
@Slf4j
@Configuration
public class AiCodeGenTypeRoutingServiceFactory {

    @Resource
    private RoutingAiModelConfig routingAiModelConfig;

    /**
     * 创建AI代码生成类型路由服务实例
     */
    public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService() {
        ChatModel chatModel = SpringContextUtil.getBean("routingChatModelPrototype", ChatModel.class);
        return AiServices.builder(AiCodeGenTypeRoutingService.class)
                .chatModel(chatModel)
                .build();
    }

    public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService(String modelKey) {
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(routingAiModelConfig.getApiKey())
                .baseUrl(routingAiModelConfig.getBaseUrl())
                .modelName(AiModelEnum.normalize(modelKey))
                .maxTokens(routingAiModelConfig.getMaxTokens())
                .temperature(routingAiModelConfig.getTemperature())
                .logRequests(routingAiModelConfig.getLogRequests())
                .logResponses(routingAiModelConfig.getLogResponses())
                .build();
        return AiServices.builder(AiCodeGenTypeRoutingService.class)
                .chatModel(chatModel)
                .build();
    }

    /**
     * 默认提供一个 Bean
     */
    @Bean
    public AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService() {
        return createAiCodeGenTypeRoutingService();
    }
}
