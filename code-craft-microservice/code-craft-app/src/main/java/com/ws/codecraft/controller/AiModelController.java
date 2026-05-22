package com.ws.codecraft.controller;

import com.ws.codecraft.ai.UserAiConfigManager;
import com.ws.codecraft.annotation.AuthCheck;
import com.ws.codecraft.common.BaseResponse;
import com.ws.codecraft.common.ResultUtils;
import com.ws.codecraft.model.enums.AiModelEnum;
import com.ws.codecraft.model.enums.AiModelEnum.ModelEndpoint;
import com.ws.codecraft.model.vo.AiModelVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/ai_model")
public class AiModelController {

    @Resource
    private UserAiConfigManager userAiConfigManager;

    @GetMapping("/list")
    public BaseResponse<List<AiModelVO>> listModels(@RequestParam Long userId) {
        userAiConfigManager.loadUserModelsToRegistry(userId);
        List<AiModelVO> result = new ArrayList<>();
        for (AiModelEnum model : AiModelEnum.values()) {
            result.add(new AiModelVO(
                    model.getValue(), model.getText(),
                    model.getEndpoint().name(), model.isMultimodal(), false));
        }
        if (userId != null) {
            for (AiModelEnum.DynamicModel dm : userAiConfigManager.getCustomModels(userId)) {
                result.add(new AiModelVO(
                        dm.name(), dm.displayName(),
                        dm.endpoint().name(), dm.multimodal(), true));
            }
        }
        return ResultUtils.success(result);
    }

    @PostMapping("/apikey")
    public BaseResponse<Boolean> setApiKey(@RequestParam Long userId, @RequestBody String apiKey) {
        userAiConfigManager.setApiKey(userId, apiKey);
        userAiConfigManager.loadUserModelsToRegistry(userId);
        return ResultUtils.success(true);
    }

    @GetMapping("/apikey")
    public BaseResponse<Boolean> hasApiKey(@RequestParam Long userId) {
        return ResultUtils.success(userAiConfigManager.hasCustomApiKey(userId));
    }

    @PostMapping("/custom_model")
    public BaseResponse<?> addCustomModel(@RequestParam Long userId,
                                           @RequestBody UserAiConfigManager.CustomModelRequest request) {
        String apiKey = userAiConfigManager.getApiKey(userId);
        if (apiKey == null || apiKey.isBlank()) {
            return ResultUtils.error(40000, "请先设置 API Key");
        }
        userAiConfigManager.addCustomModel(userId, request);
        return ResultUtils.success(true);
    }

    @DeleteMapping("/custom_model")
    public BaseResponse<Boolean> removeCustomModel(@RequestParam Long userId,
                                                    @RequestParam String modelName) {
        userAiConfigManager.removeCustomModel(userId, modelName);
        return ResultUtils.success(true);
    }
}
