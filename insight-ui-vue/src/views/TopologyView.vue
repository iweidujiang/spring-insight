<template>
  <div class="si-page fade-in si-topo-page">
    <div class="si-page__header">
      <div>
        <h2 class="page-title mb-1">
          <i class="fa fa-project-diagram me-2"></i>服务拓扑图
        </h2>
        <p class="page-description mb-0">
          箭头：调用方 → 被调用方；点击节点/边可下钻到该服务的链路列表
        </p>
      </div>
      <span class="badge bg-info">
        <i class="fa fa-clock me-1"></i>{{ currentTime }}
      </span>
    </div>

    <div class="card stat-card si-toolbar-card">
      <div class="card-body">
        <div class="si-toolbar-inner">
          <div>
            <label class="form-label" for="hours-topology">时间范围</label>
            <select id="hours-topology" class="form-select" style="min-width: 11rem" v-model="hours" @change="loadData">
              <option :value="1">最近 1 小时</option>
              <option :value="6">最近 6 小时</option>
              <option :value="12">最近 12 小时</option>
              <option :value="24">最近 24 小时</option>
              <option :value="72">最近 72 小时</option>
            </select>
          </div>
          <div class="d-flex flex-wrap gap-2 ms-auto">
            <button class="btn btn-primary" type="button" @click="loadData" :disabled="loading">
              <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i> 刷新
            </button>
            <button class="btn btn-outline-secondary" type="button" @click="downloadTopology" :disabled="loading || dependencies.length === 0">
              <i class="fa fa-download"></i> 导出
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="loading" class="loading-spinner">
      <i class="fa fa-spinner fa-spin"></i>
      <span class="ms-2">正在加载拓扑数据...</span>
    </div>

    <div v-show="!loading" class="si-topo-layout">
      <div class="chart-container si-topo-graph-card">
        <div class="d-flex justify-content-between align-items-center mb-2 flex-shrink-0">
          <h5 class="mb-0">
            <i class="fa fa-project-diagram me-2"></i>服务依赖拓扑
          </h5>
          <div class="d-flex gap-2 align-items-center">
            <span class="si-topo-legend"><i class="fa fa-hand-pointer-o me-1"></i>点击节点/边查看链路</span>
            <span class="si-topo-legend"><i class="fa fa-long-arrow-right"></i> 调用方向</span>
            <button type="button" class="btn btn-sm btn-outline-primary" @click="refreshTopology">
              <i class="fa fa-refresh"></i> 重绘
            </button>
            <button type="button" class="btn btn-sm btn-outline-secondary" @click="fitToScreen">
              <i class="fa fa-expand"></i> 适应窗口
            </button>
          </div>
        </div>
        <div class="si-topo-canvas-wrap">
          <div ref="chartEl" class="si-topo-canvas"></div>
        </div>
      </div>

      <div class="card stat-card si-topo-table-card">
        <div class="card-body d-flex flex-column">
          <div class="d-flex justify-content-between align-items-center mb-2 flex-shrink-0">
            <h5 class="card-title mb-0">
              <i class="fa fa-list me-2"></i>依赖关系列表
            </h5>
            <span class="badge bg-primary">{{ dependencies.length }} 条</span>
          </div>
          <div class="table-responsive si-topo-table-scroll">
            <table class="table table-hover mb-0">
              <thead class="table-light">
                <tr>
                  <th>源服务</th>
                  <th></th>
                  <th>目标服务</th>
                  <th>调用次数</th>
                  <th>平均耗时(ms)</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="dep in dependencies" :key="`${dep.sourceService}-${dep.targetService}`">
                  <td><code class="si-svc">{{ dep.sourceService }}</code></td>
                  <td class="text-center text-info"><i class="fa fa-long-arrow-right"></i></td>
                  <td><code class="si-svc">{{ dep.targetService }}</code></td>
                  <td>{{ dep.callCount }}</td>
                  <td :class="dep.avgDuration > 1000 ? 'text-danger' : dep.avgDuration > 500 ? 'text-warning' : 'text-success'">
                    {{ dep.avgDuration || 0 }}
                  </td>
                  <td>
                    <div class="d-flex gap-1 flex-wrap">
                      <button class="btn btn-sm btn-outline-primary" type="button" @click="goServiceTraces(dep.sourceService)" title="查看调用方链路">
                        源
                      </button>
                      <button class="btn btn-sm btn-outline-secondary" type="button" @click="goServiceTraces(dep.targetService)" title="查看被调用方链路">
                        目标
                      </button>
                    </div>
                  </td>
                </tr>
                <tr v-if="dependencies.length === 0">
                  <td colspan="6" class="text-center text-muted py-4">
                    <i class="fa fa-info-circle fa-2x mb-2 d-block"></i>
                    暂无依赖关系数据
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
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ApiService } from '../services/ApiService'
import { buildTopologyOption, resolveTopologyClick } from '../utils/topologyGraph'

const router = useRouter()
const loading = ref(true)
const currentTime = ref('')
const hours = ref(24)
const dependencies = ref<any[]>([])
const chartEl = ref<HTMLElement | null>(null)

let topologyChart: echarts.ECharts | null = null
let timeInterval: number | null = null

const updateCurrentTime = () => {
  currentTime.value = new Date().toTimeString().split(' ')[0]
}

const goServiceTraces = (serviceName: string) => {
  if (!serviceName) return
  router.push({ path: '/traces', query: { service: serviceName, hours: String(hours.value) } })
}

const bindTopologyClick = () => {
  if (!topologyChart) return
  topologyChart.off('click')
  topologyChart.on('click', (params: any) => {
    const hit = resolveTopologyClick(params)
    if (hit?.service) {
      goServiceTraces(hit.service)
    }
  })
}

const initChart = () => {
  if (!chartEl.value) return
  topologyChart = echarts.init(chartEl.value)
  topologyChart.setOption(buildTopologyOption([]))
  bindTopologyClick()
}

const updateChart = () => {
  if (!topologyChart) return
  topologyChart.setOption(buildTopologyOption(dependencies.value), { notMerge: true })
  nextTick(() => topologyChart?.resize())
}

const refreshTopology = () => {
  updateChart()
  topologyChart?.resize()
}

const fitToScreen = () => {
  if (!topologyChart) return
  topologyChart.setOption(buildTopologyOption(dependencies.value), { notMerge: true })
  topologyChart.resize()
}

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

const loadData = async () => {
  try {
    loading.value = true
    dependencies.value = await ApiService.getServiceDependencies(hours.value)
    updateChart()
  } catch (error) {
    console.error('加载拓扑数据失败:', error)
  } finally {
    loading.value = false
    await nextTick()
    topologyChart?.resize()
  }
}

const handleResize = () => topologyChart?.resize()

onMounted(async () => {
  await nextTick()
  initChart()
  updateCurrentTime()
  timeInterval = window.setInterval(updateCurrentTime, 1000)
  window.addEventListener('resize', handleResize)
  await loadData()
})

onUnmounted(() => {
  topologyChart?.dispose()
  if (timeInterval) clearInterval(timeInterval)
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.si-topo-page {
  min-height: calc(100vh - 5.5rem);
}

.si-topo-layout {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0.75rem;
  flex: 1;
}

.si-topo-graph-card {
  min-height: clamp(360px, 52vh, 620px) !important;
  height: auto !important;
  margin-bottom: 0 !important;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.si-topo-canvas-wrap {
  flex: 1;
  min-height: 320px;
  width: 100%;
  position: relative;
  overflow: hidden;
  border-radius: 8px;
  background:
    radial-gradient(ellipse at center, rgba(15, 118, 110, 0.06) 0%, transparent 65%),
    var(--si-paper);
  border: 1px solid var(--card-border);
}

.si-topo-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.si-topo-legend {
  font-size: 0.72rem;
  color: var(--si-muted);
}

.si-topo-table-card {
  margin-bottom: 0 !important;
}

.si-topo-table-scroll {
  max-height: min(36vh, 320px);
  overflow-y: auto;
}

.si-svc {
  color: var(--si-teal);
  font-size: 0.85rem;
  background: transparent;
}
</style>
