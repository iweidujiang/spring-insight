<template>
  <div class="si-page fade-in si-services-page">
    <SiPageHeader title="服务" icon="Monitor">
      <template #description>
        点进某个服务可查看 KPI、依赖、最近链路与 JVM 运行时曲线。
      </template>
      <SiFilterBar
        v-model:hours="hours"
        :show-service="false"
        :loading="loading"
        hours-id="services-hours"
        @hours-change="onHoursChange"
        @refresh="load"
      />
    </SiPageHeader>

    <SiAlert v-if="loadError" tone="danger" class="si-services__alert">{{ loadError }}</SiAlert>
    <SiLoading v-if="loading" text="正在加载服务列表…" />

    <template v-else>
      <SiEmpty v-if="rows.length === 0" :icon="Monitor">
        <p class="si-services__empty-text">{{ emptyInRangeShort(hours, '服务') }}</p>
        <p class="si-services__empty-hint">业务服务接入并上报后，会出现在此列表。</p>
        <el-button type="primary" plain size="small" @click="goAboutSetup">查看接入说明</el-button>
      </SiEmpty>

      <section v-else class="si-services__panel" aria-label="服务列表">
        <SiDataTable
          class="si-services__table"
          :rows="rows"
          :columns="columns"
          row-key="serviceName"
          clickable
          @row-click="goDetail"
        >
          <template #cell-service="{ row }">
            <span class="si-services__name">{{ row.serviceName }}</span>
          </template>
          <template #cell-action="{ row }">
            <el-button type="primary" link size="small" @click.stop="goDetail(row)">详情</el-button>
            <el-button link size="small" @click.stop="goTraces(row.serviceName)">链路</el-button>
          </template>
        </SiDataTable>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Monitor } from '@element-plus/icons-vue'
import { ApiService } from '../services/ApiService'
import SiAlert from '../components/SiAlert.vue'
import SiDataTable from '../components/SiDataTable.vue'
import SiEmpty from '../components/SiEmpty.vue'
import SiFilterBar from '../components/SiFilterBar.vue'
import SiLoading from '../components/SiLoading.vue'
import SiPageHeader from '../components/SiPageHeader.vue'
import type { SiColumn } from '../components/siTable'
import { formatDuration } from '../utils/traceTimeline'
import { emptyInRangeShort, loadFailedMessage } from '../utils/timeRange'
import { resolveHours, persistHours, mergeQuery } from '../utils/insightQuery'

type ServiceRow = {
  serviceName: string
  spanCount: number
  errorCount: number
  errorRate: number
  avgMs: number
  p95Ms: number
}

const route = useRoute()
const router = useRouter()

const hours = ref(24)
const loading = ref(true)
const loadError = ref('')
const rows = ref<ServiceRow[]>([])

const columns: SiColumn<ServiceRow>[] = [
  {
    key: 'service',
    label: '服务',
    cellClass: 'si-services__col-name',
    value: (row) => row.serviceName,
    title: (row) => row.serviceName
  },
  { key: 'spans', label: 'Span', value: (row) => row.spanCount },
  {
    key: 'errors',
    label: '错误',
    value: (row) => `${row.errorCount}（${row.errorRate.toFixed(1)}%）`,
    cellClass: (row) => (row.errorCount > 0 ? 'si-services__cell--danger' : undefined)
  },
  { key: 'avg', label: '平均', value: (row) => formatDuration(row.avgMs) },
  { key: 'p95', label: 'p95', value: (row) => formatDuration(row.p95Ms) },
  { key: 'action', label: '操作', cellClass: 'si-services__col-action' }
]

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

const goDetail = (row: ServiceRow) => {
  const name = String(row.serviceName || '').trim()
  if (!name) return
  router.push({
    path: `/services/${encodeURIComponent(name)}`,
    query: { hours: String(hours.value) }
  })
}

const goTraces = (serviceName: string) => {
  const name = String(serviceName || '').trim()
  if (!name) return
  router.push({
    path: '/traces',
    query: { service: name, hours: String(hours.value) }
  })
}

const goAboutSetup = () => {
  void router.push({ path: '/about', hash: '#setup' })
}

const load = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const latency = await ApiService.getServiceLatency(hours.value, 200)
    const mapped = latency
      .map((row) => ({
        serviceName: String(row.serviceName || '').trim(),
        spanCount: Number(row.spanCount || 0),
        errorCount: Number(row.errorCount || 0),
        errorRate: Number(row.errorRate || 0),
        avgMs: Number(row.avgMs || 0),
        p95Ms: Number(row.p95Ms || 0)
      }))
      .filter((row) => row.serviceName)
      .sort((a, b) => b.spanCount - a.spanCount)

    if (mapped.length > 0) {
      rows.value = mapped
    } else {
      const names = await ApiService.getServiceNames()
      rows.value = names
        .map((n) => String(n || '').trim())
        .filter(Boolean)
        .sort((a, b) => a.localeCompare(b))
        .map((serviceName) => ({
          serviceName,
          spanCount: 0,
          errorCount: 0,
          errorRate: 0,
          avgMs: 0,
          p95Ms: 0
        }))
    }
  } catch {
    loadError.value = loadFailedMessage('服务列表')
    rows.value = []
  } finally {
    loading.value = false
  }
}

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
.si-services__alert {
  margin-bottom: 0.75rem;
}

.si-services__empty-text {
  margin: 0 0 0.35rem;
  font-size: 0.92rem;
  color: var(--si-ink);
}

.si-services__empty-hint {
  margin: 0 0 0.85rem;
  font-size: 0.82rem;
  color: var(--si-muted);
}

.si-services__panel {
  border: 1px solid var(--card-border);
  border-radius: 12px;
  background: var(--card-bg);
  padding: 0.35rem 0.5rem 0.65rem;
}

.si-services__name {
  font-weight: 650;
  color: var(--si-ink);
}

.si-services__cell--danger {
  color: var(--danger-color);
  font-weight: 600;
}

.si-services__col-action {
  white-space: nowrap;
}
</style>
