package com.ws.codecraft.ai.tools;

import com.ws.codecraft.config.CodeProjectProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class FileToolCompensationTest {

    @TempDir
    Path tempDir;

    private CodeProjectProperties props;

    @BeforeEach
    void setUp() {
        props = new CodeProjectProperties();
        props.setOutputRootDir(tempDir.toString());
    }

    /**
     * 验证 writeFile 的补偿动作会把刚写入的文件删除。
     */
    @Test
    void writeFileIsRolledBackOnCompensation() {
        FileWriteTool writeTool = new FileWriteTool(props);
        Path written = tempDir.resolve("vue_project_1/src/App.vue");

        writeTool.writeFile("src/App.vue", "<template>hello</template>", 1L);
        assertTrue(Files.exists(written), "文件应已写入");

        String result = writeTool.compensateWriteFile("src/App.vue", "<template>hello</template>", 1L);
        assertTrue(result.contains("已回滚删除"), "补偿应删除写入的文件: " + result);
        assertTrue(!Files.exists(written), "补偿后文件应被删除");
    }

    /**
     * 验证 modifyFile 的补偿会把新内容回滚成旧内容。
     */
    @Test
    void modifyFileIsRolledBackToOriginalContent() throws IOException {
        FileModifyTool modifyTool = new FileModifyTool(props);
        Path file = tempDir.resolve("vue_project_1/src/App.vue");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "old content");

        String modifyResult = modifyTool.modifyFile("src/App.vue", "old content", "new content", 1L);
        assertTrue(modifyResult.contains("文件修改成功"), "应先执行修改: " + modifyResult);
        assertEquals("new content", Files.readString(file), "修改后应为新内容");

        String result = modifyTool.compensateModifyFile("src/App.vue", "old content", "new content", 1L);
        assertTrue(result.contains("已回滚文件修改"), "补偿应回滚修改: " + result);
        assertEquals("old content", Files.readString(file), "内容应恢复为旧内容");
    }

    private static void assertCompensateForAnnotation() {
        // 确保注解被正确保留（编译期验证）
        try {
            assertEquals("writeFile", FileWriteTool.class.getMethod("compensateWriteFile",
                    String.class, String.class, Long.class)
                    .getAnnotation(dev.langchain4j.agent.tool.CompensateFor.class).value());
            assertEquals("modifyFile", FileModifyTool.class.getMethod("compensateModifyFile",
                    String.class, String.class, String.class, Long.class)
                    .getAnnotation(dev.langchain4j.agent.tool.CompensateFor.class).value());
        } catch (NoSuchMethodException e) {
            throw new AssertionError("补偿方法签名与工具不一致", e);
        }
    }

    @Test
    void compensateForAnnotationsArePresent() {
        assertCompensateForAnnotation();
    }

    /**
     * 模拟生产 AiServices 构建路径：开启 compensateOnToolErrors 后，
     * 若 @CompensateFor 签名不匹配，AiServices.build() 会在构建期抛 IllegalConfigurationException。
     * 能成功 build 即说明补偿配置合法。
     */
    @Test
    void aiServicesBuildSucceedsWithCompensationEnabled() {
        FileWriteTool writeTool = new FileWriteTool(props);
        FileModifyTool modifyTool = new FileModifyTool(props);
        FileDeleteTool deleteTool = new FileDeleteTool(props);
        FileReadTool readTool = new FileReadTool(props);
        FileDirReadTool dirReadTool = new FileDirReadTool(props);
        ExitTool exitTool = new ExitTool();

        ChatModel model = mock(ChatModel.class);
        TestAi ai = AiServices.builder(TestAi.class)
                .chatModel(model)
                .tools(writeTool, modifyTool, deleteTool, readTool, dirReadTool, exitTool)
                .compensateOnToolErrors(true)
                .build();

        assertNotNull(ai, "AiServices 应成功构建");
    }

    private interface TestAi {
        String chat(String message);
    }
}
