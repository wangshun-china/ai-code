package com.ws.codecraft.service.impl;

import com.ws.codecraft.innerservice.InnerAiUsageRecordService;
import com.ws.codecraft.mapper.AiUsageRecordMapper;
import com.ws.codecraft.model.entity.AiUsageRecord;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * AI 使用记录内部服务实现
 */
@DubboService
@Slf4j
public class InnerAiUsageRecordServiceImpl implements InnerAiUsageRecordService {

    @Resource
    private AiUsageRecordMapper aiUsageRecordMapper;

    @Override
    public AiUsageRecord saveRecord(AiUsageRecord record) {
        aiUsageRecordMapper.insert(record);
        return record;
    }

    @Override
    public void updateRecord(AiUsageRecord record) {
        aiUsageRecordMapper.update(record);
    }

    @Override
    public List<AiUsageRecord> listByDateRange(LocalDate startDate, LocalDate endDate) {
        return List.of();
    }

    @Override
    public List<AiUsageRecord> listByUserId(String userId, LocalDate startDate, LocalDate endDate) {
        return List.of();
    }
}
