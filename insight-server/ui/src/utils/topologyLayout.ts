import type { ServiceDependency } from './topologyGraph'

/** 拓扑布局模式 */
export type TopologyLayoutMode = 'circular' | 'force' | 'hierarchical'

export const TOPOLOGY_LAYOUT_OPTIONS: ReadonlyArray<{ value: TopologyLayoutMode; label: string }> = [
  { value: 'circular', label: '环形' },
  { value: 'force', label: '力导向' },
  { value: 'hierarchical', label: '层次（网关在上）' }
]

const LAYOUT_KEY = 'si.ui.topology.layout'
const MIN_CALL_KEY = 'si.ui.topology.minCall'

/**
 * 是否像网关 / 入口服务（用于层次布局置顶）。
 */
export function isGatewayLikeName(serviceName: string): boolean {
  const name = String(serviceName || '').toLowerCase()
  return /gateway|gw|^api$|ingress|edge|proxy/.test(name)
}

/**
 * 按最小调用次数过滤边；孤立节点随之消失。
 */
export function filterDependenciesByMinCall(
  dependencies: ServiceDependency[],
  minCallCount: number
): ServiceDependency[] {
  const min = Math.max(0, Math.floor(Number(minCallCount) || 0))
  if (min <= 0) return dependencies.slice()
  return dependencies.filter((d) => (Number(d.callCount) || 0) >= min)
}

/**
 * 层次布局坐标（虚拟画布约 1000×720，网关层在上）。
 *
 * @returns name → { x, y }
 */
export function assignHierarchicalPositions(
  dependencies: ServiceDependency[]
): Map<string, { x: number; y: number }> {
  const nodes = new Set<string>()
  const outs = new Map<string, Set<string>>()
  const indeg = new Map<string, number>()

  for (const dep of dependencies) {
    const s = String(dep.sourceService || '').trim()
    const t = String(dep.targetService || '').trim()
    if (!s || !t || s === t) continue
    nodes.add(s)
    nodes.add(t)
    if (!outs.has(s)) outs.set(s, new Set())
    outs.get(s)!.add(t)
    indeg.set(t, (indeg.get(t) || 0) + 1)
    if (!indeg.has(s)) indeg.set(s, 0)
  }

  const names = [...nodes]
  if (names.length === 0) return new Map()

  const layer = new Map<string, number>()
  const roots = names.filter((n) => isGatewayLikeName(n) || (indeg.get(n) || 0) === 0)
  const seed = roots.length ? roots : [names.slice().sort()[0]]
  for (const r of seed) layer.set(r, 0)

  // 按出边松弛层号（最长路径）；限制迭代避免环导致发散
  for (let i = 0; i < names.length; i++) {
    let changed = false
    for (const [src, targets] of outs) {
      const base = layer.get(src)
      if (base == null) continue
      for (const tgt of targets) {
        const next = base + 1
        const prev = layer.get(tgt)
        if (prev == null || next > prev) {
          layer.set(tgt, next)
          changed = true
        }
      }
    }
    if (!changed) break
  }

  for (const name of names) {
    if (!layer.has(name)) layer.set(name, 1)
    if (isGatewayLikeName(name)) layer.set(name, 0)
  }

  const byLayer = new Map<number, string[]>()
  for (const name of names) {
    const L = layer.get(name) || 0
    if (!byLayer.has(L)) byLayer.set(L, [])
    byLayer.get(L)!.push(name)
  }
  for (const list of byLayer.values()) list.sort((a, b) => a.localeCompare(b))

  const layers = [...byLayer.keys()].sort((a, b) => a - b)
  const width = 1000
  const height = 720
  const top = 60
  const bottom = 60
  const left = 80
  const right = 80
  const usableH = height - top - bottom
  const usableW = width - left - right
  const layerCount = Math.max(1, layers.length)
  const positions = new Map<string, { x: number; y: number }>()

  layers.forEach((L, li) => {
    const row = byLayer.get(L) || []
    const y = layerCount === 1 ? height / 2 : top + (usableH * li) / Math.max(1, layerCount - 1)
    row.forEach((name, i) => {
      const x = row.length === 1 ? width / 2 : left + (usableW * i) / Math.max(1, row.length - 1)
      positions.set(name, { x, y })
    })
  })

  return positions
}

/**
 * 读取记住的布局模式。
 */
export function readStoredLayoutMode(): TopologyLayoutMode {
  try {
    const raw = sessionStorage.getItem(LAYOUT_KEY)
    if (raw === 'force' || raw === 'hierarchical' || raw === 'circular') return raw
  } catch {
    /* ignore */
  }
  return 'circular'
}

/**
 * 持久化布局模式。
 */
export function persistLayoutMode(mode: TopologyLayoutMode): void {
  try {
    sessionStorage.setItem(LAYOUT_KEY, mode)
  } catch {
    /* ignore */
  }
}

/**
 * 读取最小调用次数过滤。
 */
export function readStoredMinCallCount(): number {
  try {
    const n = Number(sessionStorage.getItem(MIN_CALL_KEY))
    if (Number.isFinite(n) && n >= 0) return Math.floor(n)
  } catch {
    /* ignore */
  }
  return 0
}

/**
 * 持久化最小调用次数。
 */
export function persistMinCallCount(min: number): void {
  try {
    sessionStorage.setItem(MIN_CALL_KEY, String(Math.max(0, Math.floor(min))))
  } catch {
    /* ignore */
  }
}
