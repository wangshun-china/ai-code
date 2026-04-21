package com.ws.codecraft.core.builder;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.ws.codecraft.config.CodeProjectProperties;
import com.ws.codecraft.exception.BusinessException;
import com.ws.codecraft.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 生产环境 Vue 构建器，调用远程 Node 服务构建。
 */
@Slf4j
@Component
public class VueProjectBuilderProd {

    private final CodeProjectProperties codeProjectProperties;

    public VueProjectBuilderProd(CodeProjectProperties codeProjectProperties) {
        this.codeProjectProperties = codeProjectProperties;
    }

    public boolean buildProject(String sourceDirPath) {
        log.info("开始调用远程 Node 服务构建 Vue 项目, path={}", sourceDirPath);

        File projectDir = new File(sourceDirPath);
        String projectDirName = projectDir.getName();

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("projectDirName", projectDirName);

        try (HttpResponse response = HttpRequest.post(codeProjectProperties.getNodeBuilderUrl())
                .body(JSONUtil.toJsonStr(requestMap))
                .header("Content-Type", "application/json;charset=UTF-8")
                .timeout(300000)
                .execute()) {
            String body = response.body();
            log.info("远程构建响应: {}", body);

            if (response.isOk()) {
                log.info("Vue 项目远程构建成功");
                return true;
            }

            log.error("Vue 项目远程构建失败: {}", body);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "远程构建失败: " + body);
        } catch (Exception e) {
            log.error("调用远程 Node 服务异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "调用远程构建服务失败: " + e.getMessage());
        }
    }
}
