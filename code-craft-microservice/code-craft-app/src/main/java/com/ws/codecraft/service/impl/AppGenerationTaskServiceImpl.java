package com.ws.codecraft.service.impl;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.ws.codecraft.exception.BusinessException;
import com.ws.codecraft.exception.ErrorCode;
import com.ws.codecraft.mapper.AppGenerationTaskMapper;
import com.ws.codecraft.model.entity.App;
import com.ws.codecraft.model.entity.AppGenerationTask;
import com.ws.codecraft.model.enums.AppGenerationTaskStatusEnum;
import com.ws.codecraft.service.AppGenerationTaskService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * AI 生成/对话任务服务实现。
 */
@Service
public class AppGenerationTaskServiceImpl extends ServiceImpl<AppGenerationTaskMapper, AppGenerationTask>
        implements AppGenerationTaskService {

    @Override
    public AppGenerationTask createTask(App app, Long userId, String mode, String userMessage) {
        AppGenerationTask task = new AppGenerationTask();
        task.setAppId(app.getId());
        task.setUserId(userId);
        task.setMode(mode);
        task.setStatus(AppGenerationTaskStatusEnum.PENDING.getValue());
        task.setModelKey(app.getModelKey());
        task.setCodeGenType(app.getCodeGenType());
        task.setUserMessage(userMessage);
        boolean saved = this.save(task);
        if (!saved) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建 AI 任务失败");
        }
        return task;
    }

    @Override
    public void markRunning(Long taskId) {
        AppGenerationTask task = new AppGenerationTask();
        task.setId(taskId);
        task.setStatus(AppGenerationTaskStatusEnum.RUNNING.getValue());
        task.setStartTime(LocalDateTime.now());
        this.updateById(task);
    }

    @Override
    public void markSuccess(Long taskId, String aiMessage) {
        AppGenerationTask task = new AppGenerationTask();
        task.setId(taskId);
        task.setStatus(AppGenerationTaskStatusEnum.SUCCESS.getValue());
        task.setAiMessage(limitMessage(aiMessage));
        task.setEndTime(LocalDateTime.now());
        this.updateById(task);
    }

    @Override
    public void markFailed(Long taskId, String errorMessage) {
        AppGenerationTask task = new AppGenerationTask();
        task.setId(taskId);
        task.setStatus(AppGenerationTaskStatusEnum.FAILED.getValue());
        task.setErrorMessage(limitMessage(errorMessage));
        task.setEndTime(LocalDateTime.now());
        this.updateById(task);
    }

    @Override
    public void markCanceled(Long taskId, String errorMessage) {
        AppGenerationTask task = new AppGenerationTask();
        task.setId(taskId);
        task.setStatus(AppGenerationTaskStatusEnum.CANCELED.getValue());
        task.setErrorMessage(limitMessage(errorMessage));
        task.setEndTime(LocalDateTime.now());
        this.updateById(task);
    }

    private String limitMessage(String message) {
        if (StrUtil.isBlank(message) || message.length() <= 60000) {
            return message;
        }
        return message.substring(0, 60000) + "...";
    }
}
