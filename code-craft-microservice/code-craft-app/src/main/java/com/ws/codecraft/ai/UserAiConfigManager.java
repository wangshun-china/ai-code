package com.ws.codecraft.ai;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.ws.codecraft.mapper.AiModelCredentialMapper;
import com.ws.codecraft.ai.config.StreamingChatModelConfig;
import com.ws.codecraft.model.ai.AiModelRegistry;
import com.ws.codecraft.model.entity.AiModelCredential;
import com.ws.codecraft.model.request.AiModelCredentialRequest;
import com.ws.codecraft.model.vo.AiModelCredentialVO;
import com.ws.codecraft.model.vo.AiModelVO;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 用户 AI 模型凭据管理。
 */
@Component
@Slf4j
public class UserAiConfigManager {

    public static final long SYSTEM_CREDENTIAL_ID = 0L;
    public static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String DEFAULT_KEY_MASK = "*****";
    private static final String NORMAL_KEY_MASK = "***";

    @Resource
    private AiModelCredentialMapper aiModelCredentialMapper;

    @Resource
    private StreamingChatModelConfig streamingChatModelConfig;

    public void setApiKey(long userId, String apiKey) {
        if (StrUtil.isBlank(apiKey)) {
            selectCredential(userId, SYSTEM_CREDENTIAL_ID);
            return;
        }
        AiModelCredentialRequest request = new AiModelCredentialRequest();
        request.setName("自定义默认 Key");
        request.setApiKey(apiKey);
        request.setBaseUrl(DEFAULT_BASE_URL);
        request.setModelNames(defaultModelNames());
        saveCredential(userId, request);
    }

    public String getApiKey(long userId) {
        AiModelCredential credential = getActiveCredential(userId);
        if (credential == null || isSystemCredential(credential)) {
            return null;
        }
        return credential.getApiKey();
    }

    public boolean hasCustomApiKey(long userId) {
        return StrUtil.isNotBlank(getApiKey(userId));
    }

    public AiModelCredential saveCredential(long userId, AiModelCredentialRequest request) {
        return saveCredential(userId, request, false);
    }

    public AiModelCredential saveCredential(long userId, AiModelCredentialRequest request, boolean allowSystemEdit) {
        validateCredentialModels(request);
        String normalizedModels = joinModelNames(request.getModelNames());
        if (request.getId() != null && request.getId() > 0) {
            AiModelCredential existing = getCredential(userId, request.getId());
            if (existing == null) {
                throw new IllegalArgumentException("模型配置不存在");
            }
            if (isSystemCredential(existing) && !allowSystemEdit) {
                throw new IllegalArgumentException("系统默认配置不能修改");
            }
            existing.setName(StrUtil.blankToDefault(request.getName(), existing.getName()));
            if (StrUtil.isNotBlank(request.getApiKey()) && !isMaskedKey(request.getApiKey())) {
                existing.setApiKey(request.getApiKey().trim());
            }
            existing.setBaseUrl(StrUtil.blankToDefault(request.getBaseUrl(), DEFAULT_BASE_URL).trim());
            existing.setModelNames(normalizedModels);
            aiModelCredentialMapper.update(existing);
            AiModelRegistry.removeDynamicModelsByPrefix(buildCredentialPrefix(existing.getId()));
            registerCredentialModels(existing);
            return existing;
        }

        validateCredentialApiKey(request);
        AiModelCredential credential = new AiModelCredential();
        credential.setUserId(userId);
        credential.setName(StrUtil.blankToDefault(request.getName(), "自定义模型配置"));
        credential.setApiKey(request.getApiKey().trim());
        credential.setBaseUrl(StrUtil.blankToDefault(request.getBaseUrl(), DEFAULT_BASE_URL).trim());
        credential.setModelNames(normalizedModels);
        credential.setIsDefault(1);
        credential.setSystemDefault(0);
        aiModelCredentialMapper.insert(credential);
        clearOtherDefaults(userId, credential.getId());
        registerCredentialModels(credential);
        log.info("用户 {} 保存了 AI 模型凭据配置: {}", userId, credential.getName());
        return credential;
    }

    public void selectCredential(long userId, long credentialId) {
        if (credentialId == SYSTEM_CREDENTIAL_ID) {
            for (AiModelCredential credential : listCredentials(userId)) {
                AiModelRegistry.removeDynamicModelsByPrefix(buildCredentialPrefix(credential.getId()));
            }
            AiModelCredential systemCredential = ensureSystemCredential(userId);
            systemCredential.setIsDefault(1);
            aiModelCredentialMapper.update(systemCredential);
            clearOtherDefaults(userId, systemCredential.getId());
            return;
        }
        AiModelCredential credential = getCredential(userId, credentialId);
        if (credential == null) {
            throw new IllegalArgumentException("模型配置不存在");
        }
        credential.setIsDefault(1);
        aiModelCredentialMapper.update(credential);
        clearOtherDefaults(userId, credentialId);
        registerCredentialModels(credential);
    }

    public void removeCredential(long userId, long credentialId) {
        AiModelCredential credential = getCredential(userId, credentialId);
        if (credential == null) {
            return;
        }
        if (isSystemCredential(credential)) {
            return;
        }
        boolean wasDefault = Objects.equals(credential.getIsDefault(), 1);
        aiModelCredentialMapper.deleteById(credentialId);
        AiModelRegistry.removeDynamicModelsByPrefix(buildCredentialPrefix(credentialId));
        if (wasDefault) {
            selectCredential(userId, SYSTEM_CREDENTIAL_ID);
        }
    }

    public List<AiModelCredentialVO> listCredentialVO(long userId, boolean admin, long viewerUserId) {
        ensureSystemCredential(userId);
        AiModelCredential active = getActiveCredential(userId);
        List<AiModelCredentialVO> result = new ArrayList<>();
        for (AiModelCredential credential : listCredentials(userId)) {
            result.add(toVO(credential, admin, viewerUserId, active));
        }
        return result;
    }

    public List<AiModelVO> listAvailableModels(long userId) {
        AiModelCredential active = getActiveCredential(userId);
        if (active == null) {
            return List.of();
        }
        boolean systemDefault = isSystemCredential(active);
        if (!systemDefault || hasUsableKey(active.getApiKey())) {
            registerCredentialModels(active);
        }
        return parseModelNames(active.getModelNames()).stream()
                .map(modelName -> new AiModelVO(
                        buildModelKey(active, modelName),
                        modelName,
                        AiModelRegistry.ENDPOINT_OPENAI_COMPATIBLE,
                        active.getBaseUrl(),
                        true,
                        !systemDefault))
                .toList();
    }

    public boolean isAvailableModelKey(long userId, String modelKey) {
        if (StrUtil.isBlank(modelKey)) {
            return false;
        }
        String normalized = AiModelRegistry.normalize(modelKey);
        return listAvailableModels(userId).stream()
                .anyMatch(model -> Objects.equals(model.getValue(), normalized));
    }

    public String resolveDefaultModelKey(long userId) {
        List<AiModelVO> models = listAvailableModels(userId);
        if (!models.isEmpty() && StrUtil.isNotBlank(models.get(0).getValue())) {
            return models.get(0).getValue();
        }
        return AiModelRegistry.DEFAULT_MODEL_KEY;
    }

    public String resolveRequestedModelKey(long userId, String requestedModelKey) {
        if (StrUtil.isBlank(requestedModelKey)) {
            return resolveDefaultModelKey(userId);
        }
        String normalized = AiModelRegistry.normalize(requestedModelKey);
        return isAvailableModelKey(userId, normalized) ? normalized : resolveDefaultModelKey(userId);
    }

    public void addCustomModel(long userId, CustomModelRequest request) {
        AiModelCredentialRequest credentialRequest = new AiModelCredentialRequest();
        credentialRequest.setName(StrUtil.blankToDefault(request.getDisplayName(), request.getModelName()));
        credentialRequest.setApiKey(request.getApiKey());
        credentialRequest.setBaseUrl(StrUtil.blankToDefault(request.getBaseUrl(), DEFAULT_BASE_URL));
        credentialRequest.setModelNames(List.of(request.getModelName()));
        saveCredential(userId, credentialRequest);
    }

    public List<AiModelRegistry.DynamicModel> getCustomModels(long userId) {
        AiModelCredential active = getActiveCredential(userId);
        if (active == null || isSystemCredential(active)) {
            return List.of();
        }
        return parseModelNames(active.getModelNames()).stream()
                .map(modelName -> new AiModelRegistry.DynamicModel(
                        buildCredentialModelKey(active.getId(), modelName),
                        modelName,
                        AiModelRegistry.ENDPOINT_OPENAI_COMPATIBLE,
                        true,
                        active.getApiKey(),
                        active.getBaseUrl(),
                        modelName))
                .toList();
    }

    public void loadUserModelsToRegistry(long userId) {
        ensureSystemCredential(userId);
        for (AiModelCredential credential : listCredentials(userId)) {
            if (!isSystemCredential(credential) && hasUsableKey(credential.getApiKey())) {
                registerCredentialModels(credential);
            }
        }
    }

    public void removeCustomModel(long userId, String modelName) {
        AiModelCredential active = getActiveCredential(userId);
        if (active == null || isSystemCredential(active) || StrUtil.isBlank(modelName)) {
            return;
        }
        List<String> names = new ArrayList<>(parseModelNames(active.getModelNames()));
        names.removeIf(item -> Objects.equals(item, modelName));
        if (names.isEmpty()) {
            removeCredential(userId, active.getId());
            return;
        }
        active.setModelNames(joinModelNames(names));
        aiModelCredentialMapper.update(active);
        AiModelRegistry.removeDynamicModelsByPrefix(buildCredentialPrefix(active.getId()));
        registerCredentialModels(active);
    }

    public static String buildCredentialModelKey(long credentialId, String modelName) {
        return "c:" + credentialId + ":" + Integer.toUnsignedString(modelName.hashCode(), 36);
    }

    private static String buildCredentialPrefix(long credentialId) {
        return "c:" + credentialId + ":";
    }

    private static String buildModelKey(AiModelCredential credential, String modelName) {
        return isSystemCredential(credential) ? modelName : buildCredentialModelKey(credential.getId(), modelName);
    }

    private void registerCredentialModels(AiModelCredential credential) {
        AiModelRegistry.removeDynamicModelsByPrefix(buildCredentialPrefix(credential.getId()));
        for (String modelName : parseModelNames(credential.getModelNames())) {
            AiModelRegistry.registerDynamicModel(
                    buildModelKey(credential, modelName),
                    modelName,
                    true,
                    credential.getApiKey(),
                    credential.getBaseUrl(),
                    modelName);
        }
    }

    private AiModelCredential getActiveCredential(long userId) {
        ensureSystemCredential(userId);
        return aiModelCredentialMapper.selectOneByQuery(QueryWrapper.create()
                .eq("userId", userId)
                .eq("isDefault", 1)
                .orderBy("updateTime", false)
                .limit(1));
    }

    private AiModelCredential getCredential(long userId, long credentialId) {
        return aiModelCredentialMapper.selectOneByQuery(QueryWrapper.create()
                .eq("id", credentialId)
                .eq("userId", userId)
                .limit(1));
    }

    private List<AiModelCredential> listCredentials(long userId) {
        return aiModelCredentialMapper.selectListByQuery(QueryWrapper.create()
                .eq("userId", userId)
                .orderBy("isDefault", false)
                .orderBy("systemDefault", false)
                .orderBy("updateTime", false));
    }

    private void clearOtherDefaults(long userId, Long keepId) {
        for (AiModelCredential credential : listCredentials(userId)) {
            if (keepId != null && keepId.equals(credential.getId())) {
                continue;
            }
            if (Objects.equals(credential.getIsDefault(), 1)) {
                credential.setIsDefault(0);
                aiModelCredentialMapper.update(credential);
            }
        }
    }

    private AiModelCredentialVO toVO(AiModelCredential credential, boolean admin, long viewerUserId,
                                     AiModelCredential active) {
        boolean systemDefault = isSystemCredential(credential);
        return new AiModelCredentialVO(
                credential.getId(),
                credential.getName(),
                visibleApiKey(credential, admin, viewerUserId),
                credential.getBaseUrl(),
                parseModelNames(credential.getModelNames()),
                active != null && Objects.equals(active.getId(), credential.getId()),
                systemDefault);
    }

    private String visibleApiKey(AiModelCredential credential, boolean admin, long viewerUserId) {
        boolean systemDefault = isSystemCredential(credential);
        if (systemDefault) {
            return admin ? credential.getApiKey() : DEFAULT_KEY_MASK;
        }
        if (Objects.equals(credential.getUserId(), viewerUserId)) {
            return credential.getApiKey();
        }
        return NORMAL_KEY_MASK;
    }

    private AiModelCredential ensureSystemCredential(long userId) {
        AiModelCredential credential = aiModelCredentialMapper.selectOneByQuery(QueryWrapper.create()
                .eq("userId", userId)
                .eq("systemDefault", 1)
                .limit(1));
        String defaultApiKey = StrUtil.blankToDefault(streamingChatModelConfig.getApiKey(), DEFAULT_KEY_MASK);
        String modelNames = joinModelNames(defaultModelNames());
        if (credential == null) {
            credential = new AiModelCredential();
            credential.setUserId(userId);
            credential.setName("系统默认额度");
            credential.setApiKey(defaultApiKey);
            credential.setBaseUrl(DEFAULT_BASE_URL);
            credential.setModelNames(modelNames);
            credential.setSystemDefault(1);
            boolean hasAnyCredential = !listCredentialsNoEnsure(userId).isEmpty();
            credential.setIsDefault(hasAnyCredential ? 0 : 1);
            aiModelCredentialMapper.insert(credential);
            return credential;
        }
        credential.setName(StrUtil.blankToDefault(credential.getName(), "系统默认额度"));
        if (StrUtil.isBlank(credential.getModelNames())) {
            credential.setModelNames(modelNames);
        }
        credential.setSystemDefault(1);
        if ((StrUtil.isBlank(credential.getApiKey()) || isMaskedKey(credential.getApiKey()))
                && StrUtil.isNotBlank(defaultApiKey) && !DEFAULT_KEY_MASK.equals(defaultApiKey)) {
            credential.setApiKey(defaultApiKey);
            credential.setBaseUrl(DEFAULT_BASE_URL);
        }
        aiModelCredentialMapper.update(credential);
        return credential;
    }

    private List<AiModelCredential> listCredentialsNoEnsure(long userId) {
        return aiModelCredentialMapper.selectListByQuery(QueryWrapper.create()
                .eq("userId", userId)
                .orderBy("isDefault", false)
                .orderBy("systemDefault", false)
                .orderBy("updateTime", false));
    }

    private static boolean isSystemCredential(AiModelCredential credential) {
        return credential != null && Objects.equals(credential.getSystemDefault(), 1);
    }

    private void validateCredentialApiKey(AiModelCredentialRequest request) {
        if (request == null || StrUtil.isBlank(request.getApiKey()) || isMaskedKey(request.getApiKey())) {
            throw new IllegalArgumentException("请填写 API Key");
        }
    }

    private void validateCredentialModels(AiModelCredentialRequest request) {
        if (request == null || request.getModelNames() == null
                || request.getModelNames().stream().allMatch(StrUtil::isBlank)) {
            throw new IllegalArgumentException("请至少填写一个模型名称");
        }
    }

    private static final List<String> DEFAULT_MODEL_NAMES = List.of(
            "qwen3.7-max-2026-05-20",
            "qwen3.6-max-preview",
            "qwen3.6-plus",
            "qwen3.6-plus-2026-04-02",
            "qwen3.5-plus",
            "qwen3.5-plus-2026-04-20",
            "qwen3.6-flash",
            "qwen3.6-flash-2026-04-16",
            "qwen3.5-flash",
            "qwen3.5-flash-2026-02-23",
            "qwen-flash-character-2026-05-20",
            "qwen3.6-35b-a3b",
            "qwen3.5-122b-a10b",
            "qwen3.5-35b-a3b",
            "qwen3.6-27b",
            "qwen3.5-27b",
            "glm-5.1",
            "gui-plus-2026-02-26",
            "deepseek-v4-pro",
            "deepseek-v4-flash"
    );

    private static List<String> defaultModelNames() {
        return DEFAULT_MODEL_NAMES;
    }

    private static boolean isMaskedKey(String apiKey) {
        return DEFAULT_KEY_MASK.equals(apiKey) || NORMAL_KEY_MASK.equals(apiKey);
    }

    private static boolean hasUsableKey(String apiKey) {
        return StrUtil.isNotBlank(apiKey) && !isMaskedKey(apiKey);
    }

    private static List<String> parseModelNames(String raw) {
        if (StrUtil.isBlank(raw)) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (String item : raw.split("[,，\\n\\r]+")) {
            if (StrUtil.isNotBlank(item)) {
                names.add(item.trim());
            }
        }
        return List.copyOf(names);
    }

    private static String joinModelNames(List<String> modelNames) {
        return String.join("\n", parseModelNames(String.join("\n", modelNames)));
    }

    @Data
    public static class CustomModelRequest {
        private String modelName;
        private String displayName;
        private String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private String endpoint = AiModelRegistry.ENDPOINT_OPENAI_COMPATIBLE;
        private boolean multimodal = false;
    }
}
