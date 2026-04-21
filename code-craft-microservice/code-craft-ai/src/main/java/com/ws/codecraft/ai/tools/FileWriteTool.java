package com.ws.codecraft.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import com.ws.codecraft.config.CodeProjectProperties;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 文件写入工具。
 */
@Slf4j
@Component
public class FileWriteTool extends BaseTool {

    private final CodeProjectProperties codeProjectProperties;

    public FileWriteTool(CodeProjectProperties codeProjectProperties) {
        this.codeProjectProperties = codeProjectProperties;
    }

    @Tool("写入文件到指定路径")
    public String writeFile(@P("文件的相对路径") String relativeFilePath,
                            @P("要写入文件的内容") String content,
                            @ToolMemoryId Long appId) {
        try {
            Path path = resolvePath(relativeFilePath, appId);
            Path parentDir = path.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            Files.write(path, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功写入文件: {}", path.toAbsolutePath());
            return "文件写入成功: " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "文件写入失败: " + relativeFilePath + ", 错误: " + e.getMessage();
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
        return "writeFile";
    }

    @Override
    public String getDisplayName() {
        return "写入文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String suffix = FileUtil.getSuffix(relativeFilePath);
        String content = arguments.getStr("content");
        return String.format("""
                        [工具调用] %s %s
                        ```%s
                        %s
                        ```
                        """, getDisplayName(), relativeFilePath, suffix, content);
    }
}
