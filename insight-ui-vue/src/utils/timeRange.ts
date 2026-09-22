/** 控制台时间范围选项与空状态文案（仪表盘 / 拓扑 / 链路共用） */

export interface TimeRangeOption {
  value: number
  label: string
}

/** 统一选项：近 N 小时 / 近 7 天 / 全部已存 */
export const TIME_RANGE_OPTIONS: readonly TimeRangeOption[] = [
  { value: 1, label: '近 1 小时' },
  { value: 6, label: '近 6 小时' },
  { value: 12, label: '近 12 小时' },
  { value: 24, label: '近 24 小时' },
  { value: 72, label: '近 72 小时' },
  { value: 168, label: '近 7 天' },
  { value: 0, label: '全部已存' }
]

/**
 * 将小时数格式化为与下拉框一致的短标签。
 *
 * @param hours 小时数；0 表示全部已存
 */
export function formatHoursLabel(hours: number): string {
  const hit = TIME_RANGE_OPTIONS.find((o) => o.value === hours)
  if (hit) return hit.label
  if (hours <= 0) return '全部已存'
  if (hours === 168) return '近 7 天'
  return `近 ${hours} 小时`
}

/**
 * 无请求时的空状态主句（仪表盘最近请求、拓扑单体列表）。
 *
 * @param hours 当前时间范围
 */
export function emptyRequestsMessage(hours: number): string {
  if (hours === 0) return '全部已存范围内还没有请求'
  return `${formatHoursLabel(hours)}内还没有请求`
}

/**
 * 有筛选但无匹配时的空状态主句（链路列表）。
 *
 * @param hours 当前时间范围
 */
export function emptyNoMatchMessage(hours: number): string {
  if (hours === 0) return '全部已存范围内没有匹配的请求'
  return `${formatHoursLabel(hours)}内没有匹配的请求`
}

/**
 * 已有服务但无延迟样本时的说明。
 *
 * @param hours 当前时间范围
 */
export function emptyLatencySampleMessage(hours: number): string {
  if (hours === 0) return '已接入监控，但全部已存范围内暂无延迟样本'
  return `已接入监控，但${formatHoursLabel(hours)}内暂无延迟样本`
}

/**
 * 「某窗口内暂无 X」短句（辅栏等窄空状态）。
 *
 * @param hours 当前时间范围
 * @param noun 名词，如「延迟样本」「依赖」
 */
export function emptyInRangeShort(hours: number, noun: string): string {
  if (hours === 0) return `全部已存范围内暂无${noun}`
  return `${formatHoursLabel(hours)}内暂无${noun}`
}

/** 空状态副句：提示放宽筛选或检查上报 */
export const EMPTY_HINT_RELAX =
  '可放宽筛选或扩大时间范围，并确认业务服务已上报到 insight-server'
