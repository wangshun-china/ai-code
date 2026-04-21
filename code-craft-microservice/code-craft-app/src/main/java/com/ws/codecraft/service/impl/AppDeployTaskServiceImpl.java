package com.ws.codecraft.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.ws.codecraft.exception.BusinessException;
import com.ws.codecraft.exception.ErrorCode;
import com.ws.codecraft.mapper.AppDeployTaskMapper;
import com.ws.codecraft.model.entity.AppDeployTask;
import com.ws.codecraft.model.enums.AppDeployTaskStatusEnum;
import com.ws.codecraft.model.vo.AppDeployTaskVO;
import com.ws.codecraft.service.AppDeployTaskService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * App deploy task service implementation.
 */
@Service
public class AppDeployTaskServiceImpl extends ServiceImpl<AppDeployTaskMapper, AppDeployTask>
        implements AppDeployTaskService {

    @Override
    public AppDeployTask createTask(Long appId, Long userId, String deployKey, String deployUrl) {
        AppDeployTask task = new AppDeployTask();
        task.setAppId(appId);
        task.setUserId(userId);
        task.setDeployKey(deployKey);
        task.setDeployUrl(deployUrl);
        task.setStatus(AppDeployTaskStatusEnum.PENDING.getValue());
        task.setCurrentStep("pending");
        task.setRetryCount(0);
        task.setLogText(formatLog("部署任务已创建"));
        boolean saved = this.save(task);
        if (!saved) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建部署任务失败");
        }
        return task;
    }

    @Override
    public void appendLog(Long taskId, String message) {
        if (taskId == null || StrUtil.isBlank(message)) {
            return;
        }
        AppDeployTask task = this.getById(taskId);
        if (task == null) {
            return;
        }
        String oldLogText = StrUtil.nullToDefault(task.getLogText(), "");
        AppDeployTask updateTask = new AppDeployTask();
        updateTask.setId(taskId);
        updateTask.setLogText(oldLogText + formatLog(message));
        this.updateById(updateTask);
    }

    @Override
    public void updateProgress(Long taskId, String status, String currentStep) {
        AppDeployTask task = new AppDeployTask();
        task.setId(taskId);
        task.setStatus(status);
        task.setCurrentStep(currentStep);
        if (AppDeployTaskStatusEnum.RUNNING.getValue().equals(status)) {
            task.setStartTime(LocalDateTime.now());
        }
        this.updateById(task);
    }

    @Override
    public void markSuccess(Long taskId, String deployUrl) {
        AppDeployTask task = new AppDeployTask();
        task.setId(taskId);
        task.setStatus(AppDeployTaskStatusEnum.SUCCESS.getValue());
        task.setCurrentStep("success");
        task.setDeployUrl(deployUrl);
        task.setEndTime(LocalDateTime.now());
        this.updateById(task);
    }

    @Override
    public void markFailed(Long taskId, String currentStep, String errorMessage) {
        AppDeployTask task = new AppDeployTask();
        task.setId(taskId);
        task.setStatus(AppDeployTaskStatusEnum.FAILED.getValue());
        task.setCurrentStep(currentStep);
        task.setErrorMessage(errorMessage);
        task.setEndTime(LocalDateTime.now());
        this.updateById(task);
        appendLog(taskId, "部署失败：" + errorMessage);
    }

    @Override
    public AppDeployTaskVO getTaskVO(AppDeployTask task) {
        if (task == null) {
            return null;
        }
        AppDeployTaskVO vo = new AppDeployTaskVO();
        BeanUtil.copyProperties(task, vo);
        return vo;
    }

    private String formatLog(String message) {
        return "[" + LocalDateTime.now() + "] " + message + System.lineSeparator();
    }
}
