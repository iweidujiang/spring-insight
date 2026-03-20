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
            <button type="button" class="btn btn-sm btn-outline-primary" @click="refreshTopologyChart">
              <i class="fa fa-refresh"></i>
            </button>
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
import * as echarts from 'echarts'
import { ApiService } from '../services/ApiService'

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
    topologyChart.setOption({
      backgroundColor: 'transparent',
      textStyle: { color: '#94a3b8' },
      tooltip: {
        trigger: 'item',
        formatter: function (params: any) {
          if (params.dataType === 'node') {
            return `<div style="font-weight:bold">${params.data.name}</div>调用次数: ${params.data.value}`
          }
          return `<div style="font-weight:bold">${params.data.source} → ${params.data.target}</div>调用次数: ${params.data.value}`
        }
      },
      animationDurationUpdate: 1500,
      animationEasingUpdate: 'quinticInOut',
      series: [{
        type: 'graph',
        layout: 'force',
        data: [],
        links: [],
        roam: true,
        label: {
          show: true,
          position: 'right',
          formatter: '{b}',
          fontSize: 11,
          color: '#e2e8f0'
        },
        lineStyle: { color: 'source', curveness: 0.3, width: 2 },
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 4 },
          itemStyle: { shadowBlur: 10, shadowColor: 'rgba(59, 130, 246, 0.5)' }
        },
        force: { repulsion: 1500, edgeLength: 200, gravity: 0.1 }
      }]
    })
  }

  const serviceRankChartDom = document.getElementById('service-rank-chart')
  if (serviceRankChartDom) {
    serviceRankChart = echarts.init(serviceRankChartDom)
    serviceRankChart.setOption({
      backgroundColor: 'transparent',
      textStyle: { color: '#94a3b8' },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: function (params: any) {
          const data = params[0]
          return `${data.name}<br/>请求数量: ${data.value} 条`
        }
      },
      grid: { left: '3%', right: '8%', bottom: '4%', top: '8%', containLabel: true },
      xAxis: {
        type: 'value',
        name: '请求',
        nameTextStyle: { fontSize: 10, color: '#94a3b8' },
        axisLine: { lineStyle: { color: '#334155' } },
        splitLine: { lineStyle: { color: 'rgba(51, 65, 85, 0.45)' } },
        axisLabel: { fontSize: 10, color: '#94a3b8' }
      },
      yAxis: {
        type: 'category',
        data: [],
        axisLine: { lineStyle: { color: '#334155' } },
        axisLabel: { fontSize: 10, color: '#cbd5e1', width: 72, overflow: 'truncate' }
      },
      series: [{
        name: '请求数量',
        type: 'bar',
        data: [],
        itemStyle: {
          color: function (params: any) {
            const colorList = ['#38bdf8', '#34d399', '#22d3ee', '#fbbf24', '#f87171', '#a78bfa', '#fb923c', '#2dd4bf']
            return colorList[params.dataIndex % colorList.length]
          },
          borderRadius: [0, 4, 4, 0]
        },
        label: { show: true, position: 'right', formatter: '{c}', fontSize: 10, color: '#e2e8f0' }
      }]
    })
  }
}

const updateCharts = () => {
  if (topologyChart) {
    const nodes = new Map()
    const links: any[] = []
    const serviceCallCounts = new Map()
    dependencies.value.forEach((dep: any) => {
      serviceCallCounts.set(dep.sourceService, (serviceCallCounts.get(dep.sourceService) || 0) + dep.callCount)
      serviceCallCounts.set(dep.targetService, (serviceCallCounts.get(dep.targetService) || 0) + dep.callCount)
      links.push({ source: dep.sourceService, target: dep.targetService, value: dep.callCount })
    })
    serviceCallCounts.forEach((value, key) => {
      nodes.set(key, {
        name: key,
        value,
        symbolSize: Math.max(28, Math.min(56, 10 + Math.sqrt(value) * 5))
      })
    })
    const nodeList = Array.from(nodes.values())
    topologyChart.setOption({
      graphic: nodeList.length === 0
        ? [{
            type: 'text',
            left: 'center',
            top: 'center',
            style: {
              text: '暂无依赖拓扑\n产生跨服务调用后将显示',
              fill: '#94a3b8',
              fontSize: 12,
              textAlign: 'center',
              lineHeight: 20
            }
          }]
        : [],
      series: [{ data: nodeList, links }]
    })
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
            style: { text: '暂无排名数据', fill: '#94a3b8', fontSize: 12, textAlign: 'center' }
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
  console.log('查看服务详情:', serviceName)
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
  gap: 0.35rem;
  overflow: hidden;
}

.si-dashboard__content {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  overflow: hidden;
}

.si-dashboard__top {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: 1fr auto auto;
  align-items: center;
  gap: 0.75rem 1rem;
  padding: 0.15rem 0.25rem;
}

.si-dashboard__title {
  font-size: clamp(1.1rem, 1.5vw, 1.35rem);
  font-weight: 800;
  color: #f8fafc;
  margin: 0;
  letter-spacing: 0.02em;
  text-shadow: 0 0 20px rgba(34, 211, 238, 0.2);
}

.si-dashboard__title i {
  color: #22d3ee;
}

.si-dashboard__subtitle {
  margin: 0;
  font-size: 0.72rem;
  color: #94a3b8;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.si-dashboard__status {
  justify-self: center;
}

.si-dashboard__pill {
  display: inline-flex;
  align-items: center;
  padding: 0.25rem 0.65rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
}

.si-dashboard__pill--ok {
  background: rgba(52, 211, 153, 0.15);
  color: #6ee7b7;
  border: 1px solid rgba(52, 211, 153, 0.35);
}

.si-dashboard__pill--warn {
  background: rgba(251, 191, 36, 0.12);
  color: #fcd34d;
  border: 1px solid rgba(251, 191, 36, 0.4);
}

.si-dashboard__actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  justify-self: end;
}

.si-dashboard__btn {
  padding: 0.35rem 0.75rem;
  font-size: 0.8rem;
}

.si-dashboard__clock {
  font-size: 0.75rem;
  font-weight: 700;
}

.si-dashboard__loading {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #cbd5e1;
  font-size: 0.95rem;
}

.si-dashboard__loading i {
  color: #22d3ee;
  font-size: 1.75rem;
}

.si-dashboard__kpis {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.5rem;
}

@media (max-width: 991px) {
  .si-dashboard__kpis {
    grid-template-columns: repeat(2, 1fr);
  }
}

.si-dashboard__kpi :deep(.card-body) {
  padding: 0.5rem 0.75rem;
}

.si-dashboard__kpi-label {
  font-size: 0.65rem !important;
  letter-spacing: 0.06em;
}

.si-dashboard__kpi-value {
  font-size: clamp(1rem, 1.8vw, 1.25rem);
  font-weight: 800;
  color: #f8fafc;
  line-height: 1.2;
  margin-top: 0.15rem;
}

.si-dashboard__kpi-icon {
  font-size: 1.35rem !important;
  opacity: 0.9;
}

.si-dashboard__body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(200px, 15.5rem) 1fr;
  gap: 0.5rem;
  overflow: hidden;
}

@media (max-width: 991px) {
  .si-dashboard__body {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(140px, auto) minmax(200px, 1fr);
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

.si-dashboard__collector :deep(.card-body) {
  padding: 0.55rem 0.7rem;
}

.si-dashboard__panel-title {
  font-size: 0.78rem;
  font-weight: 700;
  color: #e2e8f0;
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
  background: rgba(15, 23, 42, 0.5);
  border-radius: 6px;
  border: 1px solid rgba(56, 189, 248, 0.12);
}

.si-dashboard__metric-label {
  font-size: 0.62rem;
  color: #94a3b8;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.si-dashboard__metric-val {
  font-size: 0.95rem;
  font-weight: 800;
  color: #f1f5f9;
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
  border: 1px solid rgba(248, 113, 113, 0.25);
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.75);
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
  color: #fecaca;
  background: rgba(127, 29, 29, 0.25);
  border-bottom: 1px solid rgba(248, 113, 113, 0.2);
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
