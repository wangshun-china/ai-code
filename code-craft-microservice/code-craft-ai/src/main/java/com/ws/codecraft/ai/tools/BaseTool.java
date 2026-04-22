package com.ws.codecraft.ai.tools;

import cn.hutool.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工具基类
 * 定义所有工具的通用接口
 */
public abstract class BaseTool {

    private static final Pattern FENCED_CODE_BLOCK_PATTERN = Pattern.compile(
            "^\\s*```[\\w.+-]*\\s*\\R([\\s\\S]*?)\\R```\\s*$"
    );

    /**
     * 获取工具的英文名称（对应方法名）
     *
     * @return 工具英文名称
     */
    public abstract String getToolName();

    /**
     * 获取工具的中文显示名称
     *
     * @return 工具中文名称
     */
    public abstract String getDisplayName();

    /**
     * 生成工具请求时的返回值（显示给用户）
     *
     * @return 工具请求显示内容
     */
    public String generateToolRequestResponse() {
        return String.format("\n\n[选择工具] %s\n\n", getDisplayName());
    }

    /**
     * 清理模型误放入工具参数的 Markdown 代码围栏。
     */
    protected String stripMarkdownCodeFence(String content) {
        if (content == null) {
            return null;
        }
        Matcher matcher = FENCED_CODE_BLOCK_PATTERN.matcher(content);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return content;
    }

    /**
     * 工具执行摘要，用于持久化到对话历史，避免完整源码污染后续上下文。
     */
    public String generateToolExecutedSummary(JSONObject arguments) {
        return generateToolExecutedResult(arguments);
    }

    /**
     * 生成工具执行结果格式（保存到数据库）
     *
     * @param arguments 工具执行参数
     * @return 格式化的工具执行结果
     */
    public abstract String generateToolExecutedResult(JSONObject arguments);
} 
