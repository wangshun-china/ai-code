package com.ws.codecraft.core.builder;

import lombok.Builder;
import lombok.Data;

/**
 * Vue project build result with diagnostic message for auto repair.
 */
@Data
@Builder
public class VueProjectBuildResult {

    private boolean success;

    private String message;

    private Integer status;

    public static VueProjectBuildResult success(String message) {
        return VueProjectBuildResult.builder()
                .success(true)
                .message(message)
                .build();
    }

    public static VueProjectBuildResult failure(String message) {
        return VueProjectBuildResult.builder()
                .success(false)
                .message(message)
                .build();
    }
}
