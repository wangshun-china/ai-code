create table if not exists ai_model_credential
(
    id         bigint auto_increment comment '凭据ID' primary key,
    userId     bigint                                not null comment '用户ID',
    name       varchar(128) default '自定义模型配置'   not null comment '配置名称',
    apiKey     varchar(512)                          not null comment 'API Key',
    baseUrl    varchar(512)                          not null comment 'OpenAI-compatible Base URL',
    modelNames text                                  not null comment '可用模型名称，逗号或换行分隔',
    isDefault  tinyint      default 0                not null comment '是否当前默认配置',
    createTime datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint      default 0                not null comment '是否删除',
    index idx_userId (userId),
    index idx_user_default (userId, isDefault)
) comment '用户 AI 模型凭据配置' collate = utf8mb4_unicode_ci;
