<template>
  <div class="si-page fade-in">
    <div class="si-page__header">
      <div>
        <h2 class="page-title mb-1">
          <i class="fa fa-exclamation-triangle me-2"></i>错误分析
        </h2>
        <p class="page-description mb-0">按服务、HTTP 状态码与异常类聚合，可下钻到异常链路</p>
      </div>
      <div class="si-page__toolbar">
        <button
          class="btn btn-outline-primary"
          @click="onExplainErrors"
          :disabled="loading || explaining || !hasErrors || !aiReady"
          :title="aiReady ? '用当前聚合摘要调用 AI' : '请先在设置中启用 AI 并配置密钥'"
        >
          <i class="fa" :class="explaining ? 'fa-spinner fa-spin' : 'fa-magic'"></i>
          {{ explaining ? '解读中…' : '一键解读' }}
        </button>
        <button class="btn btn-primary" @click="loadData" :disabled="loading">
          <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i> 刷新
        </button>
        <button class="btn btn-outline-secondary" @click="downloadErrorData" :disabled="loading || !hasErrors">
          <i class="fa fa-download"></i> 导出
        </button>
        <span class="badge bg-info">
          <i class="fa fa-clock me-1"></i>{{ currentTime }}
        </span>
      </div>
    </div>

    <div v-if="explainMarkdown" class="card stat-card si-err-ai mb-3">
      <div class="card-body">
        <div class="d-flex justify-content-between align-items-start mb-2">
          <h5 class="card-title mb-0"><i class="fa fa-magic me-2"></i>AI 解读</h5>
          <button type="button" class="btn btn-sm btn-link text-muted" @click="explainMarkdown = ''">关闭</button>
        </div>
        <pre class="si-err-ai__md mb-0">{{ explainMarkdown }}</pre>
      </div>
    </div>

    <div class="card stat-card si-toolbar-card">
      <div class="card-body">
        <div class="si-toolbar-inner">
          <div>
            <label class="form-label" for="hours-select-err">时间范围</label>
            <select id="hours-select-err" class="form-select" style="min-width: 11rem" v-model="hours" @change="loadData">
              <option :value="1">最近 1 小时</option>
              <option :value="6">最近 6 小时</option>
              <option :value="12">最近 12 小时</option>
              <option :value="24">最近 24 小时</option>
              <option :value="72">最近 72 小时</option>
              <option :value="168">最近 7 天</option>
              <option :value="0">全部已存</option>
            </select>
          </div>
          <div class="si-err-hint" title="基于错误 Span 的 status / exception 归类">
            <i class="fa fa-info-circle me-1"></i>
            分类优先读 http.status_code，其次异常类名；可点「相关链路」下钻
          </div>
        </div>
      </div>
    </div>

    <div v-if="loading" class="loading-spinner">
      <i class="fa fa-spinner fa-spin"></i>
      <span class="ms-2">正在加载错误分析数据...</span>
    </div>

    <div v-show="!loading" class="si-err-body">
      <div v-if="!hasErrors" class="si-err-healthy">
        <div class="si-err-healthy__icon">
          <i class="fa fa-check-circle"></i>
        </div>
        <h3 class="si-err-healthy__title">运行正常</h3>
        <p class="si-err-healthy__desc">
          所选时间范围内未发现错误调用，所有已上报服务状态良好。
        </p>
        <div class="si-err-healthy__tips">
          <span><i class="fa fa-bolt me-1"></i>可在业务侧制造失败请求后再刷新本页</span>
          <span><i class="fa fa-list-ul me-1"></i>也可到「链路追踪」按状态筛选排查</span>
        </div>
      </div>

      <template v-else>
        <div class="si-err-summary">
          <div class="si-err-summary__card">
            <span class="si-err-summary__label">异常服务</span>
            <span class="si-err-summary__value text-danger">{{ errorAnalysis.length }}</span>
          </div>
          <div class="si-err-summary__card">
            <span class="si-err-summary__label">错误 Span</span>
            <span class="si-err-summary__value">{{ totalErrorSpans }}</span>
          </div>
          <div class="si-err-summary__card">
            <span class="si-err-summary__label">状态码类</span>
            <span class="si-err-summary__value">{{ byStatusCode.length }}</span>
          </div>
          <div class="si-err-summary__card">
            <span class="si-err-summary__label">异常类</span>
            <span class="si-err-summary__value text-warning">{{ byException.length }}</span>
          </div>
          <div class="si-err-summary__card">
            <span class="si-err-summary__label">最高错误率</span>
            <span class="si-err-summary__value text-warning">{{ maxErrorRate }}%</span>
          </div>
        </div>

        <div class="si-charts-row">
          <div class="chart-container si-chart-panel">
            <div class="d-flex justify-content-between align-items-center mb-2 flex-shrink-0">
              <h5 class="mb-0"><i class="fa fa-bar-chart me-2"></i>服务错误率</h5>
              <button type="button" class="btn btn-sm btn-outline-primary" @click="refreshCharts">
                <i class="fa fa-refresh"></i>
              </button>
            </div>
            <div class="si-chart-canvas-wrap">
              <div id="error-rate-chart" class="w-100 h-100" style="min-height: 220px"></div>
            </div>
          </div>
          <div class="chart-container si-chart-panel">
            <div class="d-flex justify-content-between align-items-center mb-2 flex-shrink-0">
              <h5 class="mb-0"><i class="fa fa-pie-chart me-2"></i>错误调用占比（按服务）</h5>
            </div>
            <div class="si-chart-canvas-wrap">
              <div id="error-pie-chart" class="w-100 h-100" style="min-height: 220px"></div>
            </div>
          </div>
        </div>

        <div class="si-err-split">
          <div class="card stat-card si-table-panel">
            <div class="card-body">
              <div class="d-flex justify-content-between align-items-center mb-2">
                <h5 class="card-title mb-0"><i class="fa fa-code me-2"></i>按 HTTP 状态码</h5>
                <span class="badge bg-secondary">{{ byStatusCode.length }} 类</span>
              </div>
              <div v-if="byStatusCode.length === 0" class="text-muted small py-3">暂无 HTTP 状态类错误</div>
              <div v-else class="table-responsive">
                <table class="table table-hover mb-0">
                  <thead class="table-light">
                    <tr>
                      <th>状态码</th>
                      <th>次数</th>
                      <th>涉及服务</th>
                      <th>样例</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="row in byStatusCode" :key="'st-' + row.key">
                      <td><span class="badge bg-danger">{{ row.label }}</span></td>
                      <td class="text-danger fw-semibold">{{ row.count }}</td>
                      <td>
                        <span class="small">{{ row.services.slice(0, 3).join('、') }}</span>
                        <span v-if="row.serviceCount > 3" class="text-muted small"> 等 {{ row.serviceCount }} 个</span>
                      </td>
                      <td class="si-err-sample" :title="row.sampleMessage">{{ row.sampleMessage || '—' }}</td>
                      <td>
                        <button class="btn btn-sm btn-outline-primary me-1" @click="viewCategoryTraces(row.key)">
                          <i class="fa fa-list-ul"></i> 链路
                        </button>
                        <button
                          v-if="row.sampleTraceId"
                          class="btn btn-sm btn-outline-secondary"
                          @click="viewTrace(row.sampleTraceId)"
                        >
                          样例
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          <div class="card stat-card si-table-panel">
            <div class="card-body">
              <div class="d-flex justify-content-between align-items-center mb-2">
                <h5 class="card-title mb-0"><i class="fa fa-bug me-2"></i>按异常类</h5>
                <span class="badge bg-warning text-dark">{{ byException.length }} 类</span>
              </div>
              <div v-if="byException.length === 0" class="text-muted small py-3">暂无异常类错误</div>
              <div v-else class="table-responsive">
                <table class="table table-hover mb-0">
                  <thead class="table-light">
                    <tr>
                      <th>异常</th>
                      <th>次数</th>
                      <th>涉及服务</th>
                      <th>样例</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="row in byException" :key="'ex-' + row.key">
                      <td><code class="si-err-ex">{{ row.label }}</code></td>
                      <td class="text-danger fw-semibold">{{ row.count }}</td>
                      <td>
                        <span class="small">{{ row.services.slice(0, 3).join('、') }}</span>
                        <span v-if="row.serviceCount > 3" class="text-muted small"> 等 {{ row.serviceCount }} 个</span>
                      </td>
                      <td class="si-err-sample" :title="row.sampleMessage">{{ row.sampleMessage || '—' }}</td>
                      <td>
                        <button class="btn btn-sm btn-outline-primary me-1" @click="viewCategoryTraces(row.key)">
                          <i class="fa fa-list-ul"></i> 链路
                        </button>
                        <button
                          v-if="row.sampleTraceId"
                          class="btn btn-sm btn-outline-secondary"
                          @click="viewTrace(row.sampleTraceId)"
                        >
                          样例
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>

        <div v-if="byOther.length > 0" class="card stat-card si-table-panel">
          <div class="card-body">
            <div class="d-flex justify-content-between align-items-center mb-2">
              <h5 class="card-title mb-0"><i class="fa fa-question-circle me-2"></i>其它错误</h5>
              <span class="badge bg-secondary">{{ byOther.length }} 类</span>
            </div>
            <div class="table-responsive">
              <table class="table table-hover mb-0">
                <thead class="table-light">
                  <tr>
                    <th>分类</th>
                    <th>次数</th>
                    <th>涉及服务</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in byOther" :key="'ot-' + row.key">
                    <td>{{ row.label }}</td>
                    <td>{{ row.count }}</td>
                    <td class="small">{{ row.services.join('、') || '—' }}</td>
                    <td>
                      <button class="btn btn-sm btn-outline-primary" @click="viewCategoryTraces(row.key)">
                        相关链路
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <div class="card stat-card si-table-panel">
          <div class="card-body">
            <div class="d-flex justify-content-between align-items-center mb-2">
              <h5 class="card-title mb-0"><i class="fa fa-list me-2"></i>错误服务列表</h5>
              <span class="badge bg-danger">{{ errorAnalysis.length }} 个异常服务</span>
            </div>
            <div class="table-responsive">
              <table class="table table-hover mb-0">
                <thead class="table-light">
                  <tr>
                    <th>服务名称</th>
                    <th>总调用数</th>
                    <th>错误调用数</th>
                    <th>错误率</th>
                    <th>状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="(error, index) in errorAnalysis"
                    :key="error.serviceName"
                    :class="error.errorRate > 10 ? 'table-danger' : error.errorRate > 5 ? 'table-warning' : ''"
                    :style="{ animationDelay: `${index * 0.05}s` }"
                  >
                    <td>{{ error.serviceName }}</td>
                    <td>{{ error.totalCalls }}</td>
                    <td class="text-danger">{{ error.errorCalls }}</td>
                    <td>
                      <span class="badge" :class="error.errorRate > 10 ? 'bg-danger' : error.errorRate > 5 ? 'bg-warning' : 'bg-info'">
                        {{ error.errorRate.toFixed(2) }}%
                      </span>
                    </td>
                    <td>
                      <span v-if="error.errorRate > 10" class="badge bg-danger">严重</span>
                      <span v-else-if="error.errorRate > 5" class="badge bg-warning">警告</span>
                      <span v-else class="badge bg-info">注意</span>
                    </td>
                    <td>
                      <button class="btn btn-sm btn-primary" @click="viewServiceDetails(error.serviceName)">
                        <i class="fa fa-list-ul"></i> 相关链路
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ApiService } from '../services/ApiService'

const router = useRouter()
const loading = ref(true)
const explaining = ref(false)
const explainMarkdown = ref('')
const aiReady = ref(false)
const currentTime = ref('')
const hours = ref(24)
const errorAnalysis = ref<any[]>([])
const byStatusCode = ref<any[]>([])
const byException = ref<any[]>([])
const byOther = ref<any[]>([])
const totalErrorSpans = ref(0)
const exportPayload = ref<any>(null)

let errorRateChart: echarts.ECharts | null = null
let errorPieChart: echarts.ECharts | null = null
let timeInterval: number | null = null
let chartsReady = false

const hasErrors = computed(() => totalErrorSpans.value > 0 || errorAnalysis.value.length > 0)
const maxErrorRate = computed(() => {
  if (errorAnalysis.value.length === 0) return '0.00'
  return Math.max(...errorAnalysis.value.map((e) => e.errorRate || 0)).toFixed(2)
})

const updateCurrentTime = () => {
  currentTime.value = new Date().toTimeString().split(' ')[0]
}

const disposeCharts = () => {
  errorRateChart?.dispose()
  errorPieChart?.dispose()
  errorRateChart = null
  errorPieChart = null
  chartsReady = false
}

const ensureCharts = async () => {
  if (errorAnalysis.value.length === 0) {
    disposeCharts()
    return
  }
  await nextTick()
  const rateDom = document.getElementById('error-rate-chart')
  const pieDom = document.getElementById('error-pie-chart')
  if (!rateDom || !pieDom) return

  if (!errorRateChart) {
    errorRateChart = echarts.init(rateDom)
  }
  if (!errorPieChart) {
    errorPieChart = echarts.init(pieDom)
  }
  chartsReady = true
  updateCharts()
  errorRateChart.resize()
  errorPieChart.resize()
}

const updateCharts = () => {
  if (!chartsReady || !errorRateChart || !errorPieChart) return

  const serviceNames = errorAnalysis.value.map((e) => e.serviceName)
  const errorRates = errorAnalysis.value.map((e) => e.errorRate)
  const pieData = errorAnalysis.value.map((e) => ({
    name: e.serviceName,
    value: e.errorCalls
  }))

  errorRateChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 16, top: 24, bottom: 48 },
    xAxis: {
      type: 'category',
      data: serviceNames,
      axisLabel: { rotate: serviceNames.length > 4 ? 30 : 0, color: '#6b7f76' }
    },
    yAxis: {
      type: 'value',
      name: '%',
      axisLabel: { color: '#6b7f76' }
    },
    series: [
      {
        type: 'bar',
        data: errorRates,
        itemStyle: {
          color: (params: any) => {
            const v = Number(params.value || 0)
            if (v > 10) return '#b91c1c'
            if (v > 5) return '#b45309'
            return '#0f766e'
          }
        }
      }
    ]
  })

  errorPieChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        type: 'pie',
        radius: ['35%', '62%'],
        data: pieData,
        label: { formatter: '{b}\n{d}%' }
      }
    ]
  })
}

const refreshCharts = () => {
  errorRateChart?.resize()
  errorPieChart?.resize()
  updateCharts()
}

const viewServiceDetails = (serviceName: string) => {
  router.push({ path: '/traces', query: { service: serviceName, status: 'error' } })
}

const viewCategoryTraces = (key: string) => {
  router.push({ path: '/traces', query: { status: 'error', q: key } })
}

const viewTrace = (traceId: string) => {
  router.push({ path: `/traces/${encodeURIComponent(traceId)}` })
}

async function onExplainErrors() {
  explaining.value = true
  try {
    const result = await ApiService.explainErrors(Number(hours.value))
    explainMarkdown.value = result?.markdown || result?.message || '无返回内容'
  } finally {
    explaining.value = false
  }
}

async function refreshAiStatus() {
  try {
    const st = await ApiService.getAiStatus()
    aiReady.value = !!st.invokeReady
  } catch {
    aiReady.value = false
  }
}

const downloadErrorData = () => {
  const dataStr = JSON.stringify(exportPayload.value || {
    byService: errorAnalysis.value,
    byStatusCode: byStatusCode.value,
    byException: byException.value,
    byOther: byOther.value
  }, null, 2)
  const dataBlob = new Blob([dataStr], { type: 'application/json' })
  const url = URL.createObjectURL(dataBlob)
  const link = document.createElement('a')
  link.href = url
  link.download = `error-analysis-${new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')}.json`
  link.click()
  URL.revokeObjectURL(url)
}

const loadData = async () => {
  try {
    loading.value = true
    const breakdown = await ApiService.getErrorBreakdown(hours.value)
    errorAnalysis.value = breakdown.byService
    byStatusCode.value = breakdown.byStatusCode
    byException.value = breakdown.byException
    byOther.value = breakdown.byOther
    totalErrorSpans.value = breakdown.totalErrorSpans
    exportPayload.value = breakdown
  } catch (error) {
    console.error('加载错误分析数据失败:', error)
  } finally {
    loading.value = false
    await ensureCharts()
  }
}

const handleResize = () => {
  errorRateChart?.resize()
  errorPieChart?.resize()
}

watch(() => errorAnalysis.value.length, async () => {
  if (!loading.value) await ensureCharts()
})

onMounted(async () => {
  updateCurrentTime()
  timeInterval = window.setInterval(updateCurrentTime, 1000)
  window.addEventListener('resize', handleResize)
  await refreshAiStatus()
  await loadData()
})

onUnmounted(() => {
  disposeCharts()
  if (timeInterval) clearInterval(timeInterval)
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.si-err-hint {
  align-self: center;
  font-size: 0.78rem;
  color: var(--si-muted);
  padding: 0.35rem 0.65rem;
  border-radius: 8px;
  background: var(--si-paper);
  border: 1px solid var(--card-border);
}

.si-err-body {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.si-err-healthy {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  min-height: clamp(280px, 42vh, 420px);
  padding: 2rem 1.5rem;
  border-radius: 14px;
  border: 1px solid rgba(21, 128, 61, 0.2);
  background:
    radial-gradient(ellipse at 50% 20%, rgba(21, 128, 61, 0.08), transparent 55%),
    var(--card-bg);
}

.si-err-healthy__icon {
  font-size: 3rem;
  color: #15803d;
  margin-bottom: 0.75rem;
}

.si-err-healthy__title {
  font-family: var(--font-display);
  font-size: 1.35rem;
  font-weight: 700;
  color: var(--si-ink);
  margin: 0 0 0.4rem;
}

.si-err-healthy__desc {
  max-width: 28rem;
  color: var(--si-muted);
  margin: 0 0 1.25rem;
  font-size: 0.9rem;
  line-height: 1.5;
}

.si-err-healthy__tips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1rem;
  justify-content: center;
  font-size: 0.78rem;
  color: var(--si-muted);
}

.si-err-summary {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 0.65rem;
}

@media (max-width: 1100px) {
  .si-err-summary {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 575px) {
  .si-err-summary {
    grid-template-columns: 1fr;
  }
}

.si-err-summary__card {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 1.05rem 1.2rem;
  min-height: 5.25rem;
  border-radius: 10px;
  background: var(--card-bg);
  border: 1px solid var(--card-border);
  box-shadow: var(--box-shadow);
}

.si-err-summary__label {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--si-muted);
  font-weight: 600;
}

.si-err-summary__value {
  font-family: var(--font-display);
  font-size: clamp(1.35rem, 2vw, 1.65rem);
  font-weight: 700;
  color: var(--si-ink);
  line-height: 1.15;
}

.si-err-split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}

@media (max-width: 991px) {
  .si-err-split {
    grid-template-columns: 1fr;
  }
}

.si-err-sample {
  max-width: 14rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.78rem;
  color: var(--si-muted);
}

.si-err-ex {
  font-size: 0.82rem;
  color: #9a3412;
  background: rgba(180, 83, 9, 0.08);
  padding: 0.1rem 0.35rem;
  border-radius: 4px;
}

.si-err-ai__md {
  white-space: pre-wrap;
  font-family: var(--font-body);
  font-size: 0.9rem;
  line-height: 1.55;
  margin: 0;
  color: var(--si-ink);
}
</style>
