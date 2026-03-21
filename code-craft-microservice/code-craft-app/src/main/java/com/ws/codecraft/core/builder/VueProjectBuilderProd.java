package com.ws.codecraft.core.builder;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.ws.codecraft.exception.BusinessException;
import com.ws.codecraft.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Vue 项目构建器（生产环境 - 调用远程 Node 服务）
 */
@Slf4j
@Component
public class VueProjectBuilderProd {

    // 对应 docker-compose 里的 CODE_NODE_BUILDER_URL
    @Value("${code.node-builder-url}")
    private String nodeBuilderUrl;

    public boolean buildProject(String sourceDirPath) {
        log.info("开始调用远程 Node 服务构建 Vue 项目，原始路径: {}", sourceDirPath);

        // ▼▼▼▼▼▼ 核心修改开始 ▼▼▼▼▼▼
        
        // 1. 从绝对路径中提取目录名
        // 例如：/tmp/code_output/vue_app_1  ->  vue_app_1
        File projectDir = new File(sourceDirPath);
        String projectDirName = projectDir.getName(); 

        // 2. 准备请求参数，key 必须是 projectDirName
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("projectDirName", projectDirName);

        // ▲▲▲▲▲▲ 核心修改结束 ▲▲▲▲▲▲

        try (HttpResponse response = HttpRequest.post(nodeBuilderUrl)
                .body(JSONUtil.toJsonStr(requestMap))
                .header("Content-Type", "application/json;charset=UTF-8")
                .timeout(300000) // 5分钟超时
                .execute()) {

            String body = response.body();
            log.info("远程构建响应: {}", body);

            if (response.isOk()) {
                log.info("Vue 项目远程构建成功");
                return true;
            } else {
                log.error("Vue 项目远程构建失败: {}", body);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "远程构建失败: " + body);
            }
        } catch (Exception e) {
            log.error("调用远程 Node 服务异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "调用远程构建服务失败: " + e.getMessage());
        }
    }
}