package com.ws.codecraft.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 使用记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ai_usage_record")
public class AiUsageRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 应用ID
     */
    private String appId;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 请求状态: started, success, error
     */
    private String requestStatus;

    /**
     * 输入Token数
     */
    private Long inputTokens;

    /**
     * 输出Token数
     */
    private Long outputTokens;

    /**
     * 总Token数
     */
    private Long totalTokens;

    /**
     * 响应时间(毫秒)
     */
    private Long responseTimeMs;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 请求时间
     */
    private LocalDateTime requestTime;

    /**
     * 创建时间
     */
    @Column(onInsertValue = "now()")
    private LocalDateTime createTime;
}
