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
 * Application attachment uploaded by user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app_attachment")
public class AppAttachment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("appId")
    private Long appId;

    @Column("userId")
    private Long userId;

    @Column("fileName")
    private String fileName;

    @Column("fileType")
    private String fileType;

    @Column("mimeType")
    private String mimeType;

    @Column("fileSize")
    private Long fileSize;

    @Column("storagePath")
    private String storagePath;

    @Column("parsedContent")
    private String parsedContent;

    @Column("parseStatus")
    private String parseStatus;

    @Column("errorMessage")
    private String errorMessage;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
