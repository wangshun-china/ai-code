package com.ws.codecraft.mapper;

import com.mybatisflex.core.BaseMapper;
import com.ws.codecraft.model.entity.AiModelCredential;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 模型凭据配置 Mapper。
 */
@Mapper
public interface AiModelCredentialMapper extends BaseMapper<AiModelCredential> {
}
