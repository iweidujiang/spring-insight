<template>
  <div class="si-page fade-in si-err-page">
    <div class="si-page__header">
      <div>
        <h2 class="page-title mb-1">
          <i class="fa fa-exclamation-triangle me-2"></i>错误分析
        </h2>
        <p class="page-description mb-0">
          按服务、HTTP 状态码与异常类聚合；点整行可下钻到异常链路
        </p>
      </div>
      <div class="si-page__toolbar">
        <label class="form-label mb-0 visually-hidden" for="hours-select-err">时间范围</label>
        <select
          id="hours-select-err"
          class="form-select form-select-sm si-err-hours"
          v-model.number="hours"
          @change="loadData"
        >
          <option v-for="opt in TIME_RANGE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
        <button
          class="btn btn-sm btn-outline-primary"
          @click="onExplainErrors"
          :disabled="loading || explaining || !hasErrors || !aiReady"
          :title="aiReady ? '用当前聚合摘要调用 AI' : '请先在设置中启用 AI 并配置密钥'"
        >
          <i class="fa" :class="explaining ? 'fa-spinner fa-spin' : 'fa-magic'"></i>
          {{ explaining ? '解读中…' : '一键解读' }}
        </button>
        <button class="btn btn-sm btn-primary" @click="loadData" :disabled="loading">
          <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i> 刷新
        </button>
        <button class="btn btn-sm btn-outline-secondary" @click="downloadErrorData" :disabled="loading || !hasErrors">
          <i class="fa fa-download"></i> 导出
        </button>
        <span class="badge bg-info">
          <i class="fa fa-clock me-1"></i>{{ currentTime }}
        </span>
      </div>
    </div>

    <p class="si-err-hint-line">
      <i class="fa fa-info-circle me-1"></i>
      分类优先读 http.status_code，其次异常类名 · {{ formatHoursLabel(hours) }}
    </p>

    <div v-if="loading" class="loading-spinner">
      <i class="fa fa-spinner fa-spin"></i>
      <span class="ms-2">正在加载错误分析数据…</span>
    </div>

    <div v-show="!loading" class="si-err-body">
      <div v-if="!hasErrors" class="si-err-healthy">
        <div class="si-err-healthy__icon">
          <i class="fa fa-check-circle"></i>
        </div>
        <h3 class="si-err-healthy__title">运行正常</h3>
        <p class="si-err-healthy__desc">
          {{ hours === 0 ? '全部已存范围内' : formatHoursLabel(hours) + '内' }}未发现错误调用，所有已上报服务状态良好。
        </p>
        <div class="si-err-healthy__tips">
          <span><i class="fa fa-bolt me-1"></i>可在业务侧制造失败请求后再刷新本页</span>
          <button type="button" class="btn btn-sm btn-outline-secondary" @click="goErrorTraces">
            到链路追踪按异常筛选
          </button>
        </div>
      </div>

      <template v-else>
        <section class="si-err-strip" aria-label="错误摘要">
          <div class="si-err-strip__item">
            <span class="si-err-strip__label">异常服务</span>
            <span class="si-err-strip__value is-danger">{{ errorAnalysis.length }}</span>
          </div>
          <div class="si-err-strip__item">
            <span class="si-err-strip__label">错误 Span</span>
            <span class="si-err-strip__value">{{ totalErrorSpans }}</span>
          </div>
          <div class="si-err-strip__item">
            <span class="si-err-strip__label">状态码类</span>
            <span class="si-err-strip__value">{{ byStatusCode.length }}</span>
          </div>
          <div class="si-err-strip__item">
            <span class="si-err-strip__label">异常类</span>
            <span class="si-err-strip__value">{{ byException.length }}</span>
          </div>
          <div class="si-err-strip__item">
            <span class="si-err-strip__label">最高错误率</span>
            <span class="si-err-strip__value is-warn">{{ maxErrorRate }}%</span>
          </div>
        </section>

        <!-- 主舞台：错误服务列表（对齐链路「请求主舞台」） -->
        <section class="si-err-stage" aria-label="错误服务">
          <div class="si-err-stage__head">
            <h3 class="si-err-stage__title"><i class="fa fa-list me-2"></i>错误服务</h3>
            <span class="badge bg-danger">{{ errorAnalysis.length }} 个</span>
          </div>
          <div class="table-responsive">
            <table class="table table-hover mb-0 si-err-table">
              <thead class="table-light">
                <tr>
                  <th>服务</th>
                  <th>总调用</th>
                  <th>错误</th>
                  <th>错误率</th>
                  <th>级别</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(error, index) in errorAnalysis"
                  :key="error.serviceName"
                  class="si-err-row"
                  :class="error.errorRate > 10 ? 'is-severe' : error.errorRate > 5 ? 'is-warn' : ''"
                  :style="{ animationDelay: `${Math.min(index, 20) * 0.03}s` }"
                  @click="viewServiceDetails(error.serviceName)"
                >
                  <td class="si-err-svc">{{ error.serviceName }}</td>
                  <td>{{ error.totalCalls }}</td>
                  <td class="text-danger fw-semibold">{{ error.errorCalls }}</td>
                  <td>
                    <span class="si-err-rate" :data-level="rateLevel(error.errorRate)">
                      {{ error.errorRate.toFixed(2) }}%
                    </span>
                  </td>
                  <td>
                    <span class="si-err-level" :data-level="rateLevel(error.errorRate)">
                      {{ rateLabel(error.errorRate) }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <div class="si-err-charts">
          <div class="si-err-chart">
            <div class="si-err-chart__head">
              <h4><i class="fa fa-bar-chart me-2"></i>服务错误率</h4>
              <button type="button" class="btn btn-sm btn-outline-primary" @click="refreshCharts" title="重绘">
                <i class="fa fa-refresh"></i>
              </button>
            </div>
            <div class="si-err-chart__canvas">
              <div id="error-rate-chart" class="w-100 h-100"></div>
            </div>
          </div>
          <div class="si-err-chart">
            <div class="si-err-chart__head">
              <h4><i class="fa fa-pie-chart me-2"></i>错误调用占比</h4>
            </div>
            <div class="si-err-chart__canvas">
              <div id="error-pie-chart" class="w-100 h-100"></div>
            </div>
          </div>
        </div>

        <div class="si-err-split">
          <section class="si-err-panel">
            <div class="si-err-panel__head">
              <h4><i class="fa fa-code me-2"></i>按 HTTP 状态码</h4>
              <span class="badge bg-secondary">{{ byStatusCode.length }}</span>
            </div>
            <div v-if="byStatusCode.length === 0" class="si-err-panel__empty">暂无 HTTP 状态类错误</div>
            <div v-else class="table-responsive">
              <table class="table table-hover mb-0 si-err-table si-err-table--compact">
                <thead class="table-light">
                  <tr>
                    <th>状态码</th>
                    <th>次数</th>
                    <th>服务</th>
                    <th>样例</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="row in byStatusCode"
                    :key="'st-' + row.key"
                    class="si-err-row"
                    @click="viewCategoryTraces(row.key)"
                  >
                    <td><span class="si-err-code">{{ row.label }}</span></td>
                    <td class="text-danger fw-semibold">{{ row.count }}</td>
                    <td class="small">
                      {{ row.services.slice(0, 2).join('、') }}
                      <span v-if="row.serviceCount > 2" class="text-muted"> 等{{ row.serviceCount }}</span>
                    </td>
                    <td class="si-err-sample" :title="row.sampleMessage">{{ row.sampleMessage || '—' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <section class="si-err-panel">
            <div class="si-err-panel__head">
              <h4><i class="fa fa-bug me-2"></i>按异常类</h4>
              <span class="badge bg-warning text-dark">{{ byException.length }}</span>
            </div>
            <div v-if="byException.length === 0" class="si-err-panel__empty">暂无异常类错误</div>
            <div v-else class="table-responsive">
              <table class="table table-hover mb-0 si-err-table si-err-table--compact">
                <thead class="table-light">
                  <tr>
                    <th>异常</th>
                    <th>次数</th>
                    <th>服务</th>
                    <th>样例</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="row in byException"
                    :key="'ex-' + row.key"
                    class="si-err-row"
                    @click="viewCategoryTraces(row.key)"
                  >
                    <td><code class="si-err-ex">{{ row.label }}</code></td>
                    <td class="text-danger fw-semibold">{{ row.count }}</td>
                    <td class="small">
                      {{ row.services.slice(0, 2).join('、') }}
                      <span v-if="row.serviceCount > 2" class="text-muted"> 等{{ row.serviceCount }}</span>
                    </td>
                    <td class="si-err-sample" :title="row.sampleMessage">{{ row.sampleMessage || '—' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </div>

        <details v-if="byOther.length > 0" class="si-err-fold">
          <summary class="si-err-fold__summary">
            <i class="fa fa-question-circle me-2"></i>其它错误（{{ byOther.length }}）
          </summary>
          <div class="table-responsive">
            <table class="table table-hover mb-0 si-err-table si-err-table--compact">
              <thead class="table-light">
                <tr>
                  <th>分类</th>
                  <th>次数</th>
                  <th>服务</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in byOther"
                  :key="'ot-' + row.key"
                  class="si-err-row"
                  @click="viewCategoryTraces(row.key)"
                >
                  <td>{{ row.label }}</td>
                  <td>{{ row.count }}</td>
                  <td class="small">{{ row.services.join('、') || '—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </details>

        <AiExplainPanel
          v-if="explainResult"
          :result="explainResult"
          title="AI 解读"
          @close="explainResult = null"
        />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ApiService, type AiExplainResult } from '../services/ApiService'
import AiExplainPanel from '../components/AiExplainPanel.vue'
import { TIME_RANGE_OPTIONS, formatHoursLabel } from '../utils/timeRange'

const router = useRouter()
const loading = ref(true)
const explaining = ref(false)
const explainResult = ref<AiExplainResult | null>(null)
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

const PIE_COLORS = ['#0f766e', '#b91c1c', '#b45309', '#15803d', '#1d4ed8', '#c2410c', '#047857', '#0d9488']

const rateLevel = (rate: number) => {
  if (rate > 10) return 'severe'
  if (rate > 5) return 'warn'
  return 'note'
}

const rateLabel = (rate: number) => {
  if (rate > 10) return '严重'
  if (rate > 5) return '警告'
  return '注意'
}

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
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 252, 250, 0.96)',
      borderColor: 'rgba(20, 83, 45, 0.15)',
      textStyle: { color: '#15241f' }
    },
    grid: { left: 44, right: 12, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: serviceNames,
      axisLabel: { rotate: serviceNames.length > 4 ? 28 : 0, color: '#6b7f76', fontSize: 11 },
      axisLine: { lineStyle: { color: 'rgba(20, 83, 45, 0.15)' } }
    },
    yAxis: {
      type: 'value',
      name: '%',
      nameTextStyle: { color: '#6b7f76' },
      axisLabel: { color: '#6b7f76' },
      splitLine: { lineStyle: { color: 'rgba(20, 83, 45, 0.08)' } }
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 36,
        data: errorRates,
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
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
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(255, 252, 250, 0.96)',
      borderColor: 'rgba(20, 83, 45, 0.15)',
      textStyle: { color: '#15241f' }
    },
    legend: {
      bottom: 0,
      type: 'scroll',
      textStyle: { color: '#3d524a', fontSize: 11 }
    },
    color: PIE_COLORS,
    series: [
      {
        type: 'pie',
        radius: ['38%', '64%'],
        center: ['50%', '46%'],
        data: pieData,
        label: {
          formatter: '{b}\n{d}%',
          color: '#15241f',
          fontSize: 11
        },
        itemStyle: {
          borderColor: '#fffcfa',
          borderWidth: 2
        }
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
  router.push({ path: '/traces', query: { service: serviceName, status: 'error', hours: String(hours.value) } })
}

const viewCategoryTraces = (key: string) => {
  router.push({ path: '/traces', query: { status: 'error', q: key, hours: String(hours.value) } })
}

const goErrorTraces = () => {
  router.push({ path: '/traces', query: { status: 'error', hours: String(hours.value) } })
}

async function onExplainErrors() {
  explaining.value = true
  try {
    const result = await ApiService.explainErrors(Number(hours.value))
    explainResult.value = result
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
.si-err-hours {
  min-width: 9.5rem;
  width: auto;
}

.si-err-hint-line {
  margin: -0.35rem 0 0.85rem;
  font-size: 0.78rem;
  color: var(--si-muted);
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
  border-radius: 12px;
  border: 1px solid rgba(21, 128, 61, 0.2);
  background:
    radial-gradient(ellipse at 50% 20%, rgba(21, 128, 61, 0.08), transparent 55%),
    var(--card-bg);
}

.si-err-healthy__icon {
  font-size: 2.75rem;
  color: #15803d;
  margin-bottom: 0.65rem;
}

.si-err-healthy__title {
  font-family: var(--font-display);
  font-size: 1.3rem;
  font-weight: 700;
  color: var(--si-ink);
  margin: 0 0 0.4rem;
}

.si-err-healthy__desc {
  max-width: 28rem;
  color: var(--si-muted);
  margin: 0 0 1.1rem;
  font-size: 0.9rem;
  line-height: 1.5;
}

.si-err-healthy__tips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem 0.85rem;
  justify-content: center;
  align-items: center;
  font-size: 0.78rem;
  color: var(--si-muted);
}

.si-err-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 1.25rem;
  align-items: baseline;
  padding: 0.55rem 0.9rem;
  border-radius: 8px;
  border: 1px solid var(--card-border);
  background: rgba(255, 252, 250, 0.65);
}

.si-err-strip__item {
  display: inline-flex;
  align-items: baseline;
  gap: 0.4rem;
}

.si-err-strip__label {
  font-size: 0.68rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--si-muted);
}

.si-err-strip__value {
  font-family: var(--font-display);
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--si-ink);
  font-variant-numeric: tabular-nums;
}

.si-err-strip__value.is-danger {
  color: #b91c1c;
}

.si-err-strip__value.is-warn {
  color: #b45309;
}

.si-err-stage,
.si-err-panel,
.si-err-chart,
.si-err-ai,
.si-err-fold {
  border-radius: 10px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
}

.si-err-stage {
  padding: 0.85rem 0.95rem 0.5rem;
}

.si-err-stage__head,
.si-err-panel__head,
.si-err-chart__head,
.si-err-ai__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.55rem;
}

.si-err-stage__title,
.si-err-panel__head h4,
.si-err-chart__head h4,
.si-err-ai__head h3 {
  margin: 0;
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-err-table .si-err-row {
  cursor: pointer;
  transition: background 0.15s ease;
}

.si-err-table .si-err-row:hover {
  background: rgba(15, 118, 110, 0.05);
}

.si-err-table .si-err-row.is-severe:hover {
  background: rgba(185, 28, 28, 0.06);
}

.si-err-svc {
  font-weight: 600;
  color: var(--si-ink);
}

.si-err-rate,
.si-err-level {
  display: inline-block;
  font-size: 0.78rem;
  font-weight: 700;
  padding: 0.12rem 0.45rem;
  border-radius: 5px;
}

.si-err-rate[data-level='severe'],
.si-err-level[data-level='severe'] {
  color: #991b1b;
  background: rgba(185, 28, 28, 0.1);
}

.si-err-rate[data-level='warn'],
.si-err-level[data-level='warn'] {
  color: #9a3412;
  background: rgba(180, 83, 9, 0.12);
}

.si-err-rate[data-level='note'],
.si-err-level[data-level='note'] {
  color: #0f766e;
  background: rgba(15, 118, 110, 0.1);
}

.si-err-charts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}

@media (max-width: 991px) {
  .si-err-charts {
    grid-template-columns: 1fr;
  }
}

.si-err-chart {
  padding: 0.75rem 0.85rem 0.85rem;
  display: flex;
  flex-direction: column;
  min-height: 260px;
}

.si-err-chart__canvas {
  flex: 1;
  min-height: 210px;
  border-radius: 8px;
  background: var(--si-paper);
  border: 1px solid var(--card-border);
  padding: 0.35rem;
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

.si-err-panel {
  padding: 0.75rem 0.85rem 0.5rem;
}

.si-err-panel__empty {
  padding: 1rem 0.25rem 1.25rem;
  font-size: 0.85rem;
  color: var(--si-muted);
}

.si-err-table--compact td,
.si-err-table--compact th {
  font-size: 0.84rem;
  vertical-align: middle;
}

.si-err-sample {
  max-width: 12rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.76rem;
  color: var(--si-muted);
}

.si-err-code {
  display: inline-block;
  padding: 0.12rem 0.45rem;
  border-radius: 5px;
  font-size: 0.78rem;
  font-weight: 700;
  color: #991b1b;
  background: rgba(185, 28, 28, 0.1);
}

.si-err-ex {
  font-size: 0.78rem;
  color: #9a3412;
  background: rgba(180, 83, 9, 0.08);
  padding: 0.1rem 0.35rem;
  border-radius: 4px;
}

.si-err-fold {
  padding: 0 0.95rem 0.75rem;
}

.si-err-fold__summary {
  cursor: pointer;
  list-style: none;
  padding: 0.75rem 0;
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--si-ink);
  user-select: none;
}

.si-err-fold__summary::-webkit-details-marker {
  display: none;
}

.si-err-fold__summary::before {
  content: '\f0da';
  font-family: FontAwesome, 'Font Awesome 5 Free', sans-serif;
  display: inline-block;
  width: 0.9rem;
  margin-right: 0.15rem;
  color: var(--si-muted);
  transition: transform 0.15s ease;
}

.si-err-fold[open] > .si-err-fold__summary::before {
  transform: rotate(90deg);
}

.si-err-ai {
  padding: 0.85rem 0.95rem;
}

.si-err-ai__md {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font-body);
  font-size: 0.88rem;
  line-height: 1.55;
  margin: 0;
  padding: 0.7rem 0.85rem;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.04);
  border: 1px solid var(--card-border);
  max-height: 22rem;
  overflow: auto;
  color: var(--si-ink);
}
</style>
