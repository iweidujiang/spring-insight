<template>
  <div class="si-page fade-in si-err-page">
    <SiPageHeader title="错误分析" icon="WarningFilled">
      <SiFilterBar
        v-model:hours="hours"
        :show-service="false"
        :loading="loading"
        hours-id="hours-select-err"
        @hours-change="onHoursChange"
        @refresh="loadData"
      >
        <template #leading>
          <el-button
            type="primary"
            plain
            size="small"
            :loading="explaining"
            :disabled="loading || explaining || !hasErrors || !aiReady"
            :title="aiReady ? (aiStatusReason || '用当前聚合摘要调用 AI') : (aiStatusReason || '请先在设置中启用 AI 并配置密钥')"
            @click="onExplainErrors"
          >
            <i v-if="!explaining" class="fa fa-magic me-1" aria-hidden="true"></i>
            {{ explaining ? '解读中…' : '一键解读' }}
          </el-button>
        </template>
        <el-button
          size="small"
          :loading="exporting"
          :disabled="loading || exporting || !hasErrors"
          title="按当前时间窗下载错误分析 CSV"
          @click="exportCsv"
        >
          <i v-if="!exporting" class="fa fa-download me-1" aria-hidden="true"></i>
          导出 CSV
        </el-button>
        <el-tag type="info" effect="plain" size="small" round>
          <i class="fa fa-clock me-1" aria-hidden="true"></i>{{ currentTime }}
        </el-tag>
      </SiFilterBar>
    </SiPageHeader>

    <SiAlert v-if="loadError" tone="danger" class="mb-3">{{ loadError }}</SiAlert>

    <SiLoading v-if="loading" text="正在加载错误分析数据…" />

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
          <el-button size="small" @click="goErrorTraces">
            到链路追踪按异常筛选
          </el-button>
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
            <el-tag type="danger" effect="dark" size="small" round>{{ errorAnalysis.length }} 个</el-tag>
          </div>
          <SiDataTable
            :rows="errorAnalysis"
            :columns="errorServiceColumns"
            row-key="serviceName"
            clickable
            :row-class="errorServiceRowClass"
            :row-style="errorServiceRowStyle"
            empty-text="暂无错误服务"
            @row-click="onErrorServiceClick"
          >
            <template #cell-rate="{ row }">
              <span class="si-err-rate" :data-level="rateLevel(row.errorRate)">
                {{ Number(row.errorRate).toFixed(2) }}%
              </span>
            </template>
            <template #cell-level="{ row }">
              <span class="si-err-level" :data-level="rateLevel(row.errorRate)">
                {{ rateLabel(row.errorRate) }}
              </span>
            </template>
          </SiDataTable>
        </section>

        <div class="si-err-charts">
          <div class="si-err-chart">
            <div class="si-err-chart__head">
              <h4><i class="fa fa-bar-chart me-2"></i>服务错误率</h4>
              <el-button type="primary" plain size="small" title="重绘" @click="refreshCharts">
                <i class="fa fa-refresh" aria-hidden="true"></i>
              </el-button>
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
              <el-tag type="info" effect="plain" size="small" round>{{ byStatusCode.length }}</el-tag>
            </div>
            <div v-if="byStatusCode.length === 0" class="si-err-panel__empty">暂无 HTTP 状态类错误</div>
            <SiDataTable
              v-else
              class="si-err-compact"
              :rows="byStatusCode"
              :columns="statusColumns"
              row-key="key"
              clickable
              :row-class="categoryRowClass"
              @row-click="onCategoryClick"
            >
              <template #cell-label="{ row }">
                <span class="si-err-code">{{ row.label }}</span>
              </template>
              <template #cell-services="{ row }">
                {{ bucketServiceNames(row) }}
                <span v-if="row.serviceCount > 2" class="text-muted"> 等{{ row.serviceCount }}</span>
              </template>
            </SiDataTable>
          </section>

          <section class="si-err-panel">
            <div class="si-err-panel__head">
              <h4><i class="fa fa-bug me-2"></i>按异常类</h4>
              <el-tag type="warning" effect="plain" size="small" round>{{ byException.length }}</el-tag>
            </div>
            <div v-if="byException.length === 0" class="si-err-panel__empty">暂无异常类错误</div>
            <SiDataTable
              v-else
              class="si-err-compact"
              :rows="byException"
              :columns="exceptionColumns"
              row-key="key"
              clickable
              :row-class="categoryRowClass"
              @row-click="onCategoryClick"
            >
              <template #cell-label="{ row }">
                <code class="si-err-ex">{{ row.label }}</code>
              </template>
              <template #cell-services="{ row }">
                {{ bucketServiceNames(row) }}
                <span v-if="row.serviceCount > 2" class="text-muted"> 等{{ row.serviceCount }}</span>
              </template>
            </SiDataTable>
          </section>
        </div>

        <details v-if="byOther.length > 0" class="si-err-fold">
          <summary class="si-err-fold__summary">
            <i class="fa fa-question-circle me-2"></i>其它错误（{{ byOther.length }}）
          </summary>
          <SiDataTable
            class="si-err-compact"
            :rows="byOther"
            :columns="otherColumns"
            row-key="key"
            clickable
            :row-class="categoryRowClass"
            @row-click="onCategoryClick"
          />
        </details>

        <AiExplainPanel
          v-if="explainResult"
          :result="explainResult"
          title="AI 解读"
          :meta="[explainResult.provider, explainResult.model].filter(Boolean).join(' · ')"
          @close="explainResult = null"
        />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ApiService, type AiExplainResult } from '../services/ApiService'
import AiExplainPanel from '../components/AiExplainPanel.vue'
import SiAlert from '../components/SiAlert.vue'
import SiDataTable from '../components/SiDataTable.vue'
import SiFilterBar from '../components/SiFilterBar.vue'
import SiLoading from '../components/SiLoading.vue'
import SiPageHeader from '../components/SiPageHeader.vue'
import type { SiColumn } from '../components/siTable'
import { useChartTheme } from '../composables/useChartTheme'
import { formatHoursLabel, loadFailedMessage } from '../utils/timeRange'
import { mergeQuery, persistHours, resolveHours } from '../utils/insightQuery'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const loadError = ref('')
const explaining = ref(false)
const explainResult = ref<AiExplainResult | null>(null)
const aiReady = ref(false)
const aiStatusReason = ref('')
const currentTime = ref('')
const hours = ref(24)
const errorAnalysis = ref<any[]>([])
const byStatusCode = ref<any[]>([])
const byException = ref<any[]>([])
const byOther = ref<any[]>([])
const totalErrorSpans = ref(0)
const exporting = ref(false)

let errorRateChart: echarts.ECharts | null = null
let errorPieChart: echarts.ECharts | null = null
let timeInterval: number | null = null
let chartsReady = false

const { colors: chartColors, palette: chartPalette } = useChartTheme()

const hasErrors = computed(() => totalErrorSpans.value > 0 || errorAnalysis.value.length > 0)
const maxErrorRate = computed(() => {
  if (errorAnalysis.value.length === 0) return '0.00'
  return Math.max(...errorAnalysis.value.map((e) => e.errorRate || 0)).toFixed(2)
})

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

type ErrorServiceRow = Record<string, any>

const errorServiceColumns: SiColumn<ErrorServiceRow>[] = [
  { key: 'service', label: '服务', cellClass: 'si-err-svc', value: (row) => row.serviceName },
  { key: 'total', label: '总调用', value: (row) => row.totalCalls },
  { key: 'errors', label: '错误', cellClass: 'text-danger fw-semibold', value: (row) => row.errorCalls },
  { key: 'rate', label: '错误率' },
  { key: 'level', label: '级别' }
]

const errorServiceRowClass = (row: ErrorServiceRow) => {
  const rate = Number(row.errorRate) || 0
  if (rate > 10) return 'si-err-row is-severe'
  if (rate > 5) return 'si-err-row is-warn'
  return 'si-err-row'
}

const errorServiceRowStyle = (_row: ErrorServiceRow, index: number) => ({
  animationDelay: `${Math.min(index, 20) * 0.03}s`
})

const onErrorServiceClick = (row: ErrorServiceRow) => {
  viewServiceDetails(String(row.serviceName || ''))
}

type ErrorBucketRow = Record<string, any>

const bucketServiceNames = (row: ErrorBucketRow) => {
  const names = Array.isArray(row.services) ? row.services.map((name: unknown) => String(name)) : []
  return names.slice(0, 2).join('、')
}

const categorySampleColumn = (label: string): SiColumn<ErrorBucketRow> => ({
  key: 'sample',
  label,
  cellClass: 'si-err-sample',
  value: (row) => row.sampleMessage || '—',
  title: (row) => (row.sampleMessage ? String(row.sampleMessage) : undefined)
})

const statusColumns: SiColumn<ErrorBucketRow>[] = [
  { key: 'label', label: '状态码' },
  { key: 'count', label: '次数', cellClass: 'text-danger fw-semibold', value: (row) => row.count },
  { key: 'services', label: '服务', cellClass: 'small' },
  categorySampleColumn('样例')
]

const exceptionColumns: SiColumn<ErrorBucketRow>[] = [
  { key: 'label', label: '异常' },
  { key: 'count', label: '次数', cellClass: 'text-danger fw-semibold', value: (row) => row.count },
  { key: 'services', label: '服务', cellClass: 'small' },
  categorySampleColumn('样例')
]

const otherColumns: SiColumn<ErrorBucketRow>[] = [
  { key: 'label', label: '分类', value: (row) => row.label },
  { key: 'count', label: '次数', value: (row) => row.count },
  {
    key: 'services',
    label: '服务',
    cellClass: 'small',
    value: (row) => {
      const names = Array.isArray(row.services) ? row.services.map((name: unknown) => String(name)) : []
      return names.join('、') || '—'
    }
  }
]

const categoryRowClass = () => 'si-err-row'

const onCategoryClick = (row: ErrorBucketRow) => {
  viewCategoryTraces(String(row.key || ''))
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

  const c = chartColors.value
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
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText }
    },
    grid: { left: 44, right: 12, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: serviceNames,
      axisLabel: { rotate: serviceNames.length > 4 ? 28 : 0, color: c.muted, fontSize: 11 },
      axisLine: { lineStyle: { color: c.axisLine } }
    },
    yAxis: {
      type: 'value',
      name: '%',
      nameTextStyle: { color: c.muted },
      axisLabel: { color: c.muted },
      splitLine: { lineStyle: { color: c.splitLine } }
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
            if (v > 10) return c.danger
            if (v > 5) return c.warning
            return c.success
          }
        }
      }
    ]
  })

  errorPieChart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText }
    },
    legend: {
      bottom: 0,
      type: 'scroll',
      textStyle: { color: c.inkSoft, fontSize: 11 }
    },
    color: chartPalette.value,
    series: [
      {
        type: 'pie',
        radius: ['38%', '64%'],
        center: ['50%', '46%'],
        data: pieData,
        label: {
          formatter: '{b}\n{d}%',
          color: c.ink,
          fontSize: 11
        },
        itemStyle: {
          borderColor: c.surface,
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

watch(() => chartColors.value, () => {
  updateCharts()
})

const viewServiceDetails = (serviceName: string) => {
  const name = String(serviceName || '').trim()
  if (!name) return
  router.push({
    path: `/services/${encodeURIComponent(name)}`,
    query: { hours: String(hours.value) }
  })
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
    aiStatusReason.value = st.reason || ''
  } catch {
    aiReady.value = false
    aiStatusReason.value = ''
  }
}

const exportCsv = async () => {
  if (exporting.value) return
  exporting.value = true
  try {
    await ApiService.exportFile(
      '/export/errors',
      { hours: hours.value, format: 'csv' },
      'errors.csv'
    )
  } catch (error) {
    console.error('导出错误分析 CSV 失败:', error)
    loadError.value = '导出 CSV 失败'
  } finally {
    exporting.value = false
  }
}

const onHoursChange = async () => {
  persistHours(hours.value)
  await router.replace({
    path: route.path,
    query: mergeQuery(route.query, { hours: String(hours.value) })
  })
  await loadData()
}

const loadData = async () => {
  try {
    loading.value = true
    loadError.value = ''
    const breakdown = await ApiService.getErrorBreakdown(hours.value)
    errorAnalysis.value = breakdown.byService
    byStatusCode.value = breakdown.byStatusCode
    byException.value = breakdown.byException
    byOther.value = breakdown.byOther
    totalErrorSpans.value = breakdown.totalErrorSpans
  } catch (error) {
    console.error('加载错误分析数据失败:', error)
    loadError.value = loadFailedMessage('错误分析')
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
  hours.value = resolveHours(route.query, 24)
  persistHours(hours.value)
  if (route.query.hours == null || route.query.hours === '') {
    await router.replace({
      path: route.path,
      query: mergeQuery(route.query, { hours: String(hours.value) })
    })
  }
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
  border: 1px solid var(--si-ok-bullet-border);
  background:
    radial-gradient(ellipse at 50% 20%, var(--si-ok-bullet-bg), transparent 55%),
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
  background: var(--card-bg);
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
  font-family: var(--font-numeric);
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

:deep(.si-err-row) {
  cursor: pointer;
  transition: background 0.15s ease;
}

:deep(.si-err-row:hover) {
  background: rgba(15, 118, 110, 0.05);
}

:deep(.si-err-row.is-severe:hover) {
  background: rgba(185, 28, 28, 0.06);
}

.si-err-svc,
:deep(.si-err-svc) {
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

.si-err-compact :deep(td),
.si-err-compact :deep(th) {
  font-size: 0.84rem;
  vertical-align: middle;
}

:deep(.si-err-sample) {
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
