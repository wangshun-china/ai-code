package com.ws.codecraft.ai;

import cn.hutool.core.util.StrUtil;
import com.ws.codecraft.ai.config.RoutingAiModelConfig;
import com.ws.codecraft.core.AiCallHelper;
import com.ws.codecraft.model.ai.AiModelRegistry;
import com.ws.codecraft.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AiCodeGenTypeRoutingServiceFactory {

    private static final String ROUTING_SYSTEM_PROMPT = """
            你是一个代码生成类型路由专家。根据用户需求，判断应该使用哪种代码生成类型。
            只需回复以下三个选项之一，不要输出任何其他内容：

            - HTML：单文件页面，所有代码内联在一个 HTML 中，可用 CDN 引入库。适用于：落地页、简单工具页、单功能页面。
            - MULTI_FILE：多文件静态页面，HTML/CSS/JS 分离，可用 CDN。适用于：需要代码分离但无需构建的中小型页面。
            - VUE_PROJECT：Vue 3 + Vite 工程化项目，使用 .vue 单文件组件、Vue Router、npm 依赖管理。适用于：只要提到 vue、组件、路由、多页面、工程化，都选此项。即便是"简单的 Vue"，也应走 VUE_PROJECT 而非 HTML+CDN。

            判断优先级：
            1. 提到 vue、vue3、vite、组件、路由 → VUE_PROJECT
            2. 明确要求单个 HTML 文件、CDN、无构建 → HTML
            3. 需要多文件但不用构建工具 → MULTI_FILE
            4. 拿不准时默认 VUE_PROJECT（它是最灵活的方案，能覆盖 HTML 的所有能力）
            """;

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private RoutingAiModelConfig routingAiModelConfig;

    @Resource
    private AiCallHelper callHelper;

    public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService() {
        return createAiCodeGenTypeRoutingService(routingAiModelConfig.getModelName());
    }

    public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService(String modelKey) {
        String normalizedModelKey = AiModelRegistry.normalize(modelKey);
        return userMessage -> route(normalizedModelKey, userMessage);
    }

    @Bean
    public AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService() {
        return createAiCodeGenTypeRoutingService();
    }

    private CodeGenTypeEnum route(String modelKey, String userMessage) {
        CodeGenTypeEnum deterministicResult = routeByExplicitUserIntent(userMessage);
        if (deterministicResult != null) {
            return deterministicResult;
        }

        try {
            return routeWithChat(modelKey, userMessage);
        } catch (RuntimeException e) {
            log.warn("路由模型调用失败，默认使用 VUE_PROJECT: model={}, reason={}", modelKey, e.getMessage());
            return CodeGenTypeEnum.VUE_PROJECT;
        }
    }

    private CodeGenTypeEnum routeByExplicitUserIntent(String userMessage) {
        String normalized = StrUtil.blankToDefault(userMessage, "").toLowerCase();
        if (StrUtil.isBlank(normalized)) {
            return null;
        }
        if (StrUtil.containsAny(normalized, "vue项目", "vue 工程", "vue工程", "vue project",
                "vue3", "vite", "单文件组件", ".vue", "sfc", "vue")) {
            return CodeGenTypeEnum.VUE_PROJECT;
        }
        if (StrUtil.containsAny(normalized, "多文件", "分离 html css js", "分离html css js",
                "html css js 分离", "css 和 js 分离")) {
            return CodeGenTypeEnum.MULTI_FILE;
        }
        if (StrUtil.containsAny(normalized, "html", "html5", "原生html", "原生 html", "单个html", "单个 html",
                "一个html", "一个 html", "简单html", "简单 html")) {
            return CodeGenTypeEnum.HTML;
        }
        return null;
    }

    private CodeGenTypeEnum routeWithChat(String modelKey, String userMessage) {
        String normalizedKey = AiModelRegistry.normalize(modelKey);
        ChatClient chatClient = aiCodeGeneratorServiceFactory.createChatClient(
                normalizedKey,
                routingAiModelConfig.getMaxTokens(),
                routingAiModelConfig.getTemperature(),
                false);

        String response;
        try {
            response = callHelper.call(chatClient, ROUTING_SYSTEM_PROMPT, userMessage, normalizedKey);
        } catch (Exception e) {
            log.error("路由调用失败, model={}", normalizedKey, e);
            throw new RuntimeException("代码类型路由失败: " + e.getMessage(), e);
        }

        if (StrUtil.isBlank(response)) {
            log.warn("路由模型返回为空，默认使用 VUE_PROJECT");
            return CodeGenTypeEnum.VUE_PROJECT;
        }
        return parseCodeGenType(response);
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
        log.warn("路由模型返回无法识别，默认使用 VUE_PROJECT: {}", responseText);
        return CodeGenTypeEnum.VUE_PROJECT;
    }
}
