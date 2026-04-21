package com.ws.codecraft.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * Deploy submit result.
 */
@Data
public class AppDeployResultVO implements Serializable {

    private Long taskId;

    private Long appId;

    private String deployKey;

    private String deployUrl;

    private String status;

    private static final long serialVersionUID = 1L;
}
