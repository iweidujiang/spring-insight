<template>
  <div>
    <!-- 页面标题 -->
    <div class="d-flex justify-content-between align-items-center mb-4">
      <div>
        <h2 class="page-title">
          <i class="fa fa-exclamation-triangle me-2 text-primary"></i>错误分析
        </h2>
        <p class="page-description">分析服务的错误率和错误调用情况</p>
      </div>
      <div>
        <span class="badge bg-info">
          <i class="fa fa-clock me-1"></i>
          <span>{{ currentTime }}</span>
        </span>
      </div>
    </div>

    <!-- 时间范围选择 -->
    <div class="row mb-4">
      <div class="col-md-4">
        <div class="card stat-card">
          <div class="card-body">
            <h5 class="card-title">
              <i class="fa fa-filter me-2"></i>时间范围
            </h5>
            <div class="d-flex align-items-center">
              <div class="me-3">
                <label for="hours-select" class="form-label">最近</label>
                <select id="hours-select" class="form-select" v-model="hours" @change="loadData">
                  <option value="1">1小时</option>
                  <option value="6">6小时</option>
                  <option value="12">12小时</option>
                  <option value="24" selected>24小时</option>
                  <option value="72">72小时</option>
                </select>
              </div>
              <div>
                <button class="btn btn-primary" @click="loadData" :disabled="loading">
                  <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i> 刷新数据
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 错误分析图表 -->
    <div class="row">
      <div class="col-lg-8">
        <div class="chart-container">
          <h5 class="mb-3">
            <i class="fa fa-bar-chart me-2 text-danger"></i>服务错误率分布
          </h5>
          <div id="error-rate-chart" class="w-100 h-100"></div>
        </div>
      </div>
      <div class="col-lg-4">
        <div class="chart-container">
          <h5 class="mb-3">
            <i class="fa fa-pie-chart me-2 text-danger"></i>错误调用占比
          </h5>
          <div id="error-pie-chart" class="w-100 h-100"></div>
        </div>
      </div>
    </div>

    <!-- 错误服务列表 -->
    <div class="row mt-4">
      <div class="col-12">
        <div class="card stat-card">
          <div class="card-body">
            <h5 class="card-title">
              <i class="fa fa-list me-2"></i>错误服务列表
            </h5>
            <div class="table-responsive">
              <table class="table table-hover">
                <thead class="table-light">
                  <tr>
                    <th>服务名称</th>
                    <th>总调用数</th>
                    <th>错误调用数</th>
                    <th>错误率</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="error in errorAnalysis" :key="error.serviceName">
                    <td>{{ error.serviceName }}</td>
                    <td>{{ error.totalCalls }}</td>
                    <td>{{ error.errorCalls }}</td>
                    <td>
                      <span class="badge" :class="error.errorRate > 10 ? 'bg-danger' : 'bg-warning'">
                        {{ error.errorRate.toFixed(2) }}%
                      </span>
                    </td>
                    <td>
                      <span v-if="error.errorRate > 10" class="badge bg-danger">严重</span>
                      <span v-else-if="error.errorRate <= 10 && error.errorRate > 5" class="badge bg-warning">警告</span>
                      <span v-else class="badge bg-info">注意</span>
                    </td>
                  </tr>
                  <tr v-if="errorAnalysis.length === 0">
                    <td colspan="5" class="text-center text-muted">暂无错误分析数据</td>
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
      title: {
        text: '服务错误率分布',
        left: 'center',
        textStyle: {
          fontSize: 16,
          color: '#333'
        }
      },
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
        bottom: '10%',
        top: '15%',
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
            if (value > 10) return '#e74a3b' // 严重 - 红色
            if (value > 5) return '#f6c23e' // 警告 - 黄色
            return '#36b9cc' // 注意 - 蓝色
          }
        },
        label: {
          show: true,
          position: 'top',
          formatter: '{c}%',
          fontSize: 11
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
      title: {
        text: '错误调用占比',
        left: 'center',
        textStyle: {
          fontSize: 16,
          color: '#333'
        }
      },
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
          borderWidth: 2
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
