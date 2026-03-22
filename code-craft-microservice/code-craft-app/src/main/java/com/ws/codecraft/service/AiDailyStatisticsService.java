package com.ws.codecraft.service;

import com.ws.codecraft.model.entity.AiDailyStatistics;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 每日统计服务
 */
public interface AiDailyStatisticsService {

    /**
     * 根据日期获取统计数据
     */
    AiDailyStatistics getByDate(LocalDate date, String userId, String appId, String modelName);

    /**
     * 保存或更新统计数据
     */
    void saveOrUpdate(AiDailyStatistics statistics);

    /**
     * 根据日期范围查询统计
     */
    List<AiDailyStatistics> listByDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * 根据日期范围查询用户统计
     */
    List<AiDailyStatistics> listByDateRangeAndUser(LocalDate startDate, LocalDate endDate, String userId);

    /**
     * 根据日期范围查询应用统计
     */
    List<AiDailyStatistics> listByDateRangeAndApp(LocalDate startDate, LocalDate endDate, String appId);

    /**
     * 获取全局每日统计（按日期聚合）
     */
    List<AiDailyStatistics> listGlobalDailyStats(LocalDate startDate, LocalDate endDate);

    /**
     * 获取用户排行
     */
    List<AiDailyStatistics> listUserRanking(LocalDate startDate, LocalDate endDate, Integer limit);

    /**
     * 获取模型统计
     */
    List<AiDailyStatistics> listModelStats(LocalDate startDate, LocalDate endDate);
}
