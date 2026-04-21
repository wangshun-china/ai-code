# 数据库初始化
# AI 零代码应用生成平台

-- 创建库
create database if not exists ai_code_gen;

-- 切换库
use ai_code_gen;

-- 授权给 ai_code_gen_user 用户访问业务数据库
GRANT ALL PRIVILEGES ON ai_code_gen.* TO 'ai_code_gen_user'@'%';
FLUSH PRIVILEGES;

-- 用户表
-- 以下是建表语句

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 应用表
create table app
(
    id           bigint auto_increment comment 'id' primary key,
    appName      varchar(256)                       null comment '应用名称',
    cover        varchar(512)                       null comment '应用封面',
    initPrompt   text                               null comment '应用初始化的 prompt',
    codeGenType  varchar(64)                        null comment '代码生成类型（枚举）',
    deployKey    varchar(64)                        null comment '部署标识',
    deployedTime datetime                           null comment '部署时间',
    status       varchar(32) default 'draft'         not null comment '应用生命周期状态',
    priority     int      default 0                 not null comment '优先级',
    userId       bigint                             not null comment '创建用户id',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    UNIQUE KEY uk_deployKey (deployKey), -- 确保部署标识唯一
    INDEX idx_appName (appName),         -- 提升基于应用名称的查询性能
    INDEX idx_userId (userId)            -- 提升基于用户 ID 的查询性能
) comment '应用' collate = utf8mb4_unicode_ci;

-- 应用部署任务表
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

-- 应用版本记录表
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

-- 对话历史表
create table chat_history
(
    id          bigint auto_increment comment 'id' primary key,
    message     text                               not null comment '消息',
    messageType varchar(32)                        not null comment 'user/ai',
    appId       bigint                             not null comment '应用id',
    userId      bigint                             not null comment '创建用户id',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    INDEX idx_appId (appId),                       -- 提升基于应用的查询性能
    INDEX idx_createTime (createTime),             -- 提升基于时间的查询性能
    INDEX idx_appId_createTime (appId, createTime) -- 游标查询核心索引
) comment '对话历史' collate = utf8mb4_unicode_ci;
