package com.ws.codecraft.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.ws.codecraft.ai.AiCodeGenTypeRoutingService;
import com.ws.codecraft.ai.AiCodeGenTypeRoutingServiceFactory;
import com.ws.codecraft.ai.AiCodeGeneratorService;
import com.ws.codecraft.ai.AiCodeGeneratorServiceFactory;
import com.ws.codecraft.ai.AiModelFallbackRouter;
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
import com.ws.codecraft.service.CodegenTemplateRagService;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
    private AiModelFallbackRouter aiModelFallbackRouter;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private AppDeployTaskService appDeployTaskService;

    @Resource
    private AppAttachmentService appAttachmentService;

    @Resource
    private CodegenTemplateRagService codegenTemplateRagService;

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
        } else {
            chatHistoryService.addChatMessage(appId, "已确认实现方案，开始生成代码。",
                    ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        }
        updateAppStatus(appId, AppStatusEnum.GENERATING);
        appGenerationTaskService.markRunning(generationTask.getId());
        AtomicReference<String> selectedModelKey = new AtomicReference<>(AiModelEnum.normalize(app.getModelKey()));
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStreamWithFallback(
                generationMessage,
                codeGenTypeEnum,
                appId,
                app.getModelKey(),
                modelKey -> {
                    selectedModelKey.set(modelKey);
                    appGenerationTaskService.updateModelKey(generationTask.getId(), modelKey);
                });
        StringBuilder aiResponseBuilder = new StringBuilder();
        AtomicInteger streamChunkCount = new AtomicInteger();
        long generationStartTime = System.currentTimeMillis();
        return streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum)
                .doOnNext(chunk -> {
                    aiResponseBuilder.append(chunk);
                    streamChunkCount.incrementAndGet();
                })
                .doOnComplete(() -> {
                    updateAppStatus(appId, AppStatusEnum.GENERATE_SUCCESS);
                    appGenerationTaskService.markSuccess(generationTask.getId(),
                            buildGenerationQualitySummary(app, aiResponseBuilder, streamChunkCount.get(),
                                    generationStartTime, StrUtil.isNotBlank(planId), selectedModelKey.get()));
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
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        String aiResponse;
        try {
            MonitorContextHolder.setContext(MonitorContext.builder()
                    .userId(String.valueOf(loginUser.getId()))
                    .appId(String.valueOf(appId))
                    .build());
            aiResponse = aiCodeGeneratorServiceFactory.chatPlainWithFallback(chatPrompt, app.getModelKey(),
                    modelKey -> appGenerationTaskService.updateModelKey(chatTask.getId(), modelKey));
        } catch (RuntimeException e) {
            String errorMessage = getAiFriendlyErrorMessage(e);
            appGenerationTaskService.markFailed(chatTask.getId(), errorMessage);
            chatHistoryService.addChatMessage(appId, "AI 回复失败：" + errorMessage,
                    ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
        } finally {
            MonitorContextHolder.clearContext();
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

        String attachmentContext = buildAttachmentContextIfNeeded(app, message, loginUser.getId());
        String attachmentPromptBlock = StrUtil.isBlank(attachmentContext) ? "" : "\n\n" + attachmentContext;
        CodegenTemplateRagService.TemplateRetrievalResult templateRetrieval =
                codegenTemplateRagService.retrieve(message, app.getCodeGenType(), 3);
        String templatePromptBlock = StrUtil.isBlank(templateRetrieval.context()) ? "" : "\n\n" + templateRetrieval.context();
        String planPrompt = String.format("""
                用户原始需求：
                %s

                %s

                %s

                当前应用生成类型：%s
                请基于该生成类型和可用上下文输出正式编码前的结构化实现方案。
                只输出 JSON 对象，不要输出 Markdown，不要使用代码围栏。
                """, message, attachmentPromptBlock, templatePromptBlock, codeGenTypeEnum.getValue());
        AppGenerationTask planTask = appGenerationTaskService.createTask(app, loginUser.getId(),
                AppGenerationTaskModeEnum.PLAN.getValue(), message);
        appGenerationTaskService.markRunning(planTask.getId());
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        String plan;
        try {
            MonitorContextHolder.setContext(MonitorContext.builder()
                    .userId(String.valueOf(loginUser.getId()))
                    .appId(String.valueOf(appId))
                    .build());
            plan = generateAppPlanWithModelFallback(appId, codeGenTypeEnum, app.getModelKey(), planPrompt,
                    modelKey -> appGenerationTaskService.updateModelKey(planTask.getId(), modelKey));
        } catch (RuntimeException e) {
            String errorMessage = getAiFriendlyErrorMessage(e);
            appGenerationTaskService.markFailed(planTask.getId(), errorMessage);
            chatHistoryService.addChatMessage(appId, "方案生成失败：" + errorMessage,
                    ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
        } finally {
            MonitorContextHolder.clearContext();
        }

        AppGenerationPlanVO planVO = buildStructuredPlanVO(appId, message, plan, templateRetrieval.templateTitles());
        generationPlanCache.put(planVO.getPlanId(), planVO);
        chatHistoryService.addChatMessage(appId, "实现方案：\n" + planVO.getPlan(),
                ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
        appGenerationTaskService.markSuccess(planTask.getId(), JSONUtil.toJsonStr(planVO));
        return planVO;
    }

    private String generateAppPlanWithModelFallback(Long appId,
                                                    CodeGenTypeEnum codeGenTypeEnum,
                                                    String primaryModelKey,
                                                    String planPrompt,
                                                    java.util.function.Consumer<String> modelSelectionHandler) {
        List<String> candidates = aiModelFallbackRouter.resolveCandidates(primaryModelKey);
        RuntimeException lastException = null;
        for (int i = 0; i < candidates.size(); i++) {
            String candidate = candidates.get(i);
            if (modelSelectionHandler != null) {
                modelSelectionHandler.accept(candidate);
            }
            try {
                AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory
                        .getAiCodeGeneratorService(appId, codeGenTypeEnum, candidate);
                return aiCodeGeneratorService.generateAppPlan(planPrompt);
            } catch (RuntimeException e) {
                lastException = e;
                if (!aiModelFallbackRouter.isQuotaExceeded(e) || i == candidates.size() - 1) {
                    throw e;
                }
                log.warn("方案生成模型额度不足，自动切换备用模型: appId={}, from={}, to={}",
                        appId, candidate, candidates.get(i + 1));
            }
        }
        throw lastException == null ? new BusinessException(ErrorCode.SYSTEM_ERROR, "方案生成失败") : lastException;
    }

    private AppGenerationPlanVO buildStructuredPlanVO(Long appId, String message, String rawPlan,
                                                      List<String> matchedTemplates) {
        AppGenerationPlanVO planVO = new AppGenerationPlanVO();
        planVO.setAppId(appId);
        planVO.setPlanId(UUID.randomUUID().toString());
        planVO.setMessage(message);
        planVO.setMatchedTemplates(matchedTemplates);

        JSONObject planObject = parsePlanObject(rawPlan);
        if (planObject == null) {
            planVO.setPlan(rawPlan);
            return planVO;
        }
        planVO.setRequirementSummary(planObject.getStr("requirementSummary"));
        planVO.setPages(readStringList(planObject, "pages"));
        planVO.setVisualStyle(planObject.getStr("visualStyle"));
        planVO.setComponents(readStringList(planObject, "components"));
        planVO.setFilesToChange(readStringList(planObject, "filesToChange"));
        planVO.setInteractions(readStringList(planObject, "interactions"));
        planVO.setAcceptanceCriteria(readStringList(planObject, "acceptanceCriteria"));
        planVO.setRisks(readStringList(planObject, "risks"));
        planVO.setQuestions(readStringList(planObject, "questions"));
        planVO.setPlan(buildPlanMarkdown(planVO, rawPlan));
        return planVO;
    }

    private JSONObject parsePlanObject(String rawPlan) {
        if (StrUtil.isBlank(rawPlan)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(extractJsonObject(rawPlan));
        } catch (Exception e) {
            log.warn("结构化方案解析失败，降级为原始方案文本: {}", e.getMessage());
            return null;
        }
    }

    private String extractJsonObject(String rawText) {
        String text = StrUtil.trim(rawText);
        int startIndex = text.indexOf('{');
        int endIndex = text.lastIndexOf('}');
        if (startIndex < 0 || endIndex <= startIndex) {
            throw new IllegalArgumentException("未找到 JSON 对象");
        }
        return text.substring(startIndex, endIndex + 1);
    }

    private List<String> readStringList(JSONObject object, String key) {
        JSONArray array = object.getJSONArray(key);
        if (array == null) {
            return List.of();
        }
        return array.stream()
                .map(String::valueOf)
                .filter(StrUtil::isNotBlank)
                .toList();
    }

    private String buildPlanMarkdown(AppGenerationPlanVO planVO, String fallbackPlan) {
        if (StrUtil.isBlank(planVO.getRequirementSummary())
                && CollUtil.isEmpty(planVO.getPages())
                && CollUtil.isEmpty(planVO.getComponents())
                && CollUtil.isEmpty(planVO.getAcceptanceCriteria())) {
            return fallbackPlan;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("## 需求理解\n")
                .append("- ").append(StrUtil.blankToDefault(planVO.getRequirementSummary(), "已根据用户需求完成初步理解。"))
                .append("\n\n");
        appendMarkdownSection(builder, "页面与内容规划", planVO.getPages());
        if (StrUtil.isNotBlank(planVO.getVisualStyle())) {
            builder.append("## 视觉风格\n- ").append(planVO.getVisualStyle()).append("\n\n");
        }
        appendMarkdownSection(builder, "组件与文件计划", planVO.getComponents());
        appendMarkdownSection(builder, "关键文件", planVO.getFilesToChange());
        appendMarkdownSection(builder, "关键交互", planVO.getInteractions());
        appendMarkdownSection(builder, "验收标准", planVO.getAcceptanceCriteria());
        appendMarkdownSection(builder, "风险与确认点", planVO.getRisks());
        appendMarkdownSection(builder, "待确认问题", planVO.getQuestions());
        appendMarkdownSection(builder, "参考模板", planVO.getMatchedTemplates());
        builder.append("如果这个方案没问题，请点击“确认生成”，我将按此方案生成代码。");
        return builder.toString();
    }

    private void appendMarkdownSection(StringBuilder builder, String title, List<String> values) {
        if (CollUtil.isEmpty(values)) {
            return;
        }
        builder.append("## ").append(title).append('\n');
        for (String value : values) {
            builder.append("- ").append(value).append('\n');
        }
        builder.append('\n');
    }

    private String buildGenerationMessage(App app, String message, String planId, Long appId, Long userId) {
        String attachmentContext = buildAttachmentContextIfNeeded(app, message, userId);
        String recentConversationContext = buildRecentConversationContext(appId, userId, 12);
        if (StrUtil.isBlank(planId)) {
            CodegenTemplateRagService.TemplateRetrievalResult templateRetrieval =
                    codegenTemplateRagService.retrieve(message, app.getCodeGenType(), 3);
            String contextBlock = buildOptionalContextBlock(recentConversationContext, attachmentContext,
                    templateRetrieval.context());
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
        CodegenTemplateRagService.TemplateRetrievalResult templateRetrieval =
                codegenTemplateRagService.retrieve(message + "\n" + planVO.getPlan(), app.getCodeGenType(), 3);
        String contextBlock = buildOptionalContextBlock(recentConversationContext, attachmentContext,
                templateRetrieval.context());
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

    private String buildOptionalContextBlock(String... contextBlocks) {
        List<String> blocks = new ArrayList<>();
        for (String contextBlock : contextBlocks) {
            if (StrUtil.isNotBlank(contextBlock)) {
                blocks.add(contextBlock);
            }
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
            MonitorContextHolder.setContext(MonitorContext.builder()
                    .userId(String.valueOf(loginUser.getId()))
                    .build());
            selectedCodeGenType = routingService.routeCodeGenType(initPrompt);
        } catch (RuntimeException e) {
            log.error("应用代码类型路由失败, model={}, prompt={}", app.getModelKey(), initPrompt, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, getAiFriendlyErrorMessage(e));
        } finally {
            MonitorContextHolder.clearContext();
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
        ThrowUtils.throwIf(isBusyForDeploy(app.getStatus()), ErrorCode.OPERATION_ERROR,
                "应用正在生成、构建或部署中，请等待当前任务完成后再部署");

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

    private boolean isBusyForDeploy(String status) {
        return AppStatusEnum.GENERATING.getValue().equals(status)
                || AppStatusEnum.BUILDING.getValue().equals(status)
                || AppStatusEnum.DEPLOYING.getValue().equals(status);
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

    private String buildGenerationQualitySummary(App app, StringBuilder aiResponseBuilder, int streamChunkCount,
                                                 long generationStartTime, boolean usedPlan, String modelKey) {
        String sourceDirPath = getSourceDirPath(app);
        File sourceDir = new File(sourceDirPath);
        Map<String, Object> qualityMetrics = Map.of(
                "appId", app.getId(),
                "codeGenType", StrUtil.blankToDefault(app.getCodeGenType(), "unknown"),
                "modelKey", StrUtil.blankToDefault(modelKey, "unknown"),
                "usedPlan", usedPlan,
                "streamChunkCount", streamChunkCount,
                "generatedCharCount", aiResponseBuilder.length(),
                "durationMs", Math.max(0, System.currentTimeMillis() - generationStartTime),
                "sourceDirExists", sourceDir.exists() && sourceDir.isDirectory(),
                "generatedFileCount", countGeneratedFiles(sourceDir),
                "buildSuccess", true
        );
        return JSONUtil.toJsonStr(Map.of(
                "qualityMetrics", qualityMetrics,
                "aiOutputPreview", truncateText(aiResponseBuilder.toString(), 4000)
        ));
    }

    private int countGeneratedFiles(File sourceDir) {
        if (sourceDir == null || !sourceDir.exists() || !sourceDir.isDirectory()) {
            return 0;
        }
        return FileUtil.loopFiles(sourceDir).size();
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
            return "当前可用模型额度不足，系统已尝试自动切换备用模型但仍失败，请稍后重试或手动切换模型";
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
