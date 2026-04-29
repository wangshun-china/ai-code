package com.ws.codecraft.ai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.ws.codecraft.ai.monitor.AiModelMonitorListener;
import com.ws.codecraft.ai.monitor.AiModelMonitorListener.SpringAiUsageTrace;
import com.ws.codecraft.ai.stream.AiTokenStream;
import com.ws.codecraft.ai.stream.AiToolCallRequest;
import com.ws.codecraft.ai.stream.AiToolExecution;
import com.ws.codecraft.ai.tools.*;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.Disposable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Tool-enabled stream backed by Spring AI Alibaba streaming calls.
 */
public class SpringAiAlibabaTokenStream implements AiTokenStream {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final DashScopeChatModel chatModel;
    private final List<Message> messages;
    private final DashScopeChatOptions baseOptions;
    private final long appId;
    private final String modelName;
    private final String promptText;
    private final AiModelMonitorListener aiModelMonitorListener;
    private final ToolManager toolManager;
    private final AtomicBoolean ignoreErrors = new AtomicBoolean(false);

    private Consumer<String> partialResponseHandler;
    private BiConsumer<Integer, AiToolCallRequest> toolRequestHandler;
    private Consumer<AiToolExecution> toolExecutionHandler;
    private Consumer<String> completeResponseHandler;
    private Consumer<Throwable> errorHandler;

    public SpringAiAlibabaTokenStream(DashScopeChatModel chatModel,
                                      List<Message> messages,
                                      DashScopeChatOptions baseOptions,
                                      long appId,
                                      String modelName,
                                      String promptText,
                                      AiModelMonitorListener aiModelMonitorListener,
                                      ToolManager toolManager) {
        this.chatModel = chatModel;
        this.messages = messages;
        this.baseOptions = baseOptions;
        this.appId = appId;
        this.modelName = modelName;
        this.promptText = promptText;
        this.aiModelMonitorListener = aiModelMonitorListener;
        this.toolManager = toolManager;
    }

    @Override
    public AiTokenStream onPartialResponse(Consumer<String> partialResponseHandler) {
        this.partialResponseHandler = partialResponseHandler;
        return this;
    }

    @Override
    public AiTokenStream onToolRequest(BiConsumer<Integer, AiToolCallRequest> toolRequestHandler) {
        this.toolRequestHandler = toolRequestHandler;
        return this;
    }

    @Override
    public AiTokenStream onToolExecuted(Consumer<AiToolExecution> toolExecuteHandler) {
        this.toolExecutionHandler = toolExecuteHandler;
        return this;
    }

    @Override
    public AiTokenStream onComplete(Consumer<String> completeResponseHandler) {
        this.completeResponseHandler = completeResponseHandler;
        return this;
    }

    @Override
    public AiTokenStream onError(Consumer<Throwable> errorHandler) {
        this.errorHandler = errorHandler;
        return this;
    }

    @Override
    public AiTokenStream ignoreErrors() {
        this.ignoreErrors.set(true);
        return this;
    }

    @Override
    public void start() {
        if (partialResponseHandler == null) {
            throw new IllegalStateException("onPartialResponse must be configured before start");
        }
        DashScopeChatOptions options = DashScopeChatOptions.fromOptions(baseOptions);
        options.setToolCallbacks(buildToolCallbacks());
        options.setInternalToolExecutionEnabled(true);
        options.setParallelToolCalls(false);

        AtomicReference<SpringAiUsageTrace> traceRef = new AtomicReference<>();
        AtomicReference<Usage> usageRef = new AtomicReference<>();
        StringBuilder responseBuilder = new StringBuilder();
        Disposable ignored = chatModel.stream(new Prompt(messages, options))
                .doOnSubscribe(subscription ->
                        traceRef.set(aiModelMonitorListener.startSpringAiRequest(modelName, promptText)))
                .subscribe(response -> {
                    if (response != null && response.getMetadata() != null) {
                        usageRef.set(response.getMetadata().getUsage());
                    }
                    String chunk = extractText(response);
                    if (StrUtil.isNotBlank(chunk)) {
                        responseBuilder.append(chunk);
                        partialResponseHandler.accept(chunk);
                    }
                }, error -> {
                    aiModelMonitorListener.recordSpringAiError(traceRef.get(), error);
                    if (!ignoreErrors.get() && errorHandler != null) {
                        errorHandler.accept(error);
                    }
                }, () -> {
                    Usage usage = usageRef.get();
                    aiModelMonitorListener.recordSpringAiSuccess(traceRef.get(), responseBuilder.toString(),
                            usage == null ? null : usage.getPromptTokens(),
                            usage == null ? null : usage.getCompletionTokens(),
                            usage == null ? null : usage.getTotalTokens());
                    if (completeResponseHandler != null) {
                        completeResponseHandler.accept(responseBuilder.toString());
                    }
                });
    }

    private List<ToolCallback> buildToolCallbacks() {
        return List.of(
                toolCallback("writeFile", "写入文件到指定路径",
                        """
                                {"type":"object","properties":{"relativeFilePath":{"type":"string","description":"文件的相对路径"},"content":{"type":"string","description":"要写入文件的内容"}},"required":["relativeFilePath","content"]}
                                """),
                toolCallback("readFile", "读取指定路径的文件内容",
                        """
                                {"type":"object","properties":{"relativeFilePath":{"type":"string","description":"文件的相对路径"}},"required":["relativeFilePath"]}
                                """),
                toolCallback("modifyFile", "修改文件内容，用新内容替换指定的旧内容",
                        """
                                {"type":"object","properties":{"relativeFilePath":{"type":"string","description":"文件的相对路径"},"oldContent":{"type":"string","description":"要替换的旧内容"},"newContent":{"type":"string","description":"替换后的新内容"}},"required":["relativeFilePath","oldContent","newContent"]}
                                """),
                toolCallback("readDir", "读取目录结构，获取指定目录下的所有文件和子目录信息",
                        """
                                {"type":"object","properties":{"relativeDirPath":{"type":"string","description":"目录的相对路径，为空则读取整个项目结构"}}}
                                """),
                toolCallback("deleteFile", "删除指定路径的文件",
                        """
                                {"type":"object","properties":{"relativeFilePath":{"type":"string","description":"文件的相对路径"}},"required":["relativeFilePath"]}
                                """),
                toolCallback("exit", "当任务已完成或无需继续调用工具时，使用此工具退出操作，防止循环",
                        """
                                {"type":"object","properties":{}}
                                """)
        );
    }

    private ToolCallback toolCallback(String name, String description, String inputSchema) {
        return FunctionToolCallback.<Map<String, Object>, String>builder(name, args -> executeTool(name, args))
                .description(description)
                .inputSchema(inputSchema)
                .inputType(MAP_TYPE)
                .build();
    }

    private String executeTool(String toolName, Map<String, Object> args) {
        JSONObject arguments = new JSONObject(args == null ? Map.of() : args);
        AiToolCallRequest request = new AiToolCallRequest(UUID.randomUUID().toString(), toolName, arguments.toString());
        if (toolRequestHandler != null) {
            toolRequestHandler.accept(0, request);
        }

        String result = doExecuteTool(toolName, arguments);
        AiToolExecution execution = new AiToolExecution(request, result);
        if (toolExecutionHandler != null) {
            toolExecutionHandler.accept(execution);
        }
        return result;
    }

    private String doExecuteTool(String toolName, JSONObject arguments) {
        BaseTool tool = toolManager.getTool(toolName);
        if (tool == null) {
            return "Error: there is no tool called " + toolName;
        }
        return switch (toolName) {
            case "writeFile" -> ((FileWriteTool) tool).writeFile(
                    arguments.getStr("relativeFilePath"),
                    arguments.getStr("content"),
                    appId);
            case "readFile" -> ((FileReadTool) tool).readFile(
                    arguments.getStr("relativeFilePath"),
                    appId);
            case "modifyFile" -> ((FileModifyTool) tool).modifyFile(
                    arguments.getStr("relativeFilePath"),
                    arguments.getStr("oldContent"),
                    arguments.getStr("newContent"),
                    appId);
            case "readDir" -> ((FileDirReadTool) tool).readDir(
                    arguments.getStr("relativeDirPath"),
                    appId);
            case "deleteFile" -> ((FileDeleteTool) tool).deleteFile(
                    arguments.getStr("relativeFilePath"),
                    appId);
            case "exit" -> ((ExitTool) tool).exit();
            default -> "Error: unsupported tool " + toolName;
        };
    }

    private String extractText(org.springframework.ai.chat.model.ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return StrUtil.blankToDefault(response.getResult().getOutput().getText(), "");
    }
}
