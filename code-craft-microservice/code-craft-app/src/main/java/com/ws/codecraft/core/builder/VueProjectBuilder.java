package com.ws.codecraft.core.builder;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;

import com.ws.codecraft.constant.AppConstant;

/**
 * 构建 Vue 项目（通过远程 Node.js 构建服务）
 */
@Slf4j
@Component
public class VueProjectBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int BUILD_TIMEOUT_SECONDS = 900; // 15分钟超时

    /**
     * 异步构建 Vue 项目
     *
     * @param projectPath 项目路径（完整路径）
     */
    public void buildProjectAsync(String projectPath) {
        // 从完整路径中提取目录名
        String projectDirName = new File(projectPath).getName();

        Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis())
                .start(() -> {
                    try {
                        buildProject(projectDirName);
                    } catch (Exception e) {
                        log.error("异步构建 Vue 项目时发生异常: {}", e.getMessage(), e);
                    }
                });
    }

    /**
     * 构建 Vue 项目（同步执行）
     *
     * @param projectDirName 项目目录名（如 vue_project_123）
     * @return 是否构建成功
     */
    public boolean buildProject(String projectDirName) {
        log.info("开始构建 Vue 项目：{}", projectDirName);

        try {
            // 检查项目目录是否存在
            String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/" + projectDirName;
            File projectDir = new File(projectPath);
            if (!projectDir.exists() || !projectDir.isDirectory()) {
                log.error("项目目录不存在：{}", projectPath);
                return false;
            }

            // 检查 package.json 是否存在
            File packageJsonFile = new File(projectDir, "package.json");
            if (!packageJsonFile.exists()) {
                log.error("项目目录中没有 package.json 文件：{}", projectPath);
                return false;
            }

            // 调用远程 Node.js 构建服务
            boolean success = callRemoteBuildService(projectDirName);

            if (success) {
                // 验证 dist 目录是否生成
                File distDir = new File(projectPath, "dist");
                if (!distDir.exists() || !distDir.isDirectory()) {
                    log.error("构建完成但 dist 目录未生成：{}", projectPath);
                    return false;
                }
                log.info("Vue 项目构建成功，dist 目录：{}", projectPath);
            } else {
                log.error("Vue 项目构建失败：{}", projectDirName);
            }

            return success;
        } catch (Exception e) {
            log.error("构建 Vue 项目时发生异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 调用远程 Node.js 构建服务
     *
     * @param projectDirName 项目目录名
     * @return 是否构建成功
     */
    private boolean callRemoteBuildService(String projectDirName) {
        // 直接使用完整 URL（已包含 /build 路径）
        String buildUrl = AppConstant.NODE_BUILDER_URL;
        log.info("调用远程构建服务: {}", buildUrl);

        try {
            String requestBody = String.format("{\"projectDirName\":\"%s\"}", projectDirName);

            HttpResponse response = HttpRequest.post(buildUrl)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .timeout(BUILD_TIMEOUT_SECONDS * 1000)
                    .execute();

            if (response.isOk()) {
                String body = response.body();
                log.info("构建服务响应: {}", body);

                try {
                    JsonNode jsonNode = objectMapper.readTree(body);
                    boolean success = jsonNode.has("success") && jsonNode.get("success").asBoolean();

                    if (success) {
                        log.info("项目 {} 构建成功", projectDirName);
                        return true;
                    } else {
                        String message = jsonNode.has("message") ? jsonNode.get("message").asText() : "未知错误";
                        log.error("项目 {} 构建失败: {}", projectDirName, message);
                        return false;
                    }
                } catch (Exception e) {
                    log.error("解析构建响应失败: {}", e.getMessage());
                    // 如果响应成功且body包含success字段，尝试其他判断方式
                    return response.getStatus() == 200 && body.contains("success");
                }
            } else {
                log.error("远程构建服务调用失败，HTTP状态码: {}, 响应: {}",
                        response.getStatus(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("调用远程构建服务异常: {}", e.getMessage(), e);
            return false;
        }
    }
}