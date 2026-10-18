<template>
  <div class="si-page fade-in si-jdbc-page">
    <SiPageHeader title="数据库" icon="fa-database">
      <SiFilterBar
        v-model:service="selectedService"
        v-model:hours="hours"
        :services="services"
        :loading="loading"
        service-id="jdbc-service"
        hours-id="jdbc-hours"
        @service-change="onFilterChange"
        @hours-change="onHoursChange"
        @refresh="load"
      />
    </SiPageHeader>

    <p class="si-jdbc-hint">
      <i class="fa fa-info-circle me-1"></i>
      仅统计 <code>component=JDBC</code> · {{ formatHoursLabel(hours) }}
      <template v-if="selectedService"> · 服务 {{ selectedService }}</template>
    </p>

    <SiAlert v-if="loadError" tone="danger" class="mb-3">{{ loadError }}</SiAlert>

    <SiLoading v-if="loading" text="正在加载 JDBC 汇总…" />

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

      <SiEmpty v-if="summary.kpis.spanCount === 0" icon="fa-database">
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
      </SiEmpty>

      <template v-else>
        <SiSection title="按数据库" icon="fa-server" :count="summary.byRemote.length" label="按库聚合">
          <SiDataTable
            :rows="summary.byRemote"
            :columns="remoteColumns"
            row-key="remoteService"
          />
        </SiSection>

        <SiSection title="最近 JDBC Span" icon="fa-list" :count="summary.recent.length" label="最近 JDBC">
          <SiDataTable
            :rows="summary.recent"
            :columns="recentColumns"
            :row-key="recentKey"
            clickable
            @row-click="onRecentClick"
          >
            <template #cell-operationName="{ row }">
              {{ row.operationName || '—' }}
              <SiStatusBadge v-if="row.slow" class="ms-1" tone="warn">慢</SiStatusBadge>
            </template>
            <template #cell-sql="{ row }">
              <span v-if="row.sqlType" class="si-jdbc-sql-type">{{ row.sqlType }}</span>
              {{ row.sql || '—' }}
            </template>
            <template #cell-status="{ row }">
              <SiStatusBadge :tone="jdbcFailed(row) ? 'danger' : 'success'">
                {{ row.statusCode || (row.success === false ? 'ERR' : 'OK') }}
              </SiStatusBadge>
            </template>
            <template #cell-traceId="{ row }">
              <code class="small">{{ shortId(row.traceId) }}</code>
            </template>
          </SiDataTable>
        </SiSection>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SiAlert from '../components/SiAlert.vue'
import SiDataTable from '../components/SiDataTable.vue'
import SiEmpty from '../components/SiEmpty.vue'
import SiFilterBar from '../components/SiFilterBar.vue'
import SiLoading from '../components/SiLoading.vue'
import SiPageHeader from '../components/SiPageHeader.vue'
import SiSection from '../components/SiSection.vue'
import SiStatusBadge from '../components/SiStatusBadge.vue'
import type { SiColumn } from '../components/siTable'
import { ApiService } from '../services/ApiService'
import { formatDuration } from '../utils/traceTimeline'
import {
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
type JdbcSummary = Awaited<ReturnType<typeof ApiService.getJdbcSummary>>
type RemoteRow = JdbcSummary['byRemote'][number]
type RecentRow = JdbcSummary['recent'][number]

const summary = ref<JdbcSummary | null>(null)

const remoteColumns: SiColumn<RemoteRow>[] = [
  {
    key: 'remoteService',
    label: '库（remoteService）',
    truncate: true,
    title: (row) => row.remoteService
  },
  { key: 'callCount', label: '次数', numeric: true },
  {
    key: 'errorCount',
    label: '错误',
    numeric: true,
    cellClass: (row) => (row.errorCount > 0 ? 'text-danger fw-semibold' : undefined)
  },
  {
    key: 'slowCount',
    label: '慢',
    numeric: true,
    cellClass: (row) => (row.slowCount > 0 ? 'text-warning fw-semibold' : undefined)
  },
  { key: 'avgMs', label: '平均', numeric: true, value: (row) => formatDuration(row.avgMs) },
  { key: 'maxMs', label: '最大', numeric: true, value: (row) => formatDuration(row.maxMs) }
]

const recentColumns: SiColumn<RecentRow>[] = [
  { key: 'serviceName', label: '服务', value: (row) => row.serviceName || '—' },
  { key: 'operationName', label: '操作' },
  {
    key: 'remoteService',
    label: '库',
    truncate: true,
    title: (row) => row.remoteService || undefined,
    value: (row) => row.remoteService || '—'
  },
  {
    key: 'sql',
    label: 'SQL',
    truncate: true,
    title: (row) => row.sql || undefined
  },
  { key: 'durationMs', label: '耗时', numeric: true, value: (row) => formatDuration(row.durationMs) },
  { key: 'status', label: '状态' },
  { key: 'traceId', label: 'Trace' }
]

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

const jdbcFailed = (row: RecentRow) => row.success === false || row.statusCode === 'ERROR'

const recentKey = (row: RecentRow, index: number) => `${row.spanId || row.traceId}-${index}`

const onRecentClick = (row: RecentRow) => {
  goTrace(row.traceId, row.spanId)
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

.si-jdbc-sql-type {
  display: inline-block;
  margin-right: 0.35rem;
  padding: 0.05rem 0.35rem;
  border-radius: 0.25rem;
  background: var(--si-teal-soft);
  font-size: 0.7rem;
  font-weight: 600;
}

</style>
