package com.ws.codecraft.ai;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.ws.codecraft.mapper.AiModelCredentialMapper;
import com.ws.codecraft.exception.BusinessException;
import com.ws.codecraft.exception.ErrorCode;
import com.ws.codecraft.model.ai.AiModelRegistry;
import com.ws.codecraft.model.entity.AiModelCredential;
import com.ws.codecraft.model.request.AiModelCredentialRequest;
import com.ws.codecraft.model.vo.AiModelCredentialVO;
import com.ws.codecraft.model.vo.AiModelVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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

    public static final String DEFAULT_BASE_URL = AiChatClientFactory.DASHSCOPE_COMPATIBLE_BASE_URL;
    private static final String DEFAULT_KEY_MASK = "*****";
    private static final String NORMAL_KEY_MASK = "***";

    @Resource
    private AiModelCredentialMapper aiModelCredentialMapper;

    public AiModelCredential saveCredential(long userId, AiModelCredentialRequest request) {
        return saveCredential(userId, request, false);
    }

    public AiModelCredential saveCredential(long userId, AiModelCredentialRequest request, boolean allowSystemEdit) {
        validateCredentialModels(request);
        String normalizedModels = joinModelNames(request.getModelNames());
        if (request.getId() != null && request.getId() > 0) {
            AiModelCredential existing = getEditableCredential(userId, request.getId(), allowSystemEdit);
            if (existing == null) {
                throw new IllegalArgumentException("模型配置不存在");
            }
            if (isDefaultCredential(existing) && !allowSystemEdit) {
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
        boolean isOnlyCredential = listUserCredentials(userId).isEmpty();
        credential.setIsDefault(isOnlyCredential ? 1 : 0);
        credential.setSystemDefault(0);
        credential.setCreateTime(LocalDateTime.now());
        credential.setUpdateTime(LocalDateTime.now());
        aiModelCredentialMapper.insert(credential);
        if (isOnlyCredential) {
            clearOtherDefaults(userId, credential.getId());
        }
        registerCredentialModels(credential);
        log.info("用户 {} 保存了 AI 模型凭据配置: {}", userId, credential.getName());
        return credential;
    }

    public void selectCredential(long userId, long credentialId) {
        if (isSystemCredentialId(credentialId)) {
            for (AiModelCredential credential : listUserCredentials(userId)) {
                AiModelRegistry.removeDynamicModelsByPrefix(buildCredentialPrefix(credential.getId()));
            }
            clearOtherDefaults(userId, null);
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
        if (isDefaultCredential(credential)) {
            return;
        }
        boolean wasDefault = Objects.equals(credential.getIsDefault(), 1);
        aiModelCredentialMapper.deleteById(credentialId);
        AiModelRegistry.removeDynamicModelsByPrefix(buildCredentialPrefix(credentialId));
        if (wasDefault) {
            clearOtherDefaults(userId, null);
        }
    }

    /**
     * 只返回当前用户自己的模型凭据配置。
     * 管理员只能看到系统默认配置和自己创建的配置，不能借管理员身份查看普通用户的自定义 Key。
     */
    public List<AiModelCredentialVO> listCredentialVO(long userId, boolean admin) {
        AiModelCredential active = getActiveCredential(userId);
        List<AiModelCredentialVO> result = new ArrayList<>();
        AiModelCredential defaultCredential = getDefaultCredential();
        if (defaultCredential != null) {
            result.add(toVO(defaultCredential, admin, active));
        }
        for (AiModelCredential credential : listCredentials(userId)) {
            result.add(toVO(credential, admin, active));
        }
        return result;
    }

    public List<AiModelVO> listAvailableModels(long userId) {
        AiModelCredential active = getActiveCredential(userId);
        if (active == null) {
            return List.of();
        }
        boolean defaultCredential = isDefaultCredential(active);
        if (!defaultCredential || hasUsableKey(active.getApiKey())) {
            registerCredentialModels(active);
        }
        return parseModelNames(active.getModelNames()).stream()
                .map(modelName -> new AiModelVO(
                        buildModelKey(active, modelName),
                        modelName,
                        AiModelRegistry.ENDPOINT_OPENAI_COMPATIBLE,
                        active.getBaseUrl(),
                        true,
                        !defaultCredential))
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
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统默认模型配置不存在或未配置可用模型，请管理员先配置 AI 模型");
    }

    public String resolveRequestedModelKey(long userId, String requestedModelKey) {
        if (StrUtil.isBlank(requestedModelKey)) {
            return resolveDefaultModelKey(userId);
        }
        String normalized = AiModelRegistry.normalize(requestedModelKey);
        return isAvailableModelKey(userId, normalized) ? normalized : resolveDefaultModelKey(userId);
    }

    public void loadUserModelsToRegistry(long userId) {
        AiModelCredential defaultCredential = getDefaultCredential();
        if (defaultCredential != null && hasUsableKey(defaultCredential.getApiKey())) {
            registerCredentialModels(defaultCredential);
        }
        for (AiModelCredential credential : listCredentials(userId)) {
            if (hasUsableKey(credential.getApiKey())) {
                registerCredentialModels(credential);
            }
        }
    }

    public static String buildCredentialModelKey(long credentialId, String modelName) {
        return "c:" + credentialId + ":" + Integer.toUnsignedString(modelName.hashCode(), 36);
    }

    private static String buildCredentialPrefix(long credentialId) {
        return "c:" + credentialId + ":";
    }

    private static String buildModelKey(AiModelCredential credential, String modelName) {
        return isDefaultCredential(credential) ? modelName : buildCredentialModelKey(credential.getId(), modelName);
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
        AiModelCredential userDefault = aiModelCredentialMapper.selectOneByQuery(QueryWrapper.create()
                .eq("userId", userId)
                .eq("systemDefault", 0)
                .eq("isDefault", 1)
                .orderBy("updateTime", false)
                .limit(1));
        return userDefault != null ? userDefault : getDefaultCredential();
    }

    private AiModelCredential getCredential(long userId, long credentialId) {
        return aiModelCredentialMapper.selectOneByQuery(QueryWrapper.create()
                .eq("id", credentialId)
                .eq("userId", userId)
                .eq("systemDefault", 0)
                .limit(1));
    }

    private List<AiModelCredential> listCredentials(long userId) {
        return listUserCredentials(userId);
    }

    private List<AiModelCredential> listUserCredentials(long userId) {
        return aiModelCredentialMapper.selectListByQuery(QueryWrapper.create()
                .eq("userId", userId)
                .eq("systemDefault", 0)
                .orderBy("isDefault", false)
                .orderBy("updateTime", false));
    }

    private void clearOtherDefaults(long userId, Long keepId) {
        for (AiModelCredential credential : listUserCredentials(userId)) {
            if (keepId != null && keepId.equals(credential.getId())) {
                continue;
            }
            if (Objects.equals(credential.getIsDefault(), 1)) {
                credential.setIsDefault(0);
                aiModelCredentialMapper.update(credential);
            }
        }
    }

    private AiModelCredentialVO toVO(AiModelCredential credential, boolean admin, AiModelCredential active) {
        boolean defaultCredential = isDefaultCredential(credential);
        return new AiModelCredentialVO(
                credential.getId(),
                credential.getName(),
                visibleApiKey(credential, admin),
                credential.getBaseUrl(),
                parseModelNames(credential.getModelNames()),
                active != null && Objects.equals(active.getId(), credential.getId()),
                defaultCredential);
    }

    private String visibleApiKey(AiModelCredential credential, boolean admin) {
        boolean defaultCredential = isDefaultCredential(credential);
        if (defaultCredential && admin) {
            return credential.getApiKey();
        }
        return DEFAULT_KEY_MASK;
    }

    private AiModelCredential getDefaultCredential() {
        return aiModelCredentialMapper.selectOneByQuery(QueryWrapper.create()
                .eq("systemDefault", 1)
                .orderBy("updateTime", false)
                .limit(1));
    }

    private AiModelCredential getEditableCredential(long userId, long credentialId, boolean allowSystemEdit) {
        AiModelCredential credential = getCredential(userId, credentialId);
        if (credential != null || !allowSystemEdit) {
            return credential;
        }
        return aiModelCredentialMapper.selectOneByQuery(QueryWrapper.create()
                .eq("id", credentialId)
                .eq("systemDefault", 1)
                .limit(1));
    }

    private boolean isSystemCredentialId(long credentialId) {
        return aiModelCredentialMapper.selectCountByQuery(QueryWrapper.create()
                .eq("id", credentialId)
                .eq("systemDefault", 1)) > 0;
    }

    private static boolean isDefaultCredential(AiModelCredential credential) {
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

}
