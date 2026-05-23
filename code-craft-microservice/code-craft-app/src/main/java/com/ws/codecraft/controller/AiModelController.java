package com.ws.codecraft.controller;

import com.ws.codecraft.ai.UserAiConfigManager;
import com.ws.codecraft.common.BaseResponse;
import com.ws.codecraft.common.ResultUtils;
import com.ws.codecraft.constant.UserConstant;
import com.ws.codecraft.exception.BusinessException;
import com.ws.codecraft.exception.ErrorCode;
import com.ws.codecraft.innerservice.InnerUserService;
import com.ws.codecraft.model.entity.User;
import com.ws.codecraft.model.request.AiModelCredentialRequest;
import com.ws.codecraft.model.vo.AiModelCredentialVO;
import com.ws.codecraft.model.vo.AiModelVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai_model")
public class AiModelController {

    @Resource
    private UserAiConfigManager userAiConfigManager;

    @DubboReference
    private InnerUserService innerUserService;

    @GetMapping("/list")
    public BaseResponse<List<AiModelVO>> listModels(@RequestParam(required = false) Long userId,
                                                    HttpServletRequest request) {
        User loginUser = getAuthUser(request);
        long targetUserId = resolveTargetUserId(loginUser, userId);
        return ResultUtils.success(userAiConfigManager.listAvailableModels(targetUserId));
    }

    @GetMapping("/credentials")
    public BaseResponse<List<AiModelCredentialVO>> listCredentials(@RequestParam(required = false) Long userId,
                                                                   HttpServletRequest request) {
        User loginUser = getAuthUser(request);
        long targetUserId = resolveTargetUserId(loginUser, userId);
        return ResultUtils.success(userAiConfigManager.listCredentialVO(targetUserId, isAdmin(loginUser)));
    }

    @PostMapping("/credential")
    public BaseResponse<Boolean> saveCredential(@RequestParam(required = false) Long userId,
                                                @RequestBody AiModelCredentialRequest request,
                                                HttpServletRequest httpServletRequest) {
        User loginUser = getAuthUser(httpServletRequest);
        long targetUserId = resolveTargetUserId(loginUser, userId);
        userAiConfigManager.saveCredential(targetUserId, request, isAdmin(loginUser));
        return ResultUtils.success(true);
    }

    @PostMapping("/credential/select")
    public BaseResponse<Boolean> selectCredential(@RequestParam(required = false) Long userId,
                                                  @RequestParam Long credentialId,
                                                  HttpServletRequest request) {
        User loginUser = getAuthUser(request);
        long targetUserId = resolveTargetUserId(loginUser, userId);
        userAiConfigManager.selectCredential(targetUserId, credentialId);
        return ResultUtils.success(true);
    }

    @DeleteMapping("/credential")
    public BaseResponse<Boolean> removeCredential(@RequestParam(required = false) Long userId,
                                                  @RequestParam Long credentialId,
                                                  HttpServletRequest request) {
        User loginUser = getAuthUser(request);
        long targetUserId = resolveTargetUserId(loginUser, userId);
        userAiConfigManager.removeCredential(targetUserId, credentialId);
        return ResultUtils.success(true);
    }

    private User getAuthUser(HttpServletRequest request) {
        Long loginUserId = InnerUserService.getLoginUserId(request);
        User user = innerUserService.getAuthUserById(loginUserId);
        if (user == null || user.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return user;
    }

    private long resolveTargetUserId(User loginUser, Long ignoredRequestUserId) {
        return loginUser.getId();
    }

    private static boolean isAdmin(User user) {
        return user != null && UserConstant.ADMIN_ROLE.equals(user.getUserRole());
    }
}
