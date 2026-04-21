package com.ws.codecraft.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.ws.codecraft.ai.AiCodeGenTypeRoutingService;
import com.ws.codecraft.ai.AiCodeGenTypeRoutingServiceFactory;
import com.ws.codecraft.config.CodeProjectProperties;
import com.ws.codecraft.constant.AppConstant;
import com.ws.codecraft.core.AiCodeGeneratorFacade;
import com.ws.codecraft.core.builder.VueProjectBuilder;
import com.ws.codecraft.core.builder.VueProjectBuilderProd;
import com.ws.codecraft.core.handler.StreamHandlerExecutor;
import com.ws.codecraft.exception.BusinessException;
import com.ws.codecraft.exception.ErrorCode;
import com.ws.codecraft.exception.ThrowUtils;
import com.ws.codecraft.innerservice.InnerScreenshotService;
import com.ws.codecraft.innerservice.InnerUserService;
import com.ws.codecraft.mapper.AppMapper;
import com.ws.codecraft.model.dto.app.AppAddRequest;
import com.ws.codecraft.model.dto.app.AppQueryRequest;
import com.ws.codecraft.model.entity.App;
import com.ws.codecraft.model.entity.AppDeployTask;
import com.ws.codecraft.model.entity.User;
import com.ws.codecraft.model.enums.AppDeployTaskStatusEnum;
import com.ws.codecraft.model.enums.AppStatusEnum;
import com.ws.codecraft.model.enums.AppVersionSourceTypeEnum;
import com.ws.codecraft.model.enums.ChatHistoryMessageTypeEnum;
import com.ws.codecraft.model.enums.CodeGenTypeEnum;
import com.ws.codecraft.model.vo.AppDeployResultVO;
import com.ws.codecraft.model.vo.AppVO;
import com.ws.codecraft.model.vo.UserVO;
import com.ws.codecraft.monitor.MonitorContext;
import com.ws.codecraft.monitor.MonitorContextHolder;
import com.ws.codecraft.service.AppDeployTaskService;
import com.ws.codecraft.service.AppService;
import com.ws.codecraft.service.AppVersionService;
import com.ws.codecraft.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用服务实现。
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @DubboReference
    private InnerUserService userService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private AppDeployTaskService appDeployTaskService;

    @Resource
    private AppVersionService appVersionService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @DubboReference
    private InnerScreenshotService screenshotService;

    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;

    @Autowired
    private VueProjectBuilderProd vueProjectBuilderProd;

    @Resource
    private CodeProjectProperties codeProjectProperties;

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");

        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }

        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用代码生成类型错误");
        }

        MonitorContextHolder.setContext(MonitorContext.builder()
                .userId(String.valueOf(loginUser.getId()))
                .appId(String.valueOf(appId))
                .build());

        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        updateAppStatus(appId, AppStatusEnum.GENERATING);
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
        return streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum)
                .doOnComplete(() -> {
                    updateAppStatus(appId, AppStatusEnum.GENERATE_SUCCESS);
                    appVersionService.createVersion(app, AppVersionSourceTypeEnum.GENERATE.getValue(),
                            getSourceDirPath(app), app.getDeployKey(), message, loginUser.getId());
                })
                .doOnError(error -> updateAppStatus(appId, AppStatusEnum.GENERATE_FAILED))
                .doFinally(signalType -> MonitorContextHolder.clearContext());
    }

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");

        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));

        AiCodeGenTypeRoutingService routingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum selectedCodeGenType = routingService.routeCodeGenType(initPrompt);
        app.setCodeGenType(selectedCodeGenType.getValue());
        app.setStatus(AppStatusEnum.DRAFT.getValue());

        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        appVersionService.createVersion(app, AppVersionSourceTypeEnum.CREATE.getValue(),
                null, null, initPrompt, loginUser.getId());
        log.info("应用创建成功, id={}, type={}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }

    @Override
    public AppDeployResultVO deployApp(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }

        String deployKey = app.getDeployKey();
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }

        String appDeployUrl = String.format("%s/%s/", codeProjectProperties.getDeployHost(), deployKey);
        AppDeployTask task = appDeployTaskService.createTask(appId, loginUser.getId(), deployKey, appDeployUrl);
        String finalDeployKey = deployKey;
        Thread.startVirtualThread(() -> executeDeployTask(task.getId(), appId, loginUser.getId(), finalDeployKey, appDeployUrl));

        AppDeployResultVO resultVO = new AppDeployResultVO();
        resultVO.setTaskId(task.getId());
        resultVO.setAppId(appId);
        resultVO.setDeployKey(deployKey);
        resultVO.setDeployUrl(appDeployUrl);
        resultVO.setStatus(task.getStatus());
        return resultVO;
    }

    private void executeDeployTask(Long taskId, Long appId, Long userId, String deployKey, String appDeployUrl) {
        String currentStep = "prepare";
        try {
            appDeployTaskService.updateProgress(taskId, AppDeployTaskStatusEnum.RUNNING.getValue(), currentStep);
            appDeployTaskService.appendLog(taskId, "开始部署应用");
            updateAppStatus(appId, AppStatusEnum.DEPLOYING);

            App app = this.getById(appId);
            ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

            currentStep = "prepare_source";
            String sourceDirPath = getSourceDirPath(app);
            File sourceDir = new File(sourceDirPath);
            if (!sourceDir.exists() || !sourceDir.isDirectory()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码路径不存在，请先生成应用");
            }

            CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
            if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
                currentStep = "build";
                appDeployTaskService.updateProgress(taskId, AppDeployTaskStatusEnum.RUNNING.getValue(), currentStep);
                appDeployTaskService.appendLog(taskId, "正在构建");
                updateAppStatus(appId, AppStatusEnum.BUILDING);

                boolean buildSuccess;
                try {
                    if ("remote".equalsIgnoreCase(codeProjectProperties.getBuildMode())) {
                        log.info("使用远程构建模式构建 Vue 项目");
                        buildSuccess = vueProjectBuilderProd.buildProject(sourceDirPath);
                    } else {
                        log.info("使用本地构建模式构建 Vue 项目");
                        buildSuccess = vueProjectBuilder.buildProject(sourceDir.getName());
                    }
                } catch (Exception e) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "node-builder 返回 " + getErrorMessage(e));
                }
                ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "node-builder 返回构建失败");

                File distDir = new File(sourceDirPath, "dist");
                ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
                sourceDir = distDir;
                appDeployTaskService.appendLog(taskId, "构建完成");
                updateAppStatus(appId, AppStatusEnum.BUILD_SUCCESS);
            }

            currentStep = "copy";
            appDeployTaskService.updateProgress(taskId, AppDeployTaskStatusEnum.RUNNING.getValue(), currentStep);
            appDeployTaskService.appendLog(taskId, "正在复制部署目录");
            String deployDirPath = codeProjectProperties.getDeployRootDir() + File.separator + deployKey;
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);

            currentStep = "deploy";
            appDeployTaskService.updateProgress(taskId, AppDeployTaskStatusEnum.RUNNING.getValue(), currentStep);
            appDeployTaskService.appendLog(taskId, "正在写入部署信息");
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setDeployKey(deployKey);
            updateApp.setDeployedTime(LocalDateTime.now());
            updateApp.setStatus(AppStatusEnum.DEPLOY_SUCCESS.getValue());
            boolean updateResult = this.updateById(updateApp);
            ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");

            app.setDeployKey(deployKey);
            appVersionService.createVersion(app, AppVersionSourceTypeEnum.DEPLOY.getValue(),
                    sourceDir.getAbsolutePath(), deployKey, app.getInitPrompt(), userId);

            currentStep = "screenshot";
            appDeployTaskService.updateProgress(taskId, AppDeployTaskStatusEnum.RUNNING.getValue(), currentStep);
            appDeployTaskService.appendLog(taskId, "正在截图");
            tryUpdateScreenshot(taskId, appId, appDeployUrl);

            appDeployTaskService.appendLog(taskId, "部署成功");
            appDeployTaskService.markSuccess(taskId, appDeployUrl);
        } catch (Exception e) {
            log.error("应用部署任务失败, appId={}, taskId={}, step={}, error={}",
                    appId, taskId, currentStep, e.getMessage(), e);
            updateAppStatus(appId, "build".equals(currentStep) ? AppStatusEnum.BUILD_FAILED : AppStatusEnum.DEPLOY_FAILED);
            appDeployTaskService.markFailed(taskId, currentStep, getErrorMessage(e));
        }
    }

    private void tryUpdateScreenshot(Long taskId, Long appId, String appUrl) {
        try {
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
            if (StrUtil.isBlank(screenshotUrl)) {
                appDeployTaskService.appendLog(taskId, "截图失败：返回 URL 为空");
                updateAppStatus(appId, AppStatusEnum.SCREENSHOT_FAILED);
                return;
            }

            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updated = this.updateById(updateApp);
            if (updated) {
                appDeployTaskService.appendLog(taskId, "截图完成");
            } else {
                appDeployTaskService.appendLog(taskId, "截图失败：更新封面失败");
                updateAppStatus(appId, AppStatusEnum.SCREENSHOT_FAILED);
            }
        } catch (Exception e) {
            appDeployTaskService.appendLog(taskId, "截图失败：" + getErrorMessage(e));
            updateAppStatus(appId, AppStatusEnum.SCREENSHOT_FAILED);
        }
    }

    private String getSourceDirPath(App app) {
        String sourceDirName = app.getCodeGenType() + "_" + app.getId();
        return codeProjectProperties.getOutputRootDir() + File.separator + sourceDirName;
    }

    private void updateAppStatus(Long appId, AppStatusEnum statusEnum) {
        if (statusEnum == null) {
            return;
        }
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setStatus(statusEnum.getValue());
        this.updateById(updateApp);
    }

    private String getErrorMessage(Exception e) {
        return StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName());
    }

    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        Thread.startVirtualThread(() -> {
            try {
                log.info("开始生成应用截图, appId={}, url={}", appId, appUrl);
                String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
                if (StrUtil.isBlank(screenshotUrl)) {
                    log.warn("截图生成失败, 返回 URL 为空, appId={}", appId);
                    return;
                }

                App updateApp = new App();
                updateApp.setId(appId);
                updateApp.setCover(screenshotUrl);
                boolean updated = this.updateById(updateApp);
                if (updated) {
                    log.info("应用截图更新成功, appId={}, cover={}", appId, screenshotUrl);
                } else {
                    log.warn("应用截图更新失败, appId={}", appId);
                }
            } catch (Exception e) {
                log.error("生成应用截图异常, appId={}, error={}", appId, e.getMessage(), e);
            }
        });
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        Set<Long> userIds = appList.stream().map(App::getUserId).collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            appVO.setUser(userVOMap.get(app.getUserId()));
            return appVO;
        }).toList();
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        return QueryWrapper.create()
                .eq("id", appQueryRequest.getId())
                .like("appName", appQueryRequest.getAppName())
                .like("cover", appQueryRequest.getCover())
                .like("initPrompt", appQueryRequest.getInitPrompt())
                .eq("codeGenType", appQueryRequest.getCodeGenType())
                .eq("deployKey", appQueryRequest.getDeployKey())
                .eq("status", appQueryRequest.getStatus())
                .eq("priority", appQueryRequest.getPriority())
                .eq("userId", appQueryRequest.getUserId())
                .orderBy(appQueryRequest.getSortField(), "ascend".equals(appQueryRequest.getSortOrder()));
    }

    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        long appId = Long.parseLong(id.toString());
        if (appId <= 0) {
            return false;
        }
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            log.error("删除应用关联的对话历史失败: {}", e.getMessage(), e);
        }
        return super.removeById(id);
    }
}
