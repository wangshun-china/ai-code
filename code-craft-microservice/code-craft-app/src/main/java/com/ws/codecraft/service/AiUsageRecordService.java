package com.ws.codecraft.service;

import com.ws.codecraft.model.entity.AiUsageRecord;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 使用记录服务
 */
public interface AiUsageRecordService {

    /**
     * 保存使用记录
     */
    void saveRecord(AiUsageRecord record);

    /**
     * 根据日期范围查询记录
     */
    List<AiUsageRecord> listByDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * 更新记录（用于补充响应数据）
     */
    void updateRecord(AiUsageRecord record);

    /**
     * 根据用户ID查询记录
     */
    List<AiUsageRecord> listByUserId(String userId, LocalDate startDate, LocalDate endDate);
}
