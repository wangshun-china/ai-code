package com.ws.codecraft.ai;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.ws.codecraft.ai.model.HtmlCodeResult;
import com.ws.codecraft.ai.model.MultiFileCodeResult;
import com.ws.codecraft.ai.stream.AiTokenStream;
import com.ws.codecraft.core.AiCallHelper;
import com.ws.codecraft.core.parser.CodeParserExecutor;
import com.ws.codecraft.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

@Slf4j
public class SpringAiAlibabaCodeGeneratorService implements AiCodeGeneratorService {

    private static final String HTML_PROMPT = "prompt/codegen-html-system-prompt.txt";
    private static final String MULTI_FILE_PROMPT = "prompt/codegen-multi-file-system-prompt.txt";
    private static final String PLAN_PROMPT = "prompt/codegen-plan-system-prompt.txt";

    private final ChatClient chatClient;
    private final CompiledGraph codegenPipeline;
    private final String modelName;
    private final AiCallHelper callHelper;
    private final SpringAiToolCallbackRegistry toolCallbackRegistry;

    public SpringAiAlibabaCodeGeneratorService(ChatClient chatClient,
                                               CompiledGraph codegenPipeline,
                                               String modelName,
                                               AiCallHelper callHelper,
                                               SpringAiToolCallbackRegistry toolCallbackRegistry) {
        this.chatClient = chatClient;
        this.codegenPipeline = codegenPipeline;
        this.modelName = modelName;
        this.callHelper = callHelper;
        this.toolCallbackRegistry = toolCallbackRegistry;
    }

    @Override
    public HtmlCodeResult generateHtmlCode(String userMessage) {
        String response = callHelper.call(chatClient, AiCallHelper.loadPrompt(HTML_PROMPT), userMessage, modelName);
        return (HtmlCodeResult) CodeParserExecutor.executeParser(response, CodeGenTypeEnum.HTML);
    }

    @Override
    public MultiFileCodeResult generateMultiFileCode(String userMessage) {
        String response = callHelper.call(chatClient, AiCallHelper.loadPrompt(MULTI_FILE_PROMPT), userMessage, modelName);
        return (MultiFileCodeResult) CodeParserExecutor.executeParser(response, CodeGenTypeEnum.MULTI_FILE);
    }

    @Override
    public Flux<String> generateHtmlCodeStream(String userMessage) {
        return callHelper.stream(chatClient, AiCallHelper.loadPrompt(HTML_PROMPT), userMessage, modelName);
    }

    @Override
    public Flux<String> generateMultiFileCodeStream(String userMessage) {
        return callHelper.stream(chatClient, AiCallHelper.loadPrompt(MULTI_FILE_PROMPT), userMessage, modelName);
    }

    @Override
    public String generateAppPlan(String userMessage) {
        return callHelper.call(chatClient, AiCallHelper.loadPrompt(PLAN_PROMPT), userMessage, modelName);
    }

    @Override
    public AiTokenStream generateVueProjectCodeStream(long appId, String userMessage) {
        return new SpringAiAlibabaTokenStream(codegenPipeline, userMessage, appId, toolCallbackRegistry);
    }

    @Override
    public AiTokenStream repairVueProjectBuildStream(long appId, String repairPrompt) {
        return new SpringAiAlibabaTokenStream(codegenPipeline, repairPrompt, appId, toolCallbackRegistry);
    }

}
