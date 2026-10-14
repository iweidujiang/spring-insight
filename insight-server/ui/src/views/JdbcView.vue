<template>
  <div class="si-page fade-in si-jdbc-page">
    <div class="si-page__header">
      <div>
        <h2 class="page-title mb-1">
          <i class="fa fa-database me-2"></i>数据库
        </h2>
        <p class="page-description mb-0">
          JDBC 出站调用汇总，可在此查看慢 SQL / 错误
        </p>
      </div>
      <div class="si-page__toolbar">
        <label class="form-label mb-0 visually-hidden" for="jdbc-service">业务服务</label>
        <select
          id="jdbc-service"
          class="form-select form-select-sm si-jdbc-filter"
          v-model="selectedService"
          @change="onFilterChange"
        >
          <option value="">全部服务</option>
          <option v-for="name in services" :key="name" :value="name">{{ name }}</option>
        </select>
        <label class="form-label mb-0 visually-hidden" for="jdbc-hours">时间范围</label>
        <select
          id="jdbc-hours"
          class="form-select form-select-sm si-jdbc-filter"
          v-model.number="hours"
          @change="onHoursChange"
        >
          <option v-for="opt in TIME_RANGE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
        <button class="btn btn-sm btn-primary" @click="load" :disabled="loading">
          <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i> 刷新
        </button>
      </div>
    </div>

    <p class="si-jdbc-hint">
      <i class="fa fa-info-circle me-1"></i>
      仅统计 <code>component=JDBC</code> · {{ formatHoursLabel(hours) }}
      <template v-if="selectedService"> · 服务 {{ selectedService }}</template>
    </p>

    <div v-if="loadError" class="alert alert-danger py-2 mb-3" role="alert">{{ loadError }}</div>

    <div v-if="loading" class="loading-spinner">
      <i class="fa fa-spinner fa-spin"></i>
      <span class="ms-2">正在加载 JDBC 汇总…</span>
    </div>

    <template v-else-if="summary">
      <section class="si-jdbc-kpis" aria-label="关键指标">
        <div class="si-jdbc-kpi">
          <div class="si-jdbc-kpi__label">JDBC Span</div>
          <div class="si-jdbc-kpi__value">{{ summary.kpis.spanCount }}</div>
        </div>
        <div class="si-jdbc-kpi">
          <div class="si-jdbc-kpi__label">错误</div>
          <div class="si-jdbc-kpi__value" :class="{ 'is-danger': summary.kpis.errorCount > 0 }">
            {{ summary.kpis.errorCount }}
          </div>
        </div>
        <div class="si-jdbc-kpi">
          <div class="si-jdbc-kpi__label">慢查询</div>
          <div class="si-jdbc-kpi__value" :class="{ 'is-warn': summary.kpis.slowCount > 0 }">
            {{ summary.kpis.slowCount }}
          </div>
        </div>
        <div class="si-jdbc-kpi">
          <div class="si-jdbc-kpi__label">平均 / p95 / 最大</div>
          <div class="si-jdbc-kpi__value si-jdbc-kpi__value--sm">
            {{ formatDuration(summary.kpis.avgMs) }}
            <span class="si-jdbc-kpi__sub">/ {{ formatDuration(summary.kpis.p95Ms) }}</span>
            <span class="si-jdbc-kpi__sub">/ {{ formatDuration(summary.kpis.maxMs) }}</span>
          </div>
        </div>
      </section>

      <div v-if="summary.kpis.spanCount === 0" class="si-jdbc-empty">
        <i class="fa fa-database"></i>
        <p class="mb-1">{{ emptyInRangeShort(hours, 'JDBC 调用') }}</p>
        <p v-if="selectedService" class="small text-muted mb-2">
          当前按业务服务「{{ selectedService }}」筛选。JDBC Span 记在<strong>执行 SQL 的服务</strong>上
          （例如 Consumer 调 <code>/call-jdbc-slow</code> 时，库调用在 Provider）。
          可改选 Provider，或选「全部服务」。
        </p>
        <p v-else class="small text-muted mb-0">
          请确认业务已启用 <code>jdbc-tracing-enabled</code> 并上报到 insight-server；拓扑图不再显示库节点。
        </p>
        <button
          v-if="selectedService"
          type="button"
          class="btn btn-sm btn-outline-primary mt-1"
          @click="clearServiceFilter"
        >
          查看全部服务
        </button>
      </div>

      <template v-else>
        <section class="si-jdbc-panel" aria-label="按库聚合">
          <div class="si-jdbc-panel__head">
            <h3 class="si-jdbc-panel__title"><i class="fa fa-server me-2"></i>按数据库</h3>
            <span class="badge bg-secondary">{{ summary.byRemote.length }}</span>
          </div>
          <div class="table-responsive">
            <table class="table table-hover mb-0 si-data-table">
              <thead class="table-light">
                <tr>
                  <th>库（remoteService）</th>
                  <th>次数</th>
                  <th>错误</th>
                  <th>慢</th>
                  <th>平均</th>
                  <th>最大</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in summary.byRemote" :key="row.remoteService">
                  <td class="si-jdbc-remote" :title="row.remoteService">{{ row.remoteService }}</td>
                  <td>{{ row.callCount }}</td>
                  <td :class="{ 'text-danger fw-semibold': row.errorCount > 0 }">{{ row.errorCount }}</td>
                  <td :class="{ 'text-warning fw-semibold': row.slowCount > 0 }">{{ row.slowCount }}</td>
                  <td>{{ formatDuration(row.avgMs) }}</td>
                  <td>{{ formatDuration(row.maxMs) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="si-jdbc-panel" aria-label="最近 JDBC">
          <div class="si-jdbc-panel__head">
            <h3 class="si-jdbc-panel__title"><i class="fa fa-list me-2"></i>最近 JDBC Span</h3>
            <span class="badge bg-secondary">{{ summary.recent.length }}</span>
          </div>
          <div class="table-responsive">
            <table class="table table-hover mb-0 si-data-table">
              <thead class="table-light">
                <tr>
                  <th>服务</th>
                  <th>操作</th>
                  <th>库</th>
                  <th>SQL</th>
                  <th>耗时</th>
                  <th>状态</th>
                  <th>Trace</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(row, i) in summary.recent"
                  :key="(row.spanId || row.traceId) + '-' + i"
                  class="si-jdbc-row"
                  @click="goTrace(row.traceId, row.spanId)"
                >
                  <td>{{ row.serviceName || '—' }}</td>
                  <td>
                    {{ row.operationName || '—' }}
                    <span v-if="row.slow" class="badge bg-warning text-dark ms-1">慢</span>
                  </td>
                  <td class="si-jdbc-remote" :title="row.remoteService">{{ row.remoteService || '—' }}</td>
                  <td class="si-jdbc-sql" :title="row.sql || ''">
                    <span v-if="row.sqlType" class="si-jdbc-sql-type">{{ row.sqlType }}</span>
                    {{ row.sql || '—' }}
                  </td>
                  <td>{{ formatDuration(row.durationMs) }}</td>
                  <td>
                    <span
                      class="badge"
                      :class="row.success === false || row.statusCode === 'ERROR' ? 'bg-danger' : 'bg-success'"
                    >
                      {{ row.statusCode || (row.success === false ? 'ERR' : 'OK') }}
                    </span>
                  </td>
                  <td><code class="small">{{ shortId(row.traceId) }}</code></td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiService } from '../services/ApiService'
import { formatDuration } from '../utils/traceTimeline'
import {
  TIME_RANGE_OPTIONS,
  emptyInRangeShort,
  formatHoursLabel,
  loadFailedMessage
} from '../utils/timeRange'
import {
  mergeQuery,
  persistHours,
  resolveHours
} from '../utils/insightQuery'

const route = useRoute()
const router = useRouter()

const hours = ref(24)
const selectedService = ref('')
const services = ref<string[]>([])
const loading = ref(true)
const loadError = ref('')
const summary = ref<Awaited<ReturnType<typeof ApiService.getJdbcSummary>> | null>(null)

const shortId = (id: string) => {
  const s = String(id || '')
  return s.length > 12 ? `${s.slice(0, 8)}…` : s || '—'
}

const syncQueryToRoute = () => {
  persistHours(hours.value)
  // 不写 session 服务名：避免「全部服务」冲掉链路/拓扑记住的入口服务
  router.replace({
    path: '/jdbc',
    query: mergeQuery(route.query, {
      hours: String(hours.value),
      service: selectedService.value || null
    })
  })
}

const onHoursChange = () => {
  syncQueryToRoute()
  load()
}

const onFilterChange = () => {
  syncQueryToRoute()
  load()
}

const clearServiceFilter = () => {
  selectedService.value = ''
  onFilterChange()
}

const goTrace = (traceId: string, spanId?: string) => {
  if (!traceId) return
  const query: Record<string, string> = { hours: String(hours.value) }
  if (spanId) query.span = spanId
  router.push({
    name: 'trace-detail',
    params: { traceId },
    query
  })
}

const loadServices = async () => {
  try {
    services.value = await ApiService.getServiceNames()
  } catch {
    services.value = []
  }
}

const load = async () => {
  loading.value = true
  loadError.value = ''
  try {
    summary.value = await ApiService.getJdbcSummary(
      hours.value,
      selectedService.value || undefined,
      50
    )
  } catch {
    summary.value = null
    loadError.value = loadFailedMessage('数据库页')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  hours.value = resolveHours(route.query, 24)
  // 仅 URL 显式带 service 时筛选；不沿用链路/拓扑的 session 服务（常为入口 Consumer）
  selectedService.value =
    typeof route.query.service === 'string' ? route.query.service.trim() : ''
  persistHours(hours.value)
  if (route.query.hours == null || route.query.hours === '') {
    syncQueryToRoute()
  }
  await loadServices()
  await load()
})
</script>

<style scoped>
.si-jdbc-filter {
  width: auto;
  min-width: 7.5rem;
}

.si-jdbc-hint {
  margin: 0 0 1rem;
  font-size: 0.85rem;
  color: var(--si-muted, #6b7f76);
}

.si-jdbc-kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(9rem, 1fr));
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.si-jdbc-kpi {
  padding: 0.85rem 1rem;
  border: 1px solid var(--card-border);
  border-radius: 0.5rem;
  background: var(--card-bg);
}

.si-jdbc-kpi__label {
  font-size: 0.7rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--si-muted, #6b7f76);
}

.si-jdbc-kpi__value {
  margin-top: 0.25rem;
  font-size: 1.35rem;
  font-weight: 700;
  color: var(--si-ink, #15241f);
  line-height: 1.2;
}

.si-jdbc-kpi__value--sm {
  font-size: 1.05rem;
}

.si-jdbc-kpi__value.is-danger {
  color: #b91c1c;
}

.si-jdbc-kpi__value.is-warn {
  color: #b45309;
}

.si-jdbc-kpi__sub {
  font-weight: 500;
  font-size: 0.9em;
  color: var(--si-muted, #6b7f76);
}

.si-jdbc-empty {
  text-align: center;
  padding: 2.5rem 1rem;
  color: var(--si-muted, #6b7f76);
  border: 1px dashed var(--card-border);
  border-radius: 0.5rem;
}

.si-jdbc-empty .fa {
  font-size: 1.75rem;
  margin-bottom: 0.75rem;
  opacity: 0.55;
}

.si-jdbc-panel {
  margin-bottom: 1.25rem;
  border: 1px solid var(--card-border);
  border-radius: 0.5rem;
  overflow: hidden;
  background: var(--card-bg);
}

.si-jdbc-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.65rem 1rem;
  border-bottom: 1px solid var(--card-border);
}

.si-jdbc-panel__title {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 650;
}

.si-jdbc-remote,
.si-jdbc-sql {
  max-width: 16rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.85rem;
}

.si-jdbc-sql-type {
  display: inline-block;
  margin-right: 0.35rem;
  padding: 0.05rem 0.35rem;
  border-radius: 0.25rem;
  background: var(--si-teal-soft);
  font-size: 0.7rem;
  font-weight: 600;
}

.si-jdbc-row {
  cursor: pointer;
}

.si-jdbc-row:hover {
  background: rgba(15, 118, 110, 0.06);
}
</style>
