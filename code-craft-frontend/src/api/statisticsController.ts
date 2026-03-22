import request from '@/request'

/**
 * 获取 AI 使用统计数据
 * @param params 查询参数（可选日期范围）
 */
export function getAiMetrics(params?: { startDate?: string; endDate?: string }) {
  return request.get('/statistics/ai-metrics', { params })
}

/**
 * 获取用户 Token 消耗排行
 * @param params 查询参数
 */
export function getUserTokenRanking(params?: { limit?: number }) {
  return request.get('/statistics/user-ranking', { params })
}

/**
 * 获取模型使用统计
 * @param params 查询参数
 */
export function getModelStats(params?: { startDate?: string; endDate?: string }) {
  return request.get('/statistics/model-stats', { params })
}

/**
 * 获取每日统计
 * @param params 查询参数（日期范围）
 */
export function getDailyStats(params: { startDate: string; endDate: string }) {
  return request.get('/statistics/daily-stats', { params })
}
