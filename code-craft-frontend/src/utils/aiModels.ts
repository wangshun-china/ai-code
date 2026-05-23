import { listAiModels } from '@/api/aiModelController'

export type AiModelOption = {
  label: string
  value: string
  endpoint?: string
  baseUrl?: string
  custom?: boolean
}

export const loadAiModelOptions = async (userId?: number): Promise<AiModelOption[]> => {
  try {
    const res = await listAiModels(userId ? { userId } : {})
    if (res.data.code !== 0 || !res.data.data?.length) {
      return []
    }
    return res.data.data
      .filter((item) => item.value)
      .map((item) => ({
        label: item.text || item.value!,
        value: item.value!,
        endpoint: item.endpoint,
        baseUrl: item.baseUrl,
        custom: item.custom,
      }))
  } catch (error) {
    console.warn('加载模型列表失败', error)
    return []
  }
}

export const formatAiModel = (modelKey?: string, options?: AiModelOption[]) => {
  if (!modelKey) return '未选择模型'
  const found = options?.find((item) => item.value === modelKey)
  return found?.label || modelKey
}
