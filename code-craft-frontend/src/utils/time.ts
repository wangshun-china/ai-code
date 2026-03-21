import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import utc from 'dayjs/plugin/utc'
import timezone from 'dayjs/plugin/timezone'
import 'dayjs/locale/zh-cn'

// 扩展 dayjs 插件
dayjs.extend(relativeTime)
dayjs.extend(utc)
dayjs.extend(timezone)
dayjs.locale('zh-cn')

// 北京时区
const BEIJING_TIMEZONE = 'Asia/Shanghai'

/**
 * 格式化时间（默认使用北京时间）
 * @param time 时间字符串
 * @param format 格式化字符串，默认为 'YYYY-MM-DD HH:mm:ss'
 * @returns 格式化后的时间字符串，如果时间为空则返回空字符串
 */
export const formatTime = (time: string | undefined, format = 'YYYY-MM-DD HH:mm:ss'): string => {
  if (!time) return ''
  // 如果时间字符串包含时区信息（以 Z 或 +/- 结尾），则转换为北京时间
  // 否则假设已经是北京时间
  if (time.endsWith('Z') || /[+-]\d{2}:\d{2}$/.test(time)) {
    return dayjs(time).tz(BEIJING_TIMEZONE).format(format)
  }
  return dayjs(time).format(format)
}

/**
 * 格式化时间为相对时间
 * @param time 时间字符串
 * @returns 相对时间字符串，如 "2小时前"
 */
export const formatRelativeTime = (time: string | undefined): string => {
  if (!time) return ''
  // 统一转换为北京时间后计算相对时间
  if (time.endsWith('Z') || /[+-]\d{2}:\d{2}$/.test(time)) {
    return dayjs(time).tz(BEIJING_TIMEZONE).fromNow()
  }
  return dayjs(time).fromNow()
}

/**
 * 格式化时间为日期
 * @param time 时间字符串
 * @returns 日期字符串，如 "2024-01-01"
 */
export const formatDate = (time: string | undefined): string => {
  if (!time) return ''
  if (time.endsWith('Z') || /[+-]\d{2}:\d{2}$/.test(time)) {
    return dayjs(time).tz(BEIJING_TIMEZONE).format('YYYY-MM-DD')
  }
  return dayjs(time).format('YYYY-MM-DD')
}

/**
 * 获取当前北京时间
 * @returns 北京时间字符串
 */
export const getNowBeijingTime = (): string => {
  return dayjs().tz(BEIJING_TIMEZONE).format('YYYY-MM-DD HH:mm:ss')
}
