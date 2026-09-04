<template>
  <div class="si-page fade-in">
    <div class="si-page__header">
      <div>
        <h2 class="page-title mb-1">
          <i class="fa fa-exclamation-triangle me-2"></i>错误分析
        </h2>
        <p class="page-description mb-0">按时间窗口统计各服务错误调用与错误率</p>
      </div>
      <div class="si-page__toolbar">
        <button class="btn btn-primary" @click="loadData" :disabled="loading">
          <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i> 刷新
        </button>
        <button class="btn btn-outline-secondary" @click="downloadErrorData" :disabled="loading || errorAnalysis.length === 0">
          <i class="fa fa-download"></i> 导出
        </button>
        <span class="badge bg-info">
          <i class="fa fa-clock me-1"></i>{{ currentTime }}
        </span>
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
            </select>
          </div>
          <div class="si-err-hint" title="仅列出存在错误调用的服务">
            <i class="fa fa-info-circle me-1"></i>
            仅展示有错误调用的服务；全部正常时显示健康状态
          </div>
        </div>
      </div>
    </div>

    <div v-if="loading" class="loading-spinner">
      <i class="fa fa-spinner fa-spin"></i>
      <span class="ms-2">正在加载错误分析数据...</span>
    </div>

    <div v-show="!loading" class="si-err-body">
      <!-- 健康空态：不画空饼图，避免文字被遮挡 -->
      <div v-if="errorAnalysis.length === 0" class="si-err-healthy">
        <div class="si-err-healthy__icon">
          <i class="fa fa-check-circle"></i>
        </div>
        <h3 class="si-err-healthy__title">运行正常</h3>
        <p class="si-err-healthy__desc">
          所选时间范围内未发现错误调用，所有已上报服务状态良好。
        </p>
        <div class="si-err-healthy__tips">
          <span><i class="fa fa-bolt me-1"></i>可在业务侧制造失败请求后再刷新本页</span>
          <span><i class="fa fa-stream me-1"></i>也可到「链路追踪」按状态筛选排查</span>
        </div>
      </div>

      <template v-else>
        <div class="si-err-summary">
          <div class="si-err-summary__card">
            <span class="si-err-summary__label">异常服务</span>
            <span class="si-err-summary__value text-danger">{{ errorAnalysis.length }}</span>
          </div>
          <div class="si-err-summary__card">
            <span class="si-err-summary__label">错误调用合计</span>
            <span class="si-err-summary__value">{{ totalErrorCalls }}</span>
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
              <h5 class="mb-0"><i class="fa fa-pie-chart me-2"></i>错误调用占比</h5>
            </div>
            <div class="si-chart-canvas-wrap">
              <div id="error-pie-chart" class="w-100 h-100" style="min-height: 220px"></div>
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
                        <i class="fa fa-stream"></i> 相关链路
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
const currentTime = ref('')
const hours = ref(24)
const errorAnalysis = ref<any[]>([])

let errorRateChart: echarts.ECharts | null = null
let errorPieChart: echarts.ECharts | null = null
let timeInterval: number | null = null
let chartsReady = false

const totalErrorCalls = computed(() =>
  errorAnalysis.value.reduce((sum, e) => sum + (e.errorCalls || 0), 0)
)
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
        return `${data.name}<br/>错误率: ${Number(data.value).toFixed(2)}%`
      }
    },
    grid: { left: 8, right: 12, bottom: 48, top: 28, containLabel: true },
    xAxis: {
      type: 'category',
      data: serviceNames,
      axisLine: { lineStyle: { color: 'rgba(20, 83, 45, 0.25)' } },
      axisLabel: { fontSize: 10, rotate: 28, color: '#6b7f76' }
    },
    yAxis: {
      type: 'value',
      name: '%',
      max: 100,
      nameTextStyle: { color: '#6b7f76' },
      splitLine: { lineStyle: { color: 'rgba(20, 83, 45, 0.1)' } },
      axisLabel: { color: '#6b7f76' }
    },
    series: [{
      name: '错误率',
      type: 'bar',
      data: errorRates,
      itemStyle: {
        color: (params: any) => {
          const v = params.value
          if (v > 10) return '#b91c1c'
          if (v > 5) return '#b45309'
          return '#0d9488'
        },
        borderRadius: [4, 4, 0, 0]
      },
      label: { show: true, position: 'top', formatter: '{c}%', fontSize: 10, color: '#3d524a' }
    }]
  }, { notMerge: true })

  errorPieChart.setOption({
    backgroundColor: 'transparent',
    textStyle: { color: '#6b7f76' },
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} 次 ({d}%)',
      backgroundColor: 'rgba(255, 252, 250, 0.96)',
      borderColor: 'rgba(20, 83, 45, 0.15)',
      textStyle: { color: '#15241f' }
    },
    legend: {
      orient: 'vertical',
      right: 4,
      top: 'middle',
      type: 'scroll',
      textStyle: { color: '#15241f', fontSize: 12 }
    },
    series: [{
      name: '错误调用',
      type: 'pie',
      radius: ['40%', '68%'],
      center: ['38%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: {
        borderRadius: 8,
        borderColor: '#fffcfa',
        borderWidth: 2
      },
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 13, fontWeight: 'bold', color: '#15241f' }
      },
      data: pieData
    }]
  }, { notMerge: true })
}

const refreshCharts = () => {
  errorRateChart?.resize()
  errorPieChart?.resize()
  updateCharts()
}

const viewServiceDetails = (serviceName: string) => {
  router.push({ path: '/traces', query: { service: serviceName } })
}

const downloadErrorData = () => {
  const dataStr = JSON.stringify(errorAnalysis.value, null, 2)
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
    errorAnalysis.value = await ApiService.getErrorAnalysis(hours.value)
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
  grid-template-columns: repeat(3, 1fr);
  gap: 0.65rem;
}

@media (max-width: 767px) {
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
  font-size: clamp(1.45rem, 2.2vw, 1.75rem);
  font-weight: 700;
  color: var(--si-ink);
  line-height: 1.15;
}
</style>
