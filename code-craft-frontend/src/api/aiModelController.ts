// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 查询可用 AI 模型 GET /ai_model/list */
export async function listAiModels(
  params: API.listAiModelsParams,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseAiModelVOArray>('/ai_model/list', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 查询 AI 模型凭据配置 GET /ai_model/credentials */
export async function listAiModelCredentials(
  params: API.listAiModelCredentialsParams,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseAiModelCredentialVOArray>('/ai_model/credentials', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 保存 AI 模型凭据配置 POST /ai_model/credential */
export async function saveAiModelCredential(
  params: API.saveAiModelCredentialParams,
  body: API.AiModelCredentialRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean>('/ai_model/credential', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    params: {
      ...params,
    },
    data: body,
    ...(options || {}),
  })
}

/** 选择默认 AI 模型凭据 POST /ai_model/credential/select */
export async function selectAiModelCredential(
  params: API.selectAiModelCredentialParams,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean>('/ai_model/credential/select', {
    method: 'POST',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 删除 AI 模型凭据配置 DELETE /ai_model/credential */
export async function removeAiModelCredential(
  params: API.removeAiModelCredentialParams,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean>('/ai_model/credential', {
    method: 'DELETE',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 设置自定义 API Key POST /ai_model/apikey */
export async function setAiApiKey(
  params: API.setAiApiKeyParams,
  body: string,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean>('/ai_model/apikey', {
    method: 'POST',
    headers: {
      'Content-Type': 'text/plain',
    },
    params: {
      ...params,
    },
    data: body,
    ...(options || {}),
  })
}

/** 添加自定义模型 POST /ai_model/custom_model */
export async function addCustomAiModel(
  params: API.addCustomAiModelParams,
  body: API.CustomAiModelRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean>('/ai_model/custom_model', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    params: {
      ...params,
    },
    data: body,
    ...(options || {}),
  })
}

/** 删除自定义模型 DELETE /ai_model/custom_model */
export async function removeCustomAiModel(
  params: API.removeCustomAiModelParams,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean>('/ai_model/custom_model', {
    method: 'DELETE',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}
