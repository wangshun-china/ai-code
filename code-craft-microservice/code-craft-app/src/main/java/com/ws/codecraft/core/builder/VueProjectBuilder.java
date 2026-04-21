package com.ws.codecraft.core.builder;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ws.codecraft.config.CodeProjectProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 构建 Vue 项目，通过 node-builder 服务执行构建。
 */
@Slf4j
@Component
public class VueProjectBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int BUILD_TIMEOUT_SECONDS = 900;

    private final CodeProjectProperties codeProjectProperties;

    public VueProjectBuilder(CodeProjectProperties codeProjectProperties) {
        this.codeProjectProperties = codeProjectProperties;
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
        log.info("开始构建 Vue 项目: {}", projectDirName);
        try {
            String projectPath = codeProjectProperties.getOutputRootDir() + "/" + projectDirName;
            File projectDir = new File(projectPath);
            if (!projectDir.exists() || !projectDir.isDirectory()) {
                log.error("项目目录不存在: {}", projectPath);
                return false;
            }

            File packageJsonFile = new File(projectDir, "package.json");
            if (!packageJsonFile.exists()) {
                log.error("项目目录中缺少 package.json: {}", projectPath);
                return false;
            }

            boolean success = callRemoteBuildService(projectDirName);
            if (!success) {
                log.error("Vue 项目构建失败: {}", projectDirName);
                return false;
            }

            File distDir = new File(projectPath, "dist");
            if (!distDir.exists() || !distDir.isDirectory()) {
                log.error("构建完成但 dist 目录未生成: {}", projectPath);
                return false;
            }
            log.info("Vue 项目构建成功: {}", projectPath);
            return true;
        } catch (Exception e) {
            log.error("构建 Vue 项目时发生异常: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean callRemoteBuildService(String projectDirName) {
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
                return false;
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
                return success;
            } catch (Exception e) {
                log.error("解析构建响应失败: {}", e.getMessage(), e);
                return response.getStatus() == 200 && body.contains("success");
            }
        } catch (Exception e) {
            log.error("调用远程构建服务异常: {}", e.getMessage(), e);
            return false;
        }
    }
}
