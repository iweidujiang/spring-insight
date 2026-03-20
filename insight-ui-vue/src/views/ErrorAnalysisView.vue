<template>
  <div class="si-page fade-in">
    <div class="si-page__header">
      <div>
        <h2 class="page-title mb-1">
          <i class="fa fa-exclamation-triangle me-2"></i>错误分析
        </h2>
        <p class="page-description mb-0">服务错误率与错误调用占比</p>
      </div>
      <div class="si-page__toolbar">
        <button class="btn btn-primary" @click="loadData" :disabled="loading">
          <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i> 刷新数据
        </button>
        <button class="btn btn-outline-secondary" @click="downloadErrorData" :disabled="loading || errorAnalysis.length === 0">
          <i class="fa fa-download"></i> 导出
        </button>
        <span class="badge bg-info">
          <i class="fa fa-clock me-1"></i>{{ currentTime }}
        </span>
      </div>
    </div>

    <!-- 筛选与说明：整行工具条 -->
    <div class="card stat-card si-toolbar-card">
      <div class="card-body">
        <h5 class="card-title">
          <i class="fa fa-filter me-2"></i>筛选
        </h5>
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
          <p class="text-muted small mb-0 align-self-center flex-grow-1" style="min-width: 12rem">
            仅统计存在错误调用的服务；无异常时图表显示占位说明。
          </p>
        </div>
      </div>
    </div>

    <div v-if="loading" class="loading-spinner">
      <i class="fa fa-spinner fa-spin"></i>
      <span class="ms-2">正在加载错误分析数据...</span>
    </div>

    <div v-show="!loading">
      <!-- 两列等宽：柱状图 | 饼图（v-show 保留 DOM 供 ECharts 初始化） -->
      <div class="si-charts-row">
        <div class="chart-container si-chart-panel">
          <div class="d-flex justify-content-between align-items-center mb-2 flex-shrink-0">
            <h5 class="mb-0">
              <i class="fa fa-bar-chart me-2"></i>服务错误率分布
            </h5>
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
            <h5 class="mb-0">
              <i class="fa fa-pie-chart me-2"></i>错误调用占比
            </h5>
          </div>
          <div class="si-chart-canvas-wrap">
            <div id="error-pie-chart" class="w-100 h-100" style="min-height: 220px"></div>
          </div>
        </div>
      </div>

      <div class="card stat-card si-table-panel">
        <div class="card-body">
          <div class="d-flex justify-content-between align-items-center mb-2">
            <h5 class="card-title mb-0">
              <i class="fa fa-list me-2"></i>错误服务列表
            </h5>
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
                  class="fade-in"
                  :style="{ animationDelay: `${index * 0.05}s` }"
                  :class="error.errorRate > 10 ? 'table-danger' : error.errorRate > 5 ? 'table-warning' : ''"
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
                    <span v-else-if="error.errorRate <= 10 && error.errorRate > 5" class="badge bg-warning">警告</span>
                    <span v-else class="badge bg-info">注意</span>
                  </td>
                  <td>
                    <button class="btn btn-sm btn-primary" @click="viewServiceDetails(error.serviceName)">
                      <i class="fa fa-eye"></i> 详情
                    </button>
                  </td>
                </tr>
                <tr v-if="errorAnalysis.length === 0">
                  <td colspan="6" class="text-center text-muted py-4">
                    <i class="fa fa-check-circle fa-2x text-success mb-2 d-block"></i>
                    <span>暂无错误分析数据 · 所有服务运行正常</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { ApiService } from '../services/ApiService'

const loading = ref(true)
const currentTime = ref('')
const hours = ref(24)
const errorAnalysis = ref<any[]>([])

let errorRateChart: echarts.ECharts | null = null
let errorPieChart: echarts.ECharts | null = null
let timeInterval: number | null = null

const updateCurrentTime = () => {
  currentTime.value = new Date().toTimeString().split(' ')[0]
}

const initCharts = () => {
  const errorRateChartDom = document.getElementById('error-rate-chart')
  if (errorRateChartDom) {
    errorRateChart = echarts.init(errorRateChartDom)
    errorRateChart.setOption({
      backgroundColor: 'transparent',
      textStyle: { color: '#94a3b8' },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: function (params: any) {
          const data = params[0]
          return `${data.name}<br/>错误率: ${data.value.toFixed(2)}%`
        }
      },
      grid: { left: '3%', right: '4%', bottom: '18%', top: '10%', containLabel: true },
      xAxis: {
        type: 'category',
        data: [],
        axisLine: { lineStyle: { color: '#334155' } },
        axisLabel: { fontSize: 10, rotate: 40, color: '#94a3b8' },
        boundaryGap: true
      },
      yAxis: {
        type: 'value',
        name: '错误率 (%)',
        nameTextStyle: { fontSize: 11, color: '#94a3b8' },
        splitLine: { lineStyle: { color: 'rgba(51, 65, 85, 0.5)' } },
        axisLabel: { fontSize: 10, color: '#94a3b8' },
        max: 100
      },
      series: [{
        name: '错误率',
        type: 'bar',
        data: [],
        itemStyle: {
          color: function (params: any) {
            const v = params.value
            if (v > 10) return '#ef4444'
            if (v > 5) return '#f59e0b'
            return '#06b6d4'
          },
          borderRadius: [4, 4, 0, 0]
        },
        label: { show: true, position: 'top', formatter: '{c}%', fontSize: 10, color: '#e2e8f0' }
      }]
    })
  }

  const errorPieChartDom = document.getElementById('error-pie-chart')
  if (errorPieChartDom) {
    errorPieChart = echarts.init(errorPieChartDom)
    errorPieChart.setOption({
      backgroundColor: 'transparent',
      textStyle: { color: '#94a3b8' },
      tooltip: { trigger: 'item', formatter: '{b}: {c} 次 ({d}%)' },
      legend: {
        orient: 'vertical',
        right: 6,
        top: 'middle',
        type: 'scroll',
        textStyle: { color: '#e2e8f0', fontSize: 12, fontWeight: 500 },
        pageTextStyle: { color: '#94a3b8' },
        formatter: (name: string) => (name.length > 14 ? name.substring(0, 14) + '…' : name)
      },
      series: [{
        name: '错误调用',
        type: 'pie',
        radius: ['36%', '62%'],
        center: ['42%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 8,
          borderColor: 'rgba(15, 23, 42, 0.9)',
          borderWidth: 2,
          color: function (params: any) {
            const colorList = ['#ef4444', '#f59e0b', '#06b6d4', '#8b5cf6', '#10b981', '#3b82f6', '#f97316', '#14b8a6']
            return colorList[params.dataIndex % colorList.length]
          }
        },
        label: { show: false },
        emphasis: {
          label: { show: true, fontSize: 14, fontWeight: 'bold', color: '#f8fafc' },
          itemStyle: { shadowBlur: 12, shadowColor: 'rgba(0, 0, 0, 0.45)' }
        },
        labelLine: { show: false },
        data: []
      }]
    })
  }
}

const updateCharts = () => {
  if (errorRateChart) {
    const serviceNames: string[] = []
    const errorRates: number[] = []
    errorAnalysis.value.forEach((error: any) => {
      serviceNames.push(error.serviceName)
      errorRates.push(error.errorRate)
    })
    errorRateChart.setOption({
      graphic: serviceNames.length === 0
        ? [{
            type: 'text',
            left: 'center',
            top: 'center',
            style: {
              text: '暂无错误率数据\n当前无异常服务',
              fill: '#94a3b8',
              fontSize: 13,
              textAlign: 'center',
              lineHeight: 22
            }
          }]
        : [],
      xAxis: { data: serviceNames },
      series: [{ data: errorRates }]
    })
  }

  if (errorPieChart) {
    const pieData = errorAnalysis.value.map((error: any) => ({
      name: error.serviceName,
      value: error.errorCalls
    }))
    errorPieChart.setOption({
      graphic: pieData.length === 0
        ? [{
            type: 'text',
            left: 'center',
            top: 'center',
            style: {
              text: '暂无错误分布\n服务运行正常',
              fill: '#94a3b8',
              fontSize: 13,
              textAlign: 'center',
              lineHeight: 22
            }
          }]
        : [],
      series: [{ data: pieData }]
    })
  }
}

const refreshCharts = () => {
  errorRateChart?.resize()
  errorPieChart?.resize()
  updateCharts()
}

const viewServiceDetails = (serviceName: string) => {
  console.log('查看服务详情:', serviceName)
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
    updateCharts()
  } catch (error) {
    console.error('加载错误分析数据失败:', error)
  } finally {
    loading.value = false
    await nextTick()
    errorRateChart?.resize()
    errorPieChart?.resize()
  }
}

const handleResize = () => {
  errorRateChart?.resize()
  errorPieChart?.resize()
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
  errorRateChart?.dispose()
  errorPieChart?.dispose()
  if (timeInterval) clearInterval(timeInterval)
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.page-description {
  font-size: 0.95rem;
}
</style>
