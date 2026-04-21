package com.ws.codecraft.mapper;

import com.mybatisflex.core.BaseMapper;
import com.ws.codecraft.model.entity.AppVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * App version mapper.
 */
@Mapper
public interface AppVersionMapper extends BaseMapper<AppVersion> {
}
