package com.ws.codecraft.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Application attachment view object.
 */
@Data
public class AppAttachmentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long appId;

    private String fileName;

    private String fileType;

    private String mimeType;

    private Long fileSize;

    private String parsedContent;

    private String parseStatus;

    private String errorMessage;

    private LocalDateTime createTime;
}
