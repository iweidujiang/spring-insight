<template>
  <div class="si-page fade-in">
    <div class="si-page__header">
      <div>
        <h2 class="page-title mb-1">
          <i class="fa fa-stream me-2"></i>链路追踪
        </h2>
        <p class="page-description mb-0">
          一行对应一次请求（同一 Trace ID）；点「查看」进入调用时间线
        </p>
      </div>
      <div class="si-page__toolbar">
        <button class="btn btn-primary" @click="loadData" :disabled="loading">
          <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i> 刷新
        </button>
        <span class="badge bg-info">
          <i class="fa fa-clock me-1"></i>{{ currentTime }}
        </span>
      </div>
    </div>

    <div class="card stat-card si-toolbar-card">
      <div class="card-body">
        <h5 class="card-title">
          <i class="fa fa-filter me-2"></i>筛选条件
        </h5>
        <div class="row g-3 align-items-end">
          <div class="col-md-2 col-sm-6">
            <label for="service-select" class="form-label">服务</label>
            <select id="service-select" class="form-select" v-model="selectedService" @change="loadData">
              <option value="">全部</option>
              <option v-for="service in services" :key="service" :value="service">
                {{ service }}
              </option>
            </select>
          </div>
          <div class="col-md-2 col-sm-6">
            <label for="hours-select" class="form-label">时间</label>
            <select id="hours-select" class="form-select" v-model.number="hours" @change="loadData">
              <option :value="1">1小时</option>
              <option :value="6">6小时</option>
              <option :value="12">12小时</option>
              <option :value="24">24小时</option>
              <option :value="72">72小时</option>
            </select>
          </div>
          <div class="col-md-2 col-sm-6">
            <label for="status-select" class="form-label">状态</label>
            <select id="status-select" class="form-select" v-model="statusFilter" @change="loadData">
              <option value="all">全部</option>
              <option value="error">仅异常</option>
              <option value="ok">仅成功</option>
            </select>
          </div>
          <div class="col-md-2 col-sm-6">
            <label for="limit-select" class="form-label">数量</label>
            <select id="limit-select" class="form-select" v-model.number="limit" @change="loadData">
              <option :value="20">20</option>
              <option :value="50">50</option>
              <option :value="100">100</option>
              <option :value="200">200</option>
            </select>
          </div>
          <div class="col-md-4 col-sm-12">
            <label for="q-input" class="form-label">搜索</label>
            <div class="d-flex gap-2">
              <input
                id="q-input"
                class="form-control"
                type="search"
                v-model="query"
                placeholder="Trace ID / 服务 / 操作名"
                @keyup.enter="loadData"
              />
              <button class="btn btn-primary" @click="loadData" :disabled="loading" title="搜索">
                <i class="fa fa-search"></i>
              </button>
              <button class="btn btn-outline-secondary" @click="resetFilters" :disabled="loading" title="重置">
                <i class="fa fa-refresh"></i>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="copyHint" class="alert alert-success py-2 si-copy-hint" role="status">
      {{ copyHint }}
    </div>

    <div v-if="loading" class="loading-spinner">
      <i class="fa fa-spinner fa-spin"></i>
      <span class="ms-2">正在加载链路数据...</span>
    </div>

    <div v-else class="card stat-card si-table-panel">
      <div class="card-body">
        <div class="d-flex justify-content-between align-items-center mb-2">
          <h5 class="card-title mb-0">
            <i class="fa fa-list me-2"></i>链路列表
          </h5>
          <span class="badge bg-primary">{{ traces.length }} 条 Trace</span>
        </div>
        <div class="table-responsive">
          <table class="table table-hover mb-0">
            <thead class="table-light">
              <tr>
                <th>Trace ID</th>
                <th>入口服务</th>
                <th>入口操作</th>
                <th>Span</th>
                <th>开始时间</th>
                <th>总耗时</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(trace, index) in traces"
                :key="`${trace.traceId}-${index}`"
                class="fade-in"
                :style="{ animationDelay: `${Math.min(index, 20) * 0.03}s` }"
              >
                <td class="text-truncate" style="max-width: 160px;">
                  <code class="text-primary" :title="trace.traceId">{{ trace.traceId }}</code>
                </td>
                <td>{{ trace.serviceName || '-' }}</td>
                <td class="text-truncate" style="max-width: 220px;" :title="trace.operationName">
                  {{ trace.operationName || '-' }}
                </td>
                <td>
                  <span class="text-muted small">{{ trace.spanCount ?? '-' }}</span>
                  <span v-if="trace.serviceCount > 1" class="text-muted small"> · {{ trace.serviceCount }} 服务</span>
                </td>
                <td>{{ formatTime(trace.startTime) }}</td>
                <td :class="durationClass(trace.durationMs)">
                  {{ formatDuration(Number(trace.durationMs) || 0) }}
                </td>
                <td>
                  <span class="badge" :class="trace.hasError || trace.statusCode === 'ERROR' ? 'bg-danger' : 'bg-success'">
                    {{ trace.hasError || trace.statusCode === 'ERROR' ? 'ERROR' : 'OK' }}
                  </span>
                </td>
                <td>
                  <div class="d-flex gap-1">
                    <button class="btn btn-sm btn-primary" @click="viewTraceDetail(trace.traceId)">
                      <i class="fa fa-eye"></i> 查看
                    </button>
                    <button class="btn btn-sm btn-outline-secondary" @click="copyTraceId(trace.traceId)" title="复制 Trace ID">
                      <i class="fa fa-copy"></i>
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-if="traces.length === 0">
                <td colspan="8" class="text-center text-muted">
                  <div class="py-4">
                    <i class="fa fa-info-circle fa-2x mb-2"></i>
                    <p class="mb-0">暂无匹配的链路</p>
                    <p class="small mb-0 mt-1">可放宽筛选，或确认业务服务已上报到 insight-server</p>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ApiService } from '../services/ApiService'
import { formatDuration } from '../utils/traceTimeline'

const router = useRouter()
const route = useRoute()

const loading = ref(true)
const currentTime = ref('')
const services = ref<string[]>([])
const traces = ref<any[]>([])
const selectedService = ref('')
const hours = ref(24)
const limit = ref(50)
const statusFilter = ref('all')
const query = ref('')
const copyHint = ref('')

let timeInterval: number | null = null
let copyTimer: number | null = null

const formatTime = (timestamp: number) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString('zh-CN')
}

const durationClass = (ms: number) => {
  const n = Number(ms) || 0
  if (n > 1000) return 'text-danger fw-bold'
  if (n > 500) return 'text-warning'
  return 'text-success'
}

const updateCurrentTime = () => {
  currentTime.value = new Date().toTimeString().split(' ')[0]
}

const loadData = async () => {
  try {
    loading.value = true
    traces.value = await ApiService.getRecentTraces({
      hours: hours.value,
      limit: limit.value,
      service: selectedService.value || undefined,
      status: statusFilter.value,
      q: query.value
    })
  } catch (error) {
    console.error('加载链路数据失败:', error)
    traces.value = []
  } finally {
    loading.value = false
  }
}

const loadServices = async () => {
  try {
    services.value = await ApiService.getServiceNames()
  } catch (error) {
    console.error('加载服务列表失败:', error)
  }
}

const resetFilters = () => {
  selectedService.value = ''
  hours.value = 24
  limit.value = 50
  statusFilter.value = 'all'
  query.value = ''
  loadData()
}

const viewTraceDetail = (traceId: string) => {
  router.push({ name: 'trace-detail', params: { traceId } })
}

const copyTraceId = async (traceId: string) => {
  try {
    await navigator.clipboard.writeText(traceId)
    copyHint.value = `已复制 Trace ID：${traceId}`
    if (copyTimer) clearTimeout(copyTimer)
    copyTimer = window.setTimeout(() => {
      copyHint.value = ''
    }, 2200)
  } catch (err) {
    console.error('复制失败:', err)
    copyHint.value = '复制失败，请手动选择 Trace ID'
  }
}

onMounted(() => {
  const qSvc = route.query.service
  if (typeof qSvc === 'string' && qSvc) {
    selectedService.value = qSvc
  }
  const qStatus = route.query.status
  if (typeof qStatus === 'string' && (qStatus === 'error' || qStatus === 'ok' || qStatus === 'all')) {
    statusFilter.value = qStatus
  }
  const qText = route.query.q
  if (typeof qText === 'string' && qText) {
    query.value = qText
  }

  loadServices()
  loadData()
  updateCurrentTime()
  timeInterval = window.setInterval(updateCurrentTime, 1000)
})

watch(
  () => route.query.service,
  (svc) => {
    if (typeof svc === 'string') {
      selectedService.value = svc
      loadData()
    }
  }
)

onUnmounted(() => {
  if (timeInterval) clearInterval(timeInterval)
  if (copyTimer) clearTimeout(copyTimer)
})
</script>

<style scoped>
.si-copy-hint {
  margin-bottom: 0.75rem;
  font-size: 0.9rem;
}
</style>
