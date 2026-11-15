import type { LocationQuery, RouteLocationRaw } from 'vue-router'

/** sessionStorage：跨页记住时间窗与选中服务（深链优先读 URL） */
const HOURS_KEY = 'si.ui.hours'
const WINDOW_KEY = 'si.ui.window'
const SERVICE_KEY = 'si.ui.service'

const OBSERVATION_PATHS = new Set(['/', '/topology', '/traces', '/jdbc', '/redis', '/error-analysis', '/services'])

/** 滚动近 N 小时，或绝对起止（epoch ms，半开 [from, to)） */
export type ObservationWindow =
  | { kind: 'rolling'; hours: number }
  | { kind: 'absolute'; fromMs: number; toMs: number }

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
 * 读取记住的观测窗（绝对或滚动）。
 */
export function readStoredWindow(): ObservationWindow | null {
  try {
    const raw = sessionStorage.getItem(WINDOW_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as ObservationWindow
      if (parsed?.kind === 'absolute') {
        const fromMs = Number(parsed.fromMs)
        const toMs = Number(parsed.toMs)
        if (Number.isFinite(fromMs) && Number.isFinite(toMs) && toMs > fromMs) {
          return { kind: 'absolute', fromMs, toMs }
        }
      }
      if (parsed?.kind === 'rolling') {
        const hours = Number(parsed.hours)
        if (Number.isFinite(hours) && hours >= 0) return { kind: 'rolling', hours }
      }
    }
  } catch {
    /* fall through */
  }
  const hours = readStoredHours()
  return hours != null ? { kind: 'rolling', hours } : null
}

/**
 * 持久化时间窗（小时；0 = 全部已存）。
 */
export function persistHours(hours: number): void {
  if (!Number.isFinite(hours) || hours < 0) return
  try {
    sessionStorage.setItem(HOURS_KEY, String(hours))
    sessionStorage.setItem(WINDOW_KEY, JSON.stringify({ kind: 'rolling', hours }))
  } catch {
    /* ignore quota / private mode */
  }
}

/**
 * 持久化观测窗。
 */
export function persistWindow(window: ObservationWindow): void {
  try {
    if (window.kind === 'rolling') {
      persistHours(window.hours)
      return
    }
    if (!(window.toMs > window.fromMs)) return
    sessionStorage.setItem(WINDOW_KEY, JSON.stringify(window))
    // 绝对窗时仍记下等效「距今」小时，便于旧深链回退
    const approx = Math.max(1, Math.ceil((Date.now() - window.fromMs) / 3_600_000))
    sessionStorage.setItem(HOURS_KEY, String(Math.min(approx, 168)))
  } catch {
    /* ignore */
  }
}

/**
 * 解析时间窗：URL query > sessionStorage > 页面默认值。
 */
export function resolveHours(query: LocationQuery, fallback: number): number {
  const w = resolveWindow(query, fallback)
  return w.kind === 'rolling' ? w.hours : Math.max(1, Math.ceil((Date.now() - w.fromMs) / 3_600_000))
}

/**
 * 解析观测窗：URL 的 from+to 优先，否则 hours / 存储 / 默认滚动。
 */
export function resolveWindow(query: LocationQuery, fallbackHours: number): ObservationWindow {
  const fromRaw = query.from
  const toRaw = query.to
  if (typeof fromRaw === 'string' && typeof toRaw === 'string' && fromRaw && toRaw) {
    const fromMs = Number(fromRaw)
    const toMs = Number(toRaw)
    if (Number.isFinite(fromMs) && Number.isFinite(toMs) && toMs > fromMs) {
      return { kind: 'absolute', fromMs, toMs }
    }
  }
  const raw = query.hours
  if (typeof raw === 'string' && raw !== '') {
    const n = Number(raw)
    if (Number.isFinite(n) && n >= 0) return { kind: 'rolling', hours: n }
  }
  const stored = readStoredWindow()
  if (stored) return stored
  return { kind: 'rolling', hours: fallbackHours }
}

/**
 * 观测窗写入 URL query 补丁（互斥 hours 与 from/to）。
 */
export function windowQueryPatch(window: ObservationWindow): Record<string, string | null> {
  if (window.kind === 'absolute') {
    return {
      hours: null,
      from: String(window.fromMs),
      to: String(window.toMs)
    }
  }
  return {
    hours: String(window.hours),
    from: null,
    to: null
  }
}

/**
 * 取滚动小时（绝对窗时用近似距今小时，兼容仍只收 hours 的调用）。
 */
export function windowHours(window: ObservationWindow): number {
  if (window.kind === 'rolling') return window.hours
  return Math.max(1, Math.ceil((Date.now() - window.fromMs) / 3_600_000))
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
 * <p>数据库页不带 service：JDBC Span 记在执行 SQL 的服务上，与入口 consumer 常不一致。</p>
 */
export function buildNavLocation(path: string): RouteLocationRaw {
  if (!OBSERVATION_PATHS.has(path)) return path
  const patch: Record<string, string | null> = {}
  const window = readStoredWindow()
  if (window) Object.assign(patch, windowQueryPatch(window))
  if (path === '/traces' || path === '/topology') {
    const service = readStoredService()
    if (service) patch.service = service
  }
  const query = mergeQuery({}, patch)
  return Object.keys(query).length ? { path, query } : path
}
