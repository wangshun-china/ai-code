package com.ws.codecraft.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ws.codecraft.exception.BusinessException;
import com.ws.codecraft.exception.ErrorCode;
import com.ws.codecraft.model.dto.app.AppAddRequest;
import com.ws.codecraft.model.entity.User;
import com.ws.codecraft.model.enums.AiModelEnum;
import com.ws.codecraft.service.AppService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 免登录 AI 链路测试接口，只用于本地/联调验证。
 */
@RestController
@RequestMapping("/api/app/test")
public class AppAnonymousTestController {

    private static final long ANONYMOUS_TEST_USER_ID = 999999999L;

    private static final String VUE_100_PROMPT = """
            请生成一个 Vue 3 + Vite 极简清单应用，总代码尽量控制在 100 行以内。
            功能包括：添加事项、勾选完成、删除事项、统计未完成数量。
            页面风格清爽，代码保持简单，不要引入复杂依赖。
            """;

    @Resource
    private AppService appService;

    /**
     * 创建一个匿名测试应用，并直接流式生成 100 行以内 Vue 示例。
     */
    @GetMapping(value = "/vue100/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> generateVue100(@RequestParam(required = false) String modelKey) {
        User testUser = buildAnonymousTestUser();
        AppAddRequest appAddRequest = new AppAddRequest();
        appAddRequest.setInitPrompt(VUE_100_PROMPT);
        appAddRequest.setModelKey(AiModelEnum.normalize(modelKey));

        Long appId = appService.createApp(appAddRequest, testUser);
        Flux<ServerSentEvent<String>> appCreatedEvent = Flux.just(
                ServerSentEvent.<String>builder()
                        .event("app-created")
                        .data(JSONUtil.toJsonStr(Map.of("appId", appId)))
                        .build()
        );

        Flux<ServerSentEvent<String>> generationEvents = appService.chatToGenCode(appId, VUE_100_PROMPT, null, testUser)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(JSONUtil.toJsonStr(Map.of("d", chunk)))
                        .build());

        return appCreatedEvent
                .concatWith(generationEvents)
                .onErrorResume(error -> Mono.just(
                        ServerSentEvent.<String>builder()
                                .event("business-error")
                                .data(JSONUtil.toJsonStr(Map.of(
                                        "error", true,
                                        "code", ErrorCode.SYSTEM_ERROR.getCode(),
                                        "message", getAiFriendlyErrorMessage(error)
                                )))
                                .build()
                ))
                .concatWith(Mono.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build()
                ));
    }

    private User buildAnonymousTestUser() {
        User user = new User();
        user.setId(ANONYMOUS_TEST_USER_ID);
        user.setUserAccount("anonymous-test");
        user.setUserName("匿名测试用户");
        user.setUserRole("user");
        return user;
    }

    private String getAiFriendlyErrorMessage(Throwable error) {
        String errorMessage = error == null ? "" : StrUtil.blankToDefault(error.getMessage(), error.getClass().getSimpleName());
        if (errorMessage.contains("AllocationQuota.FreeTierOnly") || errorMessage.contains("403")) {
            return "当前可用模型额度不足，系统已尝试自动切换备用模型但仍失败，请稍后重试或手动切换模型";
        }
        if (error instanceof BusinessException businessException) {
            return businessException.getMessage();
        }
        return "AI 生成失败，请稍后重试";
    }
}
