package com.ws.codecraft.controller;

import com.ws.codecraft.common.BaseResponse;
import com.ws.codecraft.common.ResultUtils;
import com.ws.codecraft.model.vo.AppGenerationQualityVO;
import com.ws.codecraft.model.vo.AiMetricsVO;
import com.ws.codecraft.service.StatisticsService;
import com.ws.codecraft.annotation.AuthCheck;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统计接口
 */
@RestController
@RequestMapping("/api/statistics")
@Slf4j
public class StatisticsController {

    @Resource
    private StatisticsService statisticsService;

    /**
     * 获取 AI 使用统计
     */
    @GetMapping("/ai-metrics")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<AiMetricsVO> getAiMetrics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        AiMetricsVO metrics = statisticsService.getAiMetrics(startDate, endDate);
        return ResultUtils.success(metrics);
    }

    /**
     * 获取用户 Token 消耗排行
     */
    @GetMapping("/user-ranking")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<List<AiMetricsVO.UserStat>> getUserTokenRanking(
            @RequestParam(defaultValue = "10") Integer limit) {
        List<AiMetricsVO.UserStat> ranking = statisticsService.getUserTokenRanking(limit);
        return ResultUtils.success(ranking);
    }

    /**
     * 获取模型使用统计
     */
    @GetMapping("/model-stats")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<List<AiMetricsVO.ModelStat>> getModelStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        List<AiMetricsVO.ModelStat> stats = statisticsService.getModelStats(startDate, endDate);
        return ResultUtils.success(stats);
    }

    /**
     * 获取每日统计
     */
    @GetMapping("/daily-stats")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<List<AiMetricsVO.DailyStat>> getDailyStats(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        List<AiMetricsVO.DailyStat> stats = statisticsService.getDailyStats(startDate, endDate);
        return ResultUtils.success(stats);
    }

    /**
     * 获取最近代码生成质量摘要
     */
    @GetMapping("/generation-quality")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<List<AppGenerationQualityVO>> getGenerationQuality(
            @RequestParam(defaultValue = "10") Integer limit) {
        List<AppGenerationQualityVO> records = statisticsService.getRecentGenerationQuality(limit);
        return ResultUtils.success(records);
    }
}
