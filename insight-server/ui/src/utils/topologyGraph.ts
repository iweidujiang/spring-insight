import type { EChartsOption } from 'echarts'
import { getChartThemeColors, type ChartThemeColors } from './chartTheme'
import { healthScoreTooltipLines } from './healthScore'
import {
  assignHierarchicalPositions,
  type TopologyLayoutMode
} from './topologyLayout'

export type { TopologyLayoutMode }

export interface ServiceDependency {
  sourceService: string
  targetService: string
  callCount: number
  avgDuration?: number
}

export interface TopologyHighlight {
  /** 高亮节点名 */
  node?: string | null
  /** 高亮边（主选中，样式最强） */
  edge?: { source: string; target: string } | null
  /** 选中边时的上游路径节点（含选中边两端） */
  pathNodes?: string[] | null
  /** 选中边时的上游路径边（含选中边） */
  pathEdges?: Array<{ source: string; target: string }> | null
}

/** 上游路径节点与边集合 */
export interface UpstreamPathHighlight {
  nodes: string[]
  edges: Array<{ source: string; target: string }>
}

/**
 * 从选中边的调用方沿反向调用走到入口，收集相关上游路径上的节点与边。
 * 薄实现：整棵可达祖先锥（遇环跳过已访问节点）。
 *
 * @param dependencies 当前图上的依赖边
 * @param edge 选中边（source=调用方，target=被调方）
 */
export function collectUpstreamPath(
  dependencies: ServiceDependency[],
  edge: { source: string; target: string }
): UpstreamPathHighlight {
  const source = String(edge?.source || '').trim()
  const target = String(edge?.target || '').trim()
  if (!source || !target) {
    return { nodes: [], edges: [] }
  }

  const parents = new Map<string, string[]>()
  for (const dep of dependencies) {
    const s = String(dep.sourceService || '').trim()
    const t = String(dep.targetService || '').trim()
    if (!s || !t || s === t) continue
    const list = parents.get(t)
    if (list) list.push(s)
    else parents.set(t, [s])
  }

  const nodeSet = new Set<string>([source, target])
  const edges: Array<{ source: string; target: string }> = []
  const edgeKeys = new Set<string>()

  const addEdge = (s: string, t: string) => {
    const key = `${s}\0${t}`
    if (edgeKeys.has(key)) return
    edgeKeys.add(key)
    edges.push({ source: s, target: t })
  }

  addEdge(source, target)

  const queue = [source]
  const visited = new Set<string>([source])
  while (queue.length) {
    const cur = queue.shift()!
    for (const caller of parents.get(cur) || []) {
      addEdge(caller, cur)
      nodeSet.add(caller)
      if (!visited.has(caller)) {
        visited.add(caller)
        queue.push(caller)
      }
    }
  }

  return { nodes: [...nodeSet], edges }
}

function edgeKey(source: string, target: string): string {
  return `${source}\0${target}`
}

function pathEdgeSet(highlight: TopologyHighlight): Set<string> {
  const set = new Set<string>()
  for (const e of highlight.pathEdges || []) {
    if (e?.source && e?.target) set.add(edgeKey(e.source, e.target))
  }
  return set
}

function pathNodeSet(highlight: TopologyHighlight): Set<string> {
  return new Set((highlight.pathNodes || []).filter(Boolean))
}

/** 服务健康分（与 /services/health 对齐） */
export interface ServiceHealthInfo {
  service: string
  score: number
  level: string
  errorRate?: number
  p95?: number
  hasData?: boolean
}

/** 根据服务名推断服务类型图标 */
function inferServiceIcon(serviceName: string): string {
  const name = serviceName.toLowerCase()
  if (/gateway|gw|api/.test(name)) return '🌐'
  if (/web|frontend|ui|bff/.test(name)) return '💻'
  if (/order|trade|pay|cart/.test(name)) return '🛒'
  if (/user|auth|login|account/.test(name)) return '👤'
  if (/product|item|goods/.test(name)) return '📦'
  if (/db|mysql|postgres|mongo/.test(name)) return '🗄️'
  if (/redis|cache/.test(name)) return '⚡'
  if (/mq|kafka|rabbit|queue/.test(name)) return '📨'
  if (/search|es|elastic/.test(name)) return '🔍'
  if (/log|logging/.test(name)) return '📝'
  if (/monitor|metric|prometheus/.test(name)) return '📊'
  if (/config|nacos|consul/.test(name)) return '⚙️'
  return '🔷'
}

/**
 * 按健康等级取节点色；无健康信息时回退错误数着色。
 *
 * @param level 健康等级
 * @param errorCount 错误 Span 数（回退用）
 * @param theme 主题色
 * @param paletteIndex 无健康且无错误回退时的调色板下标
 * @param healthEnabled 是否启用了健康分着色（API 可用）
 */
export function resolveNodeColor(
  level: string | undefined,
  errorCount: number,
  theme: ChartThemeColors,
  paletteIndex: number,
  healthEnabled: boolean
): string {
  if (healthEnabled) {
    switch (level) {
      case 'healthy':
        return theme.success
      case 'warning':
        return theme.warning
      case 'degraded':
        return theme.degraded
      case 'critical':
        return theme.danger
      case 'unknown':
        return theme.muted
      default:
        return theme.muted
    }
  }
  if (errorCount > 0) {
    return theme.danger
  }
  return theme.palette[paletteIndex % theme.palette.length]
}

/**
 * 按节点数量微调缩放，节点越多略缩小，减轻挤成一团。
 *
 * @param nodeCount 图中节点数
 * @param compact 仪表盘紧凑模式
 */
function zoomForNodes(nodeCount: number, compact: boolean): number {
  if (nodeCount <= 1) return compact ? 0.72 : 0.78
  if (nodeCount <= 4) return compact ? 0.88 : 0.95
  if (nodeCount <= 8) return compact ? 0.78 : 0.86
  if (nodeCount <= 14) return compact ? 0.68 : 0.76
  return compact ? 0.58 : 0.66
}

/** 构建带箭头的服务依赖图（circular / force / hierarchical） */
export function buildTopologyOption(
  dependencies: ServiceDependency[],
  opts: {
    compact?: boolean
    standaloneServices?: string[]
    spanByService?: Record<string, number>
    /** 健康分映射；非 null 表示 API 可用 */
    healthByService?: Record<string, ServiceHealthInfo> | null
    /** 无健康 API 时按错误数回退着色 */
    errorCountByService?: Record<string, number>
    highlight?: TopologyHighlight
    theme?: ChartThemeColors
    /** 是否启用深色科技风 */
    darkTechStyle?: boolean
    /** 布局：环形 / 力导向 / 层次（网关在上） */
    layoutMode?: TopologyLayoutMode
  } = {}
): EChartsOption {
  const compact = opts.compact === true
  const highlight = opts.highlight || {}
  const theme = opts.theme || getChartThemeColors(false)
  const healthBy = opts.healthByService
  const healthEnabled = healthBy != null
  const errorBy = opts.errorCountByService || {}
  const darkTech = opts.darkTechStyle !== false // 默认启用
  const layoutMode: TopologyLayoutMode = opts.layoutMode || 'circular'
  const callCounts = new Map<string, number>()
  const links: Array<Record<string, unknown>> = []

  const pathEdges = pathEdgeSet(highlight)
  const pathNodes = pathNodeSet(highlight)
  const hasPathHighlight = pathEdges.size > 0 || pathNodes.size > 0
  const hasSelectionHighlight = !!(highlight.edge || highlight.node || hasPathHighlight)

  dependencies.forEach((dep) => {
    callCounts.set(dep.sourceService, (callCounts.get(dep.sourceService) || 0) + dep.callCount)
    callCounts.set(dep.targetService, (callCounts.get(dep.targetService) || 0) + dep.callCount)
    const edgeHit =
      !!highlight.edge &&
      highlight.edge.source === dep.sourceService &&
      highlight.edge.target === dep.targetService
    const onUpstreamPath =
      !edgeHit && pathEdges.has(edgeKey(dep.sourceService, dep.targetService))

    // 连线颜色：按状态区分
    let edgeColor: any = darkTech ? 'rgba(56, 189, 248, 0.6)' : theme.muted
    let isEdgeError = false
    if (edgeHit) {
      edgeColor = theme.brand
    } else if (onUpstreamPath) {
      edgeColor = darkTech ? 'rgba(45, 212, 191, 0.85)' : theme.brand
    } else if (dep.avgDuration && dep.avgDuration > 1000) {
      edgeColor = darkTech ? 'rgba(239, 68, 68, 0.9)' : theme.danger // 慢调用 - 红色
      isEdgeError = true
    } else if (dep.avgDuration && dep.avgDuration > 500) {
      edgeColor = darkTech ? 'rgba(245, 158, 11, 0.7)' : theme.warning // 较慢 - 橙色
    }

    const baseWidth = Math.max(
      compact ? 1.5 : 2,
      Math.min(compact ? 4 : 5, 1 + Math.log2(dep.callCount + 1))
    )
    const edgeLineStyle: any = {
      width: baseWidth + (edgeHit ? 1.5 : onUpstreamPath ? 0.8 : 0) + (isEdgeError ? 1 : 0),
      curveness: 0.16,
      color: edgeColor,
      opacity: edgeHit
        ? 1
        : onUpstreamPath
          ? 0.88
          : hasSelectionHighlight
            ? 0.22
            : (isEdgeError ? 1 : 0.9)
    }

    if (darkTech && !edgeHit && !onUpstreamPath) {
      edgeLineStyle.type = 'dashed' as const
      edgeLineStyle.dashOffset = 0
      if (isEdgeError) {
        edgeLineStyle.dashArray = [4, 4]
      }
    }

    links.push({
      source: dep.sourceService,
      target: dep.targetService,
      value: dep.callCount,
      avgDuration: dep.avgDuration ?? 0,
      label: {
        show: !compact || dependencies.length <= 12,
        formatter: `${dep.callCount}`,
        fontSize: compact ? 10 : 11,
        color: edgeHit || onUpstreamPath
          ? theme.brand
          : (darkTech ? 'rgba(255,255,255,0.7)' : theme.inkSoft),
        fontWeight: edgeHit ? 700 : onUpstreamPath ? 600 : 500,
        backgroundColor: darkTech ? 'rgba(15, 23, 42, 0.8)' : theme.surface,
        padding: [2, 6],
        borderRadius: 4,
        borderColor: darkTech ? 'rgba(56, 189, 248, 0.3)' : theme.axisLine,
        borderWidth: 1
      },
      lineStyle: edgeLineStyle
    })
  })

  // 无跨服务边时，仍展示已上报的单服务节点，避免主视图大片空白
  const spanBy = opts.spanByService || {}
  ;(opts.standaloneServices || []).forEach((name) => {
    if (!name || callCounts.has(name)) return
    callCounts.set(name, spanBy[name] || 1)
  })

  const nodes = Array.from(callCounts.entries()).map(([name, value], index) => {
    const nodeHit = highlight.node === name
    const onSelectedEdge =
      !!highlight.edge &&
      (highlight.edge.source === name || highlight.edge.target === name)
    const onUpstreamNode = pathNodes.has(name)
    const dimmed = hasSelectionHighlight && !nodeHit && !onSelectedEdge && !onUpstreamNode
    const health = healthBy?.[name]
    const errorCount = Number(errorBy[name]) || 0
    const color = resolveNodeColor(
      health?.level,
      errorCount,
      theme,
      index,
      healthEnabled
    )
    const icon = inferServiceIcon(name)
    const pathAccent = onSelectedEdge || onUpstreamNode

    const baseSize = Math.max(
      compact ? 34 : 42,
      Math.min(compact ? 54 : 68, 16 + Math.sqrt(value) * (compact ? 4.5 : 6.5))
    ) + (nodeHit || onSelectedEdge ? 6 : onUpstreamNode ? 3 : 0)

    return {
      name,
      value,
      healthScore: health?.score,
      healthLevel: health?.level,
      healthErrorRate: health?.errorRate,
      healthP95: health?.p95,
      healthHasData: health?.hasData,
      errorCount,
      symbolSize: baseSize,
      symbol: 'roundRect',
      itemStyle: {
        color: {
          type: 'linear' as const,
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: darkTech ? lightenColor(color, 30) : color },
            { offset: 1, color: darkTech ? darkenColor(color, 20) : color }
          ]
        },
        borderColor: nodeHit || pathAccent
          ? theme.brand
          : (darkTech ? 'rgba(255,255,255,0.2)' : theme.surface),
        borderWidth: nodeHit || onSelectedEdge ? 3 : onUpstreamNode ? 2.5 : 2,
        shadowBlur: nodeHit || onSelectedEdge ? 20 : onUpstreamNode ? 14 : (darkTech ? 15 : 8),
        shadowColor: nodeHit || pathAccent
          ? 'rgba(45, 212, 191, 0.55)'
          : (darkTech ? color + '80' : 'rgba(21, 36, 31, 0.18)'),
        opacity: dimmed ? 0.28 : 1,
        ...(darkTech ? {
          shadowOffsetX: 0,
          shadowOffsetY: 4
        } : {})
      },
      label: {
        show: true,
        position: 'bottom' as const,
        distance: 8,
        formatter: '{b}',
        fontSize: compact ? 10 : nodeHit || onSelectedEdge ? 13 : 12,
        fontWeight: nodeHit || onSelectedEdge ? 700 : 600,
        color: dimmed ? theme.muted : (darkTech ? 'rgba(255,255,255,0.9)' : theme.ink),
        ...(darkTech ? {
          textShadowColor: 'rgba(0,0,0,0.8)',
          textShadowBlur: 4
        } : {})
      },
      ...(darkTech ? {
        labelLine: { show: false },
        emphasis: {
          label: {
            show: true,
            position: 'top' as const,
            distance: 6,
            formatter: icon,
            fontSize: 16
          }
        }
      } : {})
    }
  })

  const empty = nodes.length === 0
  const solo = !empty && links.length === 0
  const zoom = solo ? (compact ? 0.72 : 0.78) : zoomForNodes(nodes.length, compact)

  let layout: 'none' | 'circular' | 'force' = 'circular'
  let circular: { rotateLabel: boolean } | undefined = { rotateLabel: false }
  let force:
    | {
        repulsion: number
        gravity: number
        edgeLength: number | number[]
        layoutAnimation: boolean
        friction: number
      }
    | undefined
  let seriesNodes: Array<Record<string, unknown>> = nodes

  if (layoutMode === 'force') {
    layout = 'force'
    circular = undefined
    force = {
      repulsion: compact ? 180 : 280,
      gravity: 0.08,
      edgeLength: compact ? [60, 140] : [80, 180],
      layoutAnimation: true,
      friction: 0.6
    }
  } else if (layoutMode === 'hierarchical') {
    layout = 'none'
    circular = undefined
    const positions = assignHierarchicalPositions(dependencies)
    let orphanIdx = 0
    seriesNodes = nodes.map((n) => {
      const pos = positions.get(n.name)
      if (pos) {
        return { ...n, x: pos.x, y: pos.y, fixed: true }
      }
      const x = 80 + (orphanIdx % 8) * 120
      const y = 680 + Math.floor(orphanIdx / 8) * 70
      orphanIdx += 1
      return { ...n, x, y, fixed: true }
    })
  }

  return {
    backgroundColor: 'transparent',
    textStyle: { color: darkTech ? 'rgba(255,255,255,0.7)' : theme.muted },
    tooltip: {
      trigger: 'item',
      backgroundColor: darkTech ? 'rgba(15, 23, 42, 0.95)' : theme.tooltipBg,
      borderColor: darkTech ? 'rgba(56, 189, 248, 0.3)' : theme.tooltipBorder,
      textStyle: { color: darkTech ? '#fff' : theme.tooltipText },
      formatter: (params: any) => {
        if (params.dataType === 'edge' || params.data?.source != null) {
          const d = params.data
          const avg = d.avgDuration != null ? `<br/>平均耗时: ${d.avgDuration} ms` : ''
          return `<div style="font-weight:700">${d.source} → ${d.target}</div>调用: ${d.value} 次${avg}<br/><span style="opacity:.75">点击选中 / 下钻链路</span>`
        }
        const spanHint = solo ? '本机 Span' : '关联调用'
        const d = params.data || {}
        let healthLine = ''
        if (d.healthScore != null && d.healthLevel) {
          healthLine = `<br/>健康分: <b>${d.healthScore}</b>（${levelLabel(d.healthLevel)}）`
          if (d.healthErrorRate != null || d.healthP95 != null || d.healthHasData != null) {
            const lines = healthScoreTooltipLines({
              errorRate: Number(d.healthErrorRate) || 0,
              p95: Number(d.healthP95) || 0,
              hasData: d.healthHasData !== false
            })
            healthLine += lines.map((line) => `<br/><span style="opacity:.85">${line}</span>`).join('')
          }
        } else if (d.errorCount != null && d.errorCount > 0) {
          healthLine = `<br/>错误 Span: ${d.errorCount}`
        }
        return `<div style="font-weight:700">${d.name || params.name}</div>${spanHint}: ${d.value}${healthLine}<br/><span style="opacity:.75">点击选中 / 下钻链路</span>`
      }
    },
    graphic: empty
      ? [{
          type: 'text',
          left: 'center',
          top: 'center',
          style: {
            text: '暂无服务数据\n请确认 Agent 已上报，或扩大时间范围',
            fill: darkTech ? 'rgba(255,255,255,0.5)' : theme.muted,
            fontSize: 13,
            textAlign: 'center',
            lineHeight: 22
          }
        }]
      : solo
        ? [{
            type: 'text',
            left: 'center',
            top: compact ? 8 : 12,
            style: {
              text: '当前为单应用监控（暂无跨服务调用边）· 点击节点可选中',
              fill: darkTech ? 'rgba(255,255,255,0.5)' : theme.muted,
              fontSize: 12,
              textAlign: 'center'
            }
          }]
        : [],
    animationDurationUpdate: 450,
    series: [{
      type: 'graph',
      layout,
      ...(circular ? { circular } : {}),
      ...(force ? { force } : {}),
      data: seriesNodes,
      links,
      roam: true,
      cursor: 'pointer',
      scaleLimit: { min: 0.4, max: 2.8 },
      zoom: layoutMode === 'hierarchical' ? (compact ? 0.7 : 0.78) : zoom,
      center: ['50%', solo ? '54%' : '50%'],
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: [0, compact ? 10 : 12],
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 5, color: theme.brand },
        itemStyle: {
          shadowBlur: 30,
          shadowColor: 'rgba(45, 212, 191, 0.8)',
          borderColor: theme.brand,
          borderWidth: 3
        },
        label: {
          fontSize: 14,
          fontWeight: 700
        },
        // 节点悬浮放大效果
        scale: 1.15
      },
      // 选中节点脉冲动画
      ...(darkTech && highlight.node ? {
        animationDuration: 1000,
        animationEasing: 'cubicOut'
      } : {})
    }]
  }
}

function levelLabel(level: string): string {
  switch (level) {
    case 'healthy':
      return '健康'
    case 'warning':
      return '警告'
    case 'degraded':
      return '降级'
    case 'critical':
      return '严重'
    case 'unknown':
      return '未知'
    default:
      return level
  }
}

/** 颜色变亮 */
function lightenColor(color: string, percent: number): string {
  const num = parseInt(color.replace('#', ''), 16)
  const amt = Math.round(2.55 * percent)
  const R = Math.min(255, (num >> 16) + amt)
  const G = Math.min(255, ((num >> 8) & 0x00FF) + amt)
  const B = Math.min(255, (num & 0x0000FF) + amt)
  return `#${((1 << 24) + (R << 16) + (G << 8) + B).toString(16).slice(1)}`
}

/** 颜色变暗 */
function darkenColor(color: string, percent: number): string {
  const num = parseInt(color.replace('#', ''), 16)
  const amt = Math.round(2.55 * percent)
  const R = Math.max(0, (num >> 16) - amt)
  const G = Math.max(0, ((num >> 8) & 0x00FF) - amt)
  const B = Math.max(0, (num & 0x0000FF) - amt)
  return `#${((1 << 24) + (R << 16) + (G << 8) + B).toString(16).slice(1)}`
}

/** 解析拓扑图点击：节点 → 该服务；边 → 调用方（source）与被调方 */
export function resolveTopologyClick(params: any): { kind: 'node' | 'edge'; service: string; peer?: string } | null {
  if (!params) return null
  const isEdge = params.dataType === 'edge'
    || (params.data && params.data.source != null && params.data.target != null)
  if (isEdge) {
    const source = String(params.data?.source || '')
    const target = String(params.data?.target || '')
    if (!source) return null
    return { kind: 'edge', service: source, peer: target || undefined }
  }
  const name = String(params.name || params.data?.name || '')
  if (!name) return null
  return { kind: 'node', service: name }
}
