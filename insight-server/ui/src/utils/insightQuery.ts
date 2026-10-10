import type { LocationQuery, RouteLocationRaw } from 'vue-router'

/** sessionStorage：跨页记住时间窗与选中服务（深链优先读 URL） */
const HOURS_KEY = 'si.ui.hours'
const SERVICE_KEY = 'si.ui.service'

const OBSERVATION_PATHS = new Set(['/', '/topology', '/traces', '/error-analysis', '/services'])

/**
 * 从 sessionStorage 读取上次时间窗；无效则 null。
 */
export function readStoredHours(): number | null {
  try {
    const raw = sessionStorage.getItem(HOURS_KEY)
    if (raw == null || raw === '') return null
    const n = Number(raw)
    return Number.isFinite(n) && n >= 0 ? n : null
  } catch {
    return null
  }
}

/**
 * 持久化时间窗（小时；0 = 全部已存）。
 */
export function persistHours(hours: number): void {
  if (!Number.isFinite(hours) || hours < 0) return
  try {
    sessionStorage.setItem(HOURS_KEY, String(hours))
  } catch {
    /* ignore quota / private mode */
  }
}

/**
 * 解析时间窗：URL query > sessionStorage > 页面默认值。
 */
export function resolveHours(query: LocationQuery, fallback: number): number {
  const raw = query.hours
  if (typeof raw === 'string' && raw !== '') {
    const n = Number(raw)
    if (Number.isFinite(n) && n >= 0) return n
  }
  const stored = readStoredHours()
  if (stored != null) return stored
  return fallback
}

/**
 * 读取记住的服务名。
 */
export function readStoredService(): string {
  try {
    return sessionStorage.getItem(SERVICE_KEY)?.trim() || ''
  } catch {
    return ''
  }
}

/**
 * 持久化选中服务；空字符串则清除。
 */
export function persistService(service: string): void {
  try {
    const s = service?.trim() || ''
    if (!s) sessionStorage.removeItem(SERVICE_KEY)
    else sessionStorage.setItem(SERVICE_KEY, s)
  } catch {
    /* ignore */
  }
}

/**
 * 解析服务名：URL `service` > sessionStorage。
 */
export function resolveService(query: LocationQuery): string {
  if (typeof query.service === 'string' && query.service.trim()) {
    return query.service.trim()
  }
  return readStoredService()
}

/**
 * 在现有 query 上合并 hours / 额外字段（空值删除键）。
 */
export function mergeQuery(
  current: LocationQuery,
  patch: Record<string, string | null | undefined>
): Record<string, string> {
  const out: Record<string, string> = {}
  for (const [k, v] of Object.entries(current)) {
    if (typeof v === 'string' && v !== '') out[k] = v
  }
  for (const [k, v] of Object.entries(patch)) {
    if (v == null || v === '') delete out[k]
    else out[k] = v
  }
  return out
}

/**
 * 侧栏导航：观测页带上记住的 hours（及 traces/topology 的 service），便于跨页复现。
 */
export function buildNavLocation(path: string): RouteLocationRaw {
  if (!OBSERVATION_PATHS.has(path)) return path
  const patch: Record<string, string | null> = {}
  const hours = readStoredHours()
  if (hours != null) patch.hours = String(hours)
  if (path === '/traces' || path === '/topology') {
    const service = readStoredService()
    if (service) patch.service = service
  }
  const query = mergeQuery({}, patch)
  return Object.keys(query).length ? { path, query } : path
}
