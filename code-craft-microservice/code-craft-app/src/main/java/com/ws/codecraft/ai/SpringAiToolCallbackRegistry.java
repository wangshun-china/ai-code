package com.ws.codecraft.ai;

import cn.hutool.json.JSONObject;
import com.ws.codecraft.ai.stream.AiToolCallRequest;
import com.ws.codecraft.ai.stream.AiToolExecution;
import com.ws.codecraft.ai.tools.BaseTool;
import com.ws.codecraft.ai.tools.ExitTool;
import com.ws.codecraft.ai.tools.FileDeleteTool;
import com.ws.codecraft.ai.tools.FileDirReadTool;
import com.ws.codecraft.ai.tools.FileModifyTool;
import com.ws.codecraft.ai.tools.FileReadTool;
import com.ws.codecraft.ai.tools.FileWriteTool;
import com.ws.codecraft.ai.tools.ToolManager;
import jakarta.annotation.Resource;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Central Spring AI tool callback registry for Vue project generation.
 */
@Component
public class SpringAiToolCallbackRegistry {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final List<ToolDefinition> VUE_PROJECT_TOOLS = List.of(
            new ToolDefinition("writeFile", "写入文件到指定路径",
                    """
                            {"type":"object","properties":{"relativeFilePath":{"type":"string","description":"文件的相对路径"},"content":{"type":"string","description":"要写入文件的内容"}},"required":["relativeFilePath","content"]}
                            """),
            new ToolDefinition("readFile", "读取指定路径的文件内容",
                    """
                            {"type":"object","properties":{"relativeFilePath":{"type":"string","description":"文件的相对路径"}},"required":["relativeFilePath"]}
                            """),
            new ToolDefinition("modifyFile", "修改文件内容，用新内容替换指定的旧内容",
                    """
                            {"type":"object","properties":{"relativeFilePath":{"type":"string","description":"文件的相对路径"},"oldContent":{"type":"string","description":"要替换的旧内容"},"newContent":{"type":"string","description":"替换后的新内容"}},"required":["relativeFilePath","oldContent","newContent"]}
                            """),
            new ToolDefinition("readDir", "读取目录结构，获取指定目录下的所有文件和子目录信息",
                    """
                            {"type":"object","properties":{"relativeDirPath":{"type":"string","description":"目录的相对路径，为空则读取整个项目结构"}}}
                            """),
            new ToolDefinition("deleteFile", "删除指定路径的文件",
                    """
                            {"type":"object","properties":{"relativeFilePath":{"type":"string","description":"文件的相对路径"}},"required":["relativeFilePath"]}
                            """),
            new ToolDefinition("exit", "当任务已完成或无需继续调用工具时，使用此工具退出操作，防止循环",
                    """
                            {"type":"object","properties":{}}
                            """)
    );

    @Resource
    private ToolManager toolManager;

    public List<ToolCallback> buildVueProjectToolCallbacks(long appId,
                                                           BiConsumer<Integer, AiToolCallRequest> toolRequestHandler,
                                                           Consumer<AiToolExecution> toolExecutionHandler) {
        return VUE_PROJECT_TOOLS.stream()
                .map(definition -> toolCallback(definition, appId, toolRequestHandler, toolExecutionHandler))
                .toList();
    }

    private ToolCallback toolCallback(ToolDefinition definition,
                                      long appId,
                                      BiConsumer<Integer, AiToolCallRequest> toolRequestHandler,
                                      Consumer<AiToolExecution> toolExecutionHandler) {
        return FunctionToolCallback.<Map<String, Object>, String>builder(
                        definition.name(),
                        args -> executeTool(definition.name(), args, appId, toolRequestHandler, toolExecutionHandler))
                .description(definition.description())
                .inputSchema(definition.inputSchema())
                .inputType(MAP_TYPE)
                .build();
    }

    private String executeTool(String toolName,
                               Map<String, Object> args,
                               long appId,
                               BiConsumer<Integer, AiToolCallRequest> toolRequestHandler,
                               Consumer<AiToolExecution> toolExecutionHandler) {
        JSONObject arguments = new JSONObject(args == null ? Map.of() : args);
        AiToolCallRequest request = new AiToolCallRequest(UUID.randomUUID().toString(), toolName, arguments.toString());
        if (toolRequestHandler != null) {
            toolRequestHandler.accept(0, request);
        }

        String result = doExecuteTool(toolName, arguments, appId);
        AiToolExecution execution = new AiToolExecution(request, result);
        if (toolExecutionHandler != null) {
            toolExecutionHandler.accept(execution);
        }
        return result;
    }

    private String doExecuteTool(String toolName, JSONObject arguments, long appId) {
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

    private record ToolDefinition(String name, String description, String inputSchema) {
    }
}
