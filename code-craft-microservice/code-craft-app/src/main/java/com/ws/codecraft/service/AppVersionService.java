package com.ws.codecraft.service;

import com.mybatisflex.core.service.IService;
import com.ws.codecraft.model.entity.App;
import com.ws.codecraft.model.entity.AppVersion;

/**
 * App version service.
 */
public interface AppVersionService extends IService<AppVersion> {

    AppVersion createVersion(App app, String sourceType, String codePath, String deployKey,
                             String promptSnapshot, Long createBy);
}
