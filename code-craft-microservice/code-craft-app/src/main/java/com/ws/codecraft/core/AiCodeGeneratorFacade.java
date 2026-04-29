package com.ws.codecraft.core;

import cn.hutool.json.JSONUtil;
import com.ws.codecraft.ai.AiCodeGeneratorService;
import com.ws.codecraft.ai.AiCodeGeneratorServiceFactory;
import com.ws.codecraft.ai.model.HtmlCodeResult;
import com.ws.codecraft.ai.model.MultiFileCodeResult;
import com.ws.codecraft.ai.model.message.AiResponseMessage;
import com.ws.codecraft.ai.model.message.ToolExecutedMessage;
import com.ws.codecraft.ai.model.message.ToolRequestMessage;
import com.ws.codecraft.ai.stream.AiTokenStream;
import com.ws.codecraft.ai.stream.AiToolExecution;
import com.ws.codecraft.core.builder.VueProjectBuildResult;
import com.ws.codecraft.core.builder.VueProjectBuilder;
import com.ws.codecraft.core.parser.CodeParserExecutor;
import com.ws.codecraft.core.saver.CodeFileSaverExecutor;
import com.ws.codecraft.exception.BusinessException;
import com.ws.codecraft.exception.ErrorCode;
import com.ws.codecraft.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 代码生成门面类，组合代码生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    private static final int MAX_BUILD_REPAIR_ATTEMPTS = 2;
    private static final int REPAIR_TIMEOUT_MINUTES = 10;
    private static final int MAX_BUILD_ERROR_CHARS = 6000;

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用 ID
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        return generateAndSaveCode(userMessage, codeGenTypeEnum, appId, null);
    }

    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId, String modelKey) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        // 根据 appId 获取相应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum, modelKey);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用 ID
     * @return 保存的目录
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        return generateAndSaveCodeStream(userMessage, codeGenTypeEnum, appId, null);
    }

    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId, String modelKey) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        // 根据 appId 获取相应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum, modelKey);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case VUE_PROJECT -> {
                AiTokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream, aiCodeGeneratorService, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 将 AiTokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream AiTokenStream 对象
     * @param appId       应用 ID
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(AiTokenStream tokenStream, AiCodeGeneratorService aiCodeGeneratorService, Long appId) {
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onToolRequest((index, toolExecutionRequest) -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onToolExecuted((AiToolExecution toolExecution) -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onComplete(response -> {
                        // 执行 Vue 项目构建（同步执行，确保预览时项目已就绪）
                        VueProjectBuildResult buildResult = buildVueProjectWithAutoRepair(aiCodeGeneratorService, appId, sink);
                        if (!buildResult.isSuccess()) {
                            sink.error(new BusinessException(ErrorCode.SYSTEM_ERROR,
                                    "Vue 项目构建失败: " + buildResult.getMessage()));
                            return;
                        }
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        sink.error(error);
                    })
                    .start();
        });
    }

    private VueProjectBuildResult buildVueProjectWithAutoRepair(AiCodeGeneratorService aiCodeGeneratorService,
                                                                Long appId,
                                                                FluxSink<String> sink) {
        String projectDirName = "vue_project_" + appId;
        VueProjectBuildResult lastBuildResult = null;
        for (int attempt = 0; attempt <= MAX_BUILD_REPAIR_ATTEMPTS; attempt++) {
            lastBuildResult = vueProjectBuilder.buildProjectWithResult(projectDirName);
            if (lastBuildResult.isSuccess()) {
                if (attempt > 0) {
                    emitAiMessage(sink, "\n\n自动修复完成，Vue 项目已成功构建。");
                }
                return lastBuildResult;
            }

            if (attempt >= MAX_BUILD_REPAIR_ATTEMPTS) {
                break;
            }

            int repairAttempt = attempt + 1;
            emitAiMessage(sink, String.format("""


                    检测到 Vue 项目构建失败，正在进行第 %d 次自动修复...
                    """, repairAttempt));
            boolean repaired = runBuildRepairStream(aiCodeGeneratorService, appId, repairAttempt,
                    lastBuildResult.getMessage(), sink);
            if (!repaired) {
                return VueProjectBuildResult.failure("自动修复未完成: " + lastBuildResult.getMessage());
            }
        }
        return lastBuildResult == null ? VueProjectBuildResult.failure("未知构建失败") : lastBuildResult;
    }

    private boolean runBuildRepairStream(AiCodeGeneratorService aiCodeGeneratorService,
                                         Long appId,
                                         int repairAttempt,
                                         String buildError,
                                         FluxSink<String> sink) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AiTokenStream repairStream = aiCodeGeneratorService.repairVueProjectBuildStream(appId,
                buildRepairPrompt(repairAttempt, buildError));
        repairStream.onPartialResponse((String partialResponse) -> emitAiMessage(sink, partialResponse))
                .onToolRequest((index, toolExecutionRequest) -> {
                    ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                    sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                })
                .onToolExecuted((AiToolExecution toolExecution) -> {
                    ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                    sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                })
                .onComplete(response -> latch.countDown())
                .onError((Throwable error) -> {
                    errorRef.set(error);
                    latch.countDown();
                })
                .start();

        try {
            boolean completed = latch.await(REPAIR_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!completed) {
                log.error("Vue 项目自动修复超时, appId={}, attempt={}", appId, repairAttempt);
                return false;
            }
            Throwable error = errorRef.get();
            if (error != null) {
                log.error("Vue 项目自动修复失败, appId={}, attempt={}", appId, repairAttempt, error);
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Vue 项目自动修复被中断, appId={}, attempt={}", appId, repairAttempt, e);
            return false;
        }
    }

    private String buildRepairPrompt(int repairAttempt, String buildError) {
        return String.format("""
                当前 Vue 项目在第 %d 次构建时失败。请进入构建修复模式，只修复导致构建失败的问题，不要重做整个项目，不要额外添加新功能。

                构建错误信息：
                ```text
                %s
                ```

                修复要求：
                1. 先使用目录读取工具了解当前项目结构。
                2. 根据错误日志读取相关文件，定位根因。
                3. 如果缺少 Vue 工程骨架，必须补齐 package.json、vite.config.js、index.html、src/main.js、src/App.vue。
                4. 优先使用文件修改工具做最小修改；只有确实需要时才重写文件。
                5. 不要删除用户已生成的主要页面和样式，不要改变原始需求方向。
                6. 修复完成后简要说明修改了哪些文件，然后结束。
                """, repairAttempt, truncateBuildError(buildError));
    }

    private String truncateBuildError(String buildError) {
        if (buildError == null || buildError.isBlank()) {
            return "node-builder 未返回具体构建错误。";
        }
        if (buildError.length() <= MAX_BUILD_ERROR_CHARS) {
            return buildError;
        }
        return buildError.substring(0, MAX_BUILD_ERROR_CHARS) + "\n...（错误日志已截断）";
    }

    private void emitAiMessage(FluxSink<String> sink, String content) {
        AiResponseMessage aiResponseMessage = new AiResponseMessage(content);
        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
    }

    /**
     * 通用流式代码处理方法
     *
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @param appId       应用 ID
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId) {
        // 字符串拼接器，用于当流式返回所有的代码之后，再保存代码
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(chunk -> {
            // 实时收集代码片段
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            // 流式返回完成后，保存代码
            try {
                String completeCode = codeBuilder.toString();
                // 使用执行器解析代码
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                // 使用执行器保存代码
                File saveDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                log.info("保存成功，目录为：{}", saveDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("保存失败: {}", e.getMessage());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存代码失败: " + e.getMessage());
            }
        });
    }
}
