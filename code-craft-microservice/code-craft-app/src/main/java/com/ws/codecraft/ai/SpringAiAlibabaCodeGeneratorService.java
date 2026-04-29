package com.ws.codecraft.ai;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.ws.codecraft.ai.model.HtmlCodeResult;
import com.ws.codecraft.ai.model.MultiFileCodeResult;
import com.ws.codecraft.ai.monitor.AiModelMonitorListener;
import com.ws.codecraft.ai.monitor.AiModelMonitorListener.SpringAiUsageTrace;
import com.ws.codecraft.ai.stream.AiTokenStream;
import com.ws.codecraft.ai.tools.ToolManager;
import com.ws.codecraft.core.parser.CodeParserExecutor;
import com.ws.codecraft.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Spring AI Alibaba implementation for the existing CodeCraft AI service
 * contract. The public interface is kept stable so the app layer can be
 * migrated without rewriting controllers and stream handlers.
 */
@Slf4j
public class SpringAiAlibabaCodeGeneratorService implements AiCodeGeneratorService {

    private static final String HTML_PROMPT = "prompt/codegen-html-system-prompt.txt";
    private static final String MULTI_FILE_PROMPT = "prompt/codegen-multi-file-system-prompt.txt";
    private static final String PLAN_PROMPT = "prompt/codegen-plan-system-prompt.txt";
    private static final String VUE_PROJECT_PROMPT = "prompt/codegen-vue-project-system-prompt.txt";

    private final DashScopeChatModel chatModel;
    private final DashScopeChatModel reasoningChatModel;
    private final DashScopeChatOptions chatOptions;
    private final DashScopeChatOptions reasoningOptions;
    private final String modelName;
    private final AiModelMonitorListener aiModelMonitorListener;
    private final ToolManager toolManager;

    public SpringAiAlibabaCodeGeneratorService(DashScopeChatModel chatModel,
                                               DashScopeChatModel reasoningChatModel,
                                               DashScopeChatOptions chatOptions,
                                               DashScopeChatOptions reasoningOptions,
                                               String modelName,
                                               AiModelMonitorListener aiModelMonitorListener,
                                               ToolManager toolManager) {
        this.chatModel = chatModel;
        this.reasoningChatModel = reasoningChatModel;
        this.chatOptions = chatOptions;
        this.reasoningOptions = reasoningOptions;
        this.modelName = modelName;
        this.aiModelMonitorListener = aiModelMonitorListener;
        this.toolManager = toolManager;
    }

    @Override
    public HtmlCodeResult generateHtmlCode(String userMessage) {
        String response = callText(HTML_PROMPT, userMessage, chatModel, chatOptions);
        return (HtmlCodeResult) CodeParserExecutor.executeParser(response, CodeGenTypeEnum.HTML);
    }

    @Override
    public MultiFileCodeResult generateMultiFileCode(String userMessage) {
        String response = callText(MULTI_FILE_PROMPT, userMessage, chatModel, chatOptions);
        return (MultiFileCodeResult) CodeParserExecutor.executeParser(response, CodeGenTypeEnum.MULTI_FILE);
    }

    @Override
    public Flux<String> generateHtmlCodeStream(String userMessage) {
        return streamText(HTML_PROMPT, userMessage, chatModel, chatOptions);
    }

    @Override
    public Flux<String> generateMultiFileCodeStream(String userMessage) {
        return streamText(MULTI_FILE_PROMPT, userMessage, chatModel, chatOptions);
    }

    @Override
    public String generateAppPlan(String userMessage) {
        return callText(PLAN_PROMPT, userMessage, chatModel, chatOptions);
    }

    @Override
    public AiTokenStream generateVueProjectCodeStream(long appId, String userMessage) {
        return createToolTokenStream(appId, userMessage);
    }

    @Override
    public AiTokenStream repairVueProjectBuildStream(long appId, String repairPrompt) {
        return createToolTokenStream(appId, repairPrompt);
    }

    private AiTokenStream createToolTokenStream(long appId, String userMessage) {
        List<Message> messages = buildMessages(VUE_PROJECT_PROMPT, userMessage);
        String promptText = buildPromptText(messages);
        return new SpringAiAlibabaTokenStream(reasoningChatModel, messages, reasoningOptions, appId, modelName,
                promptText, aiModelMonitorListener, toolManager);
    }

    private String callText(String systemPromptPath, String userMessage,
                            DashScopeChatModel model, DashScopeChatOptions options) {
        List<Message> messages = buildMessages(systemPromptPath, userMessage);
        String promptText = buildPromptText(messages);
        SpringAiUsageTrace trace = aiModelMonitorListener.startSpringAiRequest(modelName, promptText);
        try {
            ChatResponse response = model.call(new Prompt(messages, options));
            String responseText = extractText(response);
            Usage usage = response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
            aiModelMonitorListener.recordSpringAiSuccess(trace, responseText,
                    usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens(),
                    usage == null ? null : usage.getTotalTokens());
            return responseText;
        } catch (RuntimeException e) {
            aiModelMonitorListener.recordSpringAiError(trace, e);
            throw e;
        }
    }

    private Flux<String> streamText(String systemPromptPath, String userMessage,
                                    DashScopeChatModel model, DashScopeChatOptions options) {
        List<Message> messages = buildMessages(systemPromptPath, userMessage);
        String promptText = buildPromptText(messages);
        AtomicReference<SpringAiUsageTrace> traceRef = new AtomicReference<>();
        AtomicReference<Usage> usageRef = new AtomicReference<>();
        StringBuilder responseBuilder = new StringBuilder();
        return model.stream(new Prompt(messages, options))
                .doOnSubscribe(subscription ->
                        traceRef.set(aiModelMonitorListener.startSpringAiRequest(modelName, promptText)))
                .map(response -> {
                    if (response != null && response.getMetadata() != null) {
                        usageRef.set(response.getMetadata().getUsage());
                    }
                    String chunk = extractText(response);
                    responseBuilder.append(StrUtil.blankToDefault(chunk, ""));
                    return chunk;
                })
                .filter(StrUtil::isNotBlank)
                .doOnError(error -> aiModelMonitorListener.recordSpringAiError(traceRef.get(), error))
                .doOnComplete(() -> {
                    Usage usage = usageRef.get();
                    aiModelMonitorListener.recordSpringAiSuccess(traceRef.get(), responseBuilder.toString(),
                            usage == null ? null : usage.getPromptTokens(),
                            usage == null ? null : usage.getCompletionTokens(),
                            usage == null ? null : usage.getTotalTokens());
                });
    }

    private List<Message> buildMessages(String systemPromptPath, String userMessage) {
        return List.of(
                new SystemMessage(loadPrompt(systemPromptPath)),
                new UserMessage(userMessage)
        );
    }

    private String buildPromptText(List<Message> messages) {
        StringBuilder builder = new StringBuilder();
        for (Message message : messages) {
            builder.append(message.getMessageType()).append(": ")
                    .append(StrUtil.blankToDefault(message.getText(), ""))
                    .append("\n\n");
        }
        return builder.toString();
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return StrUtil.blankToDefault(response.getResult().getOutput().getText(), "");
    }

    private String loadPrompt(String resourcePath) {
        try {
            return StreamUtils.copyToString(new ClassPathResource(resourcePath).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取系统提示词失败: {}", resourcePath, e);
            throw new IllegalStateException("读取系统提示词失败: " + resourcePath, e);
        }
    }
}
