<template>
  <div class="fade-in">
    <!-- 页面标题 -->
    <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
      <div>
        <h2 class="page-title">
          <i class="fa fa-project-diagram me-2"></i>服务拓扑图
        </h2>
        <p class="page-description">可视化展示服务间的依赖关系和调用情况</p>
      </div>
      <div class="d-flex align-items-center gap-3">
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
              <div class="d-flex gap-2">
                <button class="btn btn-primary" @click="loadData" :disabled="loading">
                  <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i> 刷新数据
                </button>
                <button class="btn btn-outline-secondary" @click="downloadTopology" :disabled="loading || dependencies.length === 0">
                  <i class="fa fa-download"></i> 导出数据
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 加载动画 -->
    <div v-if="loading" class="loading-spinner">
      <i class="fa fa-spinner fa-spin"></i>
      <span class="ms-2">正在加载服务拓扑数据...</span>
    </div>

    <!-- 拓扑图区域 -->
    <div v-else class="row mb-4">
      <div class="col-12">
        <div class="chart-container" style="height: 600px;">
          <div class="d-flex justify-content-between align-items-center mb-3">
            <h5>
              <i class="fa fa-project-diagram me-2"></i>服务依赖拓扑图
            </h5>
            <div class="d-flex gap-2">
              <button class="btn btn-sm btn-outline-primary" @click="refreshTopology">
                <i class="fa fa-refresh"></i> 刷新视图
              </button>
              <button class="btn btn-sm btn-outline-secondary" @click="fitToScreen">
                <i class="fa fa-expand"></i> 适应屏幕
              </button>
            </div>
          </div>
          <div id="topology-chart" class="w-100 h-100"></div>
        </div>
      </div>
    </div>

    <!-- 依赖关系表格 -->
    <div class="row" v-if="!loading">
      <div class="col-12">
        <div class="card stat-card">
          <div class="card-body">
            <div class="d-flex justify-content-between align-items-center mb-3">
              <h5 class="card-title">
                <i class="fa fa-list me-2"></i>依赖关系列表
              </h5>
              <span class="badge bg-primary">{{ dependencies.length }} 条依赖</span>
            </div>
            <div class="table-responsive">
              <table class="table table-hover">
                <thead class="table-light">
                  <tr>
                    <th>源服务</th>
                    <th>目标服务</th>
                    <th>调用次数</th>
                    <th>平均耗时(ms)</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="dep in dependencies" :key="`${dep.sourceService}-${dep.targetService}`" class="fade-in" :style="{ animationDelay: `${dependencies.indexOf(dep) * 0.05}s` }">
                    <td>{{ dep.sourceService }}</td>
                    <td>{{ dep.targetService }}</td>
                    <td>{{ dep.callCount }}</td>
                    <td :class="dep.avgDuration > 1000 ? 'text-danger' : dep.avgDuration > 500 ? 'text-warning' : 'text-success'">
                      {{ dep.avgDuration || 0 }}
                    </td>
                    <td>
                      <button class="btn btn-sm btn-outline-primary" @click="viewDependencyDetails(dep)">
                        <i class="fa fa-eye"></i> 查看
                      </button>
                    </td>
                  </tr>
                  <tr v-if="dependencies.length === 0">
                    <td colspan="5" class="text-center text-muted">
                      <div class="py-4">
                        <i class="fa fa-info-circle fa-2x mb-2"></i>
                        <p>暂无依赖关系数据</p>
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
const dependencies = ref<any[]>([])

// 图表实例
let topologyChart: echarts.ECharts | null = null

// 时间更新定时器
let timeInterval: number | null = null

// 更新当前时间
const updateCurrentTime = () => {
  const now = new Date()
  currentTime.value = now.toTimeString().split(' ')[0]
}

// 初始化图表
const initChart = () => {
  const chartDom = document.getElementById('topology-chart')
  if (chartDom) {
    topologyChart = echarts.init(chartDom)
    const option = {
      tooltip: {
        trigger: 'item',
        formatter: function(params: any) {
          if (params.dataType === 'node') {
            return `<div style="font-weight:bold">${params.data.name}</div>调用次数: ${params.data.value}`
          } else {
            return `<div style="font-weight:bold">${params.data.source} → ${params.data.target}</div>调用次数: ${params.data.value}<br/>平均耗时: ${params.data.avgDuration || 0}ms`
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
          width: 2,
          opacity: 0.8
        },
        itemStyle: {
          color: function(params: any) {
            const colorList = [
              '#3b82f6', '#10b981', '#06b6d4', '#f59e0b',
              '#ef4444', '#8b5cf6', '#f97316', '#14b8a6'
            ]
            return colorList[params.dataIndex % colorList.length]
          },
          borderColor: '#fff',
          borderWidth: 2,
          shadowBlur: 10,
          shadowColor: 'rgba(59, 130, 246, 0.3)'
        },
        emphasis: {
          focus: 'adjacency',
          lineStyle: {
            width: 4
          },
          itemStyle: {
            shadowBlur: 15,
            shadowColor: 'rgba(59, 130, 246, 0.5)'
          }
        },
        force: {
          repulsion: 2000,
          edgeLength: 250,
          gravity: 0.1,
          layoutAnimation: true
        }
      }]
    }
    topologyChart.setOption(option)
  }
}

// 更新图表数据
const updateChart = () => {
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
        value: dep.callCount,
        avgDuration: dep.avgDuration
      })
    })
    
    // 生成节点数据
    serviceCallCounts.forEach((value, key) => {
      nodes.set(key, {
        name: key,
        value: value,
        symbolSize: Math.max(30, Math.min(70, 15 + Math.sqrt(value) * 8))
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
}

// 刷新拓扑图
const refreshTopology = () => {
  if (topologyChart) {
    topologyChart.resize()
    updateChart()
  }
}

// 适应屏幕
const fitToScreen = () => {
  if (topologyChart) {
    topologyChart.dispatchAction({
      type: 'restore'
    })
    topologyChart.resize()
  }
}

// 查看依赖详情
const viewDependencyDetails = (dep: any) => {
  console.log('查看依赖详情:', dep)
  // 这里可以添加跳转到依赖详情页面的逻辑
}

// 导出拓扑数据
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

// 加载数据
const loadData = async () => {
  try {
    loading.value = true
    
    // 加载服务依赖关系
    const serviceDeps = await ApiService.getServiceDependencies(hours.value)
    dependencies.value = serviceDeps
    
    // 更新图表
    updateChart()
  } catch (error) {
    console.error('加载拓扑数据失败:', error)
  } finally {
    loading.value = false
  }
}

// 监听窗口大小变化
const handleResize = () => {
  topologyChart?.resize()
}

// 组件挂载时初始化
onMounted(() => {
  // 初始化图表
  initChart()
  
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
