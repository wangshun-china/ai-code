package com.ws.codecraft.mapper;

import com.mybatisflex.core.BaseMapper;
import com.ws.codecraft.model.entity.AiUsageRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 使用记录 Mapper
 */
@Mapper
public interface AiUsageRecordMapper extends BaseMapper<AiUsageRecord> {
}
