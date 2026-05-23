package com.ws.codecraft.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ws.codecraft.ai.AiCodeGeneratorServiceFactory;
import com.ws.codecraft.ai.AiCodeGenTypeRoutingServiceFactory;
import com.ws.codecraft.ai.AiCodeGenTypeRoutingService;
import com.ws.codecraft.ai.AiCodeGeneratorService;
import com.ws.codecraft.ai.UserAiConfigManager;
import com.ws.codecraft.ai.model.HtmlCodeResult;
import com.ws.codecraft.ai.model.MultiFileCodeResult;
import com.ws.codecraft.ai.stream.AiTokenStream;
import com.ws.codecraft.model.ai.AiModelRegistry;
import com.ws.codecraft.model.enums.CodeGenTypeEnum;
import com.ws.codecraft.model.vo.AiModelVO;
import com.ws.codecraft.service.AttachmentAnalysisService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 免登录 AI 链路测试接口，只用于本地/联调验证。
 */
@Slf4j
@RestController
@RequestMapping("/api/app/test")
public class AppAnonymousTestController {

    private static final long TEST_APP_ID = 999_000_001L;
    private static final long TEST_USER_ID = 999_000_000L;
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "json", "csv", "html", "htm", "css", "js", "ts", "vue", "xml", "yml", "yaml"
    );

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private AiCodeGenTypeRoutingServiceFactory routingServiceFactory;

    @Resource
    private AttachmentAnalysisService attachmentAnalysisService;

    @Resource
    private UserAiConfigManager userAiConfigManager;

    /**
     * 测试接口说明：不调用 AI，用于确认服务已启动、测试入口可访问。
     * GET /api/app/test/health
     */
    @GetMapping("/health")
    public Map<String, Object> testHealth() {
        return Map.of(
                "status", "ok",
                "time", LocalDateTime.now().toString(),
                "testAppId", TEST_APP_ID,
                "endpoints", List.of(
                        "GET /api/app/test/models",
                        "GET /api/app/test/chat?message=你好",
                        "GET /api/app/test/route?message=帮我做一个个人博客网站",
                        "GET /api/app/test/plan?message=帮我做一个课程主页&type=vue_project",
                        "GET /api/app/test/html?message=生成一个极简清单页面",
                        "GET /api/app/test/multi-file?message=生成一个极简清单页面",
                        "POST /api/app/test/attachment/analyze",
                        "GET /api/app/test/vue100/stream?message=生成一个100行以内Vue清单应用"
                )
        );
    }

    /**
     * 查看模型白名单。
     * GET /api/app/test/models
     */
    @GetMapping("/models")
    public List<Map<String, String>> testModels() {
        return userAiConfigManager.listAvailableModels(TEST_USER_ID).stream()
                .map(model -> Map.of(
                        "value", model.getValue(),
                        "text", model.getText(),
                        "default", String.valueOf(AiModelRegistry.DEFAULT_MODEL_KEY.equals(model.getValue()))
                ))
                .toList();
    }

    /**
     * 测试 AI 聊天：直接调用 ChatClient，验证 URL/模型连接是否正常。
     * GET /api/app/test/chat?message=你好&modelKey=qwen3.6-plus
     */
    @GetMapping("/chat")
    public Map<String, String> testChat(
            @RequestParam(defaultValue = "你好，请用一句话介绍你自己") String message,
            @RequestParam(required = false) String modelKey) {
        String normalizedKey = resolveTestModelKey(modelKey);
        log.info("[test-chat] message={}, model={}", message, normalizedKey);
        long start = System.currentTimeMillis();
        String response = aiCodeGeneratorServiceFactory.chatPlain(message, normalizedKey);
        long cost = System.currentTimeMillis() - start;
        log.info("[test-chat] done, cost={}ms, responseLen={}", cost, response.length());
        return Map.of(
                "model", normalizedKey,
                "response", response,
                "costMs", String.valueOf(cost)
        );
    }

    /**
     * 测试路由分类：验证 LlmRoutingAgent 是否正常工作。
     * GET /api/app/test/route?message=帮我做一个个人博客网站
     */
    @GetMapping("/route")
    public Map<String, String> testRoute(
            @RequestParam(defaultValue = "帮我做一个个人博客网站") String message,
            @RequestParam(required = false) String modelKey) {
        log.info("[test-route] message={}, modelKey={}", message, modelKey);
        long start = System.currentTimeMillis();
        AiCodeGenTypeRoutingService routingService = StrUtil.isNotBlank(modelKey)
                ? routingServiceFactory.createAiCodeGenTypeRoutingService(modelKey)
                : routingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum result = routingService.routeCodeGenType(message);
        long cost = System.currentTimeMillis() - start;
        log.info("[test-route] done, result={}, cost={}ms", result, cost);
        return Map.of(
                "message", message,
                "result", result == null ? "" : result.name(),
                "value", result == null ? "" : result.getValue(),
                "costMs", String.valueOf(cost)
        );
    }

    /**
     * 测试方案生成：覆盖 plan prompt、模型调用、JSON 方案输出链路。
     * GET /api/app/test/plan?message=帮我做一个课程主页&type=vue_project&modelKey=qwen3.6-plus
     */
    @GetMapping("/plan")
    public Map<String, Object> testPlan(
            @RequestParam(defaultValue = "帮我做一个课程主页，包含课程简介、教学目标、教学安排和资源下载") String message,
            @RequestParam(defaultValue = "vue_project") String type,
            @RequestParam(required = false) String modelKey) {
        CodeGenTypeEnum codeGenType = parseCodeGenType(type);
        String normalizedKey = resolveTestModelKey(modelKey);
        log.info("[test-plan] type={}, model={}, message={}", codeGenType, normalizedKey, message);
        long start = System.currentTimeMillis();
        AiCodeGeneratorService service = aiCodeGeneratorServiceFactory
                .getAiCodeGeneratorService(TEST_APP_ID, codeGenType, normalizedKey);
        String plan = service.generateAppPlan(message);
        long cost = System.currentTimeMillis() - start;
        return Map.of(
                "type", codeGenType.getValue(),
                "model", normalizedKey,
                "costMs", cost,
                "planLength", plan.length(),
                "plan", plan
        );
    }

    /**
     * 测试 HTML 代码生成：不写文件，只验证 HTML 生成和解析。
     * GET /api/app/test/html?message=生成一个极简清单页面
     */
    @GetMapping("/html")
    public Map<String, Object> testHtml(
            @RequestParam(defaultValue = "生成一个极简清单页面，代码尽量短") String message,
            @RequestParam(required = false) String modelKey) {
        String normalizedKey = resolveTestModelKey(modelKey);
        long start = System.currentTimeMillis();
        HtmlCodeResult result = aiCodeGeneratorServiceFactory
                .getAiCodeGeneratorService(TEST_APP_ID, CodeGenTypeEnum.HTML, normalizedKey)
                .generateHtmlCode(message);
        long cost = System.currentTimeMillis() - start;
        String html = StrUtil.blankToDefault(result.getHtmlCode(), "");
        return Map.of(
                "model", normalizedKey,
                "costMs", cost,
                "htmlLength", html.length(),
                "description", StrUtil.blankToDefault(result.getDescription(), ""),
                "htmlPreview", StrUtil.subPre(html, 1000)
        );
    }

    /**
     * 测试多文件代码生成：不写文件，只验证 HTML/CSS/JS 生成和解析。
     * GET /api/app/test/multi-file?message=生成一个极简清单页面
     */
    @GetMapping("/multi-file")
    public Map<String, Object> testMultiFile(
            @RequestParam(defaultValue = "生成一个极简清单页面，包含基础样式和添加待办交互") String message,
            @RequestParam(required = false) String modelKey) {
        String normalizedKey = resolveTestModelKey(modelKey);
        long start = System.currentTimeMillis();
        MultiFileCodeResult result = aiCodeGeneratorServiceFactory
                .getAiCodeGeneratorService(TEST_APP_ID, CodeGenTypeEnum.MULTI_FILE, normalizedKey)
                .generateMultiFileCode(message);
        long cost = System.currentTimeMillis() - start;
        String html = StrUtil.blankToDefault(result.getHtmlCode(), "");
        String css = StrUtil.blankToDefault(result.getCssCode(), "");
        String js = StrUtil.blankToDefault(result.getJsCode(), "");
        return Map.of(
                "model", normalizedKey,
                "costMs", cost,
                "htmlLength", html.length(),
                "cssLength", css.length(),
                "jsLength", js.length(),
                "description", StrUtil.blankToDefault(result.getDescription(), ""),
                "htmlPreview", StrUtil.subPre(html, 600),
                "cssPreview", StrUtil.subPre(css, 600),
                "jsPreview", StrUtil.subPre(js, 600)
        );
    }

    /**
     * 测试附件解析：PDF/DOCX/文本走本地解析和截断，图片会调用视觉模型。
     * POST /api/app/test/attachment/analyze
     */
    @PostMapping(value = "/attachment/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> testAttachmentAnalyze(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            return Map.of("success", false, "message", "file 不能为空");
        }
        String originalFileName = StrUtil.blankToDefault(file.getOriginalFilename(), "attachment");
        String extension = StrUtil.blankToDefault(FileUtil.extName(originalFileName), "").toLowerCase();
        String mimeType = file.getContentType();
        String fileType = detectFileType(extension, mimeType);
        if (StrUtil.isBlank(fileType)) {
            return Map.of("success", false, "message", "不支持的附件类型", "fileName", originalFileName);
        }

        Path tempDir = Files.createTempDirectory("code-craft-attachment-test-");
        Path tempFile = tempDir.resolve(UUID.randomUUID() + (StrUtil.isBlank(extension) ? "" : "." + extension));
        long start = System.currentTimeMillis();
        try {
            file.transferTo(tempFile);
            String summary = attachmentAnalysisService.analyze(tempFile, originalFileName, fileType, mimeType);
            long cost = System.currentTimeMillis() - start;
            return Map.of(
                    "success", true,
                    "fileName", originalFileName,
                    "fileType", fileType,
                    "mimeType", StrUtil.blankToDefault(mimeType, ""),
                    "size", file.getSize(),
                    "costMs", cost,
                    "summaryLength", summary.length(),
                    "summary", summary
            );
        } finally {
            FileUtil.del(tempDir.toFile());
        }
    }

    /**
     * 测试 Vue 工程流式生成：会使用固定测试 appId 写入 tmp/code_output/vue_project_999000001。
     * GET /api/app/test/vue100/stream?message=生成一个100行以内Vue清单应用
     */
    @GetMapping(value = "/vue100/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> testVue100Stream(
            @RequestParam(defaultValue = "生成一个100行以内的 Vue 极简清单应用，包含添加、完成、删除待办") String message,
            @RequestParam(required = false) String modelKey) {
        String normalizedKey = resolveTestModelKey(modelKey);
        AiTokenStream tokenStream = aiCodeGeneratorServiceFactory
                .getAiCodeGeneratorService(TEST_APP_ID, CodeGenTypeEnum.VUE_PROJECT, normalizedKey)
                .generateVueProjectCodeStream(TEST_APP_ID, message);
        return Flux.<ServerSentEvent<String>>create(sink -> tokenStream
                        .onPartialResponse(chunk -> sink.next(sse("chunk", Map.of("d", chunk))))
                        .onToolRequest((seq, request) -> sink.next(sse("tool-request", Map.of(
                                "id", request.id(),
                                "name", request.name(),
                                "arguments", request.arguments()
                        ))))
                        .onToolExecuted(execution -> sink.next(sse("tool-executed", Map.of(
                                "name", execution.request().name(),
                                "result", execution.result()
                        ))))
                        .onComplete(response -> {
                            sink.next(sse("done", Map.of(
                                    "model", normalizedKey,
                                    "appId", TEST_APP_ID,
                                    "responseLength", response.length()
                            )));
                            sink.complete();
                        })
                        .onError(sink::error)
                        .start())
                .onErrorResume(error -> Mono.just(sse("business-error", Map.of(
                        "error", true,
                        "message", StrUtil.blankToDefault(error.getMessage(), error.getClass().getSimpleName())
                ))));
    }

    private CodeGenTypeEnum parseCodeGenType(String type) {
        CodeGenTypeEnum codeGenType = CodeGenTypeEnum.getEnumByValue(type);
        return codeGenType == null ? CodeGenTypeEnum.VUE_PROJECT : codeGenType;
    }

    private String detectFileType(String extension, String mimeType) {
        String normalizedMimeType = StrUtil.blankToDefault(mimeType, "").toLowerCase();
        if (normalizedMimeType.startsWith("image/")) {
            return "image";
        }
        if ("pdf".equals(extension) || "application/pdf".equals(normalizedMimeType)) {
            return "pdf";
        }
        if ("docx".equals(extension)) {
            return "docx";
        }
        if (TEXT_EXTENSIONS.contains(extension) || normalizedMimeType.startsWith("text/")) {
            return "text";
        }
        return "";
    }

    private String resolveTestModelKey(String modelKey) {
        if (StrUtil.isNotBlank(modelKey)) {
            return AiModelRegistry.normalize(modelKey);
        }
        List<AiModelVO> models = userAiConfigManager.listAvailableModels(TEST_USER_ID);
        return models.isEmpty() ? AiModelRegistry.DEFAULT_MODEL_KEY : models.get(0).getValue();
    }

    private ServerSentEvent<String> sse(String event, Map<String, Object> data) {
        return ServerSentEvent.<String>builder()
                .event(event)
                .data(JSONUtil.toJsonStr(data))
                .build();
    }
}
