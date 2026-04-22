create table if not exists app_generation_task
(
    id           bigint auto_increment comment '任务ID' primary key,
    appId        bigint                               not null comment '应用ID',
    userId       bigint                               not null comment '用户ID',
    mode         varchar(32)                          not null comment '任务模式: chat/plan/generate',
    status       varchar(32) default 'pending'        not null comment '任务状态',
    modelKey     varchar(64)                          null comment 'AI 模型标识',
    codeGenType  varchar(64)                          null comment '代码生成类型',
    userMessage  text                                 null comment '用户输入',
    aiMessage    mediumtext                           null comment 'AI 输出',
    errorMessage text                                 null comment '错误信息',
    startTime    datetime                             null comment '开始时间',
    endTime      datetime                             null comment '结束时间',
    createTime   datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint     default 0                not null comment '是否删除',
    index idx_appId (appId),
    index idx_userId (userId),
    index idx_mode (mode),
    index idx_status (status),
    index idx_createTime (createTime)
) comment 'AI 生成/对话任务' collate = utf8mb4_unicode_ci;
