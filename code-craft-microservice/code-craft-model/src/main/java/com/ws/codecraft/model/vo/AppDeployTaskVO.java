package com.ws.codecraft.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Deploy task view object.
 */
@Data
public class AppDeployTaskVO implements Serializable {

    private Long id;

    private Long appId;

    private Long userId;

    private String status;

    private String currentStep;

    private String deployKey;

    private String deployUrl;

    private String logText;

    private String errorMessage;

    private Integer retryCount;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}
