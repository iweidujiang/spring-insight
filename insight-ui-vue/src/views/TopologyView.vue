<template>
  <div class="si-page fade-in si-topo-page">
    <div class="si-page__header">
      <div>
        <h2 class="page-title mb-1">
          <i class="fa fa-sitemap me-2"></i>服务拓扑图
        </h2>
        <p class="page-description mb-0">
          {{ isSolo
            ? '当前只有一个应用、还没有跨服务调用。'
            : '箭头：调用方 → 被调用方；点击节点/边可下钻到该服务的链路列表' }}
        </p>
      </div>
      <span class="badge bg-info">
        <i class="fa fa-clock me-1"></i>{{ currentTime }}
      </span>
    </div>

    <div class="card stat-card si-toolbar-card">
      <div class="card-body">
        <div class="si-toolbar-inner">
          <div>
            <label class="form-label" for="hours-topology">时间范围</label>
            <select id="hours-topology" class="form-select" style="min-width: 11rem" v-model.number="hours" @change="loadData">
              <option v-for="opt in TIME_RANGE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
          </div>
          <div class="d-flex flex-wrap gap-2 ms-auto">
            <button class="btn btn-primary" type="button" @click="loadData" :disabled="loading">
              <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i> 刷新
            </button>
            <button
              class="btn btn-outline-secondary"
              type="button"
              @click="downloadTopology"
              :disabled="loading || dependencies.length === 0"
            >
              <i class="fa fa-download"></i> 导出
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="loading" class="loading-spinner">
      <i class="fa fa-spinner fa-spin"></i>
      <span class="ms-2">正在加载拓扑数据...</span>
    </div>

    <div v-else-if="isSolo" class="si-topo-solo">
      <section class="si-topo-solo__hero">
        <div class="si-topo-solo__node" aria-hidden="true">
          <span>{{ soloName.slice(0, 1).toUpperCase() }}</span>
        </div>
        <div class="si-topo-solo__copy">
          <p class="si-topo-solo__kicker">单应用</p>
          <h3>{{ soloName }}</h3>
          <p class="si-topo-solo__desc">
            链路追踪可看这个应用的每次请求。
          </p>
          <div class="si-topo-solo__actions">
            <button type="button" class="btn btn-primary btn-sm" @click="goServiceTraces(soloName)">查看链路</button>
            <button type="button" class="btn btn-outline-secondary btn-sm" @click="goDashboard">回仪表盘</button>
          </div>
        </div>
      </section>

      <section class="si-topo-solo__metrics" aria-label="服务延迟摘要">
        <div class="si-topo-solo__metric">
          <span>请求</span>
          <strong>{{ soloLatency.spanCount }}</strong>
        </div>
        <div class="si-topo-solo__metric">
          <span>平均</span>
          <strong>{{ formatMs(soloLatency.avgMs) }}</strong>
        </div>
        <div class="si-topo-solo__metric">
          <span>p95 <PercentileHelp /></span>
          <strong :class="soloLatency.p95Ms >= 1000 ? 'text-danger' : soloLatency.p95Ms >= 500 ? 'text-warning' : ''">
            {{ formatMs(soloLatency.p95Ms) }}
          </strong>
        </div>
        <div class="si-topo-solo__metric">
          <span>错误</span>
          <strong :class="soloLatency.errorCount > 0 ? 'text-danger' : ''">{{ soloLatency.errorCount }}</strong>
        </div>
      </section>

      <section class="si-topo-solo__panel">
        <div class="si-topo-solo__panel-head">
          <h5 class="mb-0">最近请求</h5>
          <button type="button" class="btn btn-sm btn-link py-0" @click="goServiceTraces(soloName)">全部</button>
        </div>
        <div v-if="recentTraces.length === 0" class="si-topo-solo__empty">{{ emptyRequestsMessage(hours) }}</div>
        <ul v-else class="si-topo-solo__list">
          <li
            v-for="tr in recentTraces"
            :key="tr.traceId"
            @click="goTraceDetail(tr.traceId)"
          >
            <span class="si-topo-solo__method" :data-method="traceMethod(tr)">{{ traceMethod(tr) || 'REQ' }}</span>
            <span class="si-topo-solo__path" :title="tracePath(tr)">{{ tracePath(tr) }}</span>
            <em>{{ formatMs(tr.durationMs || 0) }}</em>
            <span class="badge" :class="traceFailed(tr) ? 'bg-danger' : 'bg-success'">
              {{ traceFailed(tr) ? '失败' : '成功' }}
            </span>
          </li>
        </ul>
      </section>

      <section class="si-topo-solo__hint">
        <h6>什么情况下展示调用拓扑图？</h6>
        <ol>
          <li>业务侧依赖 <code>spring-insight-agent-starter</code>，并配置 <code>server-url</code></li>
          <li>出站走 OpenFeign、<code>WebClient.Builder</code>、Gateway 或 <code>RestTemplateBuilder</code></li>
          <li>Agent 写入 <code>remoteService</code> 后，刷新本页即可看到「调用方 → 被调用方」</li>
        </ol>
      </section>
    </div>

    <div v-else class="si-topo-layout">
      <div class="chart-container si-topo-graph-card">
        <div class="d-flex justify-content-between align-items-center mb-2 flex-shrink-0">
          <h5 class="mb-0">
            <i class="fa fa-sitemap me-2"></i>服务依赖拓扑
          </h5>
          <div class="d-flex gap-2 align-items-center">
            <span class="si-topo-legend"><i class="fa fa-hand-pointer-o me-1"></i>点击节点/边查看链路</span>
            <span class="si-topo-legend"><i class="fa fa-long-arrow-right"></i> 调用方向</span>
            <button type="button" class="btn btn-sm btn-outline-primary" @click="refreshTopology">
              <i class="fa fa-refresh"></i> 重绘
            </button>
            <button type="button" class="btn btn-sm btn-outline-secondary" @click="fitToScreen">
              <i class="fa fa-expand"></i> 适应窗口
            </button>
          </div>
        </div>
        <div class="si-topo-canvas-wrap">
          <div ref="chartEl" class="si-topo-canvas"></div>
        </div>
      </div>

      <div class="card stat-card si-topo-table-card">
        <div class="card-body d-flex flex-column">
          <div class="d-flex justify-content-between align-items-center mb-2 flex-shrink-0">
            <h5 class="card-title mb-0">
              <i class="fa fa-list me-2"></i>依赖关系列表
            </h5>
            <span class="badge bg-primary">{{ dependencies.length }} 条</span>
          </div>
          <div class="table-responsive si-topo-table-scroll">
            <table class="table table-hover mb-0">
              <thead class="table-light">
                <tr>
                  <th>源服务</th>
                  <th></th>
                  <th>目标服务</th>
                  <th>调用次数</th>
                  <th>平均耗时(ms)</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="dep in dependencies" :key="`${dep.sourceService}-${dep.targetService}`">
                  <td><code class="si-svc">{{ dep.sourceService }}</code></td>
                  <td class="text-center text-info"><i class="fa fa-long-arrow-right"></i></td>
                  <td><code class="si-svc">{{ dep.targetService }}</code></td>
                  <td>{{ dep.callCount }}</td>
                  <td :class="dep.avgDuration > 1000 ? 'text-danger' : dep.avgDuration > 500 ? 'text-warning' : 'text-success'">
                    {{ dep.avgDuration || 0 }}
                  </td>
                  <td>
                    <div class="d-flex gap-1 flex-wrap">
                      <button class="btn btn-sm btn-outline-primary" type="button" @click="goServiceTraces(dep.sourceService)" title="查看调用方链路">
                        源
                      </button>
                      <button class="btn btn-sm btn-outline-secondary" type="button" @click="goServiceTraces(dep.targetService)" title="查看被调用方链路">
                        目标
                      </button>
                      <button
                        class="btn btn-sm btn-outline-info"
                        type="button"
                        :disabled="explaining"
                        @click="explainEdge(dep.sourceService, dep.targetService)"
                        title="AI 解释该依赖边"
                      >
                        <i class="fa fa-magic"></i>
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div v-if="edgeExplain" class="card stat-card mt-3">
        <div class="card-body">
          <div class="d-flex justify-content-between">
            <h5 class="card-title mb-2"><i class="fa fa-magic me-2"></i>边解读</h5>
            <button type="button" class="btn btn-sm btn-link" @click="edgeExplain = ''">关闭</button>
          </div>
          <pre class="mb-0" style="white-space:pre-wrap;font-family:var(--font-body);font-size:0.9rem">{{ edgeExplain }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import PercentileHelp from '../components/PercentileHelp.vue'
import { ApiService } from '../services/ApiService'
import { buildTopologyOption, resolveTopologyClick } from '../utils/topologyGraph'
import { formatDuration } from '../utils/traceTimeline'
import { TIME_RANGE_OPTIONS, emptyRequestsMessage } from '../utils/timeRange'

const router = useRouter()
const loading = ref(true)
const currentTime = ref('')
const hours = ref(72)
const dependencies = ref<any[]>([])
const serviceNames = ref<string[]>([])
const serviceLatency = ref<any[]>([])
const recentTraces = ref<any[]>([])
const chartEl = ref<HTMLElement | null>(null)
const explaining = ref(false)
const edgeExplain = ref('')

let topologyChart: echarts.ECharts | null = null
let timeInterval: number | null = null

const isSolo = computed(() => dependencies.value.length === 0 && serviceNames.value.length === 1)
const soloName = computed(() => serviceNames.value[0] || '未命名服务')
const soloLatency = computed(() => {
  const row = serviceLatency.value.find((item: any) => item.serviceName === soloName.value) || serviceLatency.value[0]
  return {
    spanCount: row?.spanCount || 0,
    avgMs: row?.avgMs || 0,
    p95Ms: row?.p95Ms || 0,
    errorCount: row?.errorCount || 0
  }
})

const formatMs = (ms: number) => formatDuration(Number(ms) || 0)
const traceFailed = (tr: any) => !!(tr?.hasError || tr?.statusCode === 'ERROR')
const traceMethod = (tr: any) => {
  const op = String(tr?.operationName || '')
  const matched = /^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\b/i.exec(op)
  return matched ? matched[1].toUpperCase() : ''
}
const tracePath = (tr: any) => {
  const op = String(tr?.operationName || tr?.traceId || '—')
  return op.replace(/^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\s+/i, '')
}

const topologyOpts = () => ({
  standaloneServices: serviceNames.value
})

const updateCurrentTime = () => {
  currentTime.value = new Date().toTimeString().split(' ')[0]
}

const goDashboard = () => router.push('/')
const goServiceTraces = (serviceName: string) => {
  if (!serviceName) return
  router.push({ path: '/traces', query: { service: serviceName, hours: String(hours.value) } })
}
const goTraceDetail = (traceId: string) => {
  if (!traceId) return
  router.push({ name: 'trace-detail', params: { traceId } })
}

async function explainEdge(source: string, target: string) {
  explaining.value = true
  try {
    const result = await ApiService.explainDependency(source, target, Number(hours.value))
    edgeExplain.value = result?.markdown || result?.message || '无返回'
  } finally {
    explaining.value = false
  }
}

const bindTopologyClick = () => {
  if (!topologyChart) return
  topologyChart.off('click')
  topologyChart.on('click', (params: any) => {
    const hit = resolveTopologyClick(params)
    if (hit?.service) {
      goServiceTraces(hit.service)
    }
  })
}

const mountChart = () => {
  if (!chartEl.value || topologyChart) return
  topologyChart = echarts.init(chartEl.value)
  topologyChart.setOption(buildTopologyOption([], topologyOpts()))
  bindTopologyClick()
}

const disposeChart = () => {
  topologyChart?.dispose()
  topologyChart = null
}

const updateChart = () => {
  if (!topologyChart) return
  topologyChart.setOption(buildTopologyOption(dependencies.value, topologyOpts()), { notMerge: true })
  nextTick(() => topologyChart?.resize())
}

const refreshTopology = () => {
  updateChart()
  topologyChart?.resize()
}

const fitToScreen = () => {
  if (!topologyChart) return
  topologyChart.setOption(buildTopologyOption(dependencies.value, topologyOpts()), { notMerge: true })
  topologyChart.resize()
}

const downloadTopology = () => {
  const dataStr = JSON.stringify(dependencies.value, null, 2)
  const dataBlob = new Blob([dataStr], { type: 'application/json' })
  const url = URL.createObjectURL(dataBlob)
  const link = document.createElement('a')
  link.href = url
  link.download = `topology-${new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')}.json`
  link.click()
  URL.revokeObjectURL(url)
}

const syncChart = async () => {
  await nextTick()
  if (isSolo.value) {
    disposeChart()
    return
  }
  mountChart()
  updateChart()
  topologyChart?.resize()
}

const loadData = async () => {
  try {
    loading.value = true
    const h = hours.value
    const [deps, names, latency, recent] = await Promise.all([
      ApiService.getServiceDependencies(h),
      ApiService.getServiceNames(),
      ApiService.getServiceLatency(h, 20),
      ApiService.getRecentTraces({ hours: h === 0 ? 168 : h, limit: 12 })
    ])
    dependencies.value = deps
    serviceNames.value = names
    serviceLatency.value = latency
    recentTraces.value = Array.isArray(recent) ? recent : []
  } catch (error) {
    console.error('加载拓扑数据失败:', error)
  } finally {
    loading.value = false
    await syncChart()
  }
}

const handleResize = () => topologyChart?.resize()

onMounted(async () => {
  updateCurrentTime()
  timeInterval = window.setInterval(updateCurrentTime, 1000)
  window.addEventListener('resize', handleResize)
  await loadData()
})

onUnmounted(() => {
  disposeChart()
  if (timeInterval) clearInterval(timeInterval)
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.si-topo-page {
  min-height: calc(100dvh - 2rem);
}

.si-topo-layout {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0.75rem;
  flex: 1;
}

.si-topo-graph-card {
  min-height: clamp(360px, 52vh, 620px) !important;
  height: auto !important;
  margin-bottom: 0 !important;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.si-topo-canvas-wrap {
  flex: 1;
  min-height: 320px;
  width: 100%;
  position: relative;
  overflow: hidden;
  border-radius: 8px;
  background:
    radial-gradient(ellipse at center, rgba(15, 118, 110, 0.06) 0%, transparent 65%),
    var(--si-paper);
  border: 1px solid var(--card-border);
}

.si-topo-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.si-topo-legend {
  font-size: 0.72rem;
  color: var(--si-muted);
}

.si-topo-table-card {
  margin-bottom: 0 !important;
}

.si-topo-table-scroll {
  max-height: min(36vh, 320px);
  overflow-y: auto;
}

.si-svc {
  color: var(--si-teal);
  font-size: 0.85rem;
  background: transparent;
}

.si-topo-solo {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.si-topo-solo__hero {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 1.1rem;
  align-items: center;
  padding: 1.15rem 1.25rem;
  border: 1px solid var(--card-border);
  border-radius: 12px;
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
}

.si-topo-solo__node {
  width: 4.5rem;
  height: 4.5rem;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: #0f766e;
  color: #ecfeff;
  font-family: var(--font-display);
  font-size: 1.6rem;
  font-weight: 700;
  box-shadow: 0 0 0 8px rgba(15, 118, 110, 0.12);
}

.si-topo-solo__kicker {
  margin: 0;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--si-teal);
}

.si-topo-solo__copy h3 {
  margin: 0.15rem 0 0.35rem;
  font-family: var(--font-display);
  font-size: clamp(1.45rem, 2.4vw, 1.9rem);
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--si-ink);
}

.si-topo-solo__desc {
  margin: 0 0 0.75rem;
  max-width: 40rem;
  font-size: 0.9rem;
  color: var(--si-muted);
  line-height: 1.55;
}

.si-topo-solo__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.si-topo-solo__metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.65rem;
}

.si-topo-solo__metric {
  padding: 0.75rem 0.9rem;
  border-radius: 12px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
}

.si-topo-solo__metric span {
  display: block;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--si-muted);
}

.si-topo-solo__metric strong {
  display: block;
  margin-top: 0.2rem;
  font-family: var(--font-display);
  font-size: 1.4rem;
  font-weight: 700;
  color: var(--si-ink);
  line-height: 1.15;
}

.si-topo-solo__panel {
  border: 1px solid var(--card-border);
  border-radius: 12px;
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
  overflow: hidden;
}

.si-topo-solo__panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.7rem 0.95rem;
  border-bottom: 1px solid rgba(20, 83, 45, 0.1);
}

.si-topo-solo__panel-head h5 {
  font-size: 0.92rem;
  font-weight: 700;
}

.si-topo-solo__empty {
  padding: 1.4rem;
  text-align: center;
  color: var(--si-muted);
}

.si-topo-solo__list {
  list-style: none;
  margin: 0;
  padding: 0.25rem 0;
  max-height: 18rem;
  overflow-y: auto;
}

.si-topo-solo__list li {
  display: grid;
  grid-template-columns: 4.2rem minmax(0, 1fr) 4.5rem 3.2rem;
  gap: 0.65rem;
  align-items: center;
  padding: 0.58rem 0.95rem;
  cursor: pointer;
}

.si-topo-solo__list li:hover {
  background: rgba(15, 118, 110, 0.06);
}

.si-topo-solo__method {
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: var(--si-teal);
}

.si-topo-solo__method[data-method='POST'],
.si-topo-solo__method[data-method='PUT'],
.si-topo-solo__method[data-method='PATCH'] {
  color: #b45309;
}

.si-topo-solo__method[data-method='DELETE'] {
  color: #b91c1c;
}

.si-topo-solo__path {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 650;
  color: var(--si-ink);
}

.si-topo-solo__list em {
  font-style: normal;
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--si-ink-soft);
  text-align: right;
}

.si-topo-solo__hint {
  padding: 0.9rem 1rem;
  border-radius: 12px;
  border: 1px dashed rgba(15, 118, 110, 0.28);
  background: rgba(15, 118, 110, 0.04);
}

.si-topo-solo__hint h6 {
  margin: 0 0 0.45rem;
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-topo-solo__hint ol {
  margin: 0;
  padding-left: 1.15rem;
  font-size: 0.82rem;
  color: var(--si-ink-soft);
  line-height: 1.6;
}

.si-topo-solo__hint code {
  font-size: 0.78rem;
  color: var(--si-teal);
}

@media (max-width: 900px) {
  .si-topo-solo__hero {
    grid-template-columns: 1fr;
  }

  .si-topo-solo__metrics {
    grid-template-columns: repeat(2, 1fr);
  }

  .si-topo-solo__list li {
    grid-template-columns: 3.6rem minmax(0, 1fr) 3.2rem;
  }

  .si-topo-solo__list em {
    display: none;
  }
}
</style>
