/**
 * 服务健康分解释（与 Server HealthScoreService 对齐）。
 */

export interface HealthScoreParts {
  errorPart: number
  latencyPart: number
  availabilityPart: number
  score: number
  level: string
}

export interface HealthScoreExplainInput {
  errorRate: number
  p95: number
  hasData: boolean
}

/**
 * 错误率得分：0%→100，1%→80，5%→40，10%+→0。
 */
export function errorScore(errorRatePct: number): number {
  const r = Math.max(0, errorRatePct)
  if (r <= 0) return 100
  if (r <= 1) return lerp(r, 0, 1, 100, 80)
  if (r <= 5) return lerp(r, 1, 5, 80, 40)
  if (r <= 10) return lerp(r, 5, 10, 40, 0)
  return 0
}

/**
 * p95 延迟得分：&lt;100→100，&lt;500→70，&lt;1000→40，否则 0。
 */
export function latencyScore(p95Ms: number): number {
  if (p95Ms < 100) return 100
  if (p95Ms < 500) return 70
  if (p95Ms < 1000) return 40
  return 0
}

/**
 * 总分分级（有数据时）。
 */
export function levelOf(score: number): string {
  if (score >= 80) return 'healthy'
  if (score >= 60) return 'warning'
  if (score >= 40) return 'degraded'
  return 'critical'
}

/**
 * 合成健康分与三项子分。
 */
export function explainHealthScore(input: HealthScoreExplainInput): HealthScoreParts {
  const hasData = !!input.hasData
  const errorPart = hasData ? errorScore(Number(input.errorRate) || 0) : 0
  const latencyPart = hasData ? latencyScore(Math.max(0, Math.round(Number(input.p95) || 0))) : 0
  const availabilityPart = hasData ? 100 : 20
  let score = Math.round(errorPart * 0.5 + latencyPart * 0.3 + availabilityPart * 0.2)
  if (score < 0) score = 0
  if (score > 100) score = 100
  return {
    errorPart,
    latencyPart,
    availabilityPart,
    score,
    level: hasData ? levelOf(score) : 'unknown'
  }
}

/**
 * 悬停 / 侧栏用的多行说明。
 */
export function healthScoreTooltipLines(input: HealthScoreExplainInput): string[] {
  const parts = explainHealthScore(input)
  const rate = Number(input.errorRate) || 0
  const p95 = Math.max(0, Math.round(Number(input.p95) || 0))
  if (!input.hasData) {
    return [
      '窗口内暂无 Span，数据可用项按 20 分计',
      `错误率 50%×${fmt(parts.errorPart)} + p95 30%×${fmt(parts.latencyPart)} + 数据 20%×${fmt(parts.availabilityPart)} ≈ ${parts.score}`
    ]
  }
  return [
    `错误率 ${fmt1(rate)}% → 子分 ${fmt(parts.errorPart)}（权重 50%）`,
    `p95 ${p95}ms → 子分 ${fmt(parts.latencyPart)}（权重 30%）`,
    `有数据 → 子分 ${fmt(parts.availabilityPart)}（权重 20%）`,
    `合成 ${fmt(parts.errorPart)}×0.5 + ${fmt(parts.latencyPart)}×0.3 + ${fmt(parts.availabilityPart)}×0.2 ≈ ${parts.score}`
  ]
}

function lerp(x: number, x0: number, x1: number, y0: number, y1: number): number {
  if (x1 === x0) return y0
  return y0 + ((x - x0) / (x1 - x0)) * (y1 - y0)
}

function fmt(n: number): string {
  return Number.isInteger(n) ? String(n) : n.toFixed(1)
}

function fmt1(n: number): string {
  return (Math.round(n * 10) / 10).toFixed(1)
}
