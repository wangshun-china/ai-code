package com.ws.codecraft.service.impl;

import com.ws.codecraft.mapper.AppMapper;
import com.ws.codecraft.model.entity.User;
import com.ws.codecraft.model.vo.AiMetricsVO;
import com.ws.codecraft.service.AiDailyStatisticsService;
import com.ws.codecraft.service.AiUsageRecordService;
import com.ws.codecraft.service.StatisticsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 统计服务实现 - 从数据库读取数据
 */
@Service
@Slf4j
public class StatisticsServiceImpl implements StatisticsService {

    @Resource
    private AppMapper appMapper;

    @Resource
    private AiUsageRecordService aiUsageRecordService;

    @Resource
    private AiDailyStatisticsService aiDailyStatisticsService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @DubboReference
    private com.ws.codecraft.innerservice.InnerUserService userService;

    @Override
    public AiMetricsVO getAiMetrics(String startDate, String endDate) {
        // 查询使用记录统计
        String sql = """
            SELECT
                COALESCE(SUM(total_tokens), 0) as total_tokens,
                COALESCE(SUM(input_tokens), 0) as input_tokens,
                COALESCE(SUM(output_tokens), 0) as output_tokens,
                COUNT(*) as total_requests,
                SUM(CASE WHEN request_status = 'error' THEN 1 ELSE 0 END) as total_errors,
                COALESCE(AVG(response_time_ms), 0) as avg_response_time
            FROM ai_usage_record
            """;

        Map<String, Object> result = jdbcTemplate.queryForMap(sql);

        long totalTokens = getLongValue(result, "total_tokens");
        long inputTokens = getLongValue(result, "input_tokens");
        long outputTokens = getLongValue(result, "output_tokens");
        long totalRequests = getLongValue(result, "total_requests");
        long totalErrors = getLongValue(result, "total_errors");
        double avgResponseTime = getDoubleValue(result, "avg_response_time");

        // 计算错误率
        double errorRate = totalRequests > 0 ? (double) totalErrors / totalRequests * 100 : 0;

        return AiMetricsVO.builder()
                .totalRequests(totalRequests)
                .totalTokens(totalTokens)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .avgResponseTime(avgResponseTime)
                .errorRate(errorRate)
                .dailyStats(getDailyStats(startDate, endDate))
                .modelStats(getModelStats(startDate, endDate))
                .userStats(getUserTokenRanking(10))
                .build();
    }

    @Override
    public List<AiMetricsVO.UserStat> getUserTokenRanking(Integer limit) {
        String sql = """
            SELECT
                user_id,
                SUM(total_tokens) as total_tokens,
                COUNT(*) as total_requests,
                SUM(input_tokens) as input_tokens,
                SUM(output_tokens) as output_tokens
            FROM ai_usage_record
            GROUP BY user_id
            ORDER BY total_tokens DESC
            LIMIT ?
            """;

        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql, limit);

        List<AiMetricsVO.UserStat> result = new ArrayList<>();
        for (Map<String, Object> record : records) {
            String userId = (String) record.get("user_id");
            if (userId == null) continue;
            try {
                User user = userService.getById(Long.parseLong(userId));
                // 统计用户的应用数量
                String countSql = "SELECT COUNT(*) FROM app WHERE user_id = ?";
                Long appCount = jdbcTemplate.queryForObject(countSql, Long.class, userId);

                result.add(AiMetricsVO.UserStat.builder()
                        .userId(userId)
                        .userName(user != null ? user.getUserName() : "未知用户")
                        .totalTokens(getLongValue(record, "total_tokens"))
                        .requests(getLongValue(record, "total_requests"))
                        .appCount(appCount != null ? appCount.intValue() : 0)
                        .build());
            } catch (Exception e) {
                log.warn("获取用户信息失败: {}", userId);
                // 即使获取用户信息失败，仍然添加统计数据
                result.add(AiMetricsVO.UserStat.builder()
                        .userId(userId)
                        .userName("未知用户")
                        .totalTokens(getLongValue(record, "total_tokens"))
                        .requests(getLongValue(record, "total_requests"))
                        .appCount(0)
                        .build());
            }
        }

        return result;
    }

    @Override
    public List<AiMetricsVO.ModelStat> getModelStats(String startDate, String endDate) {
        String sql = """
            SELECT
                model_name,
                SUM(total_tokens) as total_tokens,
                COUNT(*) as total_requests,
                SUM(input_tokens) as input_tokens,
                SUM(output_tokens) as output_tokens,
                SUM(CASE WHEN request_status = 'error' THEN 1 ELSE 0 END) as total_errors,
                AVG(response_time_ms) as avg_response_time
            FROM ai_usage_record
            GROUP BY model_name
            ORDER BY total_tokens DESC
            """;

        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql);

        return records.stream().map(record -> AiMetricsVO.ModelStat.builder()
                .modelName((String) record.get("model_name"))
                .requests(getLongValue(record, "total_requests"))
                .totalTokens(getLongValue(record, "total_tokens"))
                .inputTokens(getLongValue(record, "input_tokens"))
                .outputTokens(getLongValue(record, "output_tokens"))
                .errors(getLongValue(record, "total_errors"))
                .avgResponseTime(getDoubleValue(record, "avg_response_time"))
                .build()).toList();
    }

    @Override
    public List<AiMetricsVO.DailyStat> getDailyStats(String startDate, String endDate) {
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusDays(7);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();

        String sql = """
            SELECT
                DATE(request_time) as stat_date,
                COUNT(*) as total_requests,
                SUM(total_tokens) as total_tokens,
                SUM(input_tokens) as input_tokens,
                SUM(output_tokens) as output_tokens,
                SUM(CASE WHEN request_status = 'error' THEN 1 ELSE 0 END) as total_errors,
                AVG(response_time_ms) as avg_response_time
            FROM ai_usage_record
            WHERE request_time >= ? AND request_time < ?
            GROUP BY DATE(request_time)
            ORDER BY stat_date ASC
            """;

        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql,
                start.atStartOfDay(), end.plusDays(1).atStartOfDay());

        return records.stream().map(record -> AiMetricsVO.DailyStat.builder()
                .date(record.get("stat_date").toString())
                .requests(getLongValue(record, "total_requests"))
                .tokens(getLongValue(record, "total_tokens"))
                .inputTokens(getLongValue(record, "input_tokens"))
                .outputTokens(getLongValue(record, "output_tokens"))
                .errors(getLongValue(record, "total_errors"))
                .avgResponseTime(getDoubleValue(record, "avg_response_time"))
                .build()).toList();
    }

    /**
     * 安全获取 Long 值
     */
    private long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0L;
        }
        return ((Number) value).longValue();
    }

    /**
     * 安全获取 Double 值
     */
    private double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0.0;
        }
        return ((Number) value).doubleValue();
    }
}
