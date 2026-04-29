package com.ws.codecraft.ai.tools;

import cn.hutool.json.JSONObject;
import com.ws.codecraft.config.CodeProjectProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件读取工具。
 */
@Slf4j
@Component
public class FileReadTool extends BaseTool {

    private final CodeProjectProperties codeProjectProperties;

    public FileReadTool(CodeProjectProperties codeProjectProperties) {
        this.codeProjectProperties = codeProjectProperties;
    }

    public String readFile(String relativeFilePath, Long appId) {
        try {
            Path path = resolvePath(relativeFilePath, appId);
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return "错误：文件不存在或不是文件 - " + relativeFilePath;
            }
            return Files.readString(path);
        } catch (IOException e) {
            String errorMessage = "读取文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    private Path resolvePath(String relativeFilePath, Long appId) {
        Path path = Paths.get(relativeFilePath);
        if (!path.isAbsolute()) {
            String projectDirName = "vue_project_" + appId;
            Path projectRoot = Paths.get(codeProjectProperties.getOutputRootDir(), projectDirName);
            path = projectRoot.resolve(relativeFilePath);
        }
        return path;
    }

    @Override
    public String getToolName() {
        return "readFile";
    }

    @Override
    public String getDisplayName() {
        return "读取文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        return String.format("[工具调用] %s %s", getDisplayName(), relativeFilePath);
    }
}
