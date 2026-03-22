package com.ws.codecraft.service;

import com.ws.codecraft.model.vo.AiMetricsVO;

import java.util.List;

/**
 * 统计服务接口
 */
public interface StatisticsService {

    /**
     * 获取 AI 使用统计
     */
    AiMetricsVO getAiMetrics(String startDate, String endDate);

    /**
     * 获取用户 Token 消耗排行
     */
    List<AiMetricsVO.UserStat> getUserTokenRanking(Integer limit);

    /**
     * 获取模型使用统计
     */
    List<AiMetricsVO.ModelStat> getModelStats(String startDate, String endDate);

    /**
     * 获取每日统计
     */
    List<AiMetricsVO.DailyStat> getDailyStats(String startDate, String endDate);
}
