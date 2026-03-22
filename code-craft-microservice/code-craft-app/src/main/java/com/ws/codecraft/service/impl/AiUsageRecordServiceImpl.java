package com.ws.codecraft.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.ws.codecraft.mapper.AiUsageRecordMapper;
import com.ws.codecraft.model.entity.AiUsageRecord;
import com.ws.codecraft.service.AiUsageRecordService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * AI 使用记录服务实现
 */
@Service
@Slf4j
public class AiUsageRecordServiceImpl implements AiUsageRecordService {

    @Resource
    private AiUsageRecordMapper aiUsageRecordMapper;

    @Override
    public void saveRecord(AiUsageRecord record) {
        aiUsageRecordMapper.insert(record);
    }

    @Override
    public List<AiUsageRecord> listByDateRange(LocalDate startDate, LocalDate endDate) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(AiUsageRecord::getRequestTime).ge(LocalDateTime.of(startDate, LocalTime.MIN))
                .and(AiUsageRecord::getRequestTime).le(LocalDateTime.of(endDate, LocalTime.MAX))
                .orderBy(AiUsageRecord::getRequestTime, false);
        return aiUsageRecordMapper.selectListByQuery(wrapper);
    }

    @Override
    public void updateRecord(AiUsageRecord record) {
        aiUsageRecordMapper.update(record);
    }

    @Override
    public List<AiUsageRecord> listByUserId(String userId, LocalDate startDate, LocalDate endDate) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(AiUsageRecord::getUserId).eq(userId)
                .and(AiUsageRecord::getRequestTime).ge(LocalDateTime.of(startDate, LocalTime.MIN))
                .and(AiUsageRecord::getRequestTime).le(LocalDateTime.of(endDate, LocalTime.MAX))
                .orderBy(AiUsageRecord::getRequestTime, false);
        return aiUsageRecordMapper.selectListByQuery(wrapper);
    }
}
