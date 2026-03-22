package com.ws.codecraft.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * 数据库表初始化器
 * 应用启动时自动创建不存在的表
 */
@Component
@Slf4j
public class DatabaseTableInitializer {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    @Transactional
    public void initializeTables() {
        log.info("开始初始化数据库表...");

        try {
            // 检查并创建 ai_usage_record 表
            if (!tableExists("ai_usage_record")) {
                log.info("创建 ai_usage_record 表...");
                createAiUsageRecordTable();
            }

            // 检查并创建 ai_daily_statistics 表
            if (!tableExists("ai_daily_statistics")) {
                log.info("创建 ai_daily_statistics 表...");
                createAiDailyStatisticsTable();
            }

            log.info("数据库表初始化完成");
        } catch (Exception e) {
            log.error("数据库表初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 检查表是否存在
     */
    private boolean tableExists(String tableName) {
        try {
            Connection conn = jdbcTemplate.getDataSource().getConnection();
            ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null);
            boolean exists = rs.next();
            rs.close();
            conn.close();
            return exists;
        } catch (SQLException e) {
            log.error("检查表是否存在失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 创建 ai_usage_record 表
     */
    private void createAiUsageRecordTable() {
        String sql = """
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
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI使用记录表'
            """;
        jdbcTemplate.execute(sql);
        log.info("ai_usage_record 表创建成功");
    }

    /**
     * 创建 ai_daily_statistics 表
     */
    private void createAiDailyStatisticsTable() {
        String sql = """
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
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI每日统计表'
            """;
        jdbcTemplate.execute(sql);
        log.info("ai_daily_statistics 表创建成功");
    }
}
