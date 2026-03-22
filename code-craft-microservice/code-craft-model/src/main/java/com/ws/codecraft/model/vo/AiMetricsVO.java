package com.ws.codecraft.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * AI 使用统计 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMetricsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 总请求次数
    private Long totalRequests;

    // 总 Token 消耗
    private Long totalTokens;

    // Input Tokens
    private Long inputTokens;

    // Output Tokens
    private Long outputTokens;

    // 平均响应时间(ms)
    private Double avgResponseTime;

    // 错误率(%)
    private Double errorRate;

    // 每日统计
    private List<DailyStat> dailyStats;

    // 模型统计
    private List<ModelStat> modelStats;

    // 用户统计
    private List<UserStat> userStats;

    /**
     * 每日统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyStat implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String date;
        private Long requests;
        private Long tokens;
        private Long inputTokens;
        private Long outputTokens;
        private Long errors;
        private Double avgResponseTime;
    }

    /**
     * 模型统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelStat implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String modelName;
        private Long requests;
        private Long totalTokens;
        private Long inputTokens;
        private Long outputTokens;
        private Long errors;
        private Double avgResponseTime;
    }

    /**
     * 用户统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserStat implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String userId;
        private String userName;
        private Long requests;
        private Long totalTokens;
        private Integer appCount;
    }
}
