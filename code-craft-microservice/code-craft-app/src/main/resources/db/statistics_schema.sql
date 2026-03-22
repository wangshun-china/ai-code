-- AI 使用记录表（每次AI调用一条记录）
CREATE TABLE IF NOT EXISTS ai_usage_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    app_id VARCHAR(64) COMMENT '应用ID',
    model_name VARCHAR(64) NOT NULL COMMENT '模型名称',
    request_status VARCHAR(20) NOT NULL COMMENT '请求状态: started, success, error',
    input_tokens BIGINT DEFAULT 0 COMMENT '输入Token数',
    output_tokens BIGINT DEFAULT 0 COMMENT '输出Token数',
    total_tokens BIGINT DEFAULT 0 COMMENT '总Token数',
    response_time_ms BIGINT COMMENT '响应时间(毫秒)',
    error_message TEXT COMMENT '错误信息',
    request_time DATETIME NOT NULL COMMENT '请求时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_app_id (app_id),
    INDEX idx_model_name (model_name),
    INDEX idx_request_time (request_time),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI使用记录表';

-- AI 每日统计表（每日汇总数据）
CREATE TABLE IF NOT EXISTS ai_daily_statistics (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    stat_date DATE NOT NULL COMMENT '统计日期',
    user_id VARCHAR(64) COMMENT '用户ID（为空表示全局统计）',
    app_id VARCHAR(64) COMMENT '应用ID（为空表示用户级统计）',
    model_name VARCHAR(64) COMMENT '模型名称（为空表示全模型统计）',
    total_requests INT DEFAULT 0 COMMENT '总请求次数',
    success_requests INT DEFAULT 0 COMMENT '成功请求次数',
    error_requests INT DEFAULT 0 COMMENT '失败请求次数',
    input_tokens BIGINT DEFAULT 0 COMMENT '输入Token总数',
    output_tokens BIGINT DEFAULT 0 COMMENT '输出Token总数',
    total_tokens BIGINT DEFAULT 0 COMMENT '总Token数',
    avg_response_time DOUBLE DEFAULT 0 COMMENT '平均响应时间(毫秒)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_stat_date_user_app_model (stat_date, user_id, app_id, model_name),
    INDEX idx_stat_date (stat_date),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI每日统计表';
