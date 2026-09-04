/** 将扁平 Span 列表整理为瀑布图行（按开始时间排序，并计算相对偏移与树深度） */

export interface TraceSpanLike {
  traceId?: string
  spanId?: string
  parentSpanId?: string | null
  serviceName?: string
  operationName?: string
  spanKind?: string
  startTime?: number
  endTime?: number
  durationMs?: number
  statusCode?: string
  success?: boolean
}

export interface WaterfallRow {
  span: TraceSpanLike
  depth: number
  offsetMs: number
  durationMs: number
  offsetPct: number
  widthPct: number
  color: string
  isError: boolean
}

export interface TraceTimelineModel {
  rows: WaterfallRow[]
  minStart: number
  maxEnd: number
  totalDurationMs: number
  serviceCount: number
  errorCount: number
  tickMarks: number[]
}

const SERVICE_COLORS = [
  '#0f766e', '#15803d', '#0d9488', '#1d4ed8',
  '#b45309', '#b91c1c', '#047857', '#c2410c'
]

function n(v: unknown, fallback = 0): number {
  const x = Number(v)
  return Number.isFinite(x) ? x : fallback
}

function isError(span: TraceSpanLike): boolean {
  if (span.success === false) return true
  const code = (span.statusCode || '').toUpperCase()
  return code !== '' && code !== 'OK' && code !== '0' && code !== '200'
}

/** 根据 parentSpanId 计算深度；找不到父节点时深度为 0 */
function computeDepths(spans: TraceSpanLike[]): Map<string, number> {
  const byId = new Map<string, TraceSpanLike>()
  spans.forEach((s) => {
    if (s.spanId) byId.set(s.spanId, s)
  })
  const depthCache = new Map<string, number>()

  const depthOf = (spanId: string, visiting: Set<string>): number => {
    if (depthCache.has(spanId)) return depthCache.get(spanId)!
    if (visiting.has(spanId)) return 0
    visiting.add(spanId)
    const span = byId.get(spanId)
    if (!span || !span.parentSpanId || !byId.has(span.parentSpanId)) {
      depthCache.set(spanId, 0)
      return 0
    }
    const d = depthOf(span.parentSpanId, visiting) + 1
    depthCache.set(spanId, d)
    return d
  }

  spans.forEach((s) => {
    if (s.spanId) depthOf(s.spanId, new Set())
  })
  return depthCache
}

export function buildTraceTimeline(rawSpans: TraceSpanLike[]): TraceTimelineModel {
  const spans = (rawSpans || []).map((s) => ({ ...s }))
  if (spans.length === 0) {
    return {
      rows: [],
      minStart: 0,
      maxEnd: 0,
      totalDurationMs: 0,
      serviceCount: 0,
      errorCount: 0,
      tickMarks: [0]
    }
  }

  spans.forEach((s) => {
    const start = n(s.startTime)
    let duration = n(s.durationMs)
    if (duration <= 0 && s.endTime != null) {
      duration = Math.max(0, n(s.endTime) - start)
    }
    if (duration <= 0) duration = 1
    s.startTime = start
    s.durationMs = duration
    if (s.endTime == null) s.endTime = start + duration
  })

  spans.sort((a, b) => n(a.startTime) - n(b.startTime) || String(a.spanId).localeCompare(String(b.spanId)))

  const minStart = Math.min(...spans.map((s) => n(s.startTime)))
  const maxEnd = Math.max(...spans.map((s) => n(s.endTime, n(s.startTime) + n(s.durationMs))))
  const totalDurationMs = Math.max(1, maxEnd - minStart)
  const depths = computeDepths(spans)

  const serviceIndex = new Map<string, number>()
  spans.forEach((s) => {
    const name = s.serviceName || 'unknown'
    if (!serviceIndex.has(name)) serviceIndex.set(name, serviceIndex.size)
  })

  const rows: WaterfallRow[] = spans.map((span) => {
    const offsetMs = Math.max(0, n(span.startTime) - minStart)
    const durationMs = Math.max(1, n(span.durationMs))
    const svc = span.serviceName || 'unknown'
    const color = SERVICE_COLORS[(serviceIndex.get(svc) || 0) % SERVICE_COLORS.length]
    return {
      span,
      depth: span.spanId ? (depths.get(span.spanId) || 0) : 0,
      offsetMs,
      durationMs,
      offsetPct: (offsetMs / totalDurationMs) * 100,
      widthPct: Math.max(0.4, (durationMs / totalDurationMs) * 100),
      color,
      isError: isError(span)
    }
  })

  const tickCount = 5
  const tickMarks: number[] = []
  for (let i = 0; i <= tickCount; i++) {
    tickMarks.push(Math.round((totalDurationMs * i) / tickCount))
  }

  return {
    rows,
    minStart,
    maxEnd,
    totalDurationMs,
    serviceCount: serviceIndex.size,
    errorCount: rows.filter((r) => r.isError).length,
    tickMarks
  }
}

export function formatDuration(ms: number): string {
  if (!Number.isFinite(ms)) return '-'
  if (ms < 1) return '<1ms'
  if (ms < 1000) return `${Math.round(ms)}ms`
  if (ms < 10_000) return `${(ms / 1000).toFixed(2)}s`
  return `${(ms / 1000).toFixed(1)}s`
}
