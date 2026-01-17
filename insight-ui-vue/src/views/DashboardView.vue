<template>
  <div>
    <!-- 页面标题 -->
    <div class="d-flex justify-content-between align-items-center mb-4">
      <div>
        <h2 class="page-title">
          <i class="fa fa-tachometer-alt me-2 text-primary"></i>监控仪表盘
        </h2>
        <p class="page-description">实时监控您的 Spring Boot 应用架构</p>
      </div>
      <div>
        <span class="badge bg-info">
          <i class="fa fa-clock me-1"></i>
          <span>{{ currentTime }}</span>
        </span>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="row mb-4" v-if="loading">
      <div class="col-md-3 col-sm-6">
        <div class="card stat-card border-left-primary">
          <div class="card-body">
            <div class="row align-items-center">
              <div class="col-8">
                <div class="text-xs font-weight-bold text-primary text-uppercase mb-1">
                  监控服务
                </div>
                <div class="h5 mb-0 font-weight-bold text-gray-800">
                  <i class="fa fa-spinner fa-spin"></i> 加载中...
                </div>
              </div>
              <div class="col-4 text-end">
                <i class="fa fa-server fa-2x text-primary"></i>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="col-md-3 col-sm-6">
        <div class="card stat-card border-left-success">
          <div class="card-body">
            <div class="row align-items-center">
              <div class="col-8">
                <div class="text-xs font-weight-bold text-success text-uppercase mb-1">
                  链路总数
                </div>
                <div class="h5 mb-0 font-weight-bold text-gray-800">
                  <i class="fa fa-spinner fa-spin"></i> 加载中...
                </div>
              </div>
              <div class="col-4 text-end">
                <i class="fa fa-stream fa-2x text-success"></i>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="col-md-3 col-sm-6">
        <div class="card stat-card border-left-info">
          <div class="card-body">
            <div class="row align-items-center">
              <div class="col-8">
                <div class="text-xs font-weight-bold text-info text-uppercase mb-1">
                  依赖关系
                </div>
                <div class="h5 mb-0 font-weight-bold text-gray-800">
                  <i class="fa fa-spinner fa-spin"></i> 加载中...
                </div>
              </div>
              <div class="col-4 text-end">
                <i class="fa fa-project-diagram fa-2x text-info"></i>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="col-md-3 col-sm-6">
        <div class="card stat-card border-left-warning">
          <div class="card-body">
            <div class="row align-items-center">
              <div class="col-8">
                <div class="text-xs font-weight-bold text-warning text-uppercase mb-1">
                  异常服务
                </div>
                <div class="h5 mb-0 font-weight-bold text-gray-800">
                  <i class="fa fa-spinner fa-spin"></i> 加载中...
                </div>
              </div>
              <div class="col-4 text-end">
                <i class="fa fa-exclamation-triangle fa-2x text-warning"></i>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 统计卡片 - 数据加载完成 -->
    <div class="row mb-4" v-else>
      <div class="col-md-3 col-sm-6">
        <div class="card stat-card border-left-primary">
          <div class="card-body">
            <div class="row align-items-center">
              <div class="col-8">
                <div class="text-xs font-weight-bold text-primary text-uppercase mb-1">
                  监控服务
                </div>
                <div class="h5 mb-0 font-weight-bold text-gray-800">
                  {{ services.length }} 个
                </div>
              </div>
              <div class="col-4 text-end">
                <i class="fa fa-server fa-2x text-primary"></i>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="col-md-3 col-sm-6">
        <div class="card stat-card border-left-success">
          <div class="card-body">
            <div class="row align-items-center">
              <div class="col-8">
                <div class="text-xs font-weight-bold text-success text-uppercase mb-1">
                  链路总数
                </div>
                <div class="h5 mb-0 font-weight-bold text-gray-800">
                  {{ totalSpans }} 条
                </div>
              </div>
              <div class="col-4 text-end">
                <i class="fa fa-stream fa-2x text-success"></i>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="col-md-3 col-sm-6">
        <div class="card stat-card border-left-info">
          <div class="card-body">
            <div class="row align-items-center">
              <div class="col-8">
                <div class="text-xs font-weight-bold text-info text-uppercase mb-1">
                  依赖关系
                </div>
                <div class="h5 mb-0 font-weight-bold text-gray-800">
                  {{ dependencies.length }} 条
                </div>
              </div>
              <div class="col-4 text-end">
                <i class="fa fa-project-diagram fa-2x text-info"></i>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="col-md-3 col-sm-6">
        <div class="card stat-card border-left-warning">
          <div class="card-body">
            <div class="row align-items-center">
              <div class="col-8">
                <div class="text-xs font-weight-bold text-warning text-uppercase mb-1">
                  异常服务
                </div>
                <div class="h5 mb-0 font-weight-bold text-gray-800">
                  {{ errorAnalysis.length }} 个
                </div>
              </div>
              <div class="col-4 text-end">
                <i class="fa fa-exclamation-triangle fa-2x text-warning"></i>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Collector 状态 -->
    <div class="row mb-4" v-if="collectorStats && !loading">
      <div class="col-12">
        <div class="card stat-card">
          <div class="card-body">
            <h5 class="card-title">
              <i class="fa fa-database me-2"></i>Collector 状态
            </h5>
            <div class="row">
              <div class="col-md-3">
                <small class="text-muted">接收请求数</small>
                <div class="h4">{{ collectorStats.totalReceivedRequests || 0 }}</div>
              </div>
              <div class="col-md-3">
                <small class="text-muted">总Span数</small>
                <div class="h4">{{ collectorStats.totalReceivedSpans || 0 }}</div>
              </div>
              <div class="col-md-3">
                <small class="text-muted">成功率</small>
                <div class="h4 text-success">{{ collectorStats.successRate || 100 }}%</div>
              </div>
              <div class="col-md-3">
                <small class="text-muted">运行时长</small>
                <div class="h4">{{ collectorStats.runningHours || 0 }}小时</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 图表区域 -->
    <div class="row">
      <div class="col-lg-8">
        <div class="chart-container">
          <h5 class="mb-3">
            <i class="fa fa-project-diagram me-2 text-primary"></i>服务依赖拓扑
          </h5>
          <div id="topology-chart" class="w-100 h-100"></div>
        </div>
      </div>
      <div class="col-lg-4">
        <div class="chart-container">
          <h5 class="mb-3">
            <i class="fa fa-chart-bar me-2 text-success"></i>服务请求排名
          </h5>
          <div id="service-rank-chart" class="w-100 h-100"></div>
        </div>
      </div>
    </div>

    <!-- 错误服务表格 -->
    <div class="row" v-if="errorAnalysis && errorAnalysis.length > 0">
      <div class="col-12">
        <div class="chart-container">
          <h5 class="mb-3">
            <i class="fa fa-exclamation-triangle me-2 text-danger"></i>异常服务告警
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
              </tbody>
            </table>
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
      title: {
        text: '服务依赖拓扑',
        left: 'center',
        textStyle: {
          fontSize: 16,
          color: '#333'
        }
      },
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
          formatter: '{b}'
        },
        lineStyle: {
          color: 'source',
          curveness: 0.3
        },
        emphasis: {
          focus: 'adjacency',
          lineStyle: {
            width: 10
          }
        },
        force: {
          repulsion: 1000,
          edgeLength: 150,
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
      title: {
        text: '服务请求排名',
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
          width: 80,
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
              '#4e73df', '#1cc88a', '#36b9cc', '#f6c23e',
              '#e74a3b', '#6f42c1', '#fd7e14', '#20c9a6'
            ]
            return colorList[params.dataIndex % colorList.length]
          }
        },
        label: {
          show: true,
          position: 'right',
          formatter: '{c}',
          fontSize: 11
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
        value: value
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
