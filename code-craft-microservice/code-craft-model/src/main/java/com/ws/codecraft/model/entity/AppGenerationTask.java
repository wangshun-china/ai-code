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
 * AI 生成/对话任务。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app_generation_task")
public class AppGenerationTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("appId")
    private Long appId;

    @Column("userId")
    private Long userId;

    private String mode;

    private String status;

    @Column("modelKey")
    private String modelKey;

    @Column("codeGenType")
    private String codeGenType;

    @Column("userMessage")
    private String userMessage;

    @Column("aiMessage")
    private String aiMessage;

    @Column("errorMessage")
    private String errorMessage;

    @Column("startTime")
    private LocalDateTime startTime;

    @Column("endTime")
    private LocalDateTime endTime;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
