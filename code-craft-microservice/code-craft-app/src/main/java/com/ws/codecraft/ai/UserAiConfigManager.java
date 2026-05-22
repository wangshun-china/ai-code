package com.ws.codecraft.ai;

import cn.hutool.core.util.StrUtil;
import com.ws.codecraft.model.enums.AiModelEnum;
import com.ws.codecraft.model.enums.AiModelEnum.ModelEndpoint;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class UserAiConfigManager {

    private static final String API_KEY_PREFIX = "ai:apikey:";
    private static final String CUSTOM_MODELS_PREFIX = "ai:custom_models:";
    private static final long CONFIG_TTL_DAYS = 30;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void setApiKey(long userId, String apiKey) {
        if (StrUtil.isBlank(apiKey)) {
            stringRedisTemplate.delete(API_KEY_PREFIX + userId);
            return;
        }
        stringRedisTemplate.opsForValue().set(API_KEY_PREFIX + userId, apiKey, CONFIG_TTL_DAYS, TimeUnit.DAYS);
        log.info("用户 {} 设置了自定义 AI API Key", userId);
    }

    public String getApiKey(long userId) {
        return stringRedisTemplate.opsForValue().get(API_KEY_PREFIX + userId);
    }

    public boolean hasCustomApiKey(long userId) {
        return StrUtil.isNotBlank(getApiKey(userId));
    }

    public void addCustomModel(long userId, CustomModelRequest request) {
        String key = CUSTOM_MODELS_PREFIX + userId;
        String existing = stringRedisTemplate.opsForValue().get(key);
        List<Map<String, String>> models = StrUtil.isNotBlank(existing)
                ? parseModels(existing) : new ArrayList<>();

        models.removeIf(m -> request.getModelName().equals(m.get("name")));
        Map<String, String> model = new HashMap<>();
        model.put("name", request.getModelName());
        model.put("displayName", request.getDisplayName());
        model.put("endpoint", request.getEndpoint().name());
        model.put("multimodal", String.valueOf(request.isMultimodal()));
        models.add(model);

        stringRedisTemplate.opsForValue().set(key, serializeModels(models), CONFIG_TTL_DAYS, TimeUnit.DAYS);
        AiModelEnum.registerDynamicModel(
                request.getModelName(), request.getDisplayName(),
                request.getEndpoint(), request.isMultimodal(),
                getApiKey(userId));
        log.info("用户 {} 添加了自定义模型: {}", userId, request.getModelName());
    }

    public void removeCustomModel(long userId, String modelName) {
        String key = CUSTOM_MODELS_PREFIX + userId;
        String existing = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(existing)) return;
        List<Map<String, String>> models = parseModels(existing);
        models.removeIf(m -> modelName.equals(m.get("name")));
        stringRedisTemplate.opsForValue().set(key, serializeModels(models), CONFIG_TTL_DAYS, TimeUnit.DAYS);
        AiModelEnum.removeDynamicModel(modelName);
    }

    public List<AiModelEnum.DynamicModel> getCustomModels(long userId) {
        String key = CUSTOM_MODELS_PREFIX + userId;
        String existing = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(existing)) return List.of();
        return parseModels(existing).stream()
                .map(m -> new AiModelEnum.DynamicModel(
                        m.get("name"), m.get("displayName"),
                        ModelEndpoint.valueOf(m.get("endpoint")),
                        Boolean.parseBoolean(m.get("multimodal")),
                        getApiKey(userId)))
                .toList();
    }

    public void loadUserModelsToRegistry(long userId) {
        String apiKey = getApiKey(userId);
        for (AiModelEnum.DynamicModel model : getCustomModels(userId)) {
            AiModelEnum.registerDynamicModel(model.name(), model.displayName(),
                    model.endpoint(), model.multimodal(), apiKey);
        }
    }

    private String serializeModels(List<Map<String, String>> models) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> m : models) {
            if (!sb.isEmpty()) sb.append(";;");
            sb.append(m.get("name")).append("::")
              .append(m.get("displayName")).append("::")
              .append(m.get("endpoint")).append("::")
              .append(m.get("multimodal"));
        }
        return sb.toString();
    }

    private List<Map<String, String>> parseModels(String raw) {
        List<Map<String, String>> result = new ArrayList<>();
        for (String entry : raw.split(";;")) {
            String[] parts = entry.split("::", 4);
            if (parts.length < 4) continue;
            Map<String, String> m = new HashMap<>();
            m.put("name", parts[0]);
            m.put("displayName", parts[1]);
            m.put("endpoint", parts[2]);
            m.put("multimodal", parts[3]);
            result.add(m);
        }
        return result;
    }

    @Data
    public static class CustomModelRequest {
        private String modelName;
        private String displayName;
        private ModelEndpoint endpoint = ModelEndpoint.DASHSCOPE_NATIVE;
        private boolean multimodal = false;
    }
}
