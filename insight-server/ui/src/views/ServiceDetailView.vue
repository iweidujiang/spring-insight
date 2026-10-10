<template>
  <div class="si-svc-detail fade-in">
    <header class="si-svc-detail__top">
      <div class="si-svc-detail__title-block">
        <button type="button" class="btn btn-link btn-sm px-0 si-svc-detail__back" @click="goBack">
          <i class="fa fa-arrow-left me-1"></i>返回
        </button>
        <h2 class="si-svc-detail__title">
          <i class="fa fa-cube me-2"></i>{{ serviceName || '服务详情' }}
        </h2>
        <p class="si-svc-detail__subtitle">近窗请求量 · 错误 · 慢操作 · 最近链路</p>
      </div>
      <div class="si-svc-detail__actions">
        <label class="visually-hidden" for="svc-detail-hours">时间范围</label>
        <select
          id="svc-detail-hours"
          class="form-select form-select-sm si-svc-detail__hours"
          v-model.number="hours"
          @change="onHoursChange"
        >
          <option v-for="opt in TIME_RANGE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
        <button
          type="button"
          class="btn btn-primary btn-sm si-svc-detail__refresh"
          :disabled="loading"
          @click="load"
        >
          <i class="fa fa-refresh" :class="{ 'fa-spin': loading }" aria-hidden="true"></i>
          <span>刷新</span>
        </button>
      </div>
    </header>

    <div v-if="loading" class="si-svc-detail__loading">
      <i class="fa fa-spinner fa-spin"></i><span class="ms-2">加载中…</span>
    </div>
    <div v-if="loadError" class="alert alert-danger py-2 mb-3" role="alert">{{ loadError }}</div>
    <div v-else-if="!loading && deeplinkHint" class="alert alert-warning py-2 mb-3" role="alert">{{ deeplinkHint }}</div>

    <template v-if="!loading && summary">
      <section class="si-svc-detail__kpis" aria-label="关键指标">
        <div class="card si-svc-detail__kpi">
          <div class="card-body">
            <div class="text-xs text-uppercase text-muted">Span 数</div>
            <div class="si-svc-detail__kpi-value">{{ summary.kpis.spanCount }}</div>
          </div>
        </div>
        <div class="card si-svc-detail__kpi">
          <div class="card-body">
            <div class="text-xs text-uppercase text-muted">错误</div>
            <div class="si-svc-detail__kpi-value" :class="{ 'text-danger': summary.kpis.errorCount > 0 }">
              {{ summary.kpis.errorCount }}
              <span class="si-svc-detail__kpi-sub">{{ summary.kpis.errorRate.toFixed(1) }}%</span>
            </div>
          </div>
        </div>
        <div class="card si-svc-detail__kpi">
          <div class="card-body">
            <div class="text-xs text-uppercase text-muted">p95</div>
            <div class="si-svc-detail__kpi-value">{{ formatDuration(summary.kpis.p95Ms) }}</div>
          </div>
        </div>
        <div class="card si-svc-detail__kpi">
          <div class="card-body">
            <div class="text-xs text-uppercase text-muted">平均 / 最大</div>
            <div class="si-svc-detail__kpi-value si-svc-detail__kpi-value--sm">
              {{ formatDuration(summary.kpis.avgMs) }}
              <span class="si-svc-detail__kpi-sub">/ {{ formatDuration(summary.kpis.maxMs) }}</span>
            </div>
          </div>
        </div>
      </section>

      <div class="si-svc-detail__ctas">
        <button type="button" class="btn btn-sm btn-outline-primary" @click="goTraces()">全部链路</button>
        <button type="button" class="btn btn-sm btn-outline-danger" @click="goTraces({ status: 'error' })">仅错误</button>
        <button type="button" class="btn btn-sm btn-outline-secondary" @click="goTopology">打开拓扑</button>
      </div>

      <div class="row g-3 si-svc-detail__grid">
        <div class="col-lg-6">
          <section class="card h-100">
            <div class="card-header py-2 fw-semibold">慢操作 Top</div>
            <div class="card-body p-0">
              <div v-if="summary.slowOperations.length === 0" class="p-3 text-muted small">
                {{ emptyRequestsMessage(hours) }}
              </div>
              <div v-else class="table-responsive">
                <table class="table table-sm table-hover mb-0 si-data-table">
                  <thead class="table-light">
                    <tr>
                      <th>操作</th>
                      <th>次数</th>
                      <th>错误</th>
                      <th>p95</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr
                      v-for="(op, i) in summary.slowOperations"
                      :key="i"
                      style="cursor: pointer"
                      @click="goTraces({ q: op.operationName, minDurationMs: Math.max(0, Math.floor(op.p95Ms)) })"
                    >
                      <td class="text-truncate" style="max-width: 240px" :title="op.operationName">{{ op.operationName }}</td>
                      <td>{{ op.spanCount }}</td>
                      <td :class="{ 'text-danger': op.errorCount > 0 }">{{ op.errorCount }}</td>
                      <td>{{ formatDuration(op.p95Ms) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </section>
        </div>

        <div class="col-lg-6">
          <section class="card h-100">
            <div class="card-header py-2 fw-semibold">依赖（出站 / 入站）</div>
            <div class="card-body">
              <p class="small text-muted mb-2">出站 →</p>
              <ul v-if="summary.outbound.length" class="si-svc-detail__deps">
                <li v-for="(e, i) in summary.outbound" :key="'o' + i">
                  <button type="button" class="btn btn-link btn-sm px-0" @click="goService(e.targetService)">
                    {{ e.targetService }}
                  </button>
                  <span class="text-muted small ms-1">×{{ e.callCount }}</span>
                </li>
              </ul>
              <p v-else class="small text-muted">暂无出站边</p>
              <p class="small text-muted mb-2 mt-3">← 入站</p>
              <ul v-if="summary.inbound.length" class="si-svc-detail__deps">
                <li v-for="(e, i) in summary.inbound" :key="'i' + i">
                  <button type="button" class="btn btn-link btn-sm px-0" @click="goService(e.sourceService)">
                    {{ e.sourceService }}
                  </button>
                  <span class="text-muted small ms-1">×{{ e.callCount }}</span>
                </li>
              </ul>
              <p v-else class="small text-muted">暂无入站边</p>
            </div>
          </section>
        </div>

        <div class="col-12">
          <section class="card">
            <div class="card-header py-2 d-flex justify-content-between align-items-center">
              <span class="fw-semibold">最近链路</span>
              <button type="button" class="btn btn-link btn-sm py-0" @click="goTraces()">查看全部</button>
            </div>
            <div class="card-body p-0">
              <div v-if="summary.recentTraces.length === 0" class="p-3 text-muted small">
                {{ emptyRequestsMessage(hours) }}
              </div>
              <div v-else class="table-responsive">
                <table class="table table-hover mb-0 si-data-table">
                  <thead class="table-light">
                    <tr>
                      <th>操作</th>
                      <th>耗时</th>
                      <th>状态</th>
                      <th>Trace</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr
                      v-for="(row, i) in summary.recentTraces"
                      :key="row.traceId || i"
                      style="cursor: pointer"
                      @click="goTrace(row.traceId)"
                    >
                      <td class="text-truncate" style="max-width: 320px">{{ row.operationName || '—' }}</td>
                      <td>{{ formatDuration(Number(row.durationMs) || 0) }}</td>
                      <td>
                        <span class="badge" :class="row.hasError ? 'bg-danger' : 'bg-success'">
                          {{ row.statusCode || (row.hasError ? 'ERR' : 'OK') }}
                        </span>
                      </td>
                      <td><code class="small">{{ shortId(row.traceId) }}</code></td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </section>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiService } from '../services/ApiService'
import { formatDuration } from '../utils/traceTimeline'
import { TIME_RANGE_OPTIONS, emptyRequestsMessage } from '../utils/timeRange'
import { resolveHours, persistHours, persistService, mergeQuery } from '../utils/insightQuery'
import { loadFailedMessage, deeplinkMissMessage } from '../utils/timeRange'

const route = useRoute()
const router = useRouter()

const serviceName = ref('')
const hours = ref(24)
const loading = ref(true)
const loadError = ref('')
const deeplinkHint = ref('')
const summary = ref<Awaited<ReturnType<typeof ApiService.getServiceSummary>> | null>(null)

const shortId = (id: string) => {
  const s = String(id || '')
  return s.length > 12 ? `${s.slice(0, 8)}…` : s || '—'
}

const syncHoursToRoute = () => {
  persistHours(hours.value)
  router.replace({
    path: route.path,
    query: mergeQuery(route.query, { hours: String(hours.value) })
  })
}

const onHoursChange = () => {
  syncHoursToRoute()
  load()
}

const goBack = () => {
  if (window.history.length > 1) router.back()
  else router.push({ path: '/topology', query: { hours: String(hours.value) } })
}

const goTraces = (extra: Record<string, string | number> = {}) => {
  const query: Record<string, string> = {
    service: serviceName.value,
    hours: String(hours.value)
  }
  for (const [k, v] of Object.entries(extra)) {
    if (v != null && v !== '') query[k] = String(v)
  }
  router.push({ path: '/traces', query })
}

const goTopology = () => {
  persistService(serviceName.value)
  router.push({
    path: '/topology',
    query: { service: serviceName.value, hours: String(hours.value) }
  })
}

const goService = (name: string) => {
  const n = String(name || '').trim()
  if (!n) return
  router.push({
    path: `/services/${encodeURIComponent(n)}`,
    query: { hours: String(hours.value) }
  })
}

const goTrace = (traceId: string) => {
  if (!traceId) return
  router.push({
    name: 'trace-detail',
    params: { traceId },
    query: { hours: String(hours.value) }
  })
}

const load = async () => {
  const name = decodeURIComponent(String(route.params.serviceName || '')).trim()
  serviceName.value = name
  loadError.value = ''
  deeplinkHint.value = ''
  summary.value = null
  if (!name) {
    loading.value = false
    deeplinkHint.value = deeplinkMissMessage('service')
    return
  }
  persistService(name)
  loading.value = true
  try {
    const data = await ApiService.getServiceSummary(name, hours.value, 15, 10)
    summary.value = data
    if (!data.found) {
      deeplinkHint.value = `近 ${hours.value === 0 ? '全部' : hours.value + ' 小时'}窗口内未找到服务「${name}」的 Span；可扩大时间窗或从拓扑重新进入。`
    }
  } catch {
    loadError.value = loadFailedMessage('服务详情')
  } finally {
    loading.value = false
  }
}

watch(
  () => route.params.serviceName,
  () => {
    hours.value = resolveHours(route.query, 24)
    load()
  }
)

onMounted(() => {
  hours.value = resolveHours(route.query, 24)
  persistHours(hours.value)
  if (route.query.hours == null || route.query.hours === '') {
    syncHoursToRoute()
  }
  load()
})
</script>

<style scoped>
.si-svc-detail__top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem 1rem;
  margin-bottom: 1rem;
}

.si-svc-detail__title-block {
  min-width: 0;
  flex: 1 1 12rem;
}

.si-svc-detail__title {
  margin: 0.15rem 0 0.2rem;
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-svc-detail__subtitle,
.si-svc-detail__back {
  color: var(--si-muted);
  text-decoration: none;
}

.si-svc-detail__subtitle {
  margin: 0;
  font-size: 0.85rem;
}

.si-svc-detail__actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex: 0 0 auto;
}

.si-svc-detail__hours {
  width: auto;
  min-width: 9.5rem;
  flex: 0 0 auto;
}

.si-svc-detail__refresh {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  flex: 0 0 auto;
  white-space: nowrap;
  min-height: 31px;
  padding: 0.25rem 0.75rem;
}

.si-svc-detail__refresh .fa {
  line-height: 1;
}

.si-svc-detail__loading {
  padding: 2rem;
  text-align: center;
  color: var(--si-muted);
}

.si-svc-detail__kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 0.65rem;
  margin-bottom: 0.85rem;
}

.si-svc-detail__kpi-value {
  font-size: 1.35rem;
  font-weight: 700;
  color: var(--si-ink);
  line-height: 1.2;
}

.si-svc-detail__kpi-value--sm {
  font-size: 1.1rem;
}

.si-svc-detail__kpi-sub {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--si-muted);
  margin-left: 0.25rem;
}

.si-svc-detail__ctas {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin-bottom: 1rem;
}

.si-svc-detail__deps {
  list-style: none;
  padding: 0;
  margin: 0;
}

.si-svc-detail__deps li {
  display: flex;
  align-items: baseline;
  gap: 0.25rem;
}
</style>
