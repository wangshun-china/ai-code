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
 * App deploy task entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app_deploy_task")
public class AppDeployTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("appId")
    private Long appId;

    @Column("userId")
    private Long userId;

    private String status;

    @Column("currentStep")
    private String currentStep;

    @Column("deployKey")
    private String deployKey;

    @Column("deployUrl")
    private String deployUrl;

    @Column("logText")
    private String logText;

    @Column("errorMessage")
    private String errorMessage;

    @Column("retryCount")
    private Integer retryCount;

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
