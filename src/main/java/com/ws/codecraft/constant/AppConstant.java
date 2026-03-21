package com.ws.codecraft.constant;

/**
 * 应用常量
 */
public interface AppConstant {

    /**
     * 精选应用的优先级
     */
    Integer GOOD_APP_PRIORITY = 99;

    /**
     * 默认应用优先级
     */
    Integer DEFAULT_APP_PRIORITY = 0;

    /**
     * 应用生成目录
     * 本地开发固定使用项目目录下的 tmp/code_output
     * Docker 环境可通过 CODE_OUTPUT_DIR 环境变量覆盖
     */
    String CODE_OUTPUT_ROOT_DIR = System.getenv("CODE_OUTPUT_DIR") != null
            ? System.getenv("CODE_OUTPUT_DIR")
            : "G:/project/code-craft/tmp/code_output";

    /**
     * 应用部署目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getenv("CODE_DEPLOY_DIR") != null
            ? System.getenv("CODE_DEPLOY_DIR")
            : "G:/project/code-craft/tmp/code_deploy";

    /**
     * 应用部署域名
     */
    String CODE_DEPLOY_HOST = "http://localhost";

    /**
     * Node.js 构建服务地址（用于构建 Vue 项目）
     * 本地开发: http://localhost:3001/build
     * Docker: http://node-builder:3000/build
     */
    String NODE_BUILDER_URL = System.getenv("NODE_BUILDER_URL") != null
            ? System.getenv("NODE_BUILDER_URL")
            : "http://localhost:3001/build";
}