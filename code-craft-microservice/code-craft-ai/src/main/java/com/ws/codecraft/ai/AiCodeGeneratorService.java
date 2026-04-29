package com.ws.codecraft.ai;

import com.ws.codecraft.ai.model.HtmlCodeResult;
import com.ws.codecraft.ai.model.MultiFileCodeResult;
import com.ws.codecraft.ai.stream.AiTokenStream;
import reactor.core.publisher.Flux;

public interface AiCodeGeneratorService {

    /**
     * 生成 HTML 代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    HtmlCodeResult generateHtmlCode(String userMessage);

    /**
     * 生成多文件代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    MultiFileCodeResult generateMultiFileCode(String userMessage);

    /**
     * 生成 HTML 代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    Flux<String> generateHtmlCodeStream(String userMessage);

    /**
     * 生成多文件代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    Flux<String> generateMultiFileCodeStream(String userMessage);

    /**
     * 生成应用实现方案
     *
     * @param appId       应用 ID
     * @param userMessage 用户提示词
     * @return 方案内容
     */
    String generateAppPlan(String userMessage);

    /**
     * 生成 Vue 项目代码（流式）
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    AiTokenStream generateVueProjectCodeStream(long appId, String userMessage);

    /**
     * 修复 Vue 项目构建失败问题（流式）
     *
     * @param appId       应用 ID
     * @param repairPrompt 修复提示词
     * @return AI 的输出结果
     */
    AiTokenStream repairVueProjectBuildStream(long appId, String repairPrompt);
}
