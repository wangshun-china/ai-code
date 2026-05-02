package com.ws.codecraft.core.builder;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ws.codecraft.config.CodeProjectProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 构建 Vue 项目，通过 node-builder 服务执行构建。
 */
@Slf4j
@Component
public class VueProjectBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int BUILD_TIMEOUT_SECONDS = 900;

    private final CodeProjectProperties codeProjectProperties;
    private final VueProjectSourceGuard vueProjectSourceGuard;

    public VueProjectBuilder(CodeProjectProperties codeProjectProperties, VueProjectSourceGuard vueProjectSourceGuard) {
        this.codeProjectProperties = codeProjectProperties;
        this.vueProjectSourceGuard = vueProjectSourceGuard;
    }

    public void buildProjectAsync(String projectPath) {
        String projectDirName = new File(projectPath).getName();
        Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis()).start(() -> {
            try {
                buildProject(projectDirName);
            } catch (Exception e) {
                log.error("异步构建 Vue 项目失败: {}", e.getMessage(), e);
            }
        });
    }

    public boolean buildProject(String projectDirName) {
        return buildProjectWithResult(projectDirName).isSuccess();
    }

    public VueProjectBuildResult buildProjectWithResult(String projectDirName) {
        log.info("开始构建 Vue 项目: {}", projectDirName);
        try {
            String projectPath = codeProjectProperties.getOutputRootDir() + "/" + projectDirName;
            File projectDir = new File(projectPath);
            if (!projectDir.exists() || !projectDir.isDirectory()) {
                String message = "项目目录不存在: " + projectPath;
                log.error(message);
                return VueProjectBuildResult.failure(message);
            }

            ensureMinimalVueProjectSkeleton(projectDir);
            repairSourceBeforeBuild(projectDir);

            File packageJsonFile = new File(projectDir, "package.json");
            if (!packageJsonFile.exists()) {
                String message = "项目目录中缺少 package.json: " + projectPath;
                log.error(message);
                return VueProjectBuildResult.failure(message);
            }

            VueProjectBuildResult buildResult = callRemoteBuildService(projectDirName);
            if (!buildResult.isSuccess() && isResolvableImportError(buildResult.getMessage())) {
                List<String> repairs = vueProjectSourceGuard.repairMissingRelativeImports(projectDir.toPath());
                if (!repairs.isEmpty()) {
                    log.info("检测到 Vue import 路径错误并已修复，准备重新构建: project={}, repairs={}", projectDirName, repairs);
                    buildResult = callRemoteBuildService(projectDirName);
                }
            }
            if (!buildResult.isSuccess()) {
                log.error("Vue 项目构建失败: {}, reason={}", projectDirName, buildResult.getMessage());
                return buildResult;
            }

            File distDir = new File(projectPath, "dist");
            if (!distDir.exists() || !distDir.isDirectory()) {
                String message = "构建完成但 dist 目录未生成: " + projectPath;
                log.error(message);
                return VueProjectBuildResult.failure(message);
            }
            log.info("Vue 项目构建成功: {}", projectPath);
            return VueProjectBuildResult.success("Build Success");
        } catch (Exception e) {
            log.error("构建 Vue 项目时发生异常: {}", e.getMessage(), e);
            return VueProjectBuildResult.failure("构建 Vue 项目时发生异常: " + e.getMessage());
        }
    }

    private void repairSourceBeforeBuild(File projectDir) {
        List<String> repairs = vueProjectSourceGuard.repairMissingRelativeImports(projectDir.toPath());
        if (!repairs.isEmpty()) {
            log.info("构建前已修复 Vue import 路径: project={}, repairs={}", projectDir.getName(), repairs);
        }
    }

    private boolean isResolvableImportError(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.contains("Could not resolve") || message.contains("Could not load") || message.contains("ENOENT");
    }

    private VueProjectBuildResult callRemoteBuildService(String projectDirName) {
        String buildUrl = codeProjectProperties.getNodeBuilderUrl();
        log.info("调用远程构建服务: {}", buildUrl);

        try {
            String requestBody = String.format("{\"projectDirName\":\"%s\"}", projectDirName);
            HttpResponse response = HttpRequest.post(buildUrl)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .timeout(BUILD_TIMEOUT_SECONDS * 1000)
                    .execute();

            if (!response.isOk()) {
                log.error("远程构建服务调用失败, status={}, body={}", response.getStatus(), response.body());
                return VueProjectBuildResult.builder()
                        .success(false)
                        .status(response.getStatus())
                        .message(normalizeBuildMessage(response.body()))
                        .build();
            }

            String body = response.body();
            log.info("构建服务响应: {}", body);
            try {
                JsonNode jsonNode = OBJECT_MAPPER.readTree(body);
                boolean success = jsonNode.has("success") && jsonNode.get("success").asBoolean();
                if (!success) {
                    String message = jsonNode.has("message") ? jsonNode.get("message").asText() : "未知错误";
                    log.error("项目 {} 构建失败: {}", projectDirName, message);
                }
                return VueProjectBuildResult.builder()
                        .success(success)
                        .status(response.getStatus())
                        .message(success ? "Build Success" : normalizeBuildMessage(body))
                        .build();
            } catch (Exception e) {
                log.error("解析构建响应失败: {}", e.getMessage(), e);
                boolean success = response.getStatus() == 200 && body.contains("success");
                return VueProjectBuildResult.builder()
                        .success(success)
                        .status(response.getStatus())
                        .message(success ? "Build Success" : normalizeBuildMessage(body))
                        .build();
            }
        } catch (Exception e) {
            log.error("调用远程构建服务异常: {}", e.getMessage(), e);
            return VueProjectBuildResult.failure("调用远程构建服务异常: " + e.getMessage());
        }
    }

    private String normalizeBuildMessage(String body) {
        if (body == null || body.isBlank()) {
            return "构建失败，node-builder 未返回错误详情";
        }
        try {
            JsonNode jsonNode = OBJECT_MAPPER.readTree(body);
            if (jsonNode.has("message")) {
                return jsonNode.get("message").asText();
            }
        } catch (Exception ignored) {
            // keep raw body
        }
        return body;
    }

    private void ensureMinimalVueProjectSkeleton(File projectDir) throws IOException {
        Path projectPath = projectDir.toPath();
        Path srcPath = projectPath.resolve("src");
        Files.createDirectories(srcPath);

        Path indexPath = projectPath.resolve("index.html");
        Path packageJsonPath = projectPath.resolve("package.json");
        Path viteConfigPath = projectPath.resolve("vite.config.js");
        Path mainJsPath = srcPath.resolve("main.js");
        Path appVuePath = srcPath.resolve("App.vue");

        String originalHtml = Files.exists(indexPath) ? Files.readString(indexPath, StandardCharsets.UTF_8) : "";

        if (!Files.exists(packageJsonPath)) {
            Files.writeString(packageJsonPath, """
                    {
                      "scripts": {
                        "dev": "vite",
                        "build": "vite build"
                      },
                      "dependencies": {
                        "vue": "^3.3.4",
                        "vue-router": "^4.2.4"
                      },
                      "devDependencies": {
                        "@vitejs/plugin-vue": "^4.2.3",
                        "vite": "^4.4.5"
                      }
                    }
                    """, StandardCharsets.UTF_8);
        }

        if (!Files.exists(viteConfigPath)) {
            Files.writeString(viteConfigPath, """
                    import { defineConfig } from 'vite'
                    import vue from '@vitejs/plugin-vue'

                    export default defineConfig({
                      base: './',
                      plugins: [vue()]
                    })
                    """, StandardCharsets.UTF_8);
        }

        if (!Files.exists(mainJsPath)) {
            Files.writeString(mainJsPath, """
                    import { createApp } from 'vue'
                    import App from './App.vue'

                    createApp(App).mount('#app')
                    """, StandardCharsets.UTF_8);
        }

        if (!Files.exists(appVuePath)) {
            Files.writeString(appVuePath, buildFallbackAppVue(originalHtml), StandardCharsets.UTF_8);
        }

        if (!Files.exists(indexPath) || !originalHtml.contains("id=\"app\"")) {
            Files.writeString(indexPath, """
                    <!DOCTYPE html>
                    <html lang="zh-CN">
                      <head>
                        <meta charset="UTF-8" />
                        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                        <title>Code Craft App</title>
                      </head>
                      <body>
                        <div id="app"></div>
                        <script type="module" src="/src/main.js"></script>
                      </body>
                    </html>
                    """, StandardCharsets.UTF_8);
        }
    }

    private String buildFallbackAppVue(String originalHtml) {
        String bodyContent = extractBodyContent(originalHtml);
        if (bodyContent.isBlank()) {
            bodyContent = """
                    <main class="app">
                      <h1>极简清单</h1>
                      <p>记录今天最重要的三件事。</p>
                    </main>
                    """;
        }
        String htmlLiteral;
        try {
            htmlLiteral = OBJECT_MAPPER.writeValueAsString(bodyContent);
        } catch (Exception e) {
            htmlLiteral = "\"\"";
        }
        return String.format("""
                <template>
                  <div class="page" v-html="html"></div>
                </template>

                <script setup>
                const html = %s
                </script>

                <style scoped>
                .page {
                  min-height: 100vh;
                  padding: 32px;
                  color: #1f2937;
                  background: #f8f5ee;
                }
                </style>
                """, htmlLiteral);
    }

    private String extractBodyContent(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String lowerHtml = html.toLowerCase();
        int bodyStart = lowerHtml.indexOf("<body");
        if (bodyStart < 0) {
            return html;
        }
        int bodyOpenEnd = lowerHtml.indexOf(">", bodyStart);
        int bodyEnd = lowerHtml.lastIndexOf("</body>");
        if (bodyOpenEnd < 0 || bodyEnd <= bodyOpenEnd) {
            return html;
        }
        return html.substring(bodyOpenEnd + 1, bodyEnd)
                .replaceAll("(?is)<script[^>]*>.*?</script>", "")
                .trim();
    }
}
