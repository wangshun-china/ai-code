package com.ws.codecraft.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * App generation plan.
 */
@Data
public class AppGenerationPlanVO implements Serializable {

    private Long appId;

    private String planId;

    private String message;

    private String plan;

    private static final long serialVersionUID = 1L;
}
