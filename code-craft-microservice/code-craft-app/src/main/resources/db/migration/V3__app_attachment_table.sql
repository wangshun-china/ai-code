create table if not exists app_attachment
(
    id            bigint auto_increment comment '附件ID' primary key,
    appId         bigint                                not null comment '应用ID',
    userId        bigint                                not null comment '用户ID',
    fileName      varchar(255)                          not null comment '原始文件名',
    fileType      varchar(32)                           not null comment '文件类型',
    mimeType      varchar(128)                          null comment 'MIME 类型',
    fileSize      bigint                                not null comment '文件大小',
    storagePath   varchar(512)                          not null comment '本地存储路径',
    parsedContent mediumtext                            null comment '附件解析摘要',
    parseStatus   varchar(32) default 'pending'         not null comment '解析状态',
    errorMessage  text                                  null comment '解析错误',
    createTime    datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint     default 0                 not null comment '是否删除',
    index idx_appId (appId),
    index idx_userId (userId),
    index idx_parseStatus (parseStatus),
    index idx_createTime (createTime)
) comment '应用附件' collate = utf8mb4_unicode_ci;
