package com.yupi.yuaicodemother.constant;

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
     * 优先使用环境变量，其次使用系统临时目录
     * Windows: 可设置 CODE_OUTPUT_DIR 环境变量，如 G:\tmp\code_output
     * Docker: 使用 /tmp/code_output
     */
    String CODE_OUTPUT_ROOT_DIR = System.getenv("CODE_OUTPUT_DIR") != null
            ? System.getenv("CODE_OUTPUT_DIR")
            : System.getProperty("java.io.tmpdir") + "/code_output";

    /**
     * 应用部署目录
     * 优先使用环境变量，其次使用系统临时目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getenv("CODE_DEPLOY_DIR") != null
            ? System.getenv("CODE_DEPLOY_DIR")
            : System.getProperty("java.io.tmpdir") + "/code_deploy";

    /**
     * 应用部署域名
     */
    String CODE_DEPLOY_HOST = "http://localhost";

    /**
     * Node.js 构建服务地址（用于构建 Vue 项目）
     * 本地开发: http://localhost:3000/build
     * Docker: http://node-builder:3000/build
     */
    String NODE_BUILDER_URL = System.getenv("NODE_BUILDER_URL") != null
            ? System.getenv("NODE_BUILDER_URL")
            : "http://localhost:3000/build";
}