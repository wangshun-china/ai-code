package com.ws.codecraft.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * App generation plan request.
 */
@Data
public class AppGeneratePlanRequest implements Serializable {

    private Long appId;

    private String message;

    private static final long serialVersionUID = 1L;
}
