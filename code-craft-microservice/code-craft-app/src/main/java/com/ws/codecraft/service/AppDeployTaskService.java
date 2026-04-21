package com.ws.codecraft.service;

import com.mybatisflex.core.service.IService;
import com.ws.codecraft.model.entity.AppDeployTask;
import com.ws.codecraft.model.vo.AppDeployTaskVO;

/**
 * App deploy task service.
 */
public interface AppDeployTaskService extends IService<AppDeployTask> {

    AppDeployTask createTask(Long appId, Long userId, String deployKey, String deployUrl);

    void appendLog(Long taskId, String message);

    void updateProgress(Long taskId, String status, String currentStep);

    void markSuccess(Long taskId, String deployUrl);

    void markFailed(Long taskId, String currentStep, String errorMessage);

    AppDeployTaskVO getTaskVO(AppDeployTask task);
}
