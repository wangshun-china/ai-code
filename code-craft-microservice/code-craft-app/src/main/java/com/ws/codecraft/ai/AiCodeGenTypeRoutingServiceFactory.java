package com.ws.codecraft.ai;

import cn.hutool.core.util.StrUtil;
import com.ws.codecraft.ai.config.RoutingAiModelConfig;
import com.ws.codecraft.core.AiCallHelper;
import com.ws.codecraft.model.enums.AiModelEnum;
import com.ws.codecraft.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
public class AiCodeGenTypeRoutingServiceFactory {

    private static final String ROUTING_SYSTEM_PROMPT = """
            你是一个代码生成类型路由专家。根据用户需求，判断应该使用哪种代码生成类型。
            只需回复以下三个选项之一，不要输出任何其他内容：
            - HTML：适合简单的静态页面，单个 HTML 文件，包含内联 CSS 和 JS
            - MULTI_FILE：适合简单的多文件静态页面，分离 HTML、CSS、JS 代码
            - VUE_PROJECT：适合复杂的现代化前端项目，涉及多页面、复杂交互、数据管理等
            """;

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private RoutingAiModelConfig routingAiModelConfig;

    @Resource
    private AiModelFallbackRouter aiModelFallbackRouter;

    @Resource
    private AiCallHelper callHelper;

    public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService() {
        return createAiCodeGenTypeRoutingService(routingAiModelConfig.getModelName());
    }

    public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService(String modelKey) {
        String normalizedModelKey = AiModelEnum.normalize(modelKey);
        return userMessage -> routeWithFallback(normalizedModelKey, userMessage);
    }

    @Bean
    public AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService() {
        return createAiCodeGenTypeRoutingService();
    }

    private CodeGenTypeEnum routeWithFallback(String primaryModelKey, String userMessage) {
        CodeGenTypeEnum deterministicResult = routeByExplicitUserIntent(userMessage);
        if (deterministicResult != null) {
            return deterministicResult;
        }

        List<String> candidates = aiModelFallbackRouter.resolveCandidates(primaryModelKey);
        RuntimeException lastException = null;
        for (int i = 0; i < candidates.size(); i++) {
            String candidate = candidates.get(i);
            try {
                return routeWithChat(candidate, userMessage);
            } catch (RuntimeException e) {
                lastException = e;
                if (!aiModelFallbackRouter.isQuotaExceeded(e) || i == candidates.size() - 1) {
                    throw e;
                }
                log.warn("路由模型额度不足，自动切换备用模型: from={}, to={}", candidate, candidates.get(i + 1));
            }
        }
        throw lastException;
    }

    private CodeGenTypeEnum routeByExplicitUserIntent(String userMessage) {
        String normalized = StrUtil.blankToDefault(userMessage, "").toLowerCase();
        if (StrUtil.isBlank(normalized)) {
            return null;
        }
        if (StrUtil.containsAny(normalized, "vue项目", "vue 工程", "vue工程", "vue project",
                "vue3", "vite", "单文件组件", ".vue", "sfc")) {
            return CodeGenTypeEnum.VUE_PROJECT;
        }
        if (StrUtil.containsAny(normalized, "多文件", "分离 html css js", "分离html css js",
                "html css js 分离", "css 和 js 分离")) {
            return CodeGenTypeEnum.MULTI_FILE;
        }
        if (StrUtil.containsAny(normalized, "原生html", "原生 html", "单个html", "单个 html",
                "一个html", "一个 html")) {
            return CodeGenTypeEnum.HTML;
        }
        return null;
    }

    private CodeGenTypeEnum routeWithChat(String modelKey, String userMessage) {
        String normalizedKey = AiModelEnum.normalize(modelKey);
        ChatClient chatClient = aiCodeGeneratorServiceFactory.createChatClient(normalizedKey);

        String response;
        try {
            response = callHelper.call(chatClient, ROUTING_SYSTEM_PROMPT, userMessage, normalizedKey);
        } catch (Exception e) {
            log.error("路由调用失败, model={}", normalizedKey, e);
            throw new RuntimeException("代码类型路由失败: " + e.getMessage(), e);
        }

        if (StrUtil.isBlank(response)) {
            log.warn("路由模型返回为空，默认使用 HTML");
            return CodeGenTypeEnum.HTML;
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
        log.warn("路由模型返回无法识别，默认使用 HTML: {}", responseText);
        return CodeGenTypeEnum.HTML;
    }
}
