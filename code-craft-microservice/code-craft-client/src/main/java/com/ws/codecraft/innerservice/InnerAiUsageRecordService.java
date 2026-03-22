package com.ws.codecraft.innerservice;

import com.ws.codecraft.model.entity.AiUsageRecord;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 使用记录内部服务（用于Dubbo调用）
 */
public interface InnerAiUsageRecordService {

    /**
     * 保存使用记录
     * @return 保存后的记录（包含生成的ID）
     */
    AiUsageRecord saveRecord(AiUsageRecord record);

    /**
     * 更新记录
     */
    void updateRecord(AiUsageRecord record);

    /**
     * 根据日期范围查询记录
     */
    List<AiUsageRecord> listByDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * 根据用户ID查询记录
     */
    List<AiUsageRecord> listByUserId(String userId, LocalDate startDate, LocalDate endDate);
}
