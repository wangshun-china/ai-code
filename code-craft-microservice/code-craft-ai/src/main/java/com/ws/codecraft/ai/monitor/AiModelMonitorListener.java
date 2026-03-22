package com.ws.codecraft.ai.monitor;

import com.ws.codecraft.innerservice.InnerAiUsageRecordService;
import com.ws.codecraft.model.entity.AiUsageRecord;
import com.ws.codecraft.monitor.MonitorContext;
import com.ws.codecraft.monitor.MonitorContextHolder;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AI 模型监听器
 */
@Component
public class AiModelMonitorListener implements ChatModelListener {

    // 用于存储请求开始时间的键
    private static final String REQUEST_START_TIME_KEY = "request_start_time";
    // 用于监控上下文传递（因为请求和响应事件的触发不是同一个线程）
    private static final String MONITOR_CONTEXT_KEY = "monitor_context";
    // 用于存储记录ID，以便更新
    private static final String RECORD_ID_KEY = "record_id";

    @Resource
    private AiModelMetricsCollector aiModelMetricsCollector;

    @DubboReference
    private InnerAiUsageRecordService innerAiUsageRecordService;

    // 临时存储正在进行的请求记录（用于更新）
    private final Map<String, Long> pendingRecords = new ConcurrentHashMap<>();

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        // 获取当前时间戳
        requestContext.attributes().put(REQUEST_START_TIME_KEY, Instant.now());
        // 从监控上下文中获取信息
        MonitorContext monitorContext = MonitorContextHolder.getContext();
        if (monitorContext != null) {
            String userId = monitorContext.getUserId();
            String appId = monitorContext.getAppId();
            requestContext.attributes().put(MONITOR_CONTEXT_KEY, monitorContext);
            // 获取模型名称
            String modelName = requestContext.chatRequest().modelName();
            // 记录请求指标
            aiModelMetricsCollector.recordRequest(userId, appId, modelName, "started");

            // 保存初始记录到数据库
            AiUsageRecord record = AiUsageRecord.builder()
                    .userId(userId != null ? userId : "unknown")
                    .appId(appId)
                    .modelName(modelName != null ? modelName : "unknown")
                    .requestStatus("started")
                    .requestTime(LocalDateTime.now())
                    .build();
            AiUsageRecord savedRecord = innerAiUsageRecordService.saveRecord(record);

            // 保存记录ID到属性中，用于后续更新
            if (savedRecord != null && savedRecord.getId() != null) {
                requestContext.attributes().put(RECORD_ID_KEY, savedRecord.getId());
            }
        }
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        // 从属性中获取监控信息（由 onRequest 方法存储）
        Map<Object, Object> attributes = responseContext.attributes();
        // 从监控上下文中获取信息
        MonitorContext context = (MonitorContext) attributes.get(MONITOR_CONTEXT_KEY);
        if (context != null) {
            String userId = context.getUserId();
            String appId = context.getAppId();
            // 获取模型名称
            String modelName = responseContext.chatResponse().modelName();
            // 记录成功请求
            aiModelMetricsCollector.recordRequest(userId, appId, modelName, "success");
            // 记录响应时间
            Long responseTimeMs = recordResponseTime(attributes, userId, appId, modelName);
            // 记录 Token 使用情况
            TokenUsage tokenUsage = responseContext.chatResponse().metadata().tokenUsage();

            // 如果没有 Token 信息，手动估算 (约1 token ≈ 4 字符)
            long inputTokens = 0;
            long outputTokens = 0;
            long totalTokens = 0;

            if (tokenUsage != null) {
                inputTokens = tokenUsage.inputTokenCount();
                outputTokens = tokenUsage.outputTokenCount();
                totalTokens = tokenUsage.totalTokenCount();
            } else {
                // 手动估算：从请求消息和响应内容估算
                inputTokens = estimateTokens(responseContext.chatRequest().messages());
                outputTokens = estimateTokensFromContent(responseContext.chatResponse().aiMessage().text());
                totalTokens = inputTokens + outputTokens;
            }

            // 更新数据库记录
            Long recordId = (Long) attributes.get(RECORD_ID_KEY);
            if (recordId != null) {
                AiUsageRecord record = AiUsageRecord.builder()
                        .id(recordId)
                        .userId(userId != null ? userId : "unknown")
                        .appId(appId)
                        .modelName(modelName != null ? modelName : "unknown")
                        .requestStatus("success")
                        .inputTokens(inputTokens)
                        .outputTokens(outputTokens)
                        .totalTokens(totalTokens)
                        .responseTimeMs(responseTimeMs)
                        .requestTime(LocalDateTime.now())
                        .build();
                innerAiUsageRecordService.updateRecord(record);
            }
        }
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        // 从监控上下文中获取信息
        MonitorContext context = MonitorContextHolder.getContext();
        Map<Object, Object> attributes = errorContext.attributes();
        if (context != null) {
            String userId = context.getUserId();
            String appId = context.getAppId();
            // 获取模型名称和错误类型
            String modelName = errorContext.chatRequest().modelName();
            String errorMessage = errorContext.error().getMessage();
            // 记录失败请求
            aiModelMetricsCollector.recordRequest(userId, appId, modelName, "error");
            aiModelMetricsCollector.recordError(userId, appId, modelName, errorMessage);
            // 记录响应时间（即使是错误响应）
            Long responseTimeMs = recordResponseTime(attributes, userId, appId, modelName);

            // 更新数据库记录
            Long recordId = (Long) attributes.get(RECORD_ID_KEY);
            if (recordId != null) {
                AiUsageRecord record = AiUsageRecord.builder()
                        .id(recordId)
                        .userId(userId != null ? userId : "unknown")
                        .appId(appId)
                        .modelName(modelName != null ? modelName : "unknown")
                        .requestStatus("error")
                        .errorMessage(errorMessage)
                        .responseTimeMs(responseTimeMs)
                        .requestTime(LocalDateTime.now())
                        .build();
                innerAiUsageRecordService.updateRecord(record);
            }
        }
    }

    /**
     * 记录响应时间
     */
    private Long recordResponseTime(Map<Object, Object> attributes, String userId, String appId, String modelName) {
        Instant startTime = (Instant) attributes.get(REQUEST_START_TIME_KEY);
        if (startTime != null) {
            Duration responseTime = Duration.between(startTime, Instant.now());
            aiModelMetricsCollector.recordResponseTime(userId, appId, modelName, responseTime);
            return responseTime.toMillis();
        }
        return null;
    }

    /**
     * 估算 Token 数（基于字符数，约4字符 ≈ 1 token）
     */
    private long estimateTokens(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        String text = messages.stream()
                .map(msg -> msg.toString())
                .collect(Collectors.joining(" "));
        return text.length() / 4;
    }

    /**
     * 从响应内容估算 Token 数
     */
    private long estimateTokensFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return content.length() / 4;
    }
}
