package com.ws.codecraft.ai.tools;

import cn.hutool.json.JSONObject;
import com.ws.codecraft.config.CodeProjectProperties;
import dev.langchain4j.agent.tool.CompensateFor;
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
 * 文件修改工具。
 */
@Slf4j
@Component
public class FileModifyTool extends BaseTool {

    private final CodeProjectProperties codeProjectProperties;

    public FileModifyTool(CodeProjectProperties codeProjectProperties) {
        this.codeProjectProperties = codeProjectProperties;
    }

    @Tool("修改文件内容，用新内容替换指定的旧内容")
    public String modifyFile(@P("文件的相对路径") String relativeFilePath,
                             @P("要替换的旧内容") String oldContent,
                             @P("替换后的新内容") String newContent,
                             @ToolMemoryId Long appId) {
        try {
            Path path = resolvePath(relativeFilePath, appId);
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return "错误：文件不存在或不是文件 - " + relativeFilePath;
            }

            String originalContent = Files.readString(path);
            String cleanOldContent = stripMarkdownCodeFence(oldContent);
            String cleanNewContent = stripMarkdownCodeFence(newContent);
            if (!originalContent.contains(cleanOldContent)) {
                return "警告：文件中未找到要替换的内容，文件未修改 - " + relativeFilePath;
            }

            String modifiedContent = originalContent.replace(cleanOldContent, cleanNewContent);
            if (originalContent.equals(modifiedContent)) {
                return "信息：替换后文件内容未发生变化 - " + relativeFilePath;
            }

            Files.writeString(path, modifiedContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功修改文件: {}", path.toAbsolutePath());
            return "文件修改成功: " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "修改文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
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

    /**
     * 补偿动作：modifyFile 执行后若本轮其他工具失败，则将 newContent 回滚替换回 oldContent。
     * 参数类型需与 modifyFile 一致（含 @ToolMemoryId）。
     */
    @CompensateFor("modifyFile")
    public String compensateModifyFile(@P("文件的相对路径") String relativeFilePath,
                                       @P("要替换的旧内容") String oldContent,
                                       @P("替换后的新内容") String newContent,
                                       @ToolMemoryId Long appId) {
        try {
            Path path = resolvePath(relativeFilePath, appId);
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return "回滚跳过（文件不存在）: " + relativeFilePath;
            }
            String currentContent = Files.readString(path);
            String cleanNewContent = stripMarkdownCodeFence(newContent);
            if (!currentContent.contains(cleanNewContent)) {
                return "回滚跳过（未找到已修改内容）: " + relativeFilePath;
            }
            String rolledBack = currentContent.replace(cleanNewContent, stripMarkdownCodeFence(oldContent));
            Files.writeString(path, rolledBack, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("补偿动作：回滚文件修改, path={}", path.toAbsolutePath());
            return "已回滚文件修改: " + relativeFilePath;
        } catch (IOException e) {
            log.error("补偿动作失败：无法回滚文件 {}，错误: {}", relativeFilePath, e.getMessage(), e);
            return "补偿失败: " + relativeFilePath;
        }
    }

    @Override
    public String getToolName() {
        return "modifyFile";
    }

    @Override
    public String getDisplayName() {
        return "修改文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String oldContent = stripMarkdownCodeFence(arguments.getStr("oldContent"));
        String newContent = stripMarkdownCodeFence(arguments.getStr("newContent"));
        return String.format("""
                [工具调用] %s %s

                替换前：
                ```
                %s
                ```

                替换后：
                ```
                %s
                ```
                """, getDisplayName(), relativeFilePath, oldContent, newContent);
    }

    @Override
    public String generateToolExecutedSummary(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String oldContent = stripMarkdownCodeFence(arguments.getStr("oldContent"));
        String newContent = stripMarkdownCodeFence(arguments.getStr("newContent"));
        int oldLength = oldContent == null ? 0 : oldContent.length();
        int newLength = newContent == null ? 0 : newContent.length();
        return String.format("[工具调用] %s %s，替换 %d 个字符为 %d 个字符",
                getDisplayName(), relativeFilePath, oldLength, newLength);
    }
}
