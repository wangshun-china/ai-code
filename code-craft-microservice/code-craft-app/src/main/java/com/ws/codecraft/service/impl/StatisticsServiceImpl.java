package com.ws.codecraft.service.impl;

import com.ws.codecraft.innerservice.InnerUserService;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统计服务实现。
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
    private InnerUserService userService;

    @Override
    public AiMetricsVO getAiMetrics(String startDate, String endDate) {
        DateRange dateRange = resolveDateRange(startDate, endDate);
        String sql = """
                SELECT
                    COALESCE(SUM(total_tokens), 0) as total_tokens,
                    COALESCE(SUM(input_tokens), 0) as input_tokens,
                    COALESCE(SUM(output_tokens), 0) as output_tokens,
                    COUNT(*) as total_requests,
                    SUM(CASE WHEN request_status = 'error' THEN 1 ELSE 0 END) as total_errors,
                    COALESCE(AVG(response_time_ms), 0) as avg_response_time
                FROM ai_usage_record
                WHERE request_time >= ? AND request_time < ?
                """;

        Map<String, Object> result = jdbcTemplate.queryForMap(sql, dateRange.start(), dateRange.endExclusive());
        long totalTokens = getLongValue(result, "total_tokens");
        long inputTokens = getLongValue(result, "input_tokens");
        long outputTokens = getLongValue(result, "output_tokens");
        long totalRequests = getLongValue(result, "total_requests");
        long totalErrors = getLongValue(result, "total_errors");
        double avgResponseTime = getDoubleValue(result, "avg_response_time");
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
                .userStats(getUserTokenRanking(10, dateRange))
                .build();
    }

    @Override
    public List<AiMetricsVO.UserStat> getUserTokenRanking(Integer limit) {
        return getUserTokenRanking(limit, null);
    }

    @Override
    public List<AiMetricsVO.ModelStat> getModelStats(String startDate, String endDate) {
        DateRange dateRange = resolveDateRange(startDate, endDate);
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
                WHERE request_time >= ? AND request_time < ?
                GROUP BY model_name
                ORDER BY total_tokens DESC
                """;

        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql, dateRange.start(), dateRange.endExclusive());
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
        DateRange dateRange = resolveDateRange(startDate, endDate);
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

        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql, dateRange.start(), dateRange.endExclusive());
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

    private List<AiMetricsVO.UserStat> getUserTokenRanking(Integer limit, DateRange dateRange) {
        StringBuilder sqlBuilder = new StringBuilder("""
                SELECT
                    user_id,
                    SUM(total_tokens) as total_tokens,
                    COUNT(*) as total_requests,
                    SUM(input_tokens) as input_tokens,
                    SUM(output_tokens) as output_tokens
                FROM ai_usage_record
                """);
        List<Object> params = new ArrayList<>();
        if (dateRange != null) {
            sqlBuilder.append(" WHERE request_time >= ? AND request_time < ? ");
            params.add(dateRange.start());
            params.add(dateRange.endExclusive());
        }
        sqlBuilder.append("""
                GROUP BY user_id
                ORDER BY total_tokens DESC
                LIMIT ?
                """);
        params.add(limit);

        List<Map<String, Object>> records = jdbcTemplate.queryForList(sqlBuilder.toString(), params.toArray());
        if (records.isEmpty()) {
            return List.of();
        }

        Map<Long, User> userMap = loadUsers(records);
        Map<Long, Integer> appCountMap = loadAppCounts(userMap.keySet());
        return buildUserStats(records, userMap, appCountMap);
    }

    private Map<Long, User> loadUsers(List<Map<String, Object>> records) {
        List<Long> userIds = records.stream()
                .map(record -> parseUserId((String) record.get("user_id")))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    private Map<Long, Integer> loadAppCounts(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = userIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT user_id, COUNT(*) AS app_count FROM app WHERE user_id IN (" + placeholders + ") GROUP BY user_id";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userIds.toArray());
        Map<Long, Integer> appCountMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object userId = row.get("user_id");
            Object appCount = row.get("app_count");
            if (userId instanceof Number userIdNumber && appCount instanceof Number appCountNumber) {
                appCountMap.put(userIdNumber.longValue(), appCountNumber.intValue());
            }
        }
        return appCountMap;
    }

    private List<AiMetricsVO.UserStat> buildUserStats(List<Map<String, Object>> records,
                                                      Map<Long, User> userMap,
                                                      Map<Long, Integer> appCountMap) {
        List<AiMetricsVO.UserStat> result = new ArrayList<>(records.size());
        for (Map<String, Object> record : records) {
            String userId = (String) record.get("user_id");
            if (userId == null) {
                continue;
            }
            Long userIdLong = parseUserId(userId);
            User user = userIdLong == null ? null : userMap.get(userIdLong);
            result.add(AiMetricsVO.UserStat.builder()
                    .userId(userId)
                    .userName(user != null ? user.getUserName() : "未知用户")
                    .totalTokens(getLongValue(record, "total_tokens"))
                    .requests(getLongValue(record, "total_requests"))
                    .appCount(userIdLong == null ? 0 : appCountMap.getOrDefault(userIdLong, 0))
                    .build());
        }
        return result;
    }

    private Long parseUserId(String userId) {
        if (userId == null) {
            return null;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            log.warn("Invalid user id in ai_usage_record: {}", userId);
            return null;
        }
    }

    private DateRange resolveDateRange(String startDate, String endDate) {
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusDays(7);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        return new DateRange(start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    private long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0L;
        }
        return ((Number) value).longValue();
    }

    private double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0.0;
        }
        return ((Number) value).doubleValue();
    }

    private record DateRange(LocalDateTime start, LocalDateTime endExclusive) {
    }
}
