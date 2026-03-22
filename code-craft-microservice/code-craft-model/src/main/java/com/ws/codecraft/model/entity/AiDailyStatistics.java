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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 每日统计实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ai_daily_statistics")
public class AiDailyStatistics implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 统计日期
     */
    private LocalDate statDate;

    /**
     * 用户ID（为空表示全局统计）
     */
    private String userId;

    /**
     * 应用ID（为空表示用户级统计）
     */
    private String appId;

    /**
     * 模型名称（为空表示全模型统计）
     */
    private String modelName;

    /**
     * 总请求次数
     */
    private Integer totalRequests;

    /**
     * 成功请求次数
     */
    private Integer successRequests;

    /**
     * 失败请求次数
     */
    private Integer errorRequests;

    /**
     * 输入Token总数
     */
    private Long inputTokens;

    /**
     * 输出Token总数
     */
    private Long outputTokens;

    /**
     * 总Token数
     */
    private Long totalTokens;

    /**
     * 平均响应时间(毫秒)
     */
    private Double avgResponseTime;

    /**
     * 创建时间
     */
    @Column(onInsertValue = "now()")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column(onInsertValue = "now()", onUpdateValue = "now()")
    private LocalDateTime updateTime;
}
