package com.ws.codecraft.ai.monitor;

import com.ws.codecraft.innerservice.InnerAiUsageRecordService;
import com.ws.codecraft.model.entity.AiUsageRecord;
import com.ws.codecraft.monitor.MonitorContext;
import com.ws.codecraft.monitor.MonitorContextHolder;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Records AI usage for Spring AI Alibaba calls.
 */
@Component
public class AiModelMonitorListener {

    @DubboReference
    private InnerAiUsageRecordService innerAiUsageRecordService;

    private final AiModelMetricsCollector aiModelMetricsCollector;

    public AiModelMonitorListener(AiModelMetricsCollector aiModelMetricsCollector) {
        this.aiModelMetricsCollector = aiModelMetricsCollector;
    }

    public SpringAiUsageTrace startSpringAiRequest(String modelName, String promptText) {
        MonitorContext monitorContext = MonitorContextHolder.getContext();
        if (monitorContext == null) {
            return SpringAiUsageTrace.empty(modelName, promptText);
        }
        String userId = monitorContext.getUserId();
        String appId = monitorContext.getAppId();
        String normalizedModelName = modelName != null ? modelName : "unknown";
        aiModelMetricsCollector.recordRequest(userId, appId, normalizedModelName, "started");
        AiUsageRecord record = AiUsageRecord.builder()
                .userId(userId != null ? userId : "unknown")
                .appId(appId)
                .modelName(normalizedModelName)
                .requestStatus("started")
                .requestTime(LocalDateTime.now())
                .build();
        AiUsageRecord savedRecord = innerAiUsageRecordService.saveRecord(record);
        Long recordId = savedRecord == null ? null : savedRecord.getId();
        return new SpringAiUsageTrace(recordId, monitorContext, normalizedModelName, promptText, Instant.now());
    }

    public void recordSpringAiSuccess(SpringAiUsageTrace trace, String responseText,
                                      Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        if (trace == null || trace.monitorContext() == null) {
            return;
        }
        String userId = trace.monitorContext().getUserId();
        String appId = trace.monitorContext().getAppId();
        aiModelMetricsCollector.recordRequest(userId, appId, trace.modelName(), "success");
        Long responseTimeMs = recordResponseTime(trace.startTime(), userId, appId, trace.modelName());
        long inputTokens = promptTokens != null ? promptTokens : estimateTokens(trace.promptText());
        long outputTokens = completionTokens != null ? completionTokens : estimateTokens(responseText);
        long resolvedTotalTokens = totalTokens != null ? totalTokens : inputTokens + outputTokens;
        if (trace.recordId() != null) {
            AiUsageRecord record = AiUsageRecord.builder()
                    .id(trace.recordId())
                    .userId(userId != null ? userId : "unknown")
                    .appId(appId)
                    .modelName(trace.modelName())
                    .requestStatus("success")
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .totalTokens(resolvedTotalTokens)
                    .responseTimeMs(responseTimeMs)
                    .requestTime(LocalDateTime.now())
                    .build();
            innerAiUsageRecordService.updateRecord(record);
        }
    }

    public void recordSpringAiError(SpringAiUsageTrace trace, Throwable error) {
        if (trace == null || trace.monitorContext() == null) {
            return;
        }
        String userId = trace.monitorContext().getUserId();
        String appId = trace.monitorContext().getAppId();
        String errorMessage = error == null ? "unknown" : error.getMessage();
        aiModelMetricsCollector.recordRequest(userId, appId, trace.modelName(), "error");
        aiModelMetricsCollector.recordError(userId, appId, trace.modelName(), errorMessage);
        Long responseTimeMs = recordResponseTime(trace.startTime(), userId, appId, trace.modelName());
        if (trace.recordId() != null) {
            AiUsageRecord record = AiUsageRecord.builder()
                    .id(trace.recordId())
                    .userId(userId != null ? userId : "unknown")
                    .appId(appId)
                    .modelName(trace.modelName())
                    .requestStatus("error")
                    .errorMessage(errorMessage)
                    .responseTimeMs(responseTimeMs)
                    .requestTime(LocalDateTime.now())
                    .build();
            innerAiUsageRecordService.updateRecord(record);
        }
    }

    private Long recordResponseTime(Instant startTime, String userId, String appId, String modelName) {
        Duration responseTime = Duration.between(startTime, Instant.now());
        aiModelMetricsCollector.recordResponseTime(userId, appId, modelName, responseTime);
        return responseTime.toMillis();
    }

    private long estimateTokens(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return content.length() / 4;
    }

    public record SpringAiUsageTrace(Long recordId, MonitorContext monitorContext, String modelName,
                                     String promptText, Instant startTime) {

        static SpringAiUsageTrace empty(String modelName, String promptText) {
            return new SpringAiUsageTrace(null, null, modelName, promptText, Instant.now());
        }
    }
}
