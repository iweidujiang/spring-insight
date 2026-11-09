<template>
  <div class="si-svc-detail fade-in">
    <header class="si-svc-detail__top">
      <div class="si-svc-detail__title-block">
        <el-button link type="primary" size="small" class="si-svc-detail__back" @click="goBack">
          <el-icon class="me-1"><ArrowLeft /></el-icon>服务列表
        </el-button>
        <h2 class="si-svc-detail__title">
          <el-icon class="me-2"><Box /></el-icon>{{ serviceName || '服务详情' }}
        </h2>
        <p class="si-svc-detail__lead">KPI、依赖、最近链路与 JVM 运行时</p>
      </div>
      <div class="si-svc-detail__actions">
        <label class="visually-hidden" for="svc-detail-hours">时间范围</label>
        <el-select
          id="svc-detail-hours"
          v-model="hours"
          class="si-svc-detail__hours"
          size="small"
          @change="onHoursChange"
        >
          <el-option
            v-for="opt in TIME_RANGE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-button
          type="primary"
          size="small"
          class="si-svc-detail__refresh"
          :loading="loading"
          @click="load"
        >
          <el-icon v-if="!loading" class="me-1"><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </header>

    <SiLoading v-if="loading" text="加载中…" />
    <SiAlert v-if="loadError" tone="danger" class="si-svc-detail__alert">{{ loadError }}</SiAlert>
    <SiAlert v-else-if="!loading && deeplinkHint" tone="warning" role="alert" class="si-svc-detail__alert">{{ deeplinkHint }}</SiAlert>

    <template v-if="!loading && summary">
      <section class="si-svc-detail__kpis" :class="{ 'is-alert': leadKpi.alert }" aria-label="关键指标">
        <div class="si-svc-detail__kpi si-svc-detail__kpi--lead" :class="{ 'is-alert': leadKpi.alert }">
          <span class="si-svc-detail__kpi-figure">
            <span class="si-svc-detail__kpi-value">{{ leadKpi.count }}</span>
            <span v-if="leadKpi.unit" class="si-svc-detail__kpi-unit">{{ leadKpi.unit }}</span>
          </span>
          <span class="si-svc-detail__kpi-name">{{ leadKpi.title }}</span>
        </div>
        <div class="si-svc-detail__kpi-side">
          <div v-for="stat in sideKpis" :key="stat.key" class="si-svc-detail__kpi" :class="{ 'is-alert': stat.alert }">
            <span class="si-svc-detail__kpi-figure">
              <span class="si-svc-detail__kpi-value">{{ stat.count }}</span>
              <span v-if="stat.unit" class="si-svc-detail__kpi-unit">{{ stat.unit }}</span>
              <template v-if="stat.extra">
                <span class="si-svc-detail__kpi-sep">/</span>
                <span class="si-svc-detail__kpi-value">{{ stat.extra }}</span>
                <span v-if="stat.extraUnit" class="si-svc-detail__kpi-unit">{{ stat.extraUnit }}</span>
              </template>
            </span>
            <span class="si-svc-detail__kpi-name">{{ stat.title }}</span>
          </div>
        </div>
      </section>

      <div class="si-svc-detail__ctas">
        <el-button type="primary" plain size="small" @click="goTraces()">全部链路</el-button>
        <el-button type="danger" plain size="small" @click="goTraces({ status: 'error' })">仅错误</el-button>
        <el-button size="small" @click="goTopology">打开拓扑</el-button>
      </div>

      <section class="si-svc-detail__jvm" aria-label="JVM 指标">
        <div class="si-svc-detail__jvm-head">
          <h3 class="si-svc-detail__jvm-title">JVM 运行时</h3>
          <el-select
            v-if="jvmInstances.length > 1"
            v-model="jvmInstance"
            size="small"
            class="si-svc-detail__jvm-instance"
            placeholder="全部实例"
            clearable
            @change="loadJvm"
          >
            <el-option
              v-for="inst in jvmInstances"
              :key="inst"
              :label="inst"
              :value="inst"
            />
          </el-select>
        </div>
        <p v-if="jvmLatestMeta" class="si-svc-detail__jvm-meta">{{ jvmLatestMeta }}</p>
        <div v-if="jvmLoading" class="si-svc-detail__card-empty">加载 JVM 指标…</div>
        <div v-else-if="!jvmFound" class="si-svc-detail__card-empty si-svc-detail__jvm-empty">
          暂无 JVM 指标。在业务服务开启
          <code>spring.insight.jvm-metrics.enabled=true</code>
          并上报后，这里会显示堆、GC 与线程曲线。
        </div>
        <div v-else class="si-svc-detail__jvm-charts">
          <div class="si-svc-detail__jvm-chart-card">
            <div class="si-svc-detail__jvm-chart-label">堆内存</div>
            <div ref="heapChartEl" class="si-svc-detail__jvm-canvas"></div>
          </div>
          <div class="si-svc-detail__jvm-chart-card">
            <div class="si-svc-detail__jvm-chart-label">GC 累计次数</div>
            <div ref="gcChartEl" class="si-svc-detail__jvm-canvas"></div>
          </div>
          <div class="si-svc-detail__jvm-chart-card">
            <div class="si-svc-detail__jvm-chart-label">线程</div>
            <div ref="threadChartEl" class="si-svc-detail__jvm-canvas"></div>
          </div>
        </div>
      </section>

      <div class="si-svc-detail__grid">
        <div class="si-svc-detail__grid-col">
          <el-card class="si-svc-detail__card si-svc-detail__card--full" shadow="never">
            <template #header>
              <span class="si-svc-detail__card-title">慢操作 Top</span>
            </template>
            <div v-if="summary.slowOperations.length === 0" class="si-svc-detail__card-empty">
              {{ emptyRequestsMessage(hours) }}
            </div>
            <SiDataTable
              v-else
              class="si-svc-detail__table--sm"
              :rows="summary.slowOperations"
              :columns="slowColumns"
              :row-key="slowRowKey"
              clickable
              @row-click="onSlowOpClick"
            />
          </el-card>
        </div>

        <div class="si-svc-detail__grid-col">
          <el-card class="si-svc-detail__card si-svc-detail__card--full" shadow="never">
            <template #header>
              <span class="si-svc-detail__card-title">依赖（出站 / 入站）</span>
            </template>
            <p class="si-svc-detail__deps-label">出站 →</p>
            <ul v-if="summary.outbound.length" class="si-svc-detail__deps">
              <li v-for="(e, i) in summary.outbound" :key="'o' + i">
                <el-button link type="primary" size="small" @click="goService(e.targetService)">
                  {{ e.targetService }}
                </el-button>
                <span class="si-svc-detail__deps-count">×{{ e.callCount }}</span>
              </li>
            </ul>
            <p v-else class="si-svc-detail__deps-empty">暂无出站边</p>
            <p class="si-svc-detail__deps-label si-svc-detail__deps-label--mt">← 入站</p>
            <ul v-if="summary.inbound.length" class="si-svc-detail__deps">
              <li v-for="(e, i) in summary.inbound" :key="'i' + i">
                <el-button link type="primary" size="small" @click="goService(e.sourceService)">
                  {{ e.sourceService }}
                </el-button>
                <span class="si-svc-detail__deps-count">×{{ e.callCount }}</span>
              </li>
            </ul>
            <p v-else class="si-svc-detail__deps-empty">暂无入站边</p>
          </el-card>
        </div>

        <div class="si-svc-detail__grid-col--full">
          <el-card class="si-svc-detail__card" shadow="never">
            <template #header>
              <div class="si-svc-detail__card-head">
                <span class="si-svc-detail__card-title">最近链路</span>
                <el-button link type="primary" size="small" @click="goTraces()">查看全部</el-button>
              </div>
            </template>
            <div v-if="summary.recentTraces.length === 0" class="si-svc-detail__card-empty">
              {{ emptyRequestsMessage(hours) }}
            </div>
            <SiDataTable
              v-else
              :rows="summary.recentTraces"
              :columns="recentColumns"
              :row-key="recentRowKey"
              clickable
              @row-click="onRecentClick"
            >
              <template #cell-status="{ row }">
                <el-tag
                  :type="row.hasError ? 'danger' : 'success'"
                  effect="dark"
                  size="small"
                  round
                >
                  {{ row.statusCode || (row.hasError ? 'ERR' : 'OK') }}
                </el-tag>
              </template>
              <template #cell-trace="{ row }">
                <code class="si-svc-detail__cell--small">{{ shortId(row.traceId) }}</code>
              </template>
            </SiDataTable>
          </el-card>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ArrowLeft, Box, Refresh } from '@element-plus/icons-vue'
import { ApiService } from '../services/ApiService'
import SiAlert from '../components/SiAlert.vue'
import SiDataTable from '../components/SiDataTable.vue'
import SiLoading from '../components/SiLoading.vue'
import type { SiColumn } from '../components/siTable'
import { useChartTheme } from '../composables/useChartTheme'
import { formatDuration } from '../utils/traceTimeline'
import { TIME_RANGE_OPTIONS, emptyRequestsMessage } from '../utils/timeRange'
import { resolveHours, persistHours, persistService, mergeQuery } from '../utils/insightQuery'
import { loadFailedMessage, deeplinkMissMessage } from '../utils/timeRange'

const route = useRoute()
const router = useRouter()
const { colors: chartColors } = useChartTheme()

const serviceName = ref('')
const hours = ref(24)
const loading = ref(true)
const loadError = ref('')
const deeplinkHint = ref('')
const summary = ref<Awaited<ReturnType<typeof ApiService.getServiceSummary>> | null>(null)

type JvmPoint = Awaited<ReturnType<typeof ApiService.getServiceJvmMetrics>>['points'][number]
const jvmLoading = ref(false)
const jvmFound = ref(false)
const jvmInstance = ref('')
const jvmInstances = ref<string[]>([])
const jvmPoints = ref<JvmPoint[]>([])
const jvmLatest = ref<Record<string, unknown> | null>(null)
const heapChartEl = ref<HTMLElement | null>(null)
const gcChartEl = ref<HTMLElement | null>(null)
const threadChartEl = ref<HTMLElement | null>(null)
let heapChart: echarts.ECharts | null = null
let gcChart: echarts.ECharts | null = null
let threadChart: echarts.ECharts | null = null

const jvmLatestMeta = computed(() => {
  const snap = jvmLatest.value
  if (!snap) return ''
  const parts: string[] = []
  if (snap.javaVersion) parts.push(`Java ${snap.javaVersion}`)
  if (snap.jvmName) parts.push(String(snap.jvmName))
  if (snap.serviceInstance) parts.push(String(snap.serviceInstance))
  const heapUsed = Number(snap.heapMemoryUsed ?? 0)
  const heapMax = Number(snap.heapMemoryMax ?? 0)
  if (heapMax > 0) {
    parts.push(`堆 ${formatBytes(heapUsed)} / ${formatBytes(heapMax)}`)
  }
  return parts.join(' · ')
})

function formatBytes(n: number): string {
  if (!Number.isFinite(n) || n <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let v = n
  let i = 0
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i += 1
  }
  return `${v >= 10 || i === 0 ? v.toFixed(0) : v.toFixed(1)} ${units[i]}`
}

type SvcKpi = {
  key: string
  title: string
  count: string
  unit: string
  extra?: string
  extraUnit?: string
  alert?: boolean
}

const durationParts = (ms: number) => {
  const text = formatDuration(ms)
  const matched = text.match(/^([<>]?\d+(?:\.\d+)?)([a-zA-Z]+)$/)
  if (!matched) return { count: text, unit: '' }
  return { count: matched[1], unit: matched[2] }
}

const kpiStats = computed<SvcKpi[]>(() => {
  const kpis = summary.value?.kpis
  if (!kpis) return []
  const p95 = durationParts(kpis.p95Ms)
  const avg = durationParts(kpis.avgMs)
  const max = durationParts(kpis.maxMs)
  return [
    { key: 'spans', title: 'Span 数', count: String(kpis.spanCount), unit: '' },
    {
      key: 'errors',
      title: '错误',
      count: String(kpis.errorCount),
      unit: `${kpis.errorRate.toFixed(1)}%`,
      alert: kpis.errorCount > 0
    },
    { key: 'p95', title: 'p95', count: p95.count, unit: p95.unit },
    {
      key: 'avg',
      title: '平均 / 最大',
      count: avg.count,
      unit: avg.unit,
      extra: max.count,
      extraUnit: max.unit
    }
  ]
})

const leadKpi = computed(() => {
  const rows = kpiStats.value
  return rows.find((row) => row.alert) || rows.find((row) => row.key === 'spans') || rows[0] || {
    key: 'spans',
    title: 'Span 数',
    count: '0',
    unit: ''
  }
})

const sideKpis = computed(() => kpiStats.value.filter((row) => row.key !== leadKpi.value.key))

const shortId = (id?: string) => {
  const s = String(id || '')
  return s.length > 12 ? `${s.slice(0, 8)}…` : s || '—'
}

type SlowOpRow = {
  operationName: string
  spanCount: number
  errorCount: number
  p95Ms: number
}

type RecentTraceRow = {
  traceId?: string
  operationName?: string
  durationMs?: number
  hasError?: boolean
  statusCode?: string
}

const slowColumns: SiColumn<SlowOpRow>[] = [
  {
    key: 'operation',
    label: '操作',
    cellClass: 'si-svc-detail__op',
    value: (row) => row.operationName,
    title: (row) => row.operationName
  },
  { key: 'count', label: '次数', value: (row) => row.spanCount },
  {
    key: 'errors',
    label: '错误',
    value: (row) => row.errorCount,
    cellClass: (row) => (row.errorCount > 0 ? 'si-svc-detail__cell--danger' : undefined)
  },
  { key: 'p95', label: 'p95', value: (row) => formatDuration(row.p95Ms) }
]

const slowRowKey = (row: SlowOpRow, index: number) => `${row.operationName}-${index}`

const onSlowOpClick = (row: SlowOpRow) => {
  goTraces({ q: row.operationName, minDurationMs: Math.max(0, Math.floor(row.p95Ms)) })
}

const recentColumns: SiColumn<RecentTraceRow>[] = [
  {
    key: 'operation',
    label: '操作',
    cellClass: 'si-svc-detail__trace-op',
    value: (row) => row.operationName || '—',
    title: (row) => row.operationName || undefined
  },
  { key: 'duration', label: '耗时', value: (row) => formatDuration(Number(row.durationMs) || 0) },
  { key: 'status', label: '状态' },
  { key: 'trace', label: 'Trace' }
]

const recentRowKey = (row: RecentTraceRow, index: number) => row.traceId || index

const onRecentClick = (row: RecentTraceRow) => {
  goTrace(String(row.traceId || ''))
}

const syncHoursToRoute = () => {
  persistHours(hours.value)
  router.replace({
    path: route.path,
    query: mergeQuery(route.query, { hours: String(hours.value) })
  })
}

const onHoursChange = () => {
  syncHoursToRoute()
  load()
}

const disposeJvmCharts = () => {
  heapChart?.dispose()
  gcChart?.dispose()
  threadChart?.dispose()
  heapChart = null
  gcChart = null
  threadChart = null
}

const baseLineOption = (times: string[], series: echarts.SeriesOption[]) => {
  const c = chartColors.value
  return {
    color: [c.brand, c.warning, c.success],
    grid: { left: 48, right: 16, top: 28, bottom: 28 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText }
    },
    legend: {
      top: 0,
      textStyle: { color: c.inkSoft, fontSize: 11 }
    },
    xAxis: {
      type: 'category',
      data: times,
      axisLabel: { color: c.muted, fontSize: 10 },
      axisLine: { lineStyle: { color: c.axisLine } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: c.muted, fontSize: 10 },
      splitLine: { lineStyle: { color: c.splitLine, type: 'dashed' } }
    },
    series
  } as echarts.EChartsOption
}

const renderJvmCharts = async () => {
  await nextTick()
  if (!jvmFound.value || jvmPoints.value.length === 0) {
    disposeJvmCharts()
    return
  }
  const points = jvmPoints.value
  const times = points.map((p) => {
    const d = new Date(p.timestamp)
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  })
  const c = chartColors.value

  if (heapChartEl.value) {
    heapChart ||= echarts.init(heapChartEl.value)
    heapChart.setOption(
      baseLineOption(times, [
        {
          name: '已用',
          type: 'line',
          showSymbol: false,
          data: points.map((p) => Number((p.heapMemoryUsed / (1024 * 1024)).toFixed(2)))
        },
        {
          name: '已提交',
          type: 'line',
          showSymbol: false,
          data: points.map((p) => Number((p.heapMemoryCommitted / (1024 * 1024)).toFixed(2)))
        },
        {
          name: '上限',
          type: 'line',
          showSymbol: false,
          lineStyle: { type: 'dashed' },
          data: points.map((p) =>
            p.heapMemoryMax > 0 ? Number((p.heapMemoryMax / (1024 * 1024)).toFixed(2)) : null
          )
        }
      ]),
      true
    )
    heapChart.setOption({
      yAxis: { name: 'MB', nameTextStyle: { color: c.muted, fontSize: 10 } }
    })
  }

  if (gcChartEl.value) {
    gcChart ||= echarts.init(gcChartEl.value)
    gcChart.setOption(
      baseLineOption(times, [
        {
          name: 'GC 次数',
          type: 'line',
          showSymbol: false,
          areaStyle: { opacity: 0.08 },
          data: points.map((p) => p.gcCount)
        }
      ]),
      true
    )
  }

  if (threadChartEl.value) {
    threadChart ||= echarts.init(threadChartEl.value)
    threadChart.setOption(
      baseLineOption(times, [
        {
          name: '活跃',
          type: 'line',
          showSymbol: false,
          data: points.map((p) => p.activeThreads)
        },
        {
          name: '峰值',
          type: 'line',
          showSymbol: false,
          data: points.map((p) => p.peakThreads)
        },
        {
          name: '守护',
          type: 'line',
          showSymbol: false,
          data: points.map((p) => p.daemonThreads)
        }
      ]),
      true
    )
  }
}

const loadJvm = async () => {
  const name = serviceName.value
  if (!name) {
    jvmFound.value = false
    jvmPoints.value = []
    jvmLatest.value = null
    disposeJvmCharts()
    return
  }
  jvmLoading.value = true
  try {
    const data = await ApiService.getServiceJvmMetrics(
      name,
      hours.value,
      jvmInstance.value || undefined
    )
    jvmFound.value = data.found
    jvmInstances.value = data.instances
    if (!jvmInstance.value && data.instance) {
      jvmInstance.value = data.instance
    }
    jvmPoints.value = data.points
    jvmLatest.value = data.latest
    await renderJvmCharts()
  } catch {
    jvmFound.value = false
    jvmPoints.value = []
    jvmLatest.value = null
    disposeJvmCharts()
  } finally {
    jvmLoading.value = false
  }
}

const onResize = () => {
  heapChart?.resize()
  gcChart?.resize()
  threadChart?.resize()
}

const goBack = () => {
  router.push({ path: '/services', query: { hours: String(hours.value) } })
}

const goTraces = (extra: Record<string, string | number> = {}) => {
  const query: Record<string, string> = {
    service: serviceName.value,
    hours: String(hours.value)
  }
  for (const [k, v] of Object.entries(extra)) {
    if (v != null && v !== '') query[k] = String(v)
  }
  router.push({ path: '/traces', query })
}

const goTopology = () => {
  persistService(serviceName.value)
  router.push({
    path: '/topology',
    query: { service: serviceName.value, hours: String(hours.value) }
  })
}

const goService = (name: string) => {
  const n = String(name || '').trim()
  if (!n) return
  router.push({
    path: `/services/${encodeURIComponent(n)}`,
    query: { hours: String(hours.value) }
  })
}

const goTrace = (traceId: string) => {
  if (!traceId) return
  router.push({
    name: 'trace-detail',
    params: { traceId },
    query: { hours: String(hours.value) }
  })
}

const load = async () => {
  const name = decodeURIComponent(String(route.params.serviceName || '')).trim()
  serviceName.value = name
  loadError.value = ''
  deeplinkHint.value = ''
  summary.value = null
  jvmInstance.value = ''
  if (!name) {
    loading.value = false
    deeplinkHint.value = deeplinkMissMessage('service')
    disposeJvmCharts()
    return
  }
  persistService(name)
  loading.value = true
  try {
    const data = await ApiService.getServiceSummary(name, hours.value, 15, 10)
    summary.value = data
    if (!data.found) {
      deeplinkHint.value = `近 ${hours.value === 0 ? '全部' : hours.value + ' 小时'}窗口内未找到服务「${name}」的 Span；可扩大时间窗或从拓扑重新进入。`
    }
    await loadJvm()
  } catch {
    loadError.value = loadFailedMessage('服务详情')
  } finally {
    loading.value = false
  }
}

watch(
  () => route.params.serviceName,
  () => {
    hours.value = resolveHours(route.query, 24)
    load()
  }
)

watch(chartColors, () => {
  if (jvmFound.value) void renderJvmCharts()
})

onMounted(() => {
  hours.value = resolveHours(route.query, 24)
  persistHours(hours.value)
  if (route.query.hours == null || route.query.hours === '') {
    syncHoursToRoute()
  }
  window.addEventListener('resize', onResize)
  load()
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  disposeJvmCharts()
})
</script>

<style scoped>
.si-svc-detail__alert {
  margin-bottom: 0.75rem;
}

.si-svc-detail__top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem 1rem;
  margin-bottom: 1rem;
}

.si-svc-detail__title-block {
  min-width: 0;
  flex: 1 1 12rem;
}

.si-svc-detail__title {
  margin: 0.15rem 0 0.15rem;
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-svc-detail__lead {
  margin: 0;
  font-size: 0.82rem;
  color: var(--si-muted);
}

.si-svc-detail__back {
  color: var(--si-muted);
  text-decoration: none;
}

.si-svc-detail__actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex: 0 0 auto;
}

.si-svc-detail__jvm {
  margin: 0 0 1rem;
  padding: 0.85rem 1rem 1rem;
  border: 1px solid var(--card-border);
  border-radius: 12px;
  background: var(--card-bg);
}

.si-svc-detail__jvm-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.35rem;
}

.si-svc-detail__jvm-title {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-svc-detail__jvm-instance {
  width: 12rem;
}

.si-svc-detail__jvm-meta {
  margin: 0 0 0.75rem;
  font-size: 0.78rem;
  color: var(--si-muted);
}

.si-svc-detail__jvm-empty code {
  font-size: 0.78em;
}

.si-svc-detail__jvm-charts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.65rem;
}

.si-svc-detail__jvm-chart-card {
  min-width: 0;
  padding: 0.35rem 0.25rem 0.15rem;
  border: 1px solid var(--card-border);
  border-radius: 10px;
  background: var(--si-surface-2, transparent);
}

.si-svc-detail__jvm-chart-label {
  padding: 0 0.45rem 0.15rem;
  font-size: 0.75rem;
  font-weight: 650;
  color: var(--si-ink-soft);
}

.si-svc-detail__jvm-canvas {
  width: 100%;
  height: 180px;
}

@media (max-width: 1100px) {
  .si-svc-detail__jvm-charts {
    grid-template-columns: 1fr;
  }
}

.si-svc-detail__card {
  --el-card-border-color: var(--card-border);
  --el-card-bg-color: var(--card-bg);
}

.si-svc-detail__card :deep(.el-card__header) {
  padding: 0.65rem 1rem;
}

.si-svc-detail__card :deep(.el-card__body) {
  padding: 0.85rem 1rem;
}

.si-svc-detail__hours {
  width: auto;
  min-width: 9.5rem;
  flex: 0 0 auto;
}

.si-svc-detail__refresh {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  flex: 0 0 auto;
  white-space: nowrap;
  min-height: 31px;
  padding: 0.25rem 0.75rem;
}

.si-svc-detail__loading {
  padding: 2rem;
  text-align: center;
  color: var(--si-muted);
}

.si-svc-detail__kpis {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  margin-bottom: 0.85rem;
  padding: 0.7rem 0.45rem 0.7rem 1.05rem;
  border-radius: 10px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
}

.si-svc-detail__kpis.is-alert {
  box-shadow: inset 3px 0 0 var(--danger-color), var(--box-shadow);
}

.si-svc-detail__kpi {
  display: inline-flex;
  align-items: baseline;
  gap: 0.45rem;
  min-width: 0;
  padding: 0.15rem 0.55rem;
}

.si-svc-detail__kpi--lead {
  flex: 0 0 auto;
  gap: 0.7rem;
  padding: 0.15rem 1.15rem 0.15rem 0.15rem;
  margin-right: 0.35rem;
  border-right: 1px solid var(--card-border);
}

.si-svc-detail__kpi-side {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.2rem 1.35rem;
  min-width: 0;
  padding-left: 0.35rem;
}

.si-svc-detail__kpi-figure {
  display: inline-flex;
  align-items: baseline;
  gap: 0.18rem;
  flex: 0 0 auto;
}

.si-svc-detail__kpi-value {
  font-family: var(--font-numeric);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  color: var(--si-ink);
  line-height: 1;
  letter-spacing: -0.03em;
}

.si-svc-detail__kpi--lead .si-svc-detail__kpi-value {
  font-size: clamp(1.85rem, 2.6vw, 2.35rem);
}

.si-svc-detail__kpi-side .si-svc-detail__kpi-value {
  font-size: 1.15rem;
}

.si-svc-detail__kpi.is-alert .si-svc-detail__kpi-value {
  color: var(--danger-color);
}

.si-svc-detail__kpi-unit,
.si-svc-detail__kpi-sep {
  font-family: var(--font-body);
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--si-ink-soft);
}

.si-svc-detail__kpi--lead .si-svc-detail__kpi-unit {
  font-size: 0.88rem;
}

.si-svc-detail__kpi-name {
  font-family: var(--font-body);
  font-size: 0.84rem;
  font-weight: 600;
  color: var(--si-ink-soft);
  white-space: nowrap;
}

.si-svc-detail__kpi--lead .si-svc-detail__kpi-name {
  font-size: 0.95rem;
  color: var(--si-ink);
}

@media (max-width: 991px) {
  .si-svc-detail__kpis {
    flex-direction: column;
    align-items: stretch;
    gap: 0.35rem;
    padding: 0.75rem 0.85rem;
  }

  .si-svc-detail__kpi--lead {
    margin-right: 0;
    padding: 0.1rem 0 0.55rem;
    border-right: 0;
    border-bottom: 1px solid var(--card-border);
  }

  .si-svc-detail__kpi-side {
    padding-left: 0;
  }
}

@media (max-width: 640px) {
  .si-svc-detail__kpi-side {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.35rem;
  }
}

.si-svc-detail__ctas {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin-bottom: 1rem;
}

.si-svc-detail__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
}

@media (min-width: 992px) {
  .si-svc-detail__grid {
    grid-template-columns: 1fr 1fr;
  }
}

.si-svc-detail__grid-col {
  min-width: 0;
}

.si-svc-detail__grid-col--full {
  grid-column: 1 / -1;
}

.si-svc-detail__card--full {
  height: 100%;
}

.si-svc-detail__card-title {
  font-weight: 600;
}

.si-svc-detail__card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.si-svc-detail__card-empty {
  padding: 0.75rem;
  color: var(--si-muted);
  font-size: 0.85rem;
}

.si-svc-detail__deps-label {
  font-size: 0.85rem;
  color: var(--si-muted);
  margin-bottom: 0.5rem;
}

.si-svc-detail__deps-label--mt {
  margin-top: 0.75rem;
}

.si-svc-detail__deps-count {
  font-size: 0.85rem;
  color: var(--si-muted);
  margin-left: 0.25rem;
}

.si-svc-detail__deps-empty {
  font-size: 0.85rem;
  color: var(--si-muted);
}

:deep(.si-svc-detail__cell--small) {
  font-size: 0.84rem;
}

:deep(.si-svc-detail__cell--danger) {
  color: var(--danger-color);
}

.si-svc-detail__deps {
  list-style: none;
  padding: 0;
  margin: 0;
}

.si-svc-detail__deps li {
  display: flex;
  align-items: baseline;
  gap: 0.25rem;
}

.si-svc-detail__table--sm :deep(td),
.si-svc-detail__table--sm :deep(th) {
  padding: 0.25rem;
}

:deep(.si-svc-detail__op) {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.si-svc-detail__trace-op) {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
