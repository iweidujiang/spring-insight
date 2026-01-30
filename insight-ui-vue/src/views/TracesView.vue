<template>
  <div class="fade-in">
    <!-- 页面标题 -->
    <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
      <div>
        <h2 class="page-title">
          <i class="fa fa-stream me-2"></i>链路追踪
        </h2>
        <p class="page-description">查看和分析服务调用链路的详细信息</p>
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

    <!-- 筛选条件 -->
    <div class="row mb-4">
      <div class="col-12">
        <div class="card stat-card">
          <div class="card-body">
            <h5 class="card-title">
              <i class="fa fa-filter me-2"></i>筛选条件
            </h5>
            <div class="row g-3 align-items-end">
              <div class="col-md-3 col-sm-6">
                <label for="service-select" class="form-label">服务名称</label>
                <select id="service-select" class="form-select" v-model="selectedService" @change="loadData">
                  <option value="">所有服务</option>
                  <option v-for="service in services" :key="service" :value="service">
                    {{ service }}
                  </option>
                </select>
              </div>
              <div class="col-md-3 col-sm-6">
                <label for="hours-select" class="form-label">时间范围</label>
                <select id="hours-select" class="form-select" v-model="hours" @change="loadData">
                  <option value="1">1小时</option>
                  <option value="6">6小时</option>
                  <option value="12">12小时</option>
                  <option value="24" selected>24小时</option>
                  <option value="72">72小时</option>
                </select>
              </div>
              <div class="col-md-3 col-sm-6">
                <label for="limit-select" class="form-label">显示数量</label>
                <select id="limit-select" class="form-select" v-model="limit" @change="loadData">
                  <option value="20">20条</option>
                  <option value="50" selected>50条</option>
                  <option value="100">100条</option>
                  <option value="200">200条</option>
                </select>
              </div>
              <div class="col-md-3 col-sm-6">
                <div class="d-flex gap-2">
                  <button class="btn btn-primary flex-grow-1" @click="loadData" :disabled="loading">
                    <i class="fa fa-search"></i> 搜索
                  </button>
                  <button class="btn btn-outline-secondary" @click="resetFilters" :disabled="loading">
                    <i class="fa fa-refresh"></i>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 加载动画 -->
    <div v-if="loading" class="loading-spinner">
      <i class="fa fa-spinner fa-spin"></i>
      <span class="ms-2">正在加载链路数据...</span>
    </div>

    <!-- 链路列表 -->
    <div v-else class="row">
      <div class="col-12">
        <div class="card stat-card">
          <div class="card-body">
            <div class="d-flex justify-content-between align-items-center mb-3">
              <h5 class="card-title">
                <i class="fa fa-list me-2"></i>链路列表
              </h5>
              <span class="badge bg-primary">{{ traces.length }} 条链路</span>
            </div>
            <div class="table-responsive">
              <table class="table table-hover">
                <thead class="table-light">
                  <tr>
                    <th>Trace ID</th>
                    <th>服务名称</th>
                    <th>操作名称</th>
                    <th>开始时间</th>
                    <th>耗时(ms)</th>
                    <th>状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(trace, index) in traces" :key="trace.traceId" class="fade-in" :style="{ animationDelay: `${index * 0.05}s` }">
                    <td class="text-truncate" style="max-width: 150px;">
                      <code class="text-primary">{{ trace.traceId }}</code>
                    </td>
                    <td>{{ trace.serviceName }}</td>
                    <td class="text-truncate" style="max-width: 200px;">{{ trace.operationName }}</td>
                    <td>{{ formatTime(trace.startTime) }}</td>
                    <td :class="trace.durationMs > 1000 ? 'text-danger font-weight-bold' : trace.durationMs > 500 ? 'text-warning' : 'text-success'">
                      {{ trace.durationMs }}
                    </td>
                    <td>
                      <span class="badge" :class="trace.statusCode === 'OK' ? 'bg-success' : 'bg-danger'">
                        {{ trace.statusCode }}
                      </span>
                    </td>
                    <td>
                      <div class="d-flex gap-1">
                        <button class="btn btn-sm btn-primary" @click="viewTraceDetail(trace.traceId)">
                          <i class="fa fa-eye"></i> 查看
                        </button>
                        <button class="btn btn-sm btn-outline-secondary" @click="copyTraceId(trace.traceId)">
                          <i class="fa fa-copy"></i>
                        </button>
                      </div>
                    </td>
                  </tr>
                  <tr v-if="traces.length === 0">
                    <td colspan="7" class="text-center text-muted">
                      <div class="py-4">
                        <i class="fa fa-info-circle fa-2x mb-2"></i>
                        <p>暂无链路数据</p>
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
import { useRouter } from 'vue-router'
import { ApiService } from '../services/ApiService'

// 路由
const router = useRouter()

// 响应式数据
const loading = ref(true)
const currentTime = ref('')
const services = ref<string[]>([])
const traces = ref<any[]>([])
const selectedService = ref('')
const hours = ref(24)
const limit = ref(50)

// 时间更新定时器
let timeInterval: number | null = null

// 格式化时间
const formatTime = (timestamp: number) => {
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN')
}

// 更新当前时间
const updateCurrentTime = () => {
  const now = new Date()
  currentTime.value = now.toTimeString().split(' ')[0]
}

// 加载数据
const loadData = async () => {
  try {
    loading.value = true
    
    let traceData: any[] = []
    
    if (selectedService.value) {
      // 获取指定服务的链路
      traceData = await ApiService.getRecentSpansByService(selectedService.value, limit.value)
    } else {
      // 获取所有服务的链路
      traceData = await ApiService.getRecentSpans(hours.value, limit.value)
    }
    
    traces.value = traceData
  } catch (error) {
    console.error('加载链路数据失败:', error)
  } finally {
    loading.value = false
  }
}

// 加载服务列表
const loadServices = async () => {
  try {
    const serviceNames = await ApiService.getServiceNames()
    services.value = serviceNames
  } catch (error) {
    console.error('加载服务列表失败:', error)
  }
}

// 重置筛选条件
const resetFilters = () => {
  selectedService.value = ''
  hours.value = 24
  limit.value = 50
  loadData()
}

// 查看链路详情
const viewTraceDetail = (traceId: string) => {
  // 这里可以跳转到链路详情页面，暂时先在控制台输出
  console.log(`查看链路详情: ${traceId}`)
  // router.push(`/trace/${traceId}`)
}

// 复制Trace ID
const copyTraceId = (traceId: string) => {
  navigator.clipboard.writeText(traceId).then(() => {
    // 可以添加一个提示，告诉用户复制成功
    console.log('Trace ID 已复制到剪贴板:', traceId)
  }).catch(err => {
    console.error('复制失败:', err)
  })
}

// 组件挂载时初始化
onMounted(() => {
  // 加载服务列表
  loadServices()
  
  // 加载链路数据
  loadData()
  
  // 启动时间更新
  updateCurrentTime()
  timeInterval = window.setInterval(updateCurrentTime, 1000)
})

// 组件卸载时清理
onUnmounted(() => {
  // 清理定时器
  if (timeInterval) {
    clearInterval(timeInterval)
  }
})
</script>

<style scoped>
/* 组件特定样式 */
</style>
