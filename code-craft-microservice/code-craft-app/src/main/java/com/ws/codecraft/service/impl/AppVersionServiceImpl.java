package com.ws.codecraft.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.ws.codecraft.exception.BusinessException;
import com.ws.codecraft.exception.ErrorCode;
import com.ws.codecraft.mapper.AppVersionMapper;
import com.ws.codecraft.model.entity.App;
import com.ws.codecraft.model.entity.AppVersion;
import com.ws.codecraft.service.AppVersionService;
import org.springframework.stereotype.Service;

/**
 * App version service implementation.
 */
@Service
public class AppVersionServiceImpl extends ServiceImpl<AppVersionMapper, AppVersion>
        implements AppVersionService {

    @Override
    public AppVersion createVersion(App app, String sourceType, String codePath, String deployKey,
                                    String promptSnapshot, Long createBy) {
        if (app == null || app.getId() == null) {
            return null;
        }
        long versionCount = this.count(QueryWrapper.create().eq("appId", app.getId()));
        AppVersion version = new AppVersion();
        version.setAppId(app.getId());
        version.setVersionNo((int) versionCount + 1);
        version.setSourceType(sourceType);
        version.setCodeGenType(app.getCodeGenType());
        version.setCodePath(codePath);
        version.setDeployKey(deployKey);
        version.setPromptSnapshot(promptSnapshot);
        version.setCreateBy(createBy);
        boolean saved = this.save(version);
        if (!saved) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建应用版本记录失败");
        }
        return version;
    }
}
