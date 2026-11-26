<template>
  <div class="si-page fade-in si-redis-page">
    <SiPageHeader title="缓存" icon="Box">
      <SiFilterBar
        v-model:service="selectedService"
        v-model:window="observationWindow"
        :services="services"
        :loading="loading"
        service-id="redis-service"
        hours-id="redis-hours"
        @service-change="onFilterChange"
        @window-change="onWindowChange"
        @refresh="load"
      />
    </SiPageHeader>

    <p class="si-redis-hint">
      <el-icon class="me-1"><InfoFilled /></el-icon>
      仅统计 <code>component=REDIS</code> · {{ formatWindowLabel(observationWindow) }}
      <template v-if="selectedService"> · 服务 {{ selectedService }}</template>
    </p>

    <SiAlert v-if="loadError" tone="danger" class="si-redis__alert">{{ loadError }}</SiAlert>

    <SiLoading v-if="loading" text="正在加载 Redis 汇总…" />

    <template v-else-if="summary">
      <section class="si-redis-kpis" aria-label="关键指标">
        <div class="si-redis-kpi">
          <div class="si-redis-kpi__label">Redis Span</div>
          <div class="si-redis-kpi__value">{{ summary.kpis.spanCount }}</div>
        </div>
        <div class="si-redis-kpi">
          <div class="si-redis-kpi__label">错误</div>
          <div class="si-redis-kpi__value" :class="{ 'is-danger': summary.kpis.errorCount > 0 }">
            {{ summary.kpis.errorCount }}
          </div>
        </div>
        <div class="si-redis-kpi">
          <div class="si-redis-kpi__label">慢命令</div>
          <div class="si-redis-kpi__value" :class="{ 'is-warn': summary.kpis.slowCount > 0 }">
            {{ summary.kpis.slowCount }}
          </div>
        </div>
        <div class="si-redis-kpi">
          <div class="si-redis-kpi__label">平均 / p95 / 最大</div>
          <div class="si-redis-kpi__value si-redis-kpi__value--sm">
            {{ formatDuration(summary.kpis.avgMs) }}
            <span class="si-redis-kpi__sub">/ {{ formatDuration(summary.kpis.p95Ms) }}</span>
            <span class="si-redis-kpi__sub">/ {{ formatDuration(summary.kpis.maxMs) }}</span>
          </div>
        </div>
      </section>

      <SiEmpty v-if="summary.kpis.spanCount === 0" :icon="Box">
        <p class="si-redis-empty__text">{{ emptyInRangeShort(hours, 'Redis 调用') }}</p>
        <p v-if="selectedService" class="si-redis-empty__hint">
          当前按业务服务「{{ selectedService }}」筛选。Redis Span 记在<strong>执行命令的服务</strong>上。
          可改选实际访问 Redis 的服务，或选「全部服务」。
        </p>
        <p v-else class="si-redis-empty__hint">
          请确认业务已启用 <code>redis-tracing-enabled</code> 并上报到 insight-server；拓扑图不再显示缓存节点。
        </p>
        <el-button
          v-if="selectedService"
          type="primary"
          plain
          size="small"
          class="si-redis-empty__btn"
          @click="clearServiceFilter"
        >
          查看全部服务
        </el-button>
      </SiEmpty>

      <template v-else>
        <SiSection title="按 Redis 实例" icon="Monitor" :count="summary.byRemote.length" label="按实例聚合">
          <SiDataTable
            :rows="summary.byRemote"
            :columns="remoteColumns"
            row-key="remoteService"
          />
        </SiSection>

        <SiSection
          title="按命令"
          icon="Collection"
          :count="summary.byCommand.length"
          label="按 Redis 命令聚合"
        >
          <p class="si-redis-cmd-hint">按 GET / SET / HGET 等命令汇总次数与耗时；点击行或「样例链路」打开 Trace。</p>
          <ul v-if="commandBars.length" class="si-redis-cmd-bars" aria-label="命令次数分布">
            <li v-for="bar in commandBars" :key="bar.command" class="si-redis-cmd-bar">
              <span class="si-redis-cmd-type">{{ bar.command }}</span>
              <div class="si-redis-cmd-bar__track" :title="`${bar.callCount} 次`">
                <div class="si-redis-cmd-bar__fill" :style="{ width: bar.pct + '%' }" />
              </div>
              <span class="si-redis-cmd-bar__count">{{ bar.callCount }}</span>
            </li>
          </ul>
          <SiDataTable
            :rows="summary.byCommand"
            :columns="commandColumns"
            :row-key="commandKey"
            clickable
            @row-click="onCommandClick"
          >
            <template #cell-command="{ row }">
              <span class="si-redis-cmd-type">{{ row.command || '—' }}</span>
              <span v-if="row.sampleKey" class="si-redis-cmd-sample-key">{{ row.sampleKey }}</span>
            </template>
            <template #cell-action="{ row }">
              <el-button
                type="primary"
                plain
                size="small"
                :disabled="!row.sampleTraceId"
                @click.stop="goTrace(row.sampleTraceId, row.sampleSpanId)"
              >
                样例链路
              </el-button>
            </template>
          </SiDataTable>
        </SiSection>

        <SiSection title="最近 Redis Span" icon="List" :count="summary.recent.length" label="最近 Redis">
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
            <template #cell-command="{ row }">
              <span v-if="row.command" class="si-redis-cmd-type">{{ row.command }}</span>
              {{ row.key || '—' }}
            </template>
            <template #cell-status="{ row }">
              <SiStatusBadge :tone="redisFailed(row) ? 'danger' : 'success'">
                {{ row.statusCode || (row.success === false ? 'ERR' : 'OK') }}
              </SiStatusBadge>
            </template>
            <template #cell-traceId="{ row }">
              <code class="si-redis-cell--small">{{ shortId(row.traceId) }}</code>
            </template>
          </SiDataTable>
        </SiSection>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { InfoFilled, Box } from '@element-plus/icons-vue'
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
  formatWindowLabel,
  loadFailedMessage
} from '../utils/timeRange'
import {
  mergeQuery,
  persistWindow,
  resolveWindow,
  windowHours,
  windowQueryPatch,
  type ObservationWindow
} from '../utils/insightQuery'

const route = useRoute()
const router = useRouter()

const observationWindow = ref<ObservationWindow>({ kind: 'rolling', hours: 24 })
const hours = computed(() => windowHours(observationWindow.value))
const selectedService = ref('')
const services = ref<string[]>([])
const loading = ref(true)
const loadError = ref('')
type RedisSummary = Awaited<ReturnType<typeof ApiService.getRedisSummary>>
type RemoteRow = RedisSummary['byRemote'][number]
type CommandRow = RedisSummary['byCommand'][number]
type RecentRow = RedisSummary['recent'][number]

const summary = ref<RedisSummary | null>(null)

const commandBars = computed(() => {
  const rows = summary.value?.byCommand || []
  if (!rows.length) return []
  const top = rows.slice(0, 8)
  const max = Math.max(...top.map((r) => r.callCount), 1)
  return top.map((r) => ({
    command: r.command || '(unknown)',
    callCount: r.callCount,
    pct: Math.max(4, Math.round((r.callCount / max) * 100))
  }))
})

const remoteColumns: SiColumn<RemoteRow>[] = [
  {
    key: 'remoteService',
    label: '实例（remoteService）',
    truncate: true,
    title: (row) => row.remoteService
  },
  { key: 'callCount', label: '次数', numeric: true },
  {
    key: 'errorCount',
    label: '错误',
    numeric: true,
    cellClass: (row) => (row.errorCount > 0 ? 'si-redis-cell--danger' : undefined)
  },
  {
    key: 'slowCount',
    label: '慢',
    numeric: true,
    cellClass: (row) => (row.slowCount > 0 ? 'si-redis-cell--warning' : undefined)
  },
  { key: 'avgMs', label: '平均', numeric: true, value: (row) => formatDuration(row.avgMs) },
  { key: 'maxMs', label: '最大', numeric: true, value: (row) => formatDuration(row.maxMs) }
]

const commandColumns: SiColumn<CommandRow>[] = [
  {
    key: 'command',
    label: '命令',
    truncate: true,
    title: (row) => [row.command, row.sampleKey].filter(Boolean).join(' ') || undefined
  },
  { key: 'callCount', label: '次数', numeric: true },
  {
    key: 'errorCount',
    label: '错误',
    numeric: true,
    cellClass: (row) => (row.errorCount > 0 ? 'si-redis-cell--danger' : undefined)
  },
  {
    key: 'slowCount',
    label: '慢',
    numeric: true,
    cellClass: (row) => (row.slowCount > 0 ? 'si-redis-cell--warning' : undefined)
  },
  { key: 'p95Ms', label: 'p95', numeric: true, value: (row) => formatDuration(row.p95Ms) },
  { key: 'maxMs', label: '最大', numeric: true, value: (row) => formatDuration(row.maxMs) },
  { key: 'action', label: '', stopRowClick: true }
]

const recentColumns: SiColumn<RecentRow>[] = [
  { key: 'serviceName', label: '服务', value: (row) => row.serviceName || '—' },
  { key: 'operationName', label: '操作' },
  {
    key: 'remoteService',
    label: '实例',
    truncate: true,
    title: (row) => row.remoteService || undefined,
    value: (row) => row.remoteService || '—'
  },
  {
    key: 'command',
    label: '命令 / Key',
    truncate: true,
    title: (row) => [row.command, row.key].filter(Boolean).join(' ') || undefined
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
  persistWindow(observationWindow.value)
  router.replace({
    path: '/redis',
    query: mergeQuery(route.query, {
      ...windowQueryPatch(observationWindow.value),
      service: selectedService.value || null
    })
  })
}

const onWindowChange = () => {
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

const redisFailed = (row: RecentRow) => row.success === false || row.statusCode === 'ERROR'

const commandKey = (row: CommandRow) => row.command || '(unknown)'

const recentKey = (row: RecentRow, index: number) => `${row.spanId || row.traceId}-${index}`

const onCommandClick = (row: CommandRow) => {
  if (row.sampleTraceId) {
    goTrace(row.sampleTraceId, row.sampleSpanId)
  }
}

const onRecentClick = (row: RecentRow) => {
  goTrace(row.traceId, row.spanId)
}

const goTrace = (traceId: string, spanId?: string) => {
  if (!traceId) return
  const query = mergeQuery({}, {
    ...windowQueryPatch(observationWindow.value),
    ...(spanId ? { span: spanId } : {})
  })
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
    summary.value = await ApiService.getRedisSummary(
      observationWindow.value,
      selectedService.value || undefined,
      50
    )
  } catch {
    summary.value = null
    loadError.value = loadFailedMessage('缓存页')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  observationWindow.value = resolveWindow(route.query, 24)
  selectedService.value =
    typeof route.query.service === 'string' ? route.query.service.trim() : ''
  persistWindow(observationWindow.value)
  const q = route.query
  const hasWindowInUrl =
    (typeof q.hours === 'string' && q.hours !== '') ||
    (typeof q.from === 'string' && q.from !== '' && typeof q.to === 'string' && q.to !== '')
  if (!hasWindowInUrl) {
    syncQueryToRoute()
  }
  await loadServices()
  await load()
})
</script>

<style scoped>
.si-redis__alert {
  margin-bottom: 0.75rem;
}

.si-redis-empty__text {
  margin-bottom: 0.25rem;
}

.si-redis-empty__hint {
  font-size: 0.85rem;
  color: var(--si-muted);
  margin-bottom: 0.5rem;
}

.si-redis-empty__btn {
  margin-top: 0.25rem;
}

:deep(.si-redis-cell--small) {
  font-size: 0.84rem;
}

:deep(.si-redis-cell--danger) {
  color: var(--danger-color);
  font-weight: 600;
}

:deep(.si-redis-cell--warning) {
  color: var(--warning-color);
  font-weight: 600;
}

.si-redis-hint {
  margin: 0 0 1rem;
  font-size: 0.85rem;
  color: var(--si-muted);
}

.si-redis-kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(9rem, 1fr));
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.si-redis-kpi {
  padding: 0.85rem 1rem;
  border: 1px solid var(--card-border);
  border-radius: 0.5rem;
  background: var(--si-surface-1);
  box-shadow: var(--box-shadow);
}

.si-redis-kpi__label {
  font-size: 0.7rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--si-muted);
}

.si-redis-kpi__value {
  margin-top: 0.25rem;
  font-family: var(--font-numeric);
  font-variant-numeric: tabular-nums;
  font-size: 1.35rem;
  font-weight: 700;
  color: var(--si-ink);
  line-height: 1.2;
}

.si-redis-kpi__value--sm {
  font-size: 1.05rem;
}

.si-redis-kpi__value.is-danger {
  color: var(--danger-color);
}

.si-redis-kpi__value.is-warn {
  color: var(--warning-color);
}

.si-redis-kpi__sub {
  font-weight: 500;
  font-size: 0.9em;
  color: var(--si-muted);
}

.si-redis-cmd-type {
  display: inline-block;
  margin-right: 0.35rem;
  padding: 0.05rem 0.35rem;
  border-radius: 0.25rem;
  background: var(--si-brand-soft);
  font-size: 0.7rem;
  font-weight: 600;
}

.si-redis-cmd-hint {
  margin: 0 0 0.65rem;
  font-size: 0.82rem;
  color: var(--si-muted);
}

.si-redis-cmd-bars {
  list-style: none;
  margin: 0 0 0.85rem;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.si-redis-cmd-bar {
  display: grid;
  grid-template-columns: 4.5rem minmax(0, 1fr) 2.5rem;
  align-items: center;
  gap: 0.5rem;
}

.si-redis-cmd-bar__track {
  height: 0.45rem;
  border-radius: 999px;
  background: var(--si-surface-3, #f1f5f9);
  overflow: hidden;
}

.si-redis-cmd-bar__fill {
  height: 100%;
  border-radius: inherit;
  background: var(--si-brand);
  opacity: 0.85;
}

.si-redis-cmd-bar__count {
  font-family: var(--font-numeric);
  font-variant-numeric: tabular-nums;
  font-size: 0.8rem;
  color: var(--si-muted);
  text-align: right;
}

.si-redis-cmd-sample-key {
  font-size: 0.82rem;
  color: var(--si-muted);
}

</style>
