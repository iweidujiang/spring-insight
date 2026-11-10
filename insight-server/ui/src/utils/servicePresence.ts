/**
 * 服务存活（Online / Stale）展示文案与样式。
 */

export type ServicePresence = 'online' | 'stale' | 'unknown' | string

/**
 * 短标签：在线 / 沉默 / 未知。
 */
export function presenceLabel(presence: ServicePresence | null | undefined): string {
  switch (String(presence || '').toLowerCase()) {
    case 'online':
      return '在线'
    case 'stale':
      return '沉默'
    default:
      return '未知'
  }
}

/**
 * 徽章 tone（对齐 SiStatusBadge）。
 */
export function presenceTone(presence: ServicePresence | null | undefined): 'success' | 'warn' | 'neutral' {
  switch (String(presence || '').toLowerCase()) {
    case 'online':
      return 'success'
    case 'stale':
      return 'warn'
    default:
      return 'neutral'
  }
}

/**
 * 悬停说明：沉默时带「N 分钟无数据」。
 */
export function presenceTitle(
  presence: ServicePresence | null | undefined,
  quietMinutes: number | null | undefined,
  staleThresholdMinutes: number | null | undefined
): string {
  const p = String(presence || '').toLowerCase()
  const threshold = Number(staleThresholdMinutes) > 0 ? Number(staleThresholdMinutes) : 5
  if (p === 'online') {
    return `近 ${threshold} 分钟内有 Span 上报`
  }
  if (p === 'stale') {
    const quiet = quietMinutes == null ? null : Number(quietMinutes)
    if (quiet != null && Number.isFinite(quiet) && quiet >= 0) {
      return `已 ${Math.floor(quiet)} 分钟无数据（阈值 ${threshold} 分钟）`
    }
    return `超过 ${threshold} 分钟无 Span 上报`
  }
  return '尚无 Span 记录'
}
