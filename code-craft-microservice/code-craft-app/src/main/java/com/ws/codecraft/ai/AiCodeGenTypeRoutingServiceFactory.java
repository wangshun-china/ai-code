package com.ws.codecraft.ai;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.ws.codecraft.ai.config.RoutingAiModelConfig;
import com.ws.codecraft.ai.monitor.AiModelMonitorListener;
import com.ws.codecraft.ai.monitor.AiModelMonitorListener.SpringAiUsageTrace;
import com.ws.codecraft.model.enums.AiModelEnum;
import com.ws.codecraft.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * AI 代码生成类型路由服务工厂。
 */
@Slf4j
@Configuration
public class AiCodeGenTypeRoutingServiceFactory {

    private static final String ROUTING_PROMPT = "prompt/codegen-routing-system-prompt.txt";

    @Resource
    private RoutingAiModelConfig routingAiModelConfig;

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private AiModelMonitorListener aiModelMonitorListener;

    public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService() {
        return createAiCodeGenTypeRoutingService(routingAiModelConfig.getModelName());
    }

    public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService(String modelKey) {
        String normalizedModelKey = AiModelEnum.normalize(modelKey);
        DashScopeChatModel chatModel = aiCodeGeneratorServiceFactory.createChatModel(normalizedModelKey);
        DashScopeChatOptions options = aiCodeGeneratorServiceFactory.createOptions(normalizedModelKey,
                routingAiModelConfig.getMaxTokens(), routingAiModelConfig.getTemperature(), false);
        return userMessage -> route(chatModel, options, normalizedModelKey, userMessage);
    }

    @Bean
    public AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService() {
        return createAiCodeGenTypeRoutingService();
    }

    private CodeGenTypeEnum route(DashScopeChatModel chatModel, DashScopeChatOptions options,
                                  String modelName, String userMessage) {
        List<Message> messages = List.of(
                new SystemMessage(loadPrompt()),
                new UserMessage(userMessage)
        );
        String promptText = messages.get(0).getText() + "\n\n" + userMessage;
        SpringAiUsageTrace trace = aiModelMonitorListener.startSpringAiRequest(modelName, promptText);
        try {
            ChatResponse response = chatModel.call(new Prompt(messages, options));
            String responseText = extractText(response);
            Usage usage = response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
            aiModelMonitorListener.recordSpringAiSuccess(trace, responseText,
                    usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens(),
                    usage == null ? null : usage.getTotalTokens());
            return parseCodeGenType(responseText);
        } catch (RuntimeException e) {
            aiModelMonitorListener.recordSpringAiError(trace, e);
            throw e;
        }
    }

    private CodeGenTypeEnum parseCodeGenType(String responseText) {
        String normalized = StrUtil.blankToDefault(responseText, "").trim().toUpperCase();
        if (normalized.contains(CodeGenTypeEnum.VUE_PROJECT.name())) {
            return CodeGenTypeEnum.VUE_PROJECT;
        }
        if (normalized.contains(CodeGenTypeEnum.MULTI_FILE.name())) {
            return CodeGenTypeEnum.MULTI_FILE;
        }
        if (normalized.contains(CodeGenTypeEnum.HTML.name())) {
            return CodeGenTypeEnum.HTML;
        }
        log.warn("路由模型返回无法识别，默认使用 HTML: {}", responseText);
        return CodeGenTypeEnum.HTML;
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return StrUtil.blankToDefault(response.getResult().getOutput().getText(), "");
    }

    private String loadPrompt() {
        try {
            return StreamUtils.copyToString(new ClassPathResource(ROUTING_PROMPT).getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取路由系统提示词失败", e);
        }
    }
}
