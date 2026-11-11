<template>
  <div class="si-page fade-in si-alerts-page">
    <SiPageHeader title="告警历史" icon="Bell">
      <template #description>
        最近触发、恢复与升级记录（进程内保留，重启后清空）。可在设置页配置告警规则与渠道。
      </template>
      <div class="si-alerts__actions">
        <el-button size="small" :loading="loading" @click="load">
          <el-icon v-if="!loading" class="me-1"><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button size="small" @click="goSettings">告警设置</el-button>
      </div>
    </SiPageHeader>

    <SiAlert v-if="loadError" tone="danger" class="si-alerts__alert">{{ loadError }}</SiAlert>
    <SiLoading v-if="loading" text="正在加载告警历史…" />

    <template v-else>
      <SiEmpty v-if="rows.length === 0" :icon="Bell">
        <p class="si-alerts__empty-text">暂无告警历史</p>
        <p class="si-alerts__empty-hint">开启告警并触发阈值后，推送成功与恢复事件会出现在此。</p>
        <el-button type="primary" plain size="small" @click="goSettings">去设置告警</el-button>
      </SiEmpty>

      <section v-else class="si-alerts__panel" aria-label="告警历史">
        <SiDataTable
          class="si-alerts__table"
          :rows="rows"
          :columns="columns"
          row-key="id"
          clickable
          @row-click="goService"
        >
          <template #cell-event="{ row }">
            <SiStatusBadge :tone="eventTone(row.event)">{{ eventLabel(row.event) }}</SiStatusBadge>
          </template>
          <template #cell-service="{ row }">
            <span class="si-alerts__svc">{{ row.serviceName || '—' }}</span>
          </template>
        </SiDataTable>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, Refresh } from '@element-plus/icons-vue'
import { ApiService } from '../services/ApiService'
import SiAlert from '../components/SiAlert.vue'
import SiDataTable from '../components/SiDataTable.vue'
import SiEmpty from '../components/SiEmpty.vue'
import SiLoading from '../components/SiLoading.vue'
import SiPageHeader from '../components/SiPageHeader.vue'
import SiStatusBadge from '../components/SiStatusBadge.vue'
import type { SiColumn } from '../components/siTable'
import { loadFailedMessage } from '../utils/timeRange'

type AlertHistoryRow = {
  id: string
  event: string
  serviceName: string
  metric: string
  value: number | null
  threshold: number | null
  at: string
  atEpochMs: number
  durationMinutes: number | null
  delivered: boolean
}

const router = useRouter()
const loading = ref(true)
const loadError = ref('')
const rows = ref<AlertHistoryRow[]>([])

const eventLabel = (event: string) => {
  switch (String(event || '').toLowerCase()) {
    case 'fire':
      return '触发'
    case 'recover':
      return '恢复'
    case 'escalate':
      return '升级'
    default:
      return event || '—'
  }
}

const eventTone = (event: string): 'danger' | 'success' | 'warn' | 'neutral' => {
  switch (String(event || '').toLowerCase()) {
    case 'fire':
      return 'danger'
    case 'recover':
      return 'success'
    case 'escalate':
      return 'warn'
    default:
      return 'neutral'
  }
}

const formatWhen = (row: AlertHistoryRow) => {
  if (row.atEpochMs > 0) {
    try {
      return new Date(row.atEpochMs).toLocaleString()
    } catch {
      /* fall through */
    }
  }
  return row.at || '—'
}

const formatValue = (row: AlertHistoryRow) => {
  if (row.value == null) return '—'
  const v = Number(row.value)
  if (!Number.isFinite(v)) return '—'
  return Number.isInteger(v) ? String(v) : v.toFixed(2)
}

const columns: SiColumn<AlertHistoryRow>[] = [
  {
    key: 'event',
    label: '事件',
    cellClass: 'si-alerts__col-event',
    value: (row) => eventLabel(row.event)
  },
  {
    key: 'service',
    label: '服务',
    value: (row) => row.serviceName,
    title: (row) => row.serviceName
  },
  { key: 'metric', label: '指标', value: (row) => row.metric || '—' },
  { key: 'value', label: '值', value: (row) => formatValue(row) },
  {
    key: 'threshold',
    label: '阈值',
    value: (row) => (row.threshold == null ? '—' : String(row.threshold))
  },
  {
    key: 'duration',
    label: '持续',
    value: (row) =>
      row.durationMinutes == null ? '—' : `${Math.floor(row.durationMinutes)} 分`
  },
  { key: 'when', label: '时间', value: (row) => formatWhen(row) }
]

const goSettings = () => {
  void router.push({ path: '/settings', query: { section: 'alert' } })
}

const goService = (row: AlertHistoryRow) => {
  const name = String(row.serviceName || '').trim()
  if (!name) return
  void router.push({ path: `/services/${encodeURIComponent(name)}` })
}

const load = async () => {
  loading.value = true
  loadError.value = ''
  try {
    rows.value = await ApiService.getAlertHistory(100)
  } catch {
    loadError.value = loadFailedMessage('告警历史')
    rows.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
})
</script>

<style scoped>
.si-alerts__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}

.si-alerts__alert {
  margin-bottom: 0.75rem;
}

.si-alerts__empty-text {
  margin: 0 0 0.35rem;
  font-size: 0.92rem;
  color: var(--si-ink);
}

.si-alerts__empty-hint {
  margin: 0 0 0.75rem;
  font-size: 0.82rem;
  color: var(--si-muted);
}

.si-alerts__panel {
  min-width: 0;
}

.si-alerts__col-event {
  width: 5.5rem;
}

.si-alerts__svc {
  font-weight: 600;
}
</style>
