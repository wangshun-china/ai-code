package com.ws.codecraft.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 代码生成与部署相关路径配置。
 * 默认值保持与当前主链路兼容。
 */
@Data
@Component
@ConfigurationProperties(prefix = "code")
public class CodeProjectProperties {

    /**
     * 应用生成目录。
     */
    private String outputRootDir = "G:/project/code-craft/tmp/code_output";

    /**
     * 应用部署目录。
     */
    private String deployRootDir = "G:/project/code-craft/tmp/code_deploy";

    /**
     * 线上构建服务地址。
     */
    private String nodeBuilderUrl = "http://localhost:8020/build";

    /**
     * 部署后访问域名。
     */
    private String deployHost = "http://localhost:8126";

    /**
     * 构建模式: local / remote
     */
    private String buildMode = "local";
}
