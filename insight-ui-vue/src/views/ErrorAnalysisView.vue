<template>
  <div class="fade-in">
    <!-- 页面标题 -->
    <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
      <div>
        <h2 class="page-title">
          <i class="fa fa-exclamation-triangle me-2"></i>错误分析
        </h2>
        <p class="page-description">分析服务的错误率和错误调用情况</p>
      </div>
      <div class="d-flex align-items-center gap-3">
        <button class="btn btn-primary" @click="loadData" :disabled="loading">
          <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i> 刷新数据
        </button>
        <button class="btn btn-outline-secondary" @click="downloadErrorData" :disabled="loading || errorAnalysis.length === 0">
          <i class="fa fa-download"></i> 导出数据
        </button>
        <span class="badge bg-info">
          <i class="fa fa-clock me-1"></i>
          <span>{{ currentTime }}</span>
        </span>
      </div>
    </div>

    <!-- 时间范围选择 -->
    <div class="row mb-4">
      <div class="col-md-6 col-sm-12">
        <div class="card stat-card">
          <div class="card-body">
            <h5 class="card-title">
              <i class="fa fa-filter me-2"></i>时间范围
            </h5>
            <div class="d-flex flex-wrap align-items-center gap-3">
              <div class="flex-grow-1">
                <label for="hours-select" class="form-label">最近</label>
                <select id="hours-select" class="form-select" v-model="hours" @change="loadData">
                  <option value="1">1小时</option>
                  <option value="6">6小时</option>
                  <option value="12">12小时</option>
                  <option value="24" selected>24小时</option>
                  <option value="72">72小时</option>
                </select>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 加载动画 -->
    <div v-if="loading" class="loading-spinner">
      <i class="fa fa-spinner fa-spin"></i>
      <span class="ms-2">正在加载错误分析数据...</span>
    </div>

    <!-- 错误分析图表 -->
    <div class="row gap-4 mb-4">
      <div class="col-lg-8 col-md-12">
        <div class="chart-container">
          <div class="d-flex justify-content-between align-items-center mb-3">
            <h5>
              <i class="fa fa-bar-chart me-2"></i>服务错误率分布
            </h5>
            <button class="btn btn-sm btn-outline-primary" @click="refreshCharts">
              <i class="fa fa-refresh"></i> 刷新图表
            </button>
          </div>
          <div id="error-rate-chart" class="w-100 h-100"></div>
        </div>
      </div>
      <div class="col-lg-4 col-md-12">
        <div class="chart-container">
          <div class="d-flex justify-content-between align-items-center mb-3">
            <h5>
              <i class="fa fa-pie-chart me-2"></i>错误调用占比
            </h5>
          </div>
          <div id="error-pie-chart" class="w-100 h-100"></div>
        </div>
      </div>
    </div>

    <!-- 错误服务列表 -->
    <div class="row">
      <div class="col-12">
        <div class="card stat-card">
          <div class="card-body">
            <div class="d-flex justify-content-between align-items-center mb-3">
              <h5 class="card-title">
                <i class="fa fa-list me-2"></i>错误服务列表
              </h5>
              <span class="badge bg-danger">{{ errorAnalysis.length }} 个异常服务</span>
            </div>
            <div class="table-responsive">
              <table class="table table-hover">
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
                  <tr v-for="(error, index) in errorAnalysis" :key="error.serviceName" class="fade-in" :style="{ animationDelay: `${index * 0.05}s` }" :class="error.errorRate > 10 ? 'table-danger' : error.errorRate > 5 ? 'table-warning' : ''">
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
                        <i class="fa fa-eye"></i> 查看详情
                      </button>
                    </td>
                  </tr>
                  <tr v-if="errorAnalysis.length === 0">
                    <td colspan="6" class="text-center text-muted">
                      <div class="py-4">
                        <i class="fa fa-check-circle fa-2x text-success mb-2"></i>
                        <p>暂无错误分析数据</p>
                        <p class="text-sm">所有服务运行正常</p>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { ApiService } from '../services/ApiService'

// 响应式数据
const loading = ref(true)
const currentTime = ref('')
const hours = ref(24)
const errorAnalysis = ref<any[]>([])

// 图表实例
let errorRateChart: echarts.ECharts | null = null
let errorPieChart: echarts.ECharts | null = null

// 时间更新定时器
let timeInterval: number | null = null

// 更新当前时间
const updateCurrentTime = () => {
  const now = new Date()
  currentTime.value = now.toTimeString().split(' ')[0]
}

// 初始化图表
const initCharts = () => {
  // 错误率柱状图
  const errorRateChartDom = document.getElementById('error-rate-chart')
  if (errorRateChartDom) {
    errorRateChart = echarts.init(errorRateChartDom)
    const option = {
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow'
        },
        formatter: function(params: any) {
          const data = params[0]
          return `${data.name}<br/>错误率: ${data.value.toFixed(2)}%`
        }
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '15%',
        top: '5%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        data: [],
        axisLabel: {
          fontSize: 11,
          rotate: 45
        },
        boundaryGap: true
      },
      yAxis: {
        type: 'value',
        name: '错误率 (%)',
        nameTextStyle: {
          fontSize: 12
        },
        axisLabel: {
          fontSize: 11
        },
        max: 100
      },
      series: [{
        name: '错误率',
        type: 'bar',
        data: [],
        itemStyle: {
          color: function(params: any) {
            const value = params.value
            if (value > 10) return '#ef4444' // 严重 - 红色
            if (value > 5) return '#f59e0b' // 警告 - 黄色
            return '#06b6d4' // 注意 - 蓝色
          },
          borderRadius: [4, 4, 0, 0]
        },
        label: {
          show: true,
          position: 'top',
          formatter: '{c}%',
          fontSize: 11
        },
        animationDelay: function(idx: number) {
          return idx * 100
        }
      }]
    }
    errorRateChart.setOption(option)
  }
  
  // 错误调用占比饼图
  const errorPieChartDom = document.getElementById('error-pie-chart')
  if (errorPieChartDom) {
    errorPieChart = echarts.init(errorPieChartDom)
    const option = {
      tooltip: {
        trigger: 'item',
        formatter: '{b}: {c} 次 ({d}%)'
      },
      legend: {
        orient: 'vertical',
        right: 10,
        top: 'center',
        type: 'scroll',
        formatter: function(name: string) {
          return name.length > 15 ? name.substring(0, 15) + '...' : name
        }
      },
      series: [{
        name: '错误调用',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['40%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2,
          color: function(params: any) {
            const colorList = [
              '#ef4444', '#f59e0b', '#06b6d4', '#8b5cf6',
              '#10b981', '#3b82f6', '#f97316', '#14b8a6'
            ]
            return colorList[params.dataIndex % colorList.length]
          }
        },
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold'
          },
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        },
        labelLine: {
          show: false
        },
        data: []
      }]
    }
    errorPieChart.setOption(option)
  }
}

// 更新图表数据
const updateCharts = () => {
  if (errorRateChart) {
    // 处理错误率柱状图数据
    const serviceNames = []
    const errorRates = []
    
    errorAnalysis.value.forEach((error: any) => {
      serviceNames.push(error.serviceName)
      errorRates.push(error.errorRate)
    })
    
    // 更新错误率柱状图
    errorRateChart.setOption({
      xAxis: {
        data: serviceNames
      },
      series: [{
        data: errorRates
      }]
    })
  }
  
  if (errorPieChart) {
    // 处理错误调用饼图数据
    const pieData = errorAnalysis.value.map((error: any) => ({
      name: error.serviceName,
      value: error.errorCalls
    }))
    
    // 更新饼图数据
    errorPieChart.setOption({
      series: [{
        data: pieData
      }]
    })
  }
}

// 刷新图表
const refreshCharts = () => {
  if (errorRateChart) {
    errorRateChart.resize()
  }
  if (errorPieChart) {
    errorPieChart.resize()
  }
  updateCharts()
}

// 查看服务详情
const viewServiceDetails = (serviceName: string) => {
  console.log('查看服务详情:', serviceName)
  // 这里可以添加跳转到服务详情页面的逻辑
}

// 导出错误数据
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

// 加载数据
const loadData = async () => {
  try {
    loading.value = true
    
    // 加载错误分析数据
    const errorAnalysisData = await ApiService.getErrorAnalysis(hours.value)
    errorAnalysis.value = errorAnalysisData
    
    // 更新图表
    updateCharts()
  } catch (error) {
    console.error('加载错误分析数据失败:', error)
  } finally {
    loading.value = false
  }
}

// 监听窗口大小变化
const handleResize = () => {
  errorRateChart?.resize()
  errorPieChart?.resize()
}

// 组件挂载时初始化
onMounted(() => {
  // 初始化图表
  initCharts()
  
  // 加载数据
  loadData()
  
  // 启动时间更新
  updateCurrentTime()
  timeInterval = window.setInterval(updateCurrentTime, 1000)
  
  // 监听窗口大小变化
  window.addEventListener('resize', handleResize)
})

// 组件卸载时清理
onUnmounted(() => {
  // 销毁图表实例
  errorRateChart?.dispose()
  errorPieChart?.dispose()
  
  // 清理定时器
  if (timeInterval) {
    clearInterval(timeInterval)
  }
  
  // 移除事件监听
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
/* 组件特定样式 */
</style>
