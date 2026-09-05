import type { EChartsOption } from 'echarts'

export interface ServiceDependency {
  sourceService: string
  targetService: string
  callCount: number
  avgDuration?: number
}

/** 浅色纸感主题下的节点色（墨青系，少霓虹） */
const NODE_COLORS = [
  '#0f766e', '#15803d', '#0d9488', '#b45309',
  '#b91c1c', '#1d4ed8', '#c2410c', '#047857'
]

/** 构建带箭头的服务依赖图（circular 布局，避免 force 把节点挤出视口） */
export function buildTopologyOption(
  dependencies: ServiceDependency[],
  opts: { compact?: boolean } = {}
): EChartsOption {
  const compact = opts.compact === true
  const callCounts = new Map<string, number>()
  const links: Array<Record<string, unknown>> = []

  dependencies.forEach((dep) => {
    callCounts.set(dep.sourceService, (callCounts.get(dep.sourceService) || 0) + dep.callCount)
    callCounts.set(dep.targetService, (callCounts.get(dep.targetService) || 0) + dep.callCount)
    links.push({
      source: dep.sourceService,
      target: dep.targetService,
      value: dep.callCount,
      avgDuration: dep.avgDuration ?? 0,
      label: {
        show: true,
        formatter: `${dep.callCount}次`,
        fontSize: compact ? 10 : 11,
        color: '#3d524a',
        backgroundColor: 'rgba(255, 252, 250, 0.92)',
        padding: [2, 4],
        borderRadius: 3,
        borderColor: 'rgba(20, 83, 45, 0.12)',
        borderWidth: 1
      },
      lineStyle: {
        width: Math.max(compact ? 1.5 : 2, Math.min(compact ? 4 : 5, 1 + Math.log2(dep.callCount + 1))),
        curveness: 0.18,
        color: '#7a9086',
        opacity: 0.9
      }
    })
  })

  const nodes = Array.from(callCounts.entries()).map(([name, value], index) => ({
    name,
    value,
    symbolSize: Math.max(compact ? 36 : 44, Math.min(compact ? 56 : 72, 18 + Math.sqrt(value) * (compact ? 5 : 7))),
    itemStyle: {
      color: NODE_COLORS[index % NODE_COLORS.length],
      borderColor: '#fffcfa',
      borderWidth: 2,
      shadowBlur: 8,
      shadowColor: 'rgba(21, 36, 31, 0.18)'
    },
    label: {
      show: true,
      position: 'bottom' as const,
      distance: 8,
      formatter: '{b}',
      fontSize: compact ? 11 : 12,
      fontWeight: 600,
      color: '#15241f'
    }
  }))

  const empty = nodes.length === 0

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
          return `<div style="font-weight:700">${d.source} → ${d.target}</div>调用: ${d.value} 次${avg}<br/><span style="opacity:.75">点击边：查看调用方链路</span>`
        }
        return `<div style="font-weight:700">${params.data.name}</div>关联调用: ${params.data.value}<br/><span style="opacity:.75">点击节点：查看该服务链路</span>`
      }
    },
    graphic: empty
      ? [{
          type: 'text',
          left: 'center',
          top: 'center',
          style: {
            text: '暂无依赖拓扑\n产生跨服务调用后将显示（箭头：调用方 → 被调用方）',
            fill: '#6b7f76',
            fontSize: 13,
            textAlign: 'center',
            lineHeight: 22
          }
        }]
      : [],
    animationDurationUpdate: 800,
    series: [{
      type: 'graph',
      layout: 'circular',
      circular: { rotateLabel: false },
      data: nodes,
      links,
      roam: true,
      cursor: 'pointer',
      scaleLimit: { min: 0.45, max: 2.5 },
      zoom: 0.92,
      center: ['50%', '50%'],
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: [0, compact ? 10 : 14],
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 5, color: '#0f766e' },
        itemStyle: { shadowBlur: 12, shadowColor: 'rgba(15, 118, 110, 0.35)' }
      }
    }]
  }
}

/** 解析拓扑图点击：节点 → 该服务；边 → 调用方（source） */
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
