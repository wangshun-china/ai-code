package com.ws.codecraft.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.ws.codecraft.ai.AiCodeGenTypeRoutingService;
import com.ws.codecraft.ai.AiCodeGenTypeRoutingServiceFactory;
import com.ws.codecraft.ai.AiCodeGeneratorService;
import com.ws.codecraft.ai.AiCodeGeneratorServiceFactory;
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
import com.ws.codecraft.model.entity.AppGenerationTask;
import com.ws.codecraft.model.entity.ChatHistory;
import com.ws.codecraft.model.entity.User;
import com.ws.codecraft.model.enums.AppDeployTaskStatusEnum;
import com.ws.codecraft.model.enums.AppGenerationTaskModeEnum;
import com.ws.codecraft.model.enums.AppStatusEnum;
import com.ws.codecraft.model.enums.AppVersionSourceTypeEnum;
import com.ws.codecraft.model.enums.AiModelEnum;
import com.ws.codecraft.model.enums.ChatHistoryMessageTypeEnum;
import com.ws.codecraft.model.enums.CodeGenTypeEnum;
import com.ws.codecraft.model.vo.AppDeployResultVO;
import com.ws.codecraft.model.vo.AppGenerationPlanVO;
import com.ws.codecraft.model.vo.AppVO;
import com.ws.codecraft.model.vo.UserVO;
import com.ws.codecraft.monitor.MonitorContext;
import com.ws.codecraft.monitor.MonitorContextHolder;
import com.ws.codecraft.service.AppDeployTaskService;
import com.ws.codecraft.service.AppAttachmentService;
import com.ws.codecraft.service.AppGenerationTaskService;
import com.ws.codecraft.service.AppService;
import com.ws.codecraft.service.AppVersionService;
import com.ws.codecraft.service.ChatHistoryService;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.io.File;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 应用服务实现。
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    private final Cache<String, AppGenerationPlanVO> generationPlanCache = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    @DubboReference
    private InnerUserService userService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private AppDeployTaskService appDeployTaskService;

    @Resource
    private AppAttachmentService appAttachmentService;

    @Resource
    private AppVersionService appVersionService;

    @Resource
    private AppGenerationTaskService appGenerationTaskService;

    @Resource
    private RedissonClient redissonClient;

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
    public Flux<String> chatToGenCode(Long appId, String message, String planId, User loginUser) {
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

        String generationMessage = buildGenerationMessage(app, message, planId, appId, loginUser.getId());

        AppGenerationTask generationTask = appGenerationTaskService.createTask(app, loginUser.getId(),
                AppGenerationTaskModeEnum.GENERATE.getValue(), message);
        String lockToken = acquireGenerationLock(appId);
        if (lockToken == null) {
            String errorMessage = "当前应用正在生成中，请等待上一轮生成完成后再试";
            appGenerationTaskService.markFailed(generationTask.getId(), errorMessage);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, errorMessage);
        }

        MonitorContextHolder.setContext(MonitorContext.builder()
                .userId(String.valueOf(loginUser.getId()))
                .appId(String.valueOf(appId))
                .build());

        if (StrUtil.isBlank(planId)) {
            chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        }
        updateAppStatus(appId, AppStatusEnum.GENERATING);
        appGenerationTaskService.markRunning(generationTask.getId());
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(generationMessage, codeGenTypeEnum, appId, app.getModelKey());
        StringBuilder aiResponseBuilder = new StringBuilder();
        return streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum)
                .doOnNext(aiResponseBuilder::append)
                .doOnComplete(() -> {
                    updateAppStatus(appId, AppStatusEnum.GENERATE_SUCCESS);
                    appGenerationTaskService.markSuccess(generationTask.getId(), aiResponseBuilder.toString());
                    appVersionService.createVersion(app, AppVersionSourceTypeEnum.GENERATE.getValue(),
                            getSourceDirPath(app), app.getDeployKey(), message, loginUser.getId());
                })
                .doOnError(error -> {
                    updateAppStatus(appId, AppStatusEnum.GENERATE_FAILED);
                    appGenerationTaskService.markFailed(generationTask.getId(), getAiFriendlyErrorMessage(error));
                })
                .doFinally(signalType -> {
                    if (SignalType.CANCEL.equals(signalType)) {
                        String errorMessage = "客户端断开连接或请求被取消";
                        updateAppStatus(appId, AppStatusEnum.GENERATE_FAILED);
                        appGenerationTaskService.markCanceled(generationTask.getId(), errorMessage);
                        chatHistoryService.addChatMessage(appId, "AI 回复中断：" + errorMessage,
                                ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    }
                    releaseGenerationLock(appId, lockToken);
                    MonitorContextHolder.clearContext();
                });
    }

    @Override
    public String chatToApp(Long appId, String message, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");

        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }

        AppGenerationTask chatTask = appGenerationTaskService.createTask(app, loginUser.getId(),
                AppGenerationTaskModeEnum.CHAT.getValue(), message);
        appGenerationTaskService.markRunning(chatTask.getId());
        String chatPrompt = buildPlainChatPrompt(app, message, loginUser.getId());
        ChatModel plainChatModel = aiCodeGeneratorServiceFactory.createPlainChatModel(app.getModelKey());
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        String aiResponse;
        try {
            aiResponse = plainChatModel.chat(UserMessage.from(chatPrompt)).aiMessage().text();
        } catch (RuntimeException e) {
            String errorMessage = getAiFriendlyErrorMessage(e);
            appGenerationTaskService.markFailed(chatTask.getId(), errorMessage);
            chatHistoryService.addChatMessage(appId, "AI 回复失败：" + errorMessage,
                    ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
        }
        chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
        appGenerationTaskService.markSuccess(chatTask.getId(), aiResponse);
        return aiResponse;
    }

    @Override
    public AppGenerationPlanVO generateAppPlan(Long appId, String message, User loginUser) {
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

        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory
                .getAiCodeGeneratorService(appId, codeGenTypeEnum, app.getModelKey());
        String attachmentContext = buildAttachmentContextIfNeeded(app, message, loginUser.getId());
        String attachmentPromptBlock = StrUtil.isBlank(attachmentContext) ? "" : "\n\n" + attachmentContext;
        String planPrompt = String.format("""
                用户原始需求：
                %s

                %s

                当前应用生成类型：%s
                请基于该生成类型输出正式编码前的实现方案。
                """, message, attachmentPromptBlock, codeGenTypeEnum.getValue());
        AppGenerationTask planTask = appGenerationTaskService.createTask(app, loginUser.getId(),
                AppGenerationTaskModeEnum.PLAN.getValue(), message);
        appGenerationTaskService.markRunning(planTask.getId());
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        String plan;
        try {
            plan = aiCodeGeneratorService.generateAppPlan(planPrompt);
        } catch (RuntimeException e) {
            String errorMessage = getAiFriendlyErrorMessage(e);
            appGenerationTaskService.markFailed(planTask.getId(), errorMessage);
            chatHistoryService.addChatMessage(appId, "方案生成失败：" + errorMessage,
                    ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
        }

        AppGenerationPlanVO planVO = new AppGenerationPlanVO();
        planVO.setAppId(appId);
        planVO.setPlanId(UUID.randomUUID().toString());
        planVO.setMessage(message);
        planVO.setPlan(plan);
        generationPlanCache.put(planVO.getPlanId(), planVO);
        chatHistoryService.addChatMessage(appId, "实现方案：\n" + plan,
                ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
        appGenerationTaskService.markSuccess(planTask.getId(), plan);
        return planVO;
    }

    private String buildGenerationMessage(App app, String message, String planId, Long appId, Long userId) {
        String attachmentContext = buildAttachmentContextIfNeeded(app, message, userId);
        String recentConversationContext = buildRecentConversationContext(appId, userId, 12);
        if (StrUtil.isBlank(planId)) {
            String contextBlock = buildOptionalContextBlock(recentConversationContext, attachmentContext);
            if (StrUtil.isNotBlank(contextBlock)) {
                return String.format("""
                        用户当前生成/修改需求：
                        %s

                        %s
                        """, message, contextBlock);
            }
            return message;
        }
        AppGenerationPlanVO planVO = generationPlanCache.getIfPresent(planId);
        if (planVO == null || !appId.equals(planVO.getAppId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成方案已过期，请重新生成方案");
        }
        generationPlanCache.invalidate(planId);
        String contextBlock = buildOptionalContextBlock(recentConversationContext, attachmentContext);
        return String.format("""
                用户原始需求：
                %s

                %s

                用户已确认以下生成方案，请严格按方案生成代码：
                %s
                """, message, StrUtil.blankToDefault(contextBlock, ""), planVO.getPlan());
    }

    private String buildPlainChatPrompt(App app, String message, Long userId) {
        String recentConversationContext = buildRecentConversationContext(app.getId(), userId, 12);
        String attachmentContext = buildAttachmentContextIfNeeded(app, message, userId);
        String contextBlock = buildOptionalContextBlock(recentConversationContext, attachmentContext);
        return String.format("""
                你是 CodeCraft 应用构建助手，当前处于普通聊天模式。
                你可以和用户对齐需求、解释当前应用、给出建议，但禁止声称已经修改代码，禁止输出会写入文件的工具调用。
                如果用户想真正修改代码，请提醒他切换到“改代码”模式后发送明确修改需求。

                当前应用信息：
                - appId: %s
                - 代码类型: %s
                - 当前状态: %s
                - 当前模型: %s

                %s

                用户当前问题：
                %s
                """, app.getId(), app.getCodeGenType(), app.getStatus(), app.getModelKey(),
                StrUtil.blankToDefault(contextBlock, "暂无额外上下文。"), message);
    }

    private String buildOptionalContextBlock(String recentConversationContext, String attachmentContext) {
        List<String> blocks = new ArrayList<>();
        if (StrUtil.isNotBlank(recentConversationContext)) {
            blocks.add(recentConversationContext);
        }
        if (StrUtil.isNotBlank(attachmentContext)) {
            blocks.add(attachmentContext);
        }
        return String.join("\n\n", blocks);
    }

    private String buildRecentConversationContext(Long appId, Long userId, int maxCount) {
        List<ChatHistory> historyList = chatHistoryService.listRecentChatHistory(appId, userId, maxCount);
        if (CollUtil.isEmpty(historyList)) {
            return "";
        }
        StringBuilder builder = new StringBuilder("最近对话上下文（供理解需求使用，不代表必须修改代码）：\n");
        for (ChatHistory history : historyList) {
            String role = ChatHistoryMessageTypeEnum.USER.getValue().equals(history.getMessageType()) ? "用户" : "AI";
            builder.append(role)
                    .append("：")
                    .append(truncateText(removeMarkdownCodeFenceMarkers(history.getMessage()), 600))
                    .append('\n');
        }
        return builder.toString();
    }

    private String buildAttachmentContextIfNeeded(App app, String message, Long userId) {
        if (app == null || app.getId() == null || userId == null) {
            return "";
        }
        if (shouldUseAttachmentContext(app, message)) {
            return appAttachmentService.buildAttachmentContext(app.getId(), userId);
        }
        return "";
    }

    private boolean shouldUseAttachmentContext(App app, String message) {
        String status = app.getStatus();
        if (StrUtil.isBlank(status)
                || AppStatusEnum.DRAFT.getValue().equals(status)
                || AppStatusEnum.GENERATE_FAILED.getValue().equals(status)) {
            return true;
        }
        String normalizedMessage = StrUtil.blankToDefault(message, "").toLowerCase();
        return normalizedMessage.contains("附件")
                || normalizedMessage.contains("简历")
                || normalizedMessage.contains("设计稿")
                || normalizedMessage.contains("上传")
                || normalizedMessage.contains("pdf")
                || normalizedMessage.contains("文档")
                || normalizedMessage.contains("文件");
    }

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");

        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setModelKey(AiModelEnum.normalize(appAddRequest.getModelKey()));
        app.setUserId(loginUser.getId());
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));

        AiCodeGenTypeRoutingService routingService = aiCodeGenTypeRoutingServiceFactory
                .createAiCodeGenTypeRoutingService(app.getModelKey());
        CodeGenTypeEnum selectedCodeGenType;
        try {
            selectedCodeGenType = routingService.routeCodeGenType(initPrompt);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, getAiFriendlyErrorMessage(e));
        }
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

    private String acquireGenerationLock(Long appId) {
        String lockKey = "code-craft:app:generation-lock:" + appId;
        String lockToken = UUID.randomUUID().toString();
        RBucket<String> lockBucket = redissonClient.getBucket(lockKey);
        boolean locked = lockBucket.trySet(lockToken, 30, TimeUnit.MINUTES);
        return locked ? lockToken : null;
    }

    private void releaseGenerationLock(Long appId, String lockToken) {
        if (appId == null || StrUtil.isBlank(lockToken)) {
            return;
        }
        String lockKey = "code-craft:app:generation-lock:" + appId;
        RBucket<String> lockBucket = redissonClient.getBucket(lockKey);
        String currentToken = lockBucket.get();
        if (lockToken.equals(currentToken)) {
            lockBucket.delete();
        }
    }

    private String truncateText(String text, int maxLength) {
        if (StrUtil.isBlank(text) || text.length() <= maxLength) {
            return StrUtil.blankToDefault(text, "");
        }
        return text.substring(0, maxLength) + "...";
    }

    private String removeMarkdownCodeFenceMarkers(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        return text.replaceAll("(?m)^\\s*```[\\w.+-]*\\s*$", "[代码块边界已省略]");
    }

    private String getAiFriendlyErrorMessage(Throwable e) {
        String message = e == null ? "" : StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName());
        if (message.contains("AllocationQuota.FreeTierOnly") || message.contains("403")) {
            return "当前模型免费额度已用完，请切换其他模型后重试";
        }
        return message;
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
