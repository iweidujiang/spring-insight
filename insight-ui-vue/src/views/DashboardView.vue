<template>
  <div class="si-dashboard fade-in">
    <!-- 顶栏：标题 + 健康摘要 + 操作 -->
    <header class="si-dashboard__top">
      <div class="si-dashboard__title-block">
        <h2 class="si-dashboard__title">
          <i class="fa fa-tachometer-alt me-2"></i>监控仪表盘
        </h2>
        <p class="si-dashboard__subtitle">实时监控 · Spring Boot 架构</p>
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
      <!-- KPI 四宫格 -->
      <section class="si-dashboard__kpis">
        <div
          v-for="(stat, index) in stats"
          :key="index"
          class="card stat-card si-dashboard__kpi"
          :style="{ animationDelay: `${index * 0.06}s` }"
        >
          <div class="card-body">
            <div class="d-flex align-items-center justify-content-between">
              <div>
                <div class="text-xs font-weight-bold" :class="`text-${stat.color} text-uppercase mb-0 si-dashboard__kpi-label`">
                  {{ stat.title }}
                </div>
                <div class="si-dashboard__kpi-value">{{ stat.value }}</div>
              </div>
              <i :class="`fa ${stat.icon} si-dashboard__kpi-icon text-${stat.color}`"></i>
            </div>
          </div>
        </div>
      </section>

      <!-- 主体：左侧 Collector + 排名 | 中间拓扑 -->
      <section class="si-dashboard__body">
        <aside class="si-dashboard__rail">
          <div v-if="collectorStats" class="card stat-card si-dashboard__panel si-dashboard__collector">
            <div class="card-body">
              <h6 class="si-dashboard__panel-title">
                <i class="fa fa-database me-2"></i>Collector
              </h6>
              <div class="si-dashboard__collector-grid">
                <div class="si-dashboard__metric">
                  <span class="si-dashboard__metric-label">接收请求</span>
                  <span class="si-dashboard__metric-val">{{ collectorStats.totalReceivedRequests ?? 0 }}</span>
                </div>
                <div class="si-dashboard__metric">
                  <span class="si-dashboard__metric-label">总 Span</span>
                  <span class="si-dashboard__metric-val">{{ collectorStats.totalReceivedSpans ?? 0 }}</span>
                </div>
                <div class="si-dashboard__metric">
                  <span class="si-dashboard__metric-label">成功率</span>
                  <span
                    class="si-dashboard__metric-val"
                    :class="(collectorStats.successRate ?? 100) < 90 ? 'text-danger' : 'text-success'"
                  >{{ collectorStats.successRate ?? 100 }}%</span>
                </div>
                <div class="si-dashboard__metric">
                  <span class="si-dashboard__metric-label">运行</span>
                  <span class="si-dashboard__metric-val">{{ collectorStats.runningHours ?? 0 }}h</span>
                </div>
              </div>
            </div>
          </div>

          <div class="chart-container si-dashboard__panel si-dashboard__chart-rank">
            <div class="si-dashboard__chart-head">
              <h6 class="si-dashboard__panel-title mb-0">
                <i class="fa fa-chart-bar me-2"></i>请求排名
              </h6>
              <button type="button" class="btn btn-sm btn-outline-primary" @click="refreshServiceRankChart">
                <i class="fa fa-refresh"></i>
              </button>
            </div>
            <div id="service-rank-chart" class="si-dashboard__chart-canvas"></div>
          </div>
        </aside>

        <div class="chart-container si-dashboard__panel si-dashboard__chart-topology">
          <div class="si-dashboard__chart-head">
            <h6 class="si-dashboard__panel-title mb-0">
              <i class="fa fa-project-diagram me-2"></i>服务依赖拓扑
            </h6>
            <div class="d-flex align-items-center gap-2">
              <span class="si-dashboard__topo-hint">箭头指向被调用方</span>
              <button type="button" class="btn btn-sm btn-outline-primary" @click="refreshTopologyChart">
                <i class="fa fa-refresh"></i>
              </button>
            </div>
          </div>
          <div id="topology-chart" class="si-dashboard__chart-canvas"></div>
        </div>
      </section>

      <!-- 异常：窄条，内部滚动，不占满屏 -->
      <section v-if="errorAnalysis && errorAnalysis.length > 0" class="si-dashboard__alerts">
        <div class="si-dashboard__alerts-head">
          <span><i class="fa fa-exclamation-triangle me-2"></i>异常服务</span>
          <span class="badge bg-danger">{{ errorAnalysis.length }}</span>
        </div>
        <div class="table-responsive si-dashboard__alerts-scroll">
          <table class="table table-hover table-sm mb-0">
            <thead class="table-light">
              <tr>
                <th>服务</th>
                <th>调用</th>
                <th>错误</th>
                <th>错误率</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="error in errorAnalysis"
                :key="error.serviceName"
                :class="{ 'table-danger': error.errorRate > 10, 'table-warning': error.errorRate <= 10 && error.errorRate > 5 }"
              >
                <td>{{ error.serviceName }}</td>
                <td>{{ error.totalCalls }}</td>
                <td>{{ error.errorCalls }}</td>
                <td>{{ error.errorRate.toFixed(1) }}%</td>
                <td>
                  <button type="button" class="btn btn-sm btn-outline-primary py-0" @click="viewServiceDetails(error.serviceName)">查看</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
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

const router = useRouter()

const loading = ref(true)
const currentTime = ref('')
const services = ref<string[]>([])
const dependencies = ref<any[]>([])
const serviceStats = ref<any[]>([])
const errorAnalysis = ref<any[]>([])
const collectorStats = ref<any>({})
const totalSpans = ref(0)

let topologyChart: echarts.ECharts | null = null
let serviceRankChart: echarts.ECharts | null = null
let timeInterval: number | null = null

const stats = computed(() => [
  { title: '监控服务', value: `${services.value.length} 个`, icon: 'fa-server', color: 'primary' },
  { title: '链路总数', value: `${totalSpans.value} 条`, icon: 'fa-stream', color: 'success' },
  { title: '依赖关系', value: `${dependencies.value.length} 条`, icon: 'fa-project-diagram', color: 'info' },
  { title: '异常服务', value: `${errorAnalysis.value.length} 个`, icon: 'fa-exclamation-triangle', color: 'warning' }
])

const updateCurrentTime = () => {
  currentTime.value = new Date().toTimeString().split(' ')[0]
}

const initCharts = () => {
  const topologyChartDom = document.getElementById('topology-chart')
  if (topologyChartDom) {
    topologyChart = echarts.init(topologyChartDom)
    topologyChart.setOption(buildTopologyOption([], { compact: true }))
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
          width: 110,
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
  }
}

const updateCharts = () => {
  if (topologyChart) {
    topologyChart.setOption(buildTopologyOption(dependencies.value, { compact: true }), { notMerge: true })
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

const viewServiceDetails = (serviceName: string) => {
  router.push({ path: '/traces', query: { service: serviceName } })
}

const loadData = async () => {
  try {
    loading.value = true
    const [serviceNames, serviceDeps, serviceStatsData, errorAnalysisData, collectorStatsData] = await Promise.all([
      ApiService.getServiceNames(),
      ApiService.getServiceDependencies(24),
      ApiService.getServiceStats(),
      ApiService.getErrorAnalysis(24),
      ApiService.getCollectorStats()
    ])
    services.value = serviceNames
    dependencies.value = serviceDeps
    serviceStats.value = serviceStatsData
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
  gap: 0.65rem;
  overflow: hidden;
}

.si-dashboard__top {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: 1fr auto auto;
  align-items: center;
  gap: 0.85rem 1.25rem;
  padding: 0.55rem 0.35rem 0.35rem;
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

.si-dashboard__title i {
  color: var(--si-teal);
}

.si-dashboard__subtitle {
  margin: 0.2rem 0 0;
  font-size: 0.8rem;
  color: var(--si-muted);
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.si-dashboard__status {
  justify-self: center;
}

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
  .si-dashboard__kpis {
    grid-template-columns: repeat(2, 1fr);
  }
}

.si-dashboard__kpi {
  min-height: 5.75rem;
}

.si-dashboard__kpi :deep(.card-body) {
  padding: 1rem 1.15rem;
  height: 100%;
  display: flex;
  align-items: center;
}

.si-dashboard__kpi :deep(.card-body > .d-flex) {
  width: 100%;
}

.si-dashboard__kpi-label {
  font-size: 0.72rem !important;
  letter-spacing: 0.08em;
  margin-bottom: 0.35rem !important;
}

.si-dashboard__kpi-value {
  font-family: var(--font-display);
  font-size: clamp(1.45rem, 2.4vw, 1.85rem);
  font-weight: 700;
  color: var(--si-ink);
  line-height: 1.15;
  margin-top: 0.1rem;
  letter-spacing: -0.02em;
}

.si-dashboard__kpi-icon {
  font-size: 2rem !important;
  opacity: 0.88;
}

.si-dashboard__body {
  flex: 1;
  min-height: 0;
  display: grid;
  /* 左侧请求排名加宽，右侧拓扑占剩余空间 */
  grid-template-columns: minmax(280px, 34%) 1fr;
  gap: 0.65rem;
  overflow: hidden;
}

@media (max-width: 991px) {
  .si-dashboard__body {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(180px, auto) minmax(220px, 1fr);
  }
}

.si-dashboard__topo-hint {
  font-size: 0.65rem;
  color: var(--si-muted);
  font-weight: 500;
  letter-spacing: 0.02em;
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

.si-dashboard__collector :deep(.card-body) {
  padding: 0.55rem 0.7rem;
}

.si-dashboard__panel-title {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--si-ink-soft);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin-bottom: 0.45rem;
}

.si-dashboard__collector-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.35rem 0.5rem;
}

.si-dashboard__metric {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  padding: 0.25rem 0.35rem;
  background: var(--si-paper);
  border-radius: 6px;
  border: 1px solid var(--card-border);
}

.si-dashboard__metric-label {
  font-size: 0.62rem;
  color: var(--si-muted);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.si-dashboard__metric-val {
  font-size: 0.95rem;
  font-weight: 800;
  color: var(--si-ink);
}

.si-dashboard__chart-rank,
.si-dashboard__chart-topology {
  flex: 1;
  min-height: 0;
  height: auto !important;
  margin-bottom: 0 !important;
  padding: 0.5rem 0.65rem !important;
}

.si-dashboard__rail .si-dashboard__chart-rank {
  flex: 1;
}

.si-dashboard__chart-topology {
  min-height: 0;
}

.si-dashboard__chart-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.35rem;
}

.si-dashboard__chart-canvas {
  flex: 1;
  min-height: 0;
  width: 100%;
}

.si-dashboard__alerts {
  flex-shrink: 0;
  max-height: 5.5rem;
  border: 1px solid rgba(185, 28, 28, 0.2);
  border-radius: 10px;
  background: #fff5f5;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.si-dashboard__alerts-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.25rem 0.6rem;
  font-size: 0.72rem;
  font-weight: 700;
  color: #7f1d1d;
  background: rgba(185, 28, 28, 0.08);
  border-bottom: 1px solid rgba(185, 28, 28, 0.15);
}

.si-dashboard__alerts-scroll {
  overflow-y: auto;
  max-height: 3.6rem;
}

.si-dashboard__alerts-scroll :deep(th),
.si-dashboard__alerts-scroll :deep(td) {
  padding: 0.25rem 0.5rem;
  font-size: 0.72rem;
}
</style>
