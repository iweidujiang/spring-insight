<template>
  <div class="si-dashboard fade-in">
    <header class="si-dashboard__top">
      <div class="si-dashboard__title-block">
        <h2 class="si-dashboard__title">
          <i class="fa fa-tachometer me-2"></i>监控仪表盘
        </h2>
        <p class="si-dashboard__subtitle">
          {{ isSolo ? '单应用 · 请求、延迟与错误' : isMesh ? '多服务 · 拓扑为主' : '多服务 · 等待调用边' }}
        </p>
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
        <label class="si-dashboard__hours visually-hidden" for="dashboard-hours">时间范围</label>
        <select id="dashboard-hours" class="form-select form-select-sm si-dashboard__hours" v-model.number="hours" @change="loadData">
          <option v-for="opt in TIME_RANGE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
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

    <div v-show="!loading" class="si-dashboard__content" :class="{ 'si-dashboard__content--mesh': isMesh }">
      <!-- KPI：多服务有边时收成细条，把高度留给拓扑 -->
      <section
        class="si-dashboard__kpis"
        :class="{ 'si-dashboard__kpis--compact': isMesh || isSolo }"
        aria-label="关键指标"
      >
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
                <div v-if="!isMesh && !isSolo" class="si-dashboard__kpi-hint">{{ stat.hint }}</div>
              </div>
              <i v-if="!isMesh" :class="`fa ${stat.icon} si-dashboard__kpi-icon text-${stat.color}`"></i>
            </div>
          </div>
        </button>
      </section>

      <section
        v-if="collectorStats && !isMesh"
        class="si-dashboard__collector-strip"
        aria-label="采集器状态"
      >
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

      <section v-if="isSolo" class="si-app" aria-label="单应用概览">
        <div class="si-app__head">
          <div>
            <p class="si-app__kicker">当前服务</p>
            <h3 class="si-app__name">{{ soloProfile.name }}</h3>
          </div>
          <button type="button" class="btn btn-sm btn-outline-secondary" @click="goTraces()">全部链路</button>
        </div>

        <div class="si-app__metrics">
          <div class="si-app__metric">
            <span>请求</span>
            <strong>{{ soloProfile.spanCount }}</strong>
          </div>
          <div class="si-app__metric">
            <span>平均</span>
            <strong>{{ formatMs(soloProfile.avgMs) }}</strong>
          </div>
          <div class="si-app__metric">
            <span>p95 <PercentileHelp /></span>
            <strong :class="soloProfile.p95Ms >= 1000 ? 'text-danger' : soloProfile.p95Ms >= 500 ? 'text-warning' : ''">
              {{ formatMs(soloProfile.p95Ms) }}
            </strong>
          </div>
          <div class="si-app__metric">
            <span>错误</span>
            <strong :class="soloProfile.errorCount > 0 ? 'text-danger' : ''">{{ soloProfile.errorCount }}</strong>
          </div>
        </div>

        <div class="si-app__traces">
          <div class="si-app__traces-head">
            <h6 class="mb-0">最近请求</h6>
            <span class="si-app__traces-hint">{{ hoursLabel }}</span>
          </div>
          <div v-if="recentTraces.length === 0" class="si-dashboard__diag-empty">{{ emptyRequestsMessage(hours) }}</div>
          <ul v-else class="si-app__list">
            <li
              v-for="tr in recentTraces"
              :key="tr.traceId"
              class="si-app__row"
              @click="goTraceDetail(tr.traceId)"
            >
              <span class="si-app__method" :data-method="traceMethod(tr)">{{ traceMethod(tr) || 'REQ' }}</span>
              <span class="si-app__path" :title="tracePath(tr)">{{ tracePath(tr) }}</span>
              <span class="si-app__dur">
                <span class="si-app__track">
                  <i class="si-app__bar" :style="{ width: durationShare(tr) + '%' }"></i>
                </span>
                <em>{{ formatMs(tr.durationMs || 0) }}</em>
              </span>
              <span class="badge" :class="traceFailed(tr) ? 'bg-danger' : 'bg-success'">
                {{ traceFailed(tr) ? '失败' : '成功' }}
              </span>
            </li>
          </ul>
        </div>
      </section>

      <!-- 多服务尚无调用边：矮诊断条 + 提示 -->
      <section v-if="isWaitingEdges" class="si-dashboard__diag si-dashboard__diag--slim" aria-label="服务健康">
        <div class="si-dashboard__diag-panel">
          <div class="si-dashboard__diag-head">
            <span><i class="fa fa-heartbeat me-2"></i>服务健康快照</span>
            <button type="button" class="btn btn-sm btn-outline-secondary py-0" @click="goSlowTraces()">看慢链路</button>
          </div>
          <div v-if="healthSnapshot.length > 0" class="table-responsive si-dashboard__diag-scroll">
            <table class="table table-hover mb-0 si-dashboard__diag-table">
              <thead class="table-light">
                <tr>
                  <th>服务</th>
                  <th>均耗时</th>
                  <th>p95</th>
                  <th>错误</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in healthSnapshot"
                  :key="'health-' + row.serviceName"
                  class="si-dashboard__diag-row"
                  @click="goServiceSlow(row)"
                >
                  <td class="text-truncate" style="max-width: 9rem" :title="row.serviceName">{{ row.serviceName }}</td>
                  <td>{{ formatMs(row.avgMs) }}</td>
                  <td :class="row.p95Ms >= 1000 ? 'text-danger fw-bold' : row.p95Ms >= 500 ? 'text-warning' : ''">
                    {{ formatMs(row.p95Ms) }}
                  </td>
                  <td :class="(row.errorCount || 0) > 0 ? 'text-danger' : 'text-muted'">
                    {{ row.errorCount || 0 }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="si-dashboard__diag-empty si-dashboard__diag-empty--rich">
            <p class="mb-1">{{ emptyLatencySampleMessage(hours) }}</p>
            <button type="button" class="btn btn-sm btn-outline-primary" @click="hours = 0; loadData()">扩大到全部已存</button>
          </div>
        </div>
        <div class="si-dashboard__diag-panel">
          <div class="si-dashboard__diag-head">
            <span><i class="fa fa-lightbulb-o me-2"></i>怎样出现调用边</span>
            <button type="button" class="btn btn-sm btn-outline-secondary py-0" @click="goTopology()">打开拓扑</button>
          </div>
          <ul class="si-dashboard__edge-tips">
            <li>出站走 OpenFeign、<code>WebClient.Builder</code>、Gateway 或 <code>RestTemplateBuilder</code></li>
            <li>Agent 写入 <code>remoteService</code> 后，刷新即可在主图看到「调用方 → 被调用方」</li>
          </ul>
          <div v-if="errorAnalysis && errorAnalysis.length > 0" class="si-dashboard__diag-foot">
            <button type="button" class="btn btn-sm btn-outline-danger py-0" @click="goErrors">
              {{ errorAnalysis.length }} 个异常服务 · 去错误分析
            </button>
          </div>
        </div>
      </section>

      <!-- 有调用边：拓扑主舞台 + 窄辅栏 -->
      <section v-if="isMesh" class="si-dashboard__body si-dashboard__body--mesh">
        <div class="chart-container si-dashboard__panel si-dashboard__chart-topology si-dashboard__hero">
          <div class="si-dashboard__chart-head">
            <div>
              <h6 class="si-dashboard__panel-title mb-0">
                <i class="fa fa-sitemap me-2"></i>服务依赖拓扑
              </h6>
              <p class="si-dashboard__panel-desc mb-0">
                箭头指向被调用方 · 点击节点 / 边下钻链路 · {{ hoursLabel }}
                <span v-if="collectorStats" class="si-dashboard__inline-meta">
                  · Collector {{ collectorStats.totalReceivedSpans ?? 0 }} Span
                </span>
              </p>
            </div>
            <div class="d-flex align-items-center gap-2">
              <button type="button" class="btn btn-sm btn-outline-secondary" @click="goTopology">完整拓扑</button>
              <button type="button" class="btn btn-sm btn-outline-primary" @click="refreshTopologyChart">
                <i class="fa fa-refresh"></i>
              </button>
            </div>
          </div>
          <div class="si-dashboard__hero-body">
            <div id="topology-chart" class="si-dashboard__chart-canvas"></div>
          </div>
        </div>

        <aside class="si-dashboard__rail">
          <div class="si-dashboard__rail-card">
            <div class="si-dashboard__rail-head">
              <span>慢服务 <PercentileHelp /></span>
              <button type="button" class="btn btn-sm btn-link py-0" @click="goSlowTraces()">更多</button>
            </div>
            <ul v-if="slowServices.length" class="si-dashboard__rail-list">
              <li
                v-for="row in slowServices.slice(0, 5)"
                :key="'rail-slow-' + row.serviceName"
                @click="goServiceSlow(row)"
              >
                <span class="si-dashboard__rail-name" :title="row.serviceName">{{ row.serviceName }}</span>
                <em :class="row.p95Ms >= 1000 ? 'text-danger' : row.p95Ms >= 500 ? 'text-warning' : ''">
                  {{ formatMs(row.p95Ms) }}
                </em>
              </li>
            </ul>
            <div v-else class="si-dashboard__rail-empty">{{ emptyInRangeShort(hours, '延迟样本') }}</div>
          </div>

          <div class="si-dashboard__rail-card">
            <div class="si-dashboard__rail-head">
              <span>热点依赖</span>
              <button type="button" class="btn btn-sm btn-link py-0" @click="goTopology()">全部</button>
            </div>
            <ul v-if="hotDependencies.length" class="si-dashboard__rail-list">
              <li
                v-for="(dep, idx) in hotDependencies.slice(0, 5)"
                :key="'rail-hot-' + idx"
                @click="goHotDependency(dep)"
              >
                <span class="si-dashboard__rail-dep" :title="`${dep.sourceService} → ${dep.targetService}`">
                  {{ dep.sourceService }} → {{ dep.targetService }}
                </span>
                <em>{{ dep.callCount }}</em>
              </li>
            </ul>
            <div v-else class="si-dashboard__rail-empty">{{ emptyInRangeShort(hours, '依赖') }}</div>
          </div>

          <div class="chart-container si-dashboard__panel si-dashboard__chart-rank si-dashboard__rail-rank">
            <div class="si-dashboard__chart-head">
              <div>
                <h6 class="si-dashboard__panel-title mb-0">请求排名</h6>
                <p class="si-dashboard__panel-desc mb-0">按 Span · 点柱筛选</p>
              </div>
            </div>
            <div id="service-rank-chart" class="si-dashboard__chart-canvas"></div>
          </div>

          <button
            v-if="errorAnalysis && errorAnalysis.length > 0"
            type="button"
            class="btn btn-sm btn-outline-danger w-100"
            @click="goErrors"
          >
            {{ errorAnalysis.length }} 个异常服务
          </button>
        </aside>
      </section>

      <!-- 多服务无边：保留拓扑画布 + 排名 -->
      <section v-if="isWaitingEdges" class="si-dashboard__body">
        <div class="chart-container si-dashboard__panel si-dashboard__chart-topology si-dashboard__hero">
          <div class="si-dashboard__chart-head">
            <div>
              <h6 class="si-dashboard__panel-title mb-0">
                <i class="fa fa-sitemap me-2"></i>已监控服务
              </h6>
              <p class="si-dashboard__panel-desc mb-0">
                {{ services.length }} 个服务 · 暂无跨服务边 · {{ hoursLabel }}
              </p>
            </div>
            <div class="d-flex align-items-center gap-2">
              <button type="button" class="btn btn-sm btn-outline-secondary" @click="goTopology">完整拓扑</button>
              <button type="button" class="btn btn-sm btn-outline-primary" @click="refreshTopologyChart">
                <i class="fa fa-refresh"></i>
              </button>
            </div>
          </div>
          <div class="si-dashboard__hero-body">
            <div id="topology-chart" class="si-dashboard__chart-canvas"></div>
          </div>
        </div>

        <aside class="si-dashboard__rail">
          <div class="chart-container si-dashboard__panel si-dashboard__chart-rank">
            <div class="si-dashboard__chart-head">
              <div>
                <h6 class="si-dashboard__panel-title mb-0">请求排名</h6>
                <p class="si-dashboard__panel-desc mb-0">按 Span 量 Top</p>
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
import PercentileHelp from '../components/PercentileHelp.vue'
import { ApiService } from '../services/ApiService'
import { buildTopologyOption, resolveTopologyClick } from '../utils/topologyGraph'
import { formatDuration } from '../utils/traceTimeline'
import {
  TIME_RANGE_OPTIONS,
  formatHoursLabel,
  emptyRequestsMessage,
  emptyLatencySampleMessage,
  emptyInRangeShort
} from '../utils/timeRange'

const router = useRouter()

const loading = ref(true)
const hours = ref(72)
const currentTime = ref('')
const services = ref<string[]>([])
const dependencies = ref<any[]>([])
const serviceStats = ref<any[]>([])
const serviceLatency = ref<any[]>([])
const errorAnalysis = ref<any[]>([])
const collectorStats = ref<any>({})
const totalSpans = ref(0)
const recentTraces = ref<any[]>([])

let topologyChart: echarts.ECharts | null = null
let serviceRankChart: echarts.ECharts | null = null
let timeInterval: number | null = null

const hasDependencies = computed(() => dependencies.value.length > 0)
/** 只有一个应用、还没有调用边时，首页改看请求而不是空拓扑 */
const isSolo = computed(() => !hasDependencies.value && services.value.length === 1)
/** 有跨服务调用边：拓扑占主舞台 */
const isMesh = computed(() => hasDependencies.value)
/** 多个服务但还没有调用边（含尚未接入任何服务） */
const isWaitingEdges = computed(() => !isSolo.value && !isMesh.value)

const soloProfile = computed(() => {
  const name = services.value[0] || ''
  const row = serviceLatency.value.find((item: any) => item.serviceName === name) || serviceLatency.value[0]
  return {
    name: name || row?.serviceName || '未命名服务',
    spanCount: row?.spanCount || 0,
    avgMs: row?.avgMs || 0,
    p95Ms: row?.p95Ms || 0,
    errorCount: row?.errorCount || 0
  }
})

const hoursLabel = computed(() => formatHoursLabel(hours.value))

const slowServices = computed(() =>
  [...serviceLatency.value]
    .sort((a, b) => (b.p95Ms || 0) - (a.p95Ms || 0))
    .slice(0, 8)
)

const healthSnapshot = computed(() =>
  [...serviceLatency.value]
    .sort((a, b) => (b.spanCount || 0) - (a.spanCount || 0))
    .slice(0, 8)
)

const hotDependencies = computed(() =>
  [...dependencies.value]
    .sort((a, b) => (b.callCount || 0) - (a.callCount || 0))
    .slice(0, 8)
)

const formatMs = (ms: number) => formatDuration(Number(ms) || 0)

const traceFailed = (tr: any) => !!(tr?.hasError || tr?.statusCode === 'ERROR')

const traceMethod = (tr: any) => {
  const op = String(tr?.operationName || tr?.endpoint || '')
  const matched = /^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\b/i.exec(op)
  return matched ? matched[1].toUpperCase() : ''
}

const tracePath = (tr: any) => {
  const op = String(tr?.operationName || tr?.endpoint || tr?.traceId || '—')
  return op.replace(/^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\s+/i, '')
}

const durationShare = (tr: any) => {
  const max = recentTraces.value.reduce((n, item) => Math.max(n, Number(item?.durationMs) || 0), 0)
  if (max <= 0) return 8
  return Math.max(8, Math.round(((Number(tr?.durationMs) || 0) / max) * 100))
}

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
    icon: 'fa-list-ul',
    color: 'success',
    hint: '最近上报 Span',
    to: '/traces'
  },
  {
    title: '依赖关系',
    value: `${dependencies.value.length} 条`,
    icon: 'fa-sitemap',
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
const goTraceDetail = (traceId: string) => {
  if (!traceId) return
  router.push({ name: 'trace-detail', params: { traceId } })
}

const goSlowTraces = () => goTraces({ minDurationMs: '500' })

const goServiceSlow = (row: { serviceName: string; p50Ms?: number }) => {
  const minDur = Math.max(100, Math.floor(Number(row.p50Ms) || 100))
  goTraces({ service: row.serviceName, minDurationMs: String(minDur) })
}

const goHotDependency = (dep: { sourceService: string; targetService?: string }) => {
  if (!dep?.sourceService) return
  goTraces({ service: dep.sourceService })
}

const mountTopologyChart = () => {
  const topologyChartDom = document.getElementById('topology-chart')
  if (!topologyChartDom || topologyChart) return
  topologyChart = echarts.init(topologyChartDom)
  topologyChart.setOption(buildTopologyOption([], { compact: false }))
  topologyChart.on('click', (params: any) => {
    const hit = resolveTopologyClick(params)
    if (hit?.service) {
      goTraces({ service: hit.service })
    }
  })
}

const mountRankChart = () => {
  const serviceRankChartDom = document.getElementById('service-rank-chart')
  if (!serviceRankChartDom || serviceRankChart) return
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

const updateCharts = () => {
  if (topologyChart) {
    const spanByService: Record<string, number> = {}
    serviceStats.value.forEach((s: any) => {
      if (s.serviceName) spanByService[s.serviceName] = s.totalSpans || 1
    })
    topologyChart.setOption(
      buildTopologyOption(dependencies.value, {
        compact: !hasDependencies.value && services.value.length > 0,
        standaloneServices: services.value,
        spanByService
      }),
      { notMerge: true }
    )
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
    const h = hours.value
    const [serviceNames, serviceDeps, serviceStatsData, latencyData, errorAnalysisData, collectorStatsData, recent] = await Promise.all([
      ApiService.getServiceNames(),
      ApiService.getServiceDependencies(h),
      ApiService.getServiceStats(h),
      ApiService.getServiceLatency(h, 20),
      ApiService.getErrorAnalysis(h),
      ApiService.getCollectorStats(),
      ApiService.getRecentTraces({ hours: h === 0 ? 168 : h, limit: 16 })
    ])
    services.value = serviceNames
    dependencies.value = serviceDeps
    serviceStats.value = serviceStatsData
    serviceLatency.value = latencyData
    errorAnalysis.value = errorAnalysisData
    collectorStats.value = collectorStatsData
    recentTraces.value = Array.isArray(recent) ? recent : []
    totalSpans.value = serviceStatsData.reduce((sum: number, s: any) => sum + (s.totalSpans || 0), 0)
  } catch (error) {
    console.error('加载仪表盘数据失败:', error)
  } finally {
    loading.value = false
    await nextTick()
    syncCharts()
  }
}

/** 单应用不挂拓扑图；进入多服务视图后再创建，DOM 切换时先 dispose。 */
const syncCharts = async () => {
  if (isSolo.value) {
    topologyChart?.dispose()
    topologyChart = null
    serviceRankChart?.dispose()
    serviceRankChart = null
    return
  }
  // v-if 切换后旧实例可能绑在已卸载节点上
  topologyChart?.dispose()
  topologyChart = null
  serviceRankChart?.dispose()
  serviceRankChart = null
  await nextTick()
  mountTopologyChart()
  mountRankChart()
  updateCharts()
  topologyChart?.resize()
  serviceRankChart?.resize()
}

const handleResize = () => {
  topologyChart?.resize()
  serviceRankChart?.resize()
}

onMounted(async () => {
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
  width: 100%;
  min-width: 0;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  overflow: hidden;
}

.si-dashboard__content {
  flex: 1;
  width: 100%;
  min-width: 0;
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

.si-dashboard__hours {
  width: auto;
  min-width: 7.5rem;
  font-size: 0.8rem;
  border-color: rgba(20, 83, 45, 0.2);
  background: #fffcfa;
  color: #15241f;
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

.si-dashboard__content--mesh .si-dashboard__kpis--compact {
  gap: 0.45rem;
}

.si-dashboard__kpis--compact .si-dashboard__kpi {
  min-height: 0;
}

.si-dashboard__kpis--compact .si-dashboard__kpi :deep(.card-body) {
  padding: 0.55rem 0.75rem;
}

.si-dashboard__kpis--compact .si-dashboard__kpi-value {
  font-size: 1.15rem;
}

.si-dashboard__kpis--compact .si-dashboard__kpi-label {
  margin-bottom: 0.1rem !important;
}

.si-dashboard__body--mesh {
  grid-template-columns: minmax(0, 1fr) minmax(220px, 24%);
}

.si-dashboard__inline-meta {
  color: var(--si-muted);
}

.si-dashboard__diag--slim {
  max-height: 11rem;
}

.si-dashboard__edge-tips {
  margin: 0;
  padding: 0.65rem 0.85rem 0.85rem 1.35rem;
  font-size: 0.78rem;
  color: var(--si-ink-soft);
  line-height: 1.55;
}

.si-dashboard__edge-tips code {
  font-size: 0.72rem;
  color: var(--si-teal);
}

.si-dashboard__rail-card {
  flex-shrink: 0;
  border: 1px solid var(--card-border);
  border-radius: 10px;
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
  overflow: hidden;
}

.si-dashboard__rail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.35rem;
  padding: 0.45rem 0.7rem;
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--si-ink);
  border-bottom: 1px solid rgba(20, 83, 45, 0.1);
  background: rgba(15, 118, 110, 0.04);
}

.si-dashboard__rail-list {
  list-style: none;
  margin: 0;
  padding: 0.2rem 0;
  max-height: 9.5rem;
  overflow-y: auto;
}

.si-dashboard__rail-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.4rem 0.7rem;
  cursor: pointer;
  font-size: 0.78rem;
}

.si-dashboard__rail-list li:hover {
  background: rgba(15, 118, 110, 0.06);
}

.si-dashboard__rail-name,
.si-dashboard__rail-dep {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  color: var(--si-ink);
}

.si-dashboard__rail-list em {
  flex: 0 0 auto;
  font-style: normal;
  font-weight: 700;
  color: var(--si-ink-soft);
}

.si-dashboard__rail-empty {
  padding: 0.75rem;
  font-size: 0.78rem;
  color: var(--si-muted);
  text-align: center;
}

.si-dashboard__rail-rank {
  flex: 1;
  min-height: 8rem;
}

@media (max-width: 991px) {
  .si-dashboard__body--mesh {
    grid-template-columns: 1fr;
  }
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
  gap: 0.65rem;
  max-height: 13.5rem;
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
  padding: 0.5rem 0.85rem;
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--si-ink);
  background: rgba(15, 118, 110, 0.05);
  border-bottom: 1px solid rgba(20, 83, 45, 0.1);
}

.si-dashboard__diag-empty {
  padding: 1.1rem 0.85rem;
  font-size: 0.95rem;
  color: var(--si-muted);
  text-align: center;
}

.si-dashboard__diag-empty--rich {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.55rem;
}

.si-dashboard__diag-scroll {
  overflow-y: auto;
  max-height: 10rem;
}

.si-dashboard__diag-table :deep(th),
.si-dashboard__diag-table :deep(td) {
  padding: 0.5rem 0.75rem;
  font-size: 0.92rem;
  vertical-align: middle;
}

.si-dashboard__diag-table :deep(th) {
  font-size: 0.82rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--si-muted);
}

.si-dashboard__diag-row {
  cursor: pointer;
}

.si-dashboard__dep-cell {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  min-width: 0;
  max-width: 16rem;
  font-size: 0.9rem;
}

.si-dashboard__dep-src,
.si-dashboard__dep-tgt {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 6.5rem;
}

.si-dashboard__dep-arrow {
  flex: 0 0 auto;
  color: var(--si-teal);
  font-size: 0.85rem;
}

.si-dashboard__diag-foot {
  flex-shrink: 0;
  padding: 0.4rem 0.75rem 0.55rem;
  border-top: 1px dashed rgba(20, 83, 45, 0.12);
  text-align: right;
}

.si-dashboard__hero-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.si-dashboard__hero-body--solo {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(240px, 42%);
  gap: 0.65rem;
  align-items: stretch;
}

.si-dashboard__hero-body--solo .si-dashboard__chart-canvas {
  min-height: 12rem;
}

.si-dashboard__solo {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  overflow: hidden;
}

.si-dashboard__solo-card {
  border: 1px solid rgba(20, 83, 45, 0.12);
  border-radius: 10px;
  background: rgba(15, 118, 110, 0.04);
  padding: 0.7rem 0.8rem;
}

.si-dashboard__solo-card--traces {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--card-bg);
  overflow: hidden;
}

.si-dashboard__solo-title {
  margin: 0 0 0.45rem;
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-dashboard__solo-tips {
  margin: 0 0 0.65rem;
  padding-left: 1.1rem;
  font-size: 0.75rem;
  color: var(--si-ink-soft);
  line-height: 1.55;
}

.si-dashboard__solo-tips code {
  font-size: 0.7rem;
  color: var(--si-teal);
}

.si-dashboard__solo-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.si-dashboard__solo-traces-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.35rem;
  flex-shrink: 0;
}

.si-dashboard__solo-traces {
  list-style: none;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  min-height: 0;
  flex: 1;
}

.si-dashboard__solo-trace {
  padding: 0.45rem 0.35rem;
  border-bottom: 1px solid rgba(20, 83, 45, 0.08);
  cursor: pointer;
  border-radius: 6px;
}

.si-dashboard__solo-trace:hover {
  background: rgba(15, 118, 110, 0.06);
}

.si-dashboard__solo-trace-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.si-dashboard__solo-trace-op {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--si-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.si-dashboard__solo-trace-meta {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  margin-top: 0.15rem;
  font-size: 0.7rem;
  color: var(--si-muted);
}

@media (max-width: 991px) {
  .si-dashboard__hero-body--solo {
    grid-template-columns: 1fr;
  }

  .si-app__metrics {
    grid-template-columns: repeat(2, 1fr);
  }

  .si-app__dur {
    display: none;
  }
}

.si-app {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  overflow: hidden;
}

.si-app__head {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.si-app__kicker {
  margin: 0;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--si-teal);
}

.si-app__name {
  margin: 0.15rem 0 0.25rem;
  font-family: var(--font-display);
  font-size: clamp(1.6rem, 2.6vw, 2.1rem);
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--si-ink);
}

.si-app__desc {
  margin: 0;
  max-width: 36rem;
  font-size: 0.88rem;
  color: var(--si-muted);
}

.si-app__metrics {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.65rem;
}

.si-app__metric {
  padding: 0.75rem 0.9rem;
  border-radius: 12px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
}

.si-app__metric span {
  display: inline-flex;
  align-items: center;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--si-muted);
}

.si-app__metric strong {
  display: block;
  margin-top: 0.2rem;
  font-family: var(--font-display);
  font-size: 1.45rem;
  font-weight: 700;
  color: var(--si-ink);
  line-height: 1.15;
}

.si-app__traces {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--card-border);
  border-radius: 12px;
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
  overflow: hidden;
}

.si-app__traces-head {
  flex-shrink: 0;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.7rem 0.95rem;
  border-bottom: 1px solid rgba(20, 83, 45, 0.1);
}

.si-app__traces-head h6 {
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-app__traces-hint {
  font-size: 0.75rem;
  color: var(--si-muted);
}

.si-app__list {
  list-style: none;
  margin: 0;
  padding: 0.25rem 0;
  overflow-y: auto;
  min-height: 0;
}

.si-app__row {
  display: grid;
  grid-template-columns: 4.2rem minmax(0, 1fr) minmax(7rem, 11rem) 3.2rem;
  align-items: center;
  gap: 0.75rem;
  padding: 0.62rem 0.95rem;
  cursor: pointer;
}

.si-app__row:hover {
  background: rgba(15, 118, 110, 0.06);
}

.si-app__method {
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: var(--si-teal);
}

.si-app__method[data-method="POST"],
.si-app__method[data-method="PUT"],
.si-app__method[data-method="PATCH"] {
  color: #b45309;
}

.si-app__method[data-method="DELETE"] {
  color: #b91c1c;
}

.si-app__path {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--si-ink);
}

.si-app__dur {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
}

.si-app__track {
  flex: 1;
  height: 4px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.12);
  overflow: hidden;
}

.si-app__bar {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: #0f766e;
}

.si-app__dur em {
  flex: 0 0 auto;
  font-style: normal;
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--si-ink-soft);
}
</style>
