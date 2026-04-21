alter table app
    add column status varchar(32) default 'draft' not null comment '应用生命周期状态' after deployedTime;

create table if not exists app_deploy_task
(
    id           bigint auto_increment comment '任务ID' primary key,
    appId        bigint                               not null comment '应用ID',
    userId       bigint                               not null comment '用户ID',
    status       varchar(32) default 'pending'        not null comment '任务状态',
    currentStep  varchar(64)                          null comment '当前步骤',
    deployKey    varchar(64)                          null comment '部署标识',
    deployUrl    varchar(512)                         null comment '部署地址',
    logText      mediumtext                           null comment '部署日志',
    errorMessage text                                 null comment '错误信息',
    retryCount   int         default 0                not null comment '重试次数',
    startTime    datetime                             null comment '开始时间',
    endTime      datetime                             null comment '结束时间',
    createTime   datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint     default 0                not null comment '是否删除',
    index idx_appId (appId),
    index idx_userId (userId),
    index idx_status (status),
    index idx_createTime (createTime)
) comment '应用部署任务' collate = utf8mb4_unicode_ci;

create table if not exists app_version
(
    id             bigint auto_increment comment '版本ID' primary key,
    appId          bigint                              not null comment '应用ID',
    versionNo      int                                 not null comment '版本号',
    sourceType     varchar(32)                         not null comment '来源类型',
    codeGenType    varchar(64)                         null comment '代码生成类型',
    codePath       varchar(512)                        null comment '代码路径',
    deployKey      varchar(64)                         null comment '部署标识',
    promptSnapshot text                                null comment '提示词快照',
    createBy       bigint                              null comment '创建人',
    createTime     datetime default CURRENT_TIMESTAMP  not null comment '创建时间',
    updateTime     datetime default CURRENT_TIMESTAMP  not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete       tinyint  default 0                  not null comment '是否删除',
    unique key uk_app_version (appId, versionNo),
    index idx_appId (appId),
    index idx_createBy (createBy),
    index idx_createTime (createTime)
) comment '应用版本记录' collate = utf8mb4_unicode_ci;
