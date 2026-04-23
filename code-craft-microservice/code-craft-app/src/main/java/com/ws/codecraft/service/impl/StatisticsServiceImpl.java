package com.ws.codecraft.service.impl;

import com.ws.codecraft.innerservice.InnerUserService;
import com.ws.codecraft.mapper.StatisticsMapper;
import com.ws.codecraft.model.entity.User;
import com.ws.codecraft.model.vo.AiMetricsVO;
import com.ws.codecraft.service.StatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import jakarta.annotation.Resource;
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

    private static final int DEFAULT_RANKING_LIMIT = 10;

    private static final int MAX_RANKING_LIMIT = 100;

    @Resource
    private StatisticsMapper statisticsMapper;

    @DubboReference
    private InnerUserService userService;

    @Override
    public AiMetricsVO getAiMetrics(String startDate, String endDate) {
        DateRange dateRange = resolveDateRange(startDate, endDate);
        Map<String, Object> result = statisticsMapper.selectTotalMetrics(dateRange.start(), dateRange.endExclusive());
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
        List<Map<String, Object>> records = statisticsMapper.selectModelStats(dateRange.start(), dateRange.endExclusive());
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
        List<Map<String, Object>> records = statisticsMapper.selectDailyStats(dateRange.start(), dateRange.endExclusive());
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
        int safeLimit = normalizeLimit(limit);
        LocalDateTime start = dateRange == null ? null : dateRange.start();
        LocalDateTime endExclusive = dateRange == null ? null : dateRange.endExclusive();
        List<Map<String, Object>> records = statisticsMapper.selectUserTokenRanking(start, endExclusive, safeLimit);
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
        try {
            return userService.listByIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
        } catch (Exception e) {
            log.warn("批量加载统计用户信息失败，将使用未知用户兜底, userIds={}", userIds, e);
            return Collections.emptyMap();
        }
    }

    private Map<Long, Integer> loadAppCounts(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = statisticsMapper.selectAppCountsByUserIds(userIds);
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
        LocalDate start = parseDateOrDefault(startDate, LocalDate.now().minusDays(7));
        LocalDate end = parseDateOrDefault(endDate, LocalDate.now());
        if (end.isBefore(start)) {
            log.warn("统计日期范围非法，已自动调整: startDate={}, endDate={}", startDate, endDate);
            end = start;
        }
        return new DateRange(start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    private LocalDate parseDateOrDefault(String date, LocalDate defaultValue) {
        if (date == null || date.isBlank()) {
            return defaultValue;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (Exception e) {
            log.warn("统计日期参数格式非法，使用默认值: date={}, default={}", date, defaultValue);
            return defaultValue;
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_RANKING_LIMIT;
        }
        return Math.min(limit, MAX_RANKING_LIMIT);
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
