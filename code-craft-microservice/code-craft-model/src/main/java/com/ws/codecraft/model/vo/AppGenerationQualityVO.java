package com.ws.codecraft.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * App generation quality summary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppGenerationQualityVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long taskId;

    private Long appId;

    private Long userId;

    private String modelKey;

    private String codeGenType;

    private String status;

    private String userMessage;

    private String aiOutputPreview;

    private QualityMetrics qualityMetrics;

    private LocalDateTime createTime;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityMetrics implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long appId;

        private String codeGenType;

        private String modelKey;

        private Boolean usedPlan;

        private Integer streamChunkCount;

        private Integer generatedCharCount;

        private Long durationMs;

        private Boolean sourceDirExists;

        private Integer generatedFileCount;

        private Boolean buildSuccess;
    }
}
