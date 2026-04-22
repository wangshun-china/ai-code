package com.ws.codecraft.service;

import com.mybatisflex.core.service.IService;
import com.ws.codecraft.model.entity.App;
import com.ws.codecraft.model.entity.AppGenerationTask;

/**
 * AI 生成/对话任务服务。
 */
public interface AppGenerationTaskService extends IService<AppGenerationTask> {

    AppGenerationTask createTask(App app, Long userId, String mode, String userMessage);

    void markRunning(Long taskId);

    void markSuccess(Long taskId, String aiMessage);

    void markFailed(Long taskId, String errorMessage);

    void markCanceled(Long taskId, String errorMessage);
}
