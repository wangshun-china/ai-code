alter table ai_model_credential
    add column systemDefault tinyint default 0 not null comment '是否系统默认配置' after isDefault;

create index idx_user_system_default on ai_model_credential (userId, systemDefault);
