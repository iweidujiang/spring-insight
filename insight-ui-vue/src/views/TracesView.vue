<template>
  <div class="si-page fade-in">
    <div class="si-page__header">
      <div>
        <h2 class="page-title mb-1">
          <i class="fa fa-list-ul me-2"></i>链路追踪
        </h2>
        <p class="page-description mb-0">
          一行一次请求。点整行看详情；筛选会写入地址栏，便于分享
        </p>
      </div>
      <div class="si-page__toolbar">
        <label class="si-auto-refresh form-check">
          <input class="form-check-input" type="checkbox" v-model="autoRefresh" />
          <span class="form-check-label">自动刷新</span>
        </label>
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
          <div class="col-lg-2 col-md-4 col-sm-6">
            <label for="service-select" class="form-label">服务</label>
            <select id="service-select" class="form-select" v-model="selectedService" @change="onFilterChange">
              <option value="">全部</option>
              <option v-for="service in services" :key="service" :value="service">
                {{ service }}
              </option>
            </select>
          </div>
          <div class="col-lg-2 col-md-4 col-sm-6">
            <label for="hours-select" class="form-label">时间范围</label>
            <select id="hours-select" class="form-select" v-model.number="hours" @change="onFilterChange">
              <option v-for="opt in TIME_RANGE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
          </div>
          <div class="col-lg-2 col-md-4 col-sm-6">
            <label for="status-select" class="form-label">状态</label>
            <select id="status-select" class="form-select" v-model="statusFilter" @change="onFilterChange">
              <option value="all">全部</option>
              <option value="error">仅异常</option>
              <option value="ok">仅成功</option>
            </select>
          </div>
          <div class="col-lg-2 col-md-4 col-sm-6">
            <label for="min-dur-select" class="form-label">最慢起</label>
            <select id="min-dur-select" class="form-select" v-model.number="minDurationMs" @change="onFilterChange">
              <option :value="0">不限</option>
              <option :value="100">≥100ms</option>
              <option :value="500">≥500ms</option>
              <option :value="1000">≥1s</option>
              <option :value="3000">≥3s</option>
            </select>
          </div>
          <div class="col-lg-1 col-md-4 col-sm-6">
            <label for="limit-select" class="form-label">数量</label>
            <select id="limit-select" class="form-select" v-model.number="limit" @change="onFilterChange">
              <option :value="20">20</option>
              <option :value="50">50</option>
              <option :value="100">100</option>
              <option :value="200">200</option>
            </select>
          </div>
          <div class="col-lg-3 col-md-8 col-sm-12">
            <label for="q-input" class="form-label">搜索</label>
            <div class="d-flex gap-2">
              <input
                id="q-input"
                class="form-control"
                type="search"
                v-model="query"
                placeholder="Trace ID / 服务 / 操作名"
                @keyup.enter="onFilterChange"
              />
              <button class="btn btn-primary" @click="onFilterChange" :disabled="loading" title="搜索">
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
          <h5 class="card-title mb-0">最近请求</h5>
          <span class="badge bg-primary">{{ traces.length }} 条</span>
        </div>
        <div class="table-responsive">
          <table class="table table-hover mb-0 si-trace-table">
            <thead class="table-light">
              <tr>
                <th>请求</th>
                <th>耗时</th>
                <th>状态</th>
                <th>服务</th>
                <th>时间</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(trace, index) in traces"
                :key="`${trace.traceId}-${index}`"
                class="fade-in si-trace-row"
                :style="{ animationDelay: `${Math.min(index, 20) * 0.03}s` }"
                @click="viewTraceDetail(trace.traceId)"
              >
                <td class="si-trace-op">
                  <span class="si-trace-method" :data-method="traceMethod(trace)">{{ traceMethod(trace) || 'REQ' }}</span>
                  <span class="si-trace-path" :title="tracePath(trace)">{{ tracePath(trace) }}</span>
                  <span v-if="trace.serviceCount > 1" class="si-trace-extra">{{ trace.serviceCount }} 个服务</span>
                </td>
                <td class="si-trace-dur">
                  <span class="si-trace-track">
                    <i :style="{ width: durationShare(trace) + '%' }"></i>
                  </span>
                  <em :class="durationClass(trace.durationMs)">{{ formatDuration(Number(trace.durationMs) || 0) }}</em>
                </td>
                <td>
                  <span class="badge" :class="traceFailed(trace) ? 'bg-danger' : 'bg-success'">
                    {{ traceFailed(trace) ? '失败' : '成功' }}
                  </span>
                </td>
                <td class="text-truncate si-trace-service" :title="trace.serviceName">{{ trace.serviceName || '—' }}</td>
                <td class="si-trace-time">{{ formatTime(trace.startTime) }}</td>
                <td @click.stop>
                  <button class="btn btn-sm btn-outline-secondary" @click="copyTraceId(trace.traceId)" title="复制 Trace ID">
                    <i class="fa fa-copy"></i>
                  </button>
                </td>
              </tr>
              <tr v-if="traces.length === 0">
                <td colspan="6" class="text-center text-muted">
                  <div class="py-4">
                    <i class="fa fa-info-circle fa-2x mb-2"></i>
                    <p class="mb-0">{{ emptyNoMatchMessage(hours) }}</p>
                    <p class="small mb-0 mt-1">{{ EMPTY_HINT_RELAX }}</p>
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
import {
  TIME_RANGE_OPTIONS,
  emptyNoMatchMessage,
  EMPTY_HINT_RELAX
} from '../utils/timeRange'

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
const minDurationMs = ref(0)
const query = ref('')
const copyHint = ref('')
const autoRefresh = ref(false)

let timeInterval: number | null = null
let copyTimer: number | null = null
let refreshTimer: number | null = null
let syncingFromRoute = false

const formatTime = (timestamp: number) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString('zh-CN')
}

const durationClass = (ms: number) => {
  const n = Number(ms) || 0
  if (n > 1000) return 'text-danger fw-bold'
  if (n > 500) return 'text-warning'
  return ''
}

const traceFailed = (trace: any) => !!(trace?.hasError || trace?.statusCode === 'ERROR')

const traceMethod = (trace: any) => {
  const op = String(trace?.operationName || '')
  const matched = /^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\b/i.exec(op)
  return matched ? matched[1].toUpperCase() : ''
}

const tracePath = (trace: any) => {
  const op = String(trace?.operationName || trace?.traceId || '—')
  return op.replace(/^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\s+/i, '')
}

const durationShare = (trace: any) => {
  const max = traces.value.reduce((n, item) => Math.max(n, Number(item?.durationMs) || 0), 0)
  if (max <= 0) return 8
  return Math.max(8, Math.round(((Number(trace?.durationMs) || 0) / max) * 100))
}

const updateCurrentTime = () => {
  currentTime.value = new Date().toTimeString().split(' ')[0]
}

const syncQueryToRoute = () => {
  const q: Record<string, string> = {}
  if (selectedService.value) q.service = selectedService.value
  if (hours.value !== 24) q.hours = String(hours.value)
  if (statusFilter.value !== 'all') q.status = statusFilter.value
  if (minDurationMs.value > 0) q.minDurationMs = String(minDurationMs.value)
  if (limit.value !== 50) q.limit = String(limit.value)
  if (query.value.trim()) q.q = query.value.trim()
  router.replace({ path: '/traces', query: q })
}

const applyRouteQuery = () => {
  syncingFromRoute = true
  const q = route.query
  selectedService.value = typeof q.service === 'string' ? q.service : ''
  hours.value = q.hours != null && String(q.hours) !== '' ? Number(q.hours) : 24
  limit.value = Number(q.limit) > 0 ? Number(q.limit) : 50
  minDurationMs.value = Number(q.minDurationMs) > 0 ? Number(q.minDurationMs) : 0
  if (typeof q.status === 'string' && (q.status === 'error' || q.status === 'ok' || q.status === 'all')) {
    statusFilter.value = q.status
  } else {
    statusFilter.value = 'all'
  }
  query.value = typeof q.q === 'string' ? q.q : ''
  syncingFromRoute = false
}

const loadData = async () => {
  try {
    loading.value = true
    traces.value = await ApiService.getRecentTraces({
      hours: hours.value,
      limit: limit.value,
      service: selectedService.value || undefined,
      status: statusFilter.value,
      q: query.value,
      minDurationMs: minDurationMs.value
    })
  } catch (error) {
    console.error('加载链路数据失败:', error)
    traces.value = []
  } finally {
    loading.value = false
  }
}

const onFilterChange = async () => {
  if (!syncingFromRoute) syncQueryToRoute()
  await loadData()
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
  minDurationMs.value = 0
  query.value = ''
  onFilterChange()
}

const viewTraceDetail = (traceId: string) => {
  router.push({ name: 'trace-detail', params: { traceId }, query: route.query })
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

const setupAutoRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
  if (autoRefresh.value) {
    refreshTimer = window.setInterval(() => {
      if (!loading.value) loadData()
    }, 15000)
  }
}

onMounted(() => {
  applyRouteQuery()
  loadServices()
  loadData()
  updateCurrentTime()
  timeInterval = window.setInterval(updateCurrentTime, 1000)
})

watch(
  () => route.query,
  () => {
    if (route.path !== '/traces') return
    applyRouteQuery()
    loadData()
  }
)

watch(autoRefresh, () => setupAutoRefresh())

onUnmounted(() => {
  if (timeInterval) clearInterval(timeInterval)
  if (copyTimer) clearTimeout(copyTimer)
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style scoped>
.si-copy-hint {
  margin-bottom: 0.75rem;
  font-size: 0.9rem;
}

.si-auto-refresh {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  margin: 0;
  font-size: 0.85rem;
  color: var(--si-ink-soft, #334);
  user-select: none;
}

.si-trace-row {
  cursor: pointer;
}

.si-trace-op {
  display: flex;
  align-items: baseline;
  gap: 0.55rem;
  min-width: 12rem;
  max-width: 36rem;
}

.si-trace-method {
  flex: 0 0 auto;
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: var(--si-teal, #0f766e);
}

.si-trace-method[data-method="POST"],
.si-trace-method[data-method="PUT"],
.si-trace-method[data-method="PATCH"] {
  color: #b45309;
}

.si-trace-method[data-method="DELETE"] {
  color: #b91c1c;
}

.si-trace-path {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 650;
  color: var(--si-ink, #15241f);
}

.si-trace-extra {
  flex: 0 0 auto;
  font-size: 0.72rem;
  color: var(--si-muted, #6b7f76);
}

.si-trace-dur {
  min-width: 9rem;
  white-space: nowrap;
}

.si-trace-track {
  display: inline-block;
  width: 4.5rem;
  height: 4px;
  margin-right: 0.45rem;
  vertical-align: middle;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.12);
  overflow: hidden;
}

.si-trace-track i {
  display: block;
  height: 100%;
  background: #0f766e;
}

.si-trace-dur em {
  font-style: normal;
  font-weight: 700;
}

.si-trace-service {
  max-width: 8rem;
}

.si-trace-time {
  font-size: 0.82rem;
  color: var(--si-muted, #6b7f76);
  white-space: nowrap;
}
</style>
