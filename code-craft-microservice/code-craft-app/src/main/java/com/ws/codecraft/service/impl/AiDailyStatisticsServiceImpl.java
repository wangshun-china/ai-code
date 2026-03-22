package com.ws.codecraft.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.ws.codecraft.mapper.AiDailyStatisticsMapper;
import com.ws.codecraft.model.entity.AiDailyStatistics;
import com.ws.codecraft.service.AiDailyStatisticsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 每日统计服务实现
 */
@Service
@Slf4j
public class AiDailyStatisticsServiceImpl implements AiDailyStatisticsService {

    @Resource
    private AiDailyStatisticsMapper aiDailyStatisticsMapper;

    @Override
    public AiDailyStatistics getByDate(LocalDate date, String userId, String appId, String modelName) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(AiDailyStatistics::getStatDate).eq(date);

        if (userId != null) {
            wrapper.and(AiDailyStatistics::getUserId).eq(userId);
        } else {
            wrapper.and(AiDailyStatistics::getUserId).isNull();
        }

        if (appId != null) {
            wrapper.and(AiDailyStatistics::getAppId).eq(appId);
        } else {
            wrapper.and(AiDailyStatistics::getAppId).isNull();
        }

        if (modelName != null) {
            wrapper.and(AiDailyStatistics::getModelName).eq(modelName);
        } else {
            wrapper.and(AiDailyStatistics::getModelName).isNull();
        }

        return aiDailyStatisticsMapper.selectOneByQuery(wrapper);
    }

    @Override
    public void saveOrUpdate(AiDailyStatistics statistics) {
        AiDailyStatistics exist = getByDate(
                statistics.getStatDate(),
                statistics.getUserId(),
                statistics.getAppId(),
                statistics.getModelName()
        );

        if (exist != null) {
            // 更新统计数据
            exist.setTotalRequests(exist.getTotalRequests() + statistics.getTotalRequests());
            exist.setSuccessRequests(exist.getSuccessRequests() + statistics.getSuccessRequests());
            exist.setErrorRequests(exist.getErrorRequests() + statistics.getErrorRequests());
            exist.setInputTokens(exist.getInputTokens() + statistics.getInputTokens());
            exist.setOutputTokens(exist.getOutputTokens() + statistics.getOutputTokens());
            exist.setTotalTokens(exist.getTotalTokens() + statistics.getTotalTokens());

            // 重新计算平均响应时间
            if (statistics.getAvgResponseTime() != null && statistics.getTotalRequests() > 0) {
                double totalTime = exist.getAvgResponseTime() * exist.getSuccessRequests()
                        + statistics.getAvgResponseTime() * statistics.getSuccessRequests();
                int totalSuccess = exist.getSuccessRequests() + statistics.getSuccessRequests();
                if (totalSuccess > 0) {
                    exist.setAvgResponseTime(totalTime / totalSuccess);
                }
            }

            aiDailyStatisticsMapper.update(exist);
        } else {
            aiDailyStatisticsMapper.insert(statistics);
        }
    }

    @Override
    public List<AiDailyStatistics> listByDateRange(LocalDate startDate, LocalDate endDate) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(AiDailyStatistics::getStatDate).ge(startDate)
                .and(AiDailyStatistics::getStatDate).le(endDate)
                .and(AiDailyStatistics::getUserId).isNull()
                .and(AiDailyStatistics::getAppId).isNull()
                .and(AiDailyStatistics::getModelName).isNull()
                .orderBy(AiDailyStatistics::getStatDate, true);
        return aiDailyStatisticsMapper.selectListByQuery(wrapper);
    }

    @Override
    public List<AiDailyStatistics> listByDateRangeAndUser(LocalDate startDate, LocalDate endDate, String userId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(AiDailyStatistics::getStatDate).ge(startDate)
                .and(AiDailyStatistics::getStatDate).le(endDate)
                .and(AiDailyStatistics::getUserId).eq(userId)
                .orderBy(AiDailyStatistics::getStatDate, true);
        return aiDailyStatisticsMapper.selectListByQuery(wrapper);
    }

    @Override
    public List<AiDailyStatistics> listByDateRangeAndApp(LocalDate startDate, LocalDate endDate, String appId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(AiDailyStatistics::getStatDate).ge(startDate)
                .and(AiDailyStatistics::getStatDate).le(endDate)
                .and(AiDailyStatistics::getAppId).eq(appId)
                .orderBy(AiDailyStatistics::getStatDate, true);
        return aiDailyStatisticsMapper.selectListByQuery(wrapper);
    }

    @Override
    public List<AiDailyStatistics> listGlobalDailyStats(LocalDate startDate, LocalDate endDate) {
        return listByDateRange(startDate, endDate);
    }

    @Override
    public List<AiDailyStatistics> listUserRanking(LocalDate startDate, LocalDate endDate, Integer limit) {
        // 使用 JDBC 查询，这里返回空列表
        return List.of();
    }

    @Override
    public List<AiDailyStatistics> listModelStats(LocalDate startDate, LocalDate endDate) {
        // 使用 JDBC 查询，这里返回空列表
        return List.of();
    }
}
