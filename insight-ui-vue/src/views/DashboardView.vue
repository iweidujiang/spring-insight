<template>
  <div class="si-dashboard fade-in">
    <header class="si-dashboard__top">
      <div class="si-dashboard__title-block">
        <h2 class="si-dashboard__title">
          <i class="fa fa-tachometer-alt me-2"></i>监控仪表盘
        </h2>
        <p class="si-dashboard__subtitle">总览 · 拓扑为主，排名为辅</p>
      </div>
      <div class="si-dashboard__status" v-show="!loading">
        <span v-if="!errorAnalysis || errorAnalysis.length === 0" class="si-dashboard__pill si-dashboard__pill--ok">
          <i class="fa fa-check-circle me-1"></i>无异常服务
        </span>
        <span v-else class="si-dashboard__pill si-dashboard__pill--warn">
          <i class="fa fa-exclamation-triangle me-1"></i>{{ errorAnalysis.length }} 个服务需关注
        </span>
      </div>
      <div class="si-dashboard__actions">
        <button class="btn btn-primary btn-sm si-dashboard__btn" @click="loadData" :disabled="loading">
          <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i> 刷新
        </button>
        <span class="badge bg-info si-dashboard__clock">
          <i class="fa fa-clock me-1"></i>{{ currentTime }}
        </span>
      </div>
    </header>

    <div v-if="loading" class="si-dashboard__loading">
      <i class="fa fa-spinner fa-spin"></i>
      <span class="ms-2">加载中…</span>
    </div>

    <div v-show="!loading" class="si-dashboard__content">
      <!-- 一级：KPI 可点击跳转 -->
      <section class="si-dashboard__kpis" aria-label="关键指标">
        <button
          v-for="(stat, index) in stats"
          :key="index"
          type="button"
          class="card stat-card si-dashboard__kpi"
          :style="{ animationDelay: `${index * 0.06}s` }"
          @click="onKpiClick(stat.to)"
        >
          <div class="card-body">
            <div class="d-flex align-items-center justify-content-between">
              <div class="text-start">
                <div class="text-xs font-weight-bold text-uppercase mb-0 si-dashboard__kpi-label" :class="`text-${stat.color}`">
                  {{ stat.title }}
                </div>
                <div class="si-dashboard__kpi-value">{{ stat.value }}</div>
                <div class="si-dashboard__kpi-hint">{{ stat.hint }}</div>
              </div>
              <i :class="`fa ${stat.icon} si-dashboard__kpi-icon text-${stat.color}`"></i>
            </div>
          </div>
        </button>
      </section>

      <!-- 三级：Collector 收成一条，不与主图抢视线 -->
      <section v-if="collectorStats" class="si-dashboard__collector-strip" aria-label="采集器状态">
        <span class="si-dashboard__collector-strip-title"><i class="fa fa-database me-1"></i>Collector</span>
        <div class="si-dashboard__collector-strip-items">
          <span><em>接收</em>{{ collectorStats.totalReceivedRequests ?? 0 }}</span>
          <span><em>Span</em>{{ collectorStats.totalReceivedSpans ?? 0 }}</span>
          <span :class="(collectorStats.successRate ?? 100) < 90 ? 'text-danger' : ''">
            <em>成功</em>{{ collectorStats.successRate ?? 100 }}%
          </span>
          <span><em>运行</em>{{ collectorStats.runningHours ?? 0 }}h</span>
        </div>
      </section>

      <!-- 诊断：慢服务 / 错服务 → 一键进已筛 Trace -->
      <section class="si-dashboard__diag" aria-label="慢请求与错误服务">
        <div class="si-dashboard__diag-panel">
          <div class="si-dashboard__diag-head">
            <span><i class="fa fa-tachometer me-2"></i>慢服务 Top（按 p95）</span>
            <button type="button" class="btn btn-sm btn-outline-secondary py-0" @click="goSlowTraces()">看慢链路</button>
          </div>
          <div v-if="slowServices.length === 0" class="si-dashboard__diag-empty">暂无延迟数据</div>
          <div v-else class="table-responsive si-dashboard__diag-scroll">
            <table class="table table-hover table-sm mb-0">
              <thead class="table-light">
                <tr>
                  <th>服务</th>
                  <th>p50</th>
                  <th>p95</th>
                  <th>Span</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in slowServices"
                  :key="'slow-' + row.serviceName"
                  class="si-dashboard__diag-row"
                  @click="goServiceSlow(row)"
                >
                  <td class="text-truncate" style="max-width: 9rem" :title="row.serviceName">{{ row.serviceName }}</td>
                  <td>{{ formatMs(row.p50Ms) }}</td>
                  <td :class="row.p95Ms >= 1000 ? 'text-danger fw-bold' : row.p95Ms >= 500 ? 'text-warning' : ''">
                    {{ formatMs(row.p95Ms) }}
                  </td>
                  <td class="text-muted">{{ row.spanCount }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="si-dashboard__diag-panel">
          <div class="si-dashboard__diag-head">
            <span><i class="fa fa-exclamation-triangle me-2"></i>错服务 Top</span>
            <button type="button" class="btn btn-sm btn-outline-danger py-0" @click="goErrors">错误分析</button>
          </div>
          <div v-if="!errorAnalysis || errorAnalysis.length === 0" class="si-dashboard__diag-empty">最近窗口无异常服务</div>
          <div v-else class="table-responsive si-dashboard__diag-scroll">
            <table class="table table-hover table-sm mb-0">
              <thead class="table-light">
                <tr>
                  <th>服务</th>
                  <th>调用</th>
                  <th>错误</th>
                  <th>错误率</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="error in errorAnalysis.slice(0, 8)"
                  :key="'err-' + error.serviceName"
                  class="si-dashboard__diag-row"
                  :class="{ 'table-danger': error.errorRate > 10, 'table-warning': error.errorRate <= 10 && error.errorRate > 5 }"
                  @click="goServiceErrors(error.serviceName)"
                >
                  <td class="text-truncate" style="max-width: 9rem" :title="error.serviceName">{{ error.serviceName }}</td>
                  <td>{{ error.totalCalls }}</td>
                  <td>{{ error.errorCalls }}</td>
                  <td>{{ error.errorRate.toFixed(1) }}%</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- 主体：拓扑主视图 + 排名辅栏 -->
      <section class="si-dashboard__body">
        <div class="chart-container si-dashboard__panel si-dashboard__chart-topology si-dashboard__hero">
          <div class="si-dashboard__chart-head">
            <div>
              <h6 class="si-dashboard__panel-title mb-0">
                <i class="fa fa-project-diagram me-2"></i>主视图 · 服务依赖拓扑
              </h6>
              <p class="si-dashboard__panel-desc mb-0">箭头指向被调用方 · 最近 24 小时</p>
            </div>
            <div class="d-flex align-items-center gap-2">
              <button type="button" class="btn btn-sm btn-outline-secondary" @click="goTopology">
                完整拓扑
              </button>
              <button type="button" class="btn btn-sm btn-outline-primary" @click="refreshTopologyChart">
                <i class="fa fa-refresh"></i>
              </button>
            </div>
          </div>
          <div id="topology-chart" class="si-dashboard__chart-canvas"></div>
        </div>

        <aside class="si-dashboard__rail">
          <div class="chart-container si-dashboard__panel si-dashboard__chart-rank">
            <div class="si-dashboard__chart-head">
              <div>
                <h6 class="si-dashboard__panel-title mb-0">
                  <i class="fa fa-chart-bar me-2"></i>辅栏 · 请求排名
                </h6>
                <p class="si-dashboard__panel-desc mb-0">按 Span 量 Top · 点击柱可筛链路</p>
              </div>
              <button type="button" class="btn btn-sm btn-outline-primary" @click="refreshServiceRankChart">
                <i class="fa fa-refresh"></i>
              </button>
            </div>
            <div id="service-rank-chart" class="si-dashboard__chart-canvas"></div>
            <button type="button" class="btn btn-sm btn-outline-secondary w-100 mt-2" @click="goTraces()">
              查看链路列表
            </button>
          </div>
        </aside>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ApiService } from '../services/ApiService'
import { buildTopologyOption } from '../utils/topologyGraph'
import { formatDuration } from '../utils/traceTimeline'

const router = useRouter()

const loading = ref(true)
const currentTime = ref('')
const services = ref<string[]>([])
const dependencies = ref<any[]>([])
const serviceStats = ref<any[]>([])
const serviceLatency = ref<any[]>([])
const errorAnalysis = ref<any[]>([])
const collectorStats = ref<any>({})
const totalSpans = ref(0)

let topologyChart: echarts.ECharts | null = null
let serviceRankChart: echarts.ECharts | null = null
let timeInterval: number | null = null

const slowServices = computed(() =>
  [...serviceLatency.value]
    .sort((a, b) => (b.p95Ms || 0) - (a.p95Ms || 0))
    .slice(0, 8)
)

const formatMs = (ms: number) => formatDuration(Number(ms) || 0)

const stats = computed(() => [
  {
    title: '监控服务',
    value: `${services.value.length} 个`,
    icon: 'fa-server',
    color: 'primary',
    hint: '进入链路筛选',
    to: '/traces'
  },
  {
    title: '链路总数',
    value: `${totalSpans.value} 条`,
    icon: 'fa-stream',
    color: 'success',
    hint: '最近上报 Span',
    to: '/traces'
  },
  {
    title: '依赖关系',
    value: `${dependencies.value.length} 条`,
    icon: 'fa-project-diagram',
    color: 'info',
    hint: '打开拓扑图',
    to: '/topology'
  },
  {
    title: '异常服务',
    value: `${errorAnalysis.value.length} 个`,
    icon: 'fa-exclamation-triangle',
    color: 'warning',
    hint: '错误分析',
    to: '/error-analysis'
  }
])

const updateCurrentTime = () => {
  currentTime.value = new Date().toTimeString().split(' ')[0]
}

const onKpiClick = (to: string) => {
  router.push(to)
}

const goTopology = () => router.push('/topology')
const goTraces = (query: Record<string, string> = {}) => router.push({ path: '/traces', query })
const goErrors = () => router.push('/error-analysis')

const goSlowTraces = () => goTraces({ minDurationMs: '500' })

const goServiceSlow = (row: { serviceName: string; p50Ms?: number }) => {
  const minDur = Math.max(100, Math.floor(Number(row.p50Ms) || 100))
  goTraces({ service: row.serviceName, minDurationMs: String(minDur) })
}

const goServiceErrors = (serviceName: string) => {
  goTraces({ service: serviceName, status: 'error' })
}

const initCharts = () => {
  const topologyChartDom = document.getElementById('topology-chart')
  if (topologyChartDom) {
    topologyChart = echarts.init(topologyChartDom)
    topologyChart.setOption(buildTopologyOption([], { compact: false }))
  }

  const serviceRankChartDom = document.getElementById('service-rank-chart')
  if (serviceRankChartDom) {
    serviceRankChart = echarts.init(serviceRankChartDom)
    serviceRankChart.setOption({
      backgroundColor: 'transparent',
      textStyle: { color: '#6b7f76' },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        backgroundColor: 'rgba(255, 252, 250, 0.96)',
        borderColor: 'rgba(20, 83, 45, 0.15)',
        textStyle: { color: '#15241f' },
        formatter: (params: any) => {
          const data = params[0]
          return `${data.name}<br/>请求数量: ${data.value} 条`
        }
      },
      grid: { left: 8, right: 28, bottom: 8, top: 12, containLabel: true },
      xAxis: {
        type: 'value',
        name: '请求',
        nameTextStyle: { fontSize: 10, color: '#6b7f76' },
        axisLine: { lineStyle: { color: 'rgba(20, 83, 45, 0.25)' } },
        splitLine: { lineStyle: { color: 'rgba(20, 83, 45, 0.1)' } },
        axisLabel: { fontSize: 10, color: '#6b7f76' }
      },
      yAxis: {
        type: 'category',
        data: [],
        axisLine: { lineStyle: { color: 'rgba(20, 83, 45, 0.25)' } },
        axisLabel: {
          fontSize: 11,
          color: '#15241f',
          width: 100,
          overflow: 'truncate',
          ellipsis: '…'
        }
      },
      series: [{
        name: '请求数量',
        type: 'bar',
        data: [],
        barMaxWidth: 18,
        itemStyle: {
          color: (params: any) => {
            const colorList = ['#0f766e', '#15803d', '#0d9488', '#b45309', '#b91c1c', '#1d4ed8', '#c2410c', '#047857']
            return colorList[params.dataIndex % colorList.length]
          },
          borderRadius: [0, 4, 4, 0]
        },
        label: { show: true, position: 'right', formatter: '{c}', fontSize: 10, color: '#3d524a' }
      }]
    })
    serviceRankChart.on('click', (params: any) => {
      const name = params?.name
      if (typeof name === 'string' && name) {
        goTraces({ service: name })
      }
    })
  }
}

const updateCharts = () => {
  if (topologyChart) {
    topologyChart.setOption(buildTopologyOption(dependencies.value, { compact: false }), { notMerge: true })
  }

  if (serviceRankChart) {
    const serviceNames: string[] = []
    const callCounts: number[] = []
    const sorted = [...serviceStats.value].sort((a, b) => (b.totalSpans || 0) - (a.totalSpans || 0)).slice(0, 8)
    sorted.forEach((s: any) => {
      serviceNames.push(s.serviceName)
      callCounts.push(s.totalSpans || 0)
    })
    serviceRankChart.setOption({
      graphic: serviceNames.length === 0
        ? [{
            type: 'text',
            left: 'center',
            top: 'center',
            style: { text: '暂无排名数据', fill: '#6b7f76', fontSize: 12, textAlign: 'center' }
          }]
        : [],
      yAxis: { data: serviceNames },
      series: [{ data: callCounts }]
    })
  }
}

const refreshTopologyChart = () => {
  topologyChart?.resize()
  updateCharts()
}

const refreshServiceRankChart = () => {
  serviceRankChart?.resize()
  updateCharts()
}

const loadData = async () => {
  try {
    loading.value = true
    const [serviceNames, serviceDeps, serviceStatsData, latencyData, errorAnalysisData, collectorStatsData] = await Promise.all([
      ApiService.getServiceNames(),
      ApiService.getServiceDependencies(24),
      ApiService.getServiceStats(),
      ApiService.getServiceLatency(24, 20),
      ApiService.getErrorAnalysis(24),
      ApiService.getCollectorStats()
    ])
    services.value = serviceNames
    dependencies.value = serviceDeps
    serviceStats.value = serviceStatsData
    serviceLatency.value = latencyData
    errorAnalysis.value = errorAnalysisData
    collectorStats.value = collectorStatsData
    totalSpans.value = serviceStatsData.reduce((sum: number, s: any) => sum + (s.totalSpans || 0), 0)
    updateCharts()
  } catch (error) {
    console.error('加载仪表盘数据失败:', error)
  } finally {
    loading.value = false
    await nextTick()
    topologyChart?.resize()
    serviceRankChart?.resize()
  }
}

const handleResize = () => {
  topologyChart?.resize()
  serviceRankChart?.resize()
}

onMounted(async () => {
  await nextTick()
  initCharts()
  updateCurrentTime()
  timeInterval = window.setInterval(updateCurrentTime, 1000)
  window.addEventListener('resize', handleResize)
  await loadData()
})

onUnmounted(() => {
  topologyChart?.dispose()
  serviceRankChart?.dispose()
  if (timeInterval) clearInterval(timeInterval)
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.si-dashboard {
  flex: 1;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  overflow: hidden;
}

.si-dashboard__content {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  overflow: hidden;
}

.si-dashboard__top {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: 1fr auto auto;
  align-items: center;
  gap: 0.85rem 1.25rem;
  padding: 0.55rem 0.35rem 0.2rem;
}

.si-dashboard__title {
  font-family: var(--font-display);
  font-size: clamp(1.45rem, 2.2vw, 1.85rem);
  font-weight: 700;
  color: var(--si-ink);
  margin: 0;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.si-dashboard__title i { color: var(--si-teal); }

.si-dashboard__subtitle {
  margin: 0.2rem 0 0;
  font-size: 0.8rem;
  color: var(--si-muted);
  letter-spacing: 0.04em;
}

.si-dashboard__status { justify-self: center; }

.si-dashboard__pill {
  display: inline-flex;
  align-items: center;
  padding: 0.4rem 0.85rem;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 600;
}

.si-dashboard__pill--ok {
  background: #dcfce7;
  color: #14532d;
  border: 1px solid rgba(21, 128, 61, 0.25);
}

.si-dashboard__pill--warn {
  background: #ffedd5;
  color: #9a3412;
  border: 1px solid rgba(180, 83, 9, 0.3);
}

.si-dashboard__actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  justify-self: end;
}

.si-dashboard__btn {
  padding: 0.45rem 0.95rem;
  font-size: 0.85rem;
}

.si-dashboard__clock {
  font-size: 0.8rem;
  font-weight: 700;
  padding: 0.45rem 0.75rem;
}

.si-dashboard__loading {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--si-muted);
  font-size: 0.95rem;
}

.si-dashboard__loading i {
  color: var(--si-teal);
  font-size: 1.75rem;
}

.si-dashboard__kpis {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.75rem;
}

@media (max-width: 991px) {
  .si-dashboard__kpis { grid-template-columns: repeat(2, 1fr); }
}

.si-dashboard__kpi {
  min-height: 5.75rem;
  text-align: left;
  cursor: pointer;
  appearance: none;
  width: 100%;
  padding: 0;
  background: inherit;
}

.si-dashboard__kpi:focus-visible {
  outline: 2px solid var(--si-teal);
  outline-offset: 2px;
}

.si-dashboard__kpi :deep(.card-body) {
  padding: 1rem 1.15rem;
  height: 100%;
  display: flex;
  align-items: center;
}

.si-dashboard__kpi :deep(.card-body > .d-flex) { width: 100%; }

.si-dashboard__kpi-label {
  font-size: 0.72rem !important;
  letter-spacing: 0.08em;
  margin-bottom: 0.25rem !important;
}

.si-dashboard__kpi-value {
  font-family: var(--font-display);
  font-size: clamp(1.45rem, 2.4vw, 1.85rem);
  font-weight: 700;
  color: var(--si-ink);
  line-height: 1.15;
  letter-spacing: -0.02em;
}

.si-dashboard__kpi-hint {
  margin-top: 0.25rem;
  font-size: 0.68rem;
  color: var(--si-muted);
  font-weight: 600;
}

.si-dashboard__kpi-icon {
  font-size: 2rem !important;
  opacity: 0.88;
}

/* Collector 收成横条 */
.si-dashboard__collector-strip {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem 1.25rem;
  padding: 0.45rem 0.85rem;
  border-radius: 10px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
}

.si-dashboard__collector-strip-title {
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--si-ink-soft);
}

.si-dashboard__collector-strip-items {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem 1.1rem;
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-dashboard__collector-strip-items em {
  font-style: normal;
  font-size: 0.68rem;
  font-weight: 600;
  color: var(--si-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-right: 0.35rem;
}

/* 拓扑主、排名辅 */
.si-dashboard__body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 1fr minmax(240px, 28%);
  gap: 0.75rem;
  overflow: hidden;
}

@media (max-width: 991px) {
  .si-dashboard__body {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(220px, 1fr) minmax(180px, auto);
  }
}

.si-dashboard__rail {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  min-height: 0;
  overflow: hidden;
}

.si-dashboard__panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  margin-bottom: 0 !important;
}

.si-dashboard__hero {
  border-color: rgba(15, 118, 110, 0.22);
  box-shadow: var(--box-shadow-hover);
}

.si-dashboard__panel-title {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--si-ink);
  text-transform: none;
  letter-spacing: 0.01em;
  margin-bottom: 0.15rem;
}

.si-dashboard__panel-desc {
  font-size: 0.7rem;
  color: var(--si-muted);
  font-weight: 500;
}

.si-dashboard__chart-rank,
.si-dashboard__chart-topology {
  flex: 1;
  min-height: 0;
  height: auto !important;
  margin-bottom: 0 !important;
  padding: 0.65rem 0.85rem !important;
}

.si-dashboard__chart-head {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.45rem;
}

.si-dashboard__chart-canvas {
  flex: 1;
  min-height: 0;
  width: 100%;
}

.si-dashboard__diag {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.55rem;
  max-height: 9.5rem;
}

@media (max-width: 991px) {
  .si-dashboard__diag {
    grid-template-columns: 1fr;
    max-height: none;
  }
}

.si-dashboard__diag-panel {
  min-height: 0;
  border: 1px solid var(--card-border);
  border-radius: 10px;
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.si-dashboard__diag-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.35rem 0.7rem;
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--si-ink);
  background: rgba(15, 118, 110, 0.05);
  border-bottom: 1px solid rgba(20, 83, 45, 0.1);
}

.si-dashboard__diag-empty {
  padding: 0.85rem 0.75rem;
  font-size: 0.78rem;
  color: var(--si-muted);
  text-align: center;
}

.si-dashboard__diag-scroll {
  overflow-y: auto;
  max-height: 7.25rem;
}

.si-dashboard__diag-scroll :deep(th),
.si-dashboard__diag-scroll :deep(td) {
  padding: 0.25rem 0.5rem;
  font-size: 0.72rem;
}

.si-dashboard__diag-row {
  cursor: pointer;
}
</style>
