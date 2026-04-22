alter table app
    add column modelKey varchar(64) default 'qwen3.6-plus' not null comment 'AI 模型标识' after codeGenType;
