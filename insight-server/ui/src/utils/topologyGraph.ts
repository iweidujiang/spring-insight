import type { EChartsOption } from 'echarts'

export interface ServiceDependency {
  sourceService: string
  targetService: string
  callCount: number
  avgDuration?: number
}

export interface TopologyHighlight {
  /** 高亮节点名 */
  node?: string | null
  /** 高亮边 */
  edge?: { source: string; target: string } | null
}

/** 浅色纸感主题下的节点色（墨青系，少霓虹） */
const NODE_COLORS = [
  '#0f766e', '#15803d', '#0d9488', '#b45309',
  '#b91c1c', '#1d4ed8', '#c2410c', '#047857'
]

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

/** 构建带箭头的服务依赖图（circular 布局，避免 force 把节点挤出视口） */
export function buildTopologyOption(
  dependencies: ServiceDependency[],
  opts: {
    compact?: boolean
    standaloneServices?: string[]
    spanByService?: Record<string, number>
    highlight?: TopologyHighlight
  } = {}
): EChartsOption {
  const compact = opts.compact === true
  const highlight = opts.highlight || {}
  const callCounts = new Map<string, number>()
  const links: Array<Record<string, unknown>> = []

  dependencies.forEach((dep) => {
    callCounts.set(dep.sourceService, (callCounts.get(dep.sourceService) || 0) + dep.callCount)
    callCounts.set(dep.targetService, (callCounts.get(dep.targetService) || 0) + dep.callCount)
    const edgeHit =
      !!highlight.edge &&
      highlight.edge.source === dep.sourceService &&
      highlight.edge.target === dep.targetService
    links.push({
      source: dep.sourceService,
      target: dep.targetService,
      value: dep.callCount,
      avgDuration: dep.avgDuration ?? 0,
      label: {
        show: !compact || dependencies.length <= 12,
        formatter: `${dep.callCount}`,
        fontSize: compact ? 10 : 11,
        color: edgeHit ? '#0f766e' : '#3d524a',
        fontWeight: edgeHit ? 700 : 500,
        backgroundColor: edgeHit ? 'rgba(204, 251, 241, 0.95)' : 'rgba(255, 252, 250, 0.92)',
        padding: [2, 4],
        borderRadius: 3,
        borderColor: edgeHit ? 'rgba(15, 118, 110, 0.35)' : 'rgba(20, 83, 45, 0.12)',
        borderWidth: 1
      },
      lineStyle: {
        width: Math.max(
          compact ? 1.5 : 2,
          Math.min(compact ? 4 : 5, 1 + Math.log2(dep.callCount + 1))
        ) + (edgeHit ? 1.5 : 0),
        curveness: 0.16,
        color: edgeHit ? '#0f766e' : '#7a9086',
        opacity: edgeHit ? 1 : highlight.edge || highlight.node ? 0.35 : 0.9
      }
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
    const dimmed = !!(highlight.node || highlight.edge) && !nodeHit && !onSelectedEdge
    return {
      name,
      value,
      symbolSize: Math.max(
        compact ? 34 : 42,
        Math.min(compact ? 54 : 68, 16 + Math.sqrt(value) * (compact ? 4.5 : 6.5))
      ) + (nodeHit ? 6 : 0),
      itemStyle: {
        color: NODE_COLORS[index % NODE_COLORS.length],
        borderColor: nodeHit || onSelectedEdge ? '#0f766e' : '#fffcfa',
        borderWidth: nodeHit ? 3 : 2,
        shadowBlur: nodeHit ? 14 : 8,
        shadowColor: nodeHit ? 'rgba(15, 118, 110, 0.4)' : 'rgba(21, 36, 31, 0.18)',
        opacity: dimmed ? 0.35 : 1
      },
      label: {
        show: true,
        position: 'bottom' as const,
        distance: 6,
        formatter: '{b}',
        fontSize: compact ? 10 : nodeHit ? 13 : 12,
        fontWeight: nodeHit ? 700 : 600,
        color: dimmed ? '#6b7f76' : '#15241f'
      }
    }
  })

  const empty = nodes.length === 0
  const solo = !empty && links.length === 0
  const zoom = solo ? (compact ? 0.72 : 0.78) : zoomForNodes(nodes.length, compact)

  return {
    backgroundColor: 'transparent',
    textStyle: { color: '#6b7f76' },
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(255, 252, 250, 0.96)',
      borderColor: 'rgba(20, 83, 45, 0.15)',
      textStyle: { color: '#15241f' },
      formatter: (params: any) => {
        if (params.dataType === 'edge' || params.data?.source != null) {
          const d = params.data
          const avg = d.avgDuration != null ? `<br/>平均耗时: ${d.avgDuration} ms` : ''
          return `<div style="font-weight:700">${d.source} → ${d.target}</div>调用: ${d.value} 次${avg}<br/><span style="opacity:.75">点击选中 / 下钻链路</span>`
        }
        const spanHint = solo ? '本机 Span' : '关联调用'
        return `<div style="font-weight:700">${params.data.name}</div>${spanHint}: ${params.data.value}<br/><span style="opacity:.75">点击选中 / 下钻链路</span>`
      }
    },
    graphic: empty
      ? [{
          type: 'text',
          left: 'center',
          top: 'center',
          style: {
            text: '暂无服务数据\n请确认 Agent 已上报，或扩大时间范围',
            fill: '#6b7f76',
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
              fill: '#6b7f76',
              fontSize: 12,
              textAlign: 'center'
            }
          }]
        : [],
    animationDurationUpdate: 450,
    series: [{
      type: 'graph',
      layout: 'circular',
      circular: { rotateLabel: false },
      data: nodes,
      links,
      roam: true,
      cursor: 'pointer',
      scaleLimit: { min: 0.4, max: 2.8 },
      zoom,
      center: ['50%', solo ? '54%' : '50%'],
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: [0, compact ? 10 : 12],
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 5, color: '#0f766e' },
        itemStyle: { shadowBlur: 12, shadowColor: 'rgba(15, 118, 110, 0.35)' }
      }
    }]
  }
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
