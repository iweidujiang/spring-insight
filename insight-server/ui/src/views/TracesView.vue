<template>
  <div class="si-page fade-in">
    <SiPageHeader title="链路追踪" icon="fa-list-ul">
      <div class="si-page__toolbar">
        <span class="badge bg-info">
          <i class="fa fa-clock me-1"></i>{{ currentTime }}
        </span>
      </div>
    </SiPageHeader>

    <SiFilterBar
      layout="fields"
      :show-refresh="false"
      v-model:service="selectedService"
      v-model:hours="hours"
      :services="services"
      :loading="loading"
      service-id="service-select"
      hours-id="hours-select"
      service-label="服务"
      all-services-label="全部"
      @service-change="onFilterChange"
      @hours-change="onFilterChange"
    >
      <template #fields>
        <div class="si-filter-bar__field">
          <label for="status-select" class="form-label">状态</label>
          <select id="status-select" class="form-select" v-model="statusFilter" @change="onFilterChange">
            <option value="all">全部</option>
            <option value="error">仅异常</option>
            <option value="ok">仅成功</option>
          </select>
        </div>
        <div class="si-filter-bar__field">
          <label for="min-dur-select" class="form-label">最慢起</label>
          <select id="min-dur-select" class="form-select" v-model.number="minDurationMs" @change="onFilterChange">
            <option :value="0">不限</option>
            <option :value="100">≥100ms</option>
            <option :value="500">≥500ms</option>
            <option :value="1000">≥1s</option>
            <option :value="3000">≥3s</option>
          </select>
        </div>
        <div class="si-filter-bar__field">
          <label for="path-prefix-input" class="form-label">路径前缀</label>
          <input
            id="path-prefix-input"
            class="form-control"
            type="search"
            v-model="pathPrefix"
            placeholder="留空不限，例 /order"
            @keyup.enter="onFilterChange"
            @change="onFilterChange"
          />
        </div>
        <div class="si-filter-bar__field si-filter-bar__field--narrow">
          <label for="limit-select" class="form-label">数量</label>
          <select id="limit-select" class="form-select" v-model.number="limit" @change="onFilterChange">
            <option :value="20">20</option>
            <option :value="50">50</option>
            <option :value="100">100</option>
            <option :value="200">200</option>
          </select>
        </div>
        <div class="si-filter-bar__field si-filter-bar__field--search">
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
            <button type="button" class="btn btn-primary" @click="onFilterChange" :disabled="loading" title="搜索">
              <i class="fa fa-search"></i>
            </button>
            <button type="button" class="btn btn-outline-secondary" @click="resetFilters" :disabled="loading" title="重置">
              <i class="fa fa-refresh"></i>
            </button>
          </div>
        </div>
      </template>
    </SiFilterBar>

    <SiAlert v-if="copyHint" tone="success" class="si-copy-hint">
      {{ copyHint }}
    </SiAlert>
    <SiAlert v-if="loadError" tone="danger" class="mb-3">{{ loadError }}</SiAlert>

    <SiLoading v-if="loading" text="正在加载链路数据..." />

    <div v-else class="card stat-card si-table-panel">
      <div class="card-body">
        <div class="si-trace-list-bar mb-2">
          <div class="si-trace-list-bar__title">
            <h5 class="card-title mb-0">最近请求</h5>
            <span class="badge bg-primary">{{ traces.length }} 条</span>
          </div>
          <div class="si-trace-list-bar__actions">
            <button
              type="button"
              class="btn btn-outline-primary btn-sm"
              :disabled="selectedIds.length !== 2"
              @click="goCompare"
              :title="selectedIds.length === 2 ? '对比已选两条 Trace' : '请勾选恰好两条 Trace'"
            >
              <i class="fa fa-columns me-1"></i>对比 ({{ selectedIds.length }}/2)
            </button>
            <button
              v-if="selectedIds.length"
              type="button"
              class="btn btn-outline-secondary btn-sm"
              @click="clearSelection"
            >
              清除选择
            </button>
            <label class="si-auto-refresh form-check mb-0">
              <input class="form-check-input" type="checkbox" v-model="autoRefresh" />
              <span class="form-check-label">自动刷新</span>
            </label>
            <button type="button" class="btn btn-primary btn-sm" @click="loadData" :disabled="loading">
              <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i>
              <span>刷新</span>
            </button>
          </div>
        </div>
        <SiDataTable
          :rows="traces"
          :columns="traceColumns"
          :row-key="traceRowKey"
          clickable
          :row-class="traceRowClass"
          :row-style="traceRowStyle"
          @row-click="onTraceRowClick"
        >
          <template #cell-check="{ row }">
            <input
              class="form-check-input"
              type="checkbox"
              :checked="isSelected(row.traceId)"
              :disabled="!isSelected(row.traceId) && selectedIds.length >= 2"
              @change="toggleSelect(row.traceId)"
              :aria-label="`选择 ${row.traceId}`"
            />
          </template>
          <template #cell-operation="{ row }">
            <div class="si-trace-op__inner">
              <span class="si-http-method" :data-method="traceMethod(row)">{{ traceMethod(row) || 'REQ' }}</span>
              <span class="si-trace-path" :title="tracePath(row)">{{ tracePath(row) }}</span>
              <span v-if="row.serviceCount > 1" class="si-trace-extra">{{ row.serviceCount }} 个服务</span>
            </div>
          </template>
          <template #cell-duration="{ row }">
            <span class="si-dur" aria-hidden="false">
              <span class="si-dur__track">
                <i class="si-dur__bar" :style="{ width: durationShare(row) + '%' }"></i>
              </span>
              <em class="si-dur__val" :class="durationClass(row.durationMs)">{{ formatDuration(Number(row.durationMs) || 0) }}</em>
            </span>
          </template>
          <template #cell-status="{ row }">
            <span class="badge" :class="traceFailed(row) ? 'bg-danger' : 'bg-success'">
              {{ traceFailed(row) ? '失败' : '成功' }}
            </span>
          </template>
          <template #cell-act="{ row }">
            <button class="btn btn-sm btn-outline-secondary" @click="copyTraceId(row.traceId)" title="复制 Trace ID">
              <i class="fa fa-copy"></i>
            </button>
          </template>
          <template #empty>
            <div class="py-4">
              <i class="fa fa-info-circle fa-2x mb-2"></i>
              <p class="mb-0">{{ emptyNoMatchMessage(hours) }}</p>
              <p class="small mb-0 mt-1">{{ EMPTY_HINT_RELAX }}</p>
            </div>
          </template>
        </SiDataTable>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ApiService } from '../services/ApiService'
import SiAlert from '../components/SiAlert.vue'
import SiDataTable from '../components/SiDataTable.vue'
import SiFilterBar from '../components/SiFilterBar.vue'
import SiLoading from '../components/SiLoading.vue'
import SiPageHeader from '../components/SiPageHeader.vue'
import type { SiColumn } from '../components/siTable'
import { formatDuration } from '../utils/traceTimeline'
import {
  emptyNoMatchMessage,
  EMPTY_HINT_RELAX,
  loadFailedMessage
} from '../utils/timeRange'
import { persistHours, persistService, resolveHours, resolveService } from '../utils/insightQuery'

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
const pathPrefix = ref('')
const query = ref('')
const copyHint = ref('')
const loadError = ref('')
const autoRefresh = ref(false)
/** 最多选 2 条 Trace 用于对比 */
const selectedIds = ref<string[]>([])

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

const isSelected = (traceId: string) => selectedIds.value.includes(String(traceId || ''))

const toggleSelect = (traceId: string) => {
  const id = String(traceId || '').trim()
  if (!id) return
  const idx = selectedIds.value.indexOf(id)
  if (idx >= 0) {
    selectedIds.value = selectedIds.value.filter((x) => x !== id)
    return
  }
  if (selectedIds.value.length >= 2) return
  selectedIds.value = [...selectedIds.value, id]
}

const clearSelection = () => {
  selectedIds.value = []
}

const goCompare = () => {
  if (selectedIds.value.length !== 2) return
  const [a, b] = selectedIds.value
  router.push({
    path: '/traces/compare',
    query: { a, b, hours: String(hours.value) }
  })
}

const updateCurrentTime = () => {
  currentTime.value = new Date().toTimeString().split(' ')[0]
}

const syncQueryToRoute = () => {
  persistHours(hours.value)
  persistService(selectedService.value)
  const q: Record<string, string> = {
    hours: String(hours.value)
  }
  if (selectedService.value) q.service = selectedService.value
  if (statusFilter.value !== 'all') q.status = statusFilter.value
  if (minDurationMs.value > 0) q.minDurationMs = String(minDurationMs.value)
  if (pathPrefix.value.trim()) q.pathPrefix = pathPrefix.value.trim()
  if (limit.value !== 50) q.limit = String(limit.value)
  if (query.value.trim()) q.q = query.value.trim()
  router.replace({ path: '/traces', query: q })
}

const applyRouteQuery = () => {
  syncingFromRoute = true
  const q = route.query
  hours.value = resolveHours(q, 24)
  // URL 显式带 service（含空）时以 URL 为准；否则用 session 记忆（侧栏跨页）
  selectedService.value = 'service' in q
    ? (typeof q.service === 'string' ? q.service : '')
    : resolveService(q)
  limit.value = Number(q.limit) > 0 ? Number(q.limit) : 50
  minDurationMs.value = Number(q.minDurationMs) > 0 ? Number(q.minDurationMs) : 0
  if (typeof q.status === 'string' && (q.status === 'error' || q.status === 'ok' || q.status === 'all')) {
    statusFilter.value = q.status
  } else {
    statusFilter.value = 'all'
  }
  pathPrefix.value = typeof q.pathPrefix === 'string' ? q.pathPrefix : ''
  query.value = typeof q.q === 'string' ? q.q : ''
  persistHours(hours.value)
  persistService(selectedService.value)
  syncingFromRoute = false
}

const loadData = async () => {
  try {
    loading.value = true
    loadError.value = ''
    traces.value = await ApiService.getRecentTraces({
      hours: hours.value,
      limit: limit.value,
      service: selectedService.value || undefined,
      status: statusFilter.value,
      q: query.value,
      minDurationMs: minDurationMs.value,
      pathPrefix: pathPrefix.value
    })
  } catch (error) {
    console.error('加载链路数据失败:', error)
    traces.value = []
    loadError.value = loadFailedMessage('链路列表')
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
  pathPrefix.value = ''
  query.value = ''
  onFilterChange()
}

const viewTraceDetail = (traceId: string) => {
  router.push({ name: 'trace-detail', params: { traceId }, query: route.query })
}

type TraceRow = Record<string, any>

const traceColumns: SiColumn<TraceRow>[] = [
  { key: 'check', label: '', headerClass: 'si-trace-col-check', cellClass: 'si-trace-check', stopRowClick: true },
  { key: 'operation', label: '请求', headerClass: 'si-trace-col-op', cellClass: 'si-trace-op' },
  { key: 'duration', label: '耗时', headerClass: 'si-trace-col-dur', cellClass: 'si-trace-dur' },
  { key: 'status', label: '状态', headerClass: 'si-trace-col-status', cellClass: 'si-trace-status' },
  {
    key: 'service',
    label: '服务',
    headerClass: 'si-trace-col-svc',
    cellClass: 'text-truncate si-trace-service',
    value: (row) => row.serviceName || '—',
    title: (row) => (row.serviceName ? String(row.serviceName) : undefined)
  },
  {
    key: 'time',
    label: '时间',
    headerClass: 'si-trace-col-time',
    cellClass: 'si-trace-time',
    value: (row) => formatTime(row.startTime)
  },
  { key: 'act', label: '', headerClass: 'si-trace-col-act', cellClass: 'si-trace-act', stopRowClick: true }
]

const traceRowKey = (row: TraceRow, index: number) => `${row.traceId}-${index}`

const traceRowClass = (row: TraceRow) =>
  isSelected(row.traceId) ? 'fade-in si-trace-row si-trace-row--selected' : 'fade-in si-trace-row'

const traceRowStyle = (_row: TraceRow, index: number) => ({
  animationDelay: `${Math.min(index, 20) * 0.03}s`
})

const onTraceRowClick = (row: TraceRow) => {
  viewTraceDetail(String(row.traceId || ''))
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
  color: var(--si-ink-soft);
  user-select: none;
}

:deep(.si-trace-row) {
  cursor: pointer;
}

:deep(.si-trace-row--selected) {
  background: var(--si-hover-teal);
}

:deep(.si-trace-col-check) {
  width: 2.25rem;
}

:deep(.si-trace-check) {
  vertical-align: middle;
}

.si-trace-list-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.55rem 0.85rem;
}

.si-trace-list-bar__title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.si-trace-list-bar__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.45rem;
}

.si-trace-list-bar__actions .btn {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  white-space: nowrap;
  flex-shrink: 0;
}

:deep(.si-trace-col-op) {
  width: 40%;
}

:deep(.si-trace-col-dur) {
  width: 11rem;
}

:deep(.si-trace-col-status) {
  width: 5.5rem;
}

:deep(.si-trace-col-svc) {
  width: 18%;
}

:deep(.si-trace-col-time) {
  width: 9.5rem;
}

:deep(.si-trace-col-act) {
  width: 3.25rem;
}

:deep(.si-trace-op) {
  /* 勿对 td 设 display:flex，否则 border-collapse/行高错位 */
  min-width: 0;
  max-width: 100%;
}

.si-trace-op__inner {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  min-width: 0;
  max-width: 100%;
}

.si-trace-path {
  min-width: 0;
  flex: 1 1 auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 650;
  color: var(--si-ink);
}

.si-trace-extra {
  flex: 0 0 auto;
  font-size: 0.72rem;
  color: var(--si-muted);
  white-space: nowrap;
}

:deep(.si-trace-dur) {
  white-space: nowrap;
}

.si-trace-status .badge {
  min-width: 2.75rem;
  justify-content: center;
}

:deep(.si-trace-service) {
  max-width: none;
}

:deep(.si-trace-time) {
  font-size: 0.82rem;
  color: var(--si-muted);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

:deep(.si-trace-act) {
  text-align: right;
  width: 3.25rem;
}
</style>
