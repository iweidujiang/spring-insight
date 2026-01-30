<template>
  <div class="fade-in">
    <!-- 页面标题 -->
    <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
      <div>
        <h2 class="page-title">
          <i class="fa fa-tachometer-alt me-2"></i>监控仪表盘
        </h2>
        <p class="page-description">实时监控您的 Spring Boot 应用架构</p>
      </div>
      <div class="d-flex align-items-center gap-3">
        <button class="btn btn-primary" @click="loadData" :disabled="loading">
          <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i> 刷新数据
        </button>
        <span class="badge bg-info">
          <i class="fa fa-clock me-1"></i>
          <span>{{ currentTime }}</span>
        </span>
      </div>
    </div>

    <!-- 加载动画 -->
    <div v-if="loading" class="loading-spinner">
      <i class="fa fa-spinner fa-spin"></i>
      <span class="ms-2">正在加载数据...</span>
    </div>

    <!-- 数据内容 -->
    <div v-else class="fade-in">
      <!-- 统计卡片 -->
      <div class="row mb-4">
        <div class="col-md-3 col-sm-6" v-for="(stat, index) in stats" :key="index">
          <div class="card stat-card" :style="{ animationDelay: `${index * 0.1}s` }">
            <div class="card-body">
              <div class="row align-items-center">
                <div class="col-8">
                  <div class="text-xs font-weight-bold" :class="`text-${stat.color} text-uppercase mb-1`">
                    {{ stat.title }}
                  </div>
                  <div class="h5 mb-0 font-weight-bold">
                    {{ stat.value }}
                  </div>
                </div>
                <div class="col-4 text-end">
                  <i :class="`fa ${stat.icon} fa-2x text-${stat.color}`"></i>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Collector 状态 -->
      <div class="row mb-4" v-if="collectorStats">
        <div class="col-12">
          <div class="card stat-card">
            <div class="card-body">
              <h5 class="card-title">
                <i class="fa fa-database me-2"></i>Collector 状态
              </h5>
              <div class="row g-4">
                <div class="col-md-3 col-sm-6">
                  <div class="stat-item">
                    <small class="text-muted">接收请求数</small>
                    <div class="h4">{{ collectorStats.totalReceivedRequests || 0 }}</div>
                  </div>
                </div>
                <div class="col-md-3 col-sm-6">
                  <div class="stat-item">
                    <small class="text-muted">总Span数</small>
                    <div class="h4">{{ collectorStats.totalReceivedSpans || 0 }}</div>
                  </div>
                </div>
                <div class="col-md-3 col-sm-6">
                  <div class="stat-item">
                    <small class="text-muted">成功率</small>
                    <div class="h4" :class="collectorStats.successRate < 90 ? 'text-danger' : 'text-success'">
                      {{ collectorStats.successRate || 100 }}%
                    </div>
                  </div>
                </div>
                <div class="col-md-3 col-sm-6">
                  <div class="stat-item">
                    <small class="text-muted">运行时长</small>
                    <div class="h4">{{ collectorStats.runningHours || 0 }}小时</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 图表区域 -->
      <div class="row gap-4">
        <div class="col-lg-8 col-md-12">
          <div class="chart-container">
            <div class="d-flex justify-content-between align-items-center mb-3">
              <h5>
                <i class="fa fa-project-diagram me-2"></i>服务依赖拓扑
              </h5>
              <div class="d-flex gap-2">
                <button class="btn btn-sm btn-outline-primary" @click="refreshTopologyChart">
                  <i class="fa fa-refresh"></i>
                </button>
              </div>
            </div>
            <div id="topology-chart" class="w-100 h-100"></div>
          </div>
        </div>
        <div class="col-lg-4 col-md-12">
          <div class="chart-container">
            <div class="d-flex justify-content-between align-items-center mb-3">
              <h5>
                <i class="fa fa-chart-bar me-2"></i>服务请求排名
              </h5>
              <div class="d-flex gap-2">
                <button class="btn btn-sm btn-outline-primary" @click="refreshServiceRankChart">
                  <i class="fa fa-refresh"></i>
                </button>
              </div>
            </div>
            <div id="service-rank-chart" class="w-100 h-100"></div>
          </div>
        </div>
      </div>

      <!-- 错误服务表格 -->
      <div class="row" v-if="errorAnalysis && errorAnalysis.length > 0">
        <div class="col-12">
          <div class="chart-container">
            <div class="d-flex justify-content-between align-items-center mb-3">
              <h5>
                <i class="fa fa-exclamation-triangle me-2"></i>异常服务告警
              </h5>
              <span class="badge bg-danger">{{ errorAnalysis.length }} 个异常</span>
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
                  <tr v-for="error in errorAnalysis" :key="error.serviceName" :class="{ 'table-danger': error.errorRate > 10, 'table-warning': error.errorRate <= 10 && error.errorRate > 5 }">
                    <td>{{ error.serviceName }}</td>
                    <td>{{ error.totalCalls }}</td>
                    <td>{{ error.errorCalls }}</td>
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
                      <button class="btn btn-sm btn-outline-primary" @click="viewServiceDetails(error.serviceName)">
                        <i class="fa fa-eye"></i> 查看
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>

      <!-- 无错误提示 -->
      <div class="row" v-else-if="!loading">
        <div class="col-12">
          <div class="chart-container">
            <div class="d-flex flex-column align-items-center justify-content-center h-100">
              <i class="fa fa-check-circle fa-4x text-success mb-3"></i>
              <h5 class="text-center">暂无异常服务</h5>
              <p class="text-muted text-center">所有服务运行正常</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { ApiService } from '../services/ApiService'

// 响应式数据
const loading = ref(true)
const currentTime = ref('')
const services = ref<string[]>([])
const dependencies = ref<any[]>([])
const serviceStats = ref<any[]>([])
const errorAnalysis = ref<any[]>([])
const collectorStats = ref<any>({})
const totalSpans = ref(0)

// 图表实例
let topologyChart: echarts.ECharts | null = null
let serviceRankChart: echarts.ECharts | null = null

// 时间更新定时器
let timeInterval: number | null = null

// 计算属性：统计卡片数据
const stats = computed(() => [
  {
    title: '监控服务',
    value: `${services.value.length} 个`,
    icon: 'fa-server',
    color: 'primary'
  },
  {
    title: '链路总数',
    value: `${totalSpans.value} 条`,
    icon: 'fa-stream',
    color: 'success'
  },
  {
    title: '依赖关系',
    value: `${dependencies.value.length} 条`,
    icon: 'fa-project-diagram',
    color: 'info'
  },
  {
    title: '异常服务',
    value: `${errorAnalysis.value.length} 个`,
    icon: 'fa-exclamation-triangle',
    color: 'warning'
  }
])

// 更新当前时间
const updateCurrentTime = () => {
  const now = new Date()
  currentTime.value = now.toTimeString().split(' ')[0]
}

// 初始化图表
const initCharts = () => {
  // 服务依赖拓扑图
  const topologyChartDom = document.getElementById('topology-chart')
  if (topologyChartDom) {
    topologyChart = echarts.init(topologyChartDom)
    const topologyOption = {
      tooltip: {
        trigger: 'item',
        formatter: function(params: any) {
          if (params.dataType === 'node') {
            return `<div style="font-weight:bold">${params.data.name}</div>调用次数: ${params.data.value}`
          } else {
            return `<div style="font-weight:bold">${params.data.source} → ${params.data.target}</div>调用次数: ${params.data.value}`
          }
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
          fontSize: 12
        },
        lineStyle: {
          color: 'source',
          curveness: 0.3,
          width: 2
        },
        emphasis: {
          focus: 'adjacency',
          lineStyle: {
            width: 4
          },
          itemStyle: {
            shadowBlur: 10,
            shadowColor: 'rgba(59, 130, 246, 0.5)'
          }
        },
        force: {
          repulsion: 1500,
          edgeLength: 200,
          gravity: 0.1
        }
      }]
    }
    topologyChart.setOption(topologyOption)
  }

  // 服务请求排名图
  const serviceRankChartDom = document.getElementById('service-rank-chart')
  if (serviceRankChartDom) {
    serviceRankChart = echarts.init(serviceRankChartDom)
    const rankOption = {
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow'
        },
        formatter: function(params: any) {
          const data = params[0]
          return `${data.name}<br/>请求数量: ${data.value} 条`
        }
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        top: '15%',
        containLabel: true
      },
      xAxis: {
        type: 'value',
        name: '请求数量',
        nameTextStyle: {
          fontSize: 12
        },
        axisLabel: {
          fontSize: 11
        },
        boundaryGap: [0, 0.01]
      },
      yAxis: {
        type: 'category',
        data: [],
        axisLabel: {
          fontSize: 11,
          width: 100,
          overflow: 'truncate'
        }
      },
      series: [{
        name: '请求数量',
        type: 'bar',
        data: [],
        itemStyle: {
          color: function(params: any) {
            const colorList = [
              '#3b82f6', '#10b981', '#06b6d4', '#f59e0b',
              '#ef4444', '#8b5cf6', '#f97316', '#14b8a6'
            ]
            return colorList[params.dataIndex % colorList.length]
          },
          borderRadius: [0, 4, 4, 0]
        },
        label: {
          show: true,
          position: 'right',
          formatter: '{c}',
          fontSize: 11
        },
        animationDelay: function(idx: number) {
          return idx * 100
        }
      }]
    }
    serviceRankChart.setOption(rankOption)
  }
}

// 更新图表数据
const updateCharts = () => {
  if (topologyChart) {
    // 处理服务依赖数据
    const nodes = new Map()
    const links = []
    
    // 统计每个服务的调用次数作为节点大小
    const serviceCallCounts = new Map()
    
    // 处理依赖关系
    dependencies.value.forEach((dep: any) => {
      // 更新服务调用次数
      serviceCallCounts.set(dep.sourceService, (serviceCallCounts.get(dep.sourceService) || 0) + dep.callCount)
      serviceCallCounts.set(dep.targetService, (serviceCallCounts.get(dep.targetService) || 0) + dep.callCount)
      
      // 添加链路
      links.push({
        source: dep.sourceService,
        target: dep.targetService,
        value: dep.callCount
      })
    })
    
    // 生成节点数据
    serviceCallCounts.forEach((value, key) => {
      nodes.set(key, {
        name: key,
        value: value,
        symbolSize: Math.max(30, Math.min(60, 10 + Math.sqrt(value) * 5))
      })
    })
    
    // 更新拓扑图数据
    topologyChart.setOption({
      series: [{
        data: Array.from(nodes.values()),
        links: links
      }]
    })
  }
  
  if (serviceRankChart) {
    // 处理服务排名数据
    const serviceNames = []
    const callCounts = []
    
    // 按调用次数排序并取前8个
    const sortedServices = [...serviceStats.value]
      .sort((a, b) => (b.totalSpans || 0) - (a.totalSpans || 0))
      .slice(0, 8)
    
    sortedServices.forEach((service: any) => {
      serviceNames.push(service.serviceName)
      callCounts.push(service.totalSpans || 0)
    })
    
    // 更新排名图数据
    serviceRankChart.setOption({
      yAxis: {
        data: serviceNames
      },
      series: [{
        data: callCounts
      }]
    })
  }
}

// 刷新拓扑图
const refreshTopologyChart = () => {
  if (topologyChart) {
    topologyChart.resize()
    updateCharts()
  }
}

// 刷新服务排名图
const refreshServiceRankChart = () => {
  if (serviceRankChart) {
    serviceRankChart.resize()
    updateCharts()
  }
}

// 查看服务详情
const viewServiceDetails = (serviceName: string) => {
  // 这里可以添加跳转到服务详情页面的逻辑
  console.log('查看服务详情:', serviceName)
  // 示例：可以跳转到链路追踪页面并筛选该服务
  // router.push({ path: '/traces', query: { service: serviceName } })
}

// 加载数据
const loadData = async () => {
  try {
    loading.value = true
    
    // 并行加载数据
    const [
      serviceNames,
      serviceDeps,
      serviceStatsData,
      errorAnalysisData,
      collectorStatsData
    ] = await Promise.all([
      ApiService.getServiceNames(),
      ApiService.getServiceDependencies(24),
      ApiService.getServiceStats(),
      ApiService.getErrorAnalysis(24),
      ApiService.getCollectorStats()
    ])
    
    // 更新数据
    services.value = serviceNames
    dependencies.value = serviceDeps
    serviceStats.value = serviceStatsData
    errorAnalysis.value = errorAnalysisData
    collectorStats.value = collectorStatsData
    
    // 计算总Span数
    totalSpans.value = serviceStatsData.reduce((sum: number, service: any) => sum + (service.totalSpans || 0), 0)
    
    // 更新图表
    updateCharts()
  } catch (error) {
    console.error('加载仪表盘数据失败:', error)
  } finally {
    loading.value = false
  }
}

// 监听窗口大小变化
const handleResize = () => {
  topologyChart?.resize()
  serviceRankChart?.resize()
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
  topologyChart?.dispose()
  serviceRankChart?.dispose()
  
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
