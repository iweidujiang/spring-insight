<template>
  <div>
    <!-- 页面标题 -->
    <div class="d-flex justify-content-between align-items-center mb-4">
      <div>
        <h2 class="page-title">
          <i class="fa fa-project-diagram me-2 text-primary"></i>服务拓扑图
        </h2>
        <p class="page-description">可视化展示服务间的依赖关系和调用情况</p>
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

    <!-- 拓扑图区域 -->
    <div class="row">
      <div class="col-12">
        <div class="chart-container" style="height: 600px;">
          <div id="topology-chart" class="w-100 h-100" v-if="!loading"></div>
          <div class="d-flex justify-content-center align-items-center h-100" v-else>
            <div class="text-center">
              <div class="spinner-border" role="status">
                <span class="visually-hidden">加载中...</span>
              </div>
              <p class="mt-2">正在加载服务拓扑数据...</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 依赖关系表格 -->
    <div class="row mt-4">
      <div class="col-12">
        <div class="card stat-card">
          <div class="card-body">
            <h5 class="card-title">
              <i class="fa fa-list me-2"></i>依赖关系列表
            </h5>
            <div class="table-responsive">
              <table class="table table-hover">
                <thead class="table-light">
                  <tr>
                    <th>源服务</th>
                    <th>目标服务</th>
                    <th>调用次数</th>
                    <th>平均耗时(ms)</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="dep in dependencies" :key="`${dep.sourceService}-${dep.targetService}`">
                    <td>{{ dep.sourceService }}</td>
                    <td>{{ dep.targetService }}</td>
                    <td>{{ dep.callCount }}</td>
                    <td>{{ dep.avgDuration || 0 }}</td>
                  </tr>
                  <tr v-if="dependencies.length === 0">
                    <td colspan="4" class="text-center text-muted">暂无依赖关系数据</td>
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
      title: {
        text: '服务依赖拓扑',
        left: 'center',
        textStyle: {
          fontSize: 18,
          color: '#333'
        }
      },
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
          repulsion: 1200,
          edgeLength: 200,
          gravity: 0.1
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
