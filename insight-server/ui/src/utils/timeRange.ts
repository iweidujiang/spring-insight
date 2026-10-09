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

/**
 * 列表/面板加载失败时的主句（控制台可见，勿仅打 console）。
 *
 * @param subject 主语，如「仪表盘」「链路列表」
 */
export function loadFailedMessage(subject: string): string {
  const s = subject?.trim() || '数据'
  return `${s}加载失败，请稍后重试或检查 insight-server 是否可达`
}

/** Trace 详情无 Span 时的主句 */
export const TRACE_NOT_FOUND =
  '未找到该 Trace 的 Span（可能已按保留策略清理，或 Trace ID 不正确）'

/**
 * 深链目标不存在时的降级提示（仍展示页面其余内容）。
 *
 * @param kind span | service | edge
 * @param ref 可选标识
 */
export function deeplinkMissMessage(
  kind: 'span' | 'service' | 'edge',
  ref?: string
): string {
  const id = ref?.trim()
  if (kind === 'span') {
    return id
      ? `深链指定的 Span「${id}」不在本 Trace 中，已改为选中默认 Span`
      : '深链未指定有效 Span，已改为选中默认 Span'
  }
  if (kind === 'edge') {
    return id
      ? `深链指定的调用边「${id}」当前窗口不存在，已展示完整拓扑`
      : '深链指定的调用边当前窗口不存在，已展示完整拓扑'
  }
  return id
    ? `深链指定的服务「${id}」当前窗口不存在，已展示完整拓扑`
    : '深链指定的服务当前窗口不存在，已展示完整拓扑'
}

/**
 * 容量接近上限时的提示（设置页）。
 *
 * @param percent 0～100
 */
export function capacityWarnMessage(percent: number): string | null {
  if (percent >= 95) {
    return '存储占用已超过 95%，新 Span 可能按上限裁掉最旧数据；建议清理或提高 max-spans / 开启时间保留'
  }
  if (percent >= 80) {
    return '存储占用已超过 80%，接近上限；可提前清理或调整保留策略'
  }
  return null
}
