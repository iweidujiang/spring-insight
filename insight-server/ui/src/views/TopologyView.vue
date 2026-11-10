<template>
  <div class="si-page fade-in si-topo-page">
    <SiPageHeader title="服务拓扑图" icon="Share">
      <SiFilterBar
        v-model:hours="hours"
        :show-service="false"
        :loading="loading"
        hours-id="hours-topology"
        @hours-change="onHoursChange"
        @refresh="loadData"
      >
        <el-button
          size="small"
          :disabled="loading || dependencies.length === 0"
          @click="downloadTopology"
        >
          <el-icon class="me-1"><Download /></el-icon> 导出
        </el-button>
        <el-tag type="info" effect="plain" size="small" round>
          <el-icon class="me-1"><Clock /></el-icon>{{ currentTime }}
        </el-tag>
      </SiFilterBar>
    </SiPageHeader>

    <SiAlert v-if="loadError" tone="danger" class="si-topo-alert">{{ loadError }}</SiAlert>
    <SiAlert v-else-if="queryBudgetHint" tone="warning" class="si-topo-alert">{{ queryBudgetHint }}</SiAlert>
    <SiAlert v-if="deeplinkHint" tone="warning" class="si-topo-alert">{{ deeplinkHint }}</SiAlert>

    <SiLoading v-if="loading" text="正在加载拓扑数据..." />

    <SiOnboardingCard
      v-else-if="isEmpty"
      class="si-topo-onboard"
      :refreshing="loading"
      @refresh="loadData"
    />

    <div v-else-if="isSolo" class="si-topo-solo">
      <section class="si-topo-solo__hero">
        <div class="si-topo-solo__node" aria-hidden="true">
          <span>{{ soloName.slice(0, 1).toUpperCase() }}</span>
        </div>
        <div class="si-topo-solo__copy">
          <h3>{{ soloName }}</h3>
          <div class="si-topo-solo__actions">
            <el-button type="primary" size="small" @click="goServiceDetail(soloName)">服务详情</el-button>
            <el-button type="primary" plain size="small" @click="goServiceTraces(soloName)">查看链路</el-button>
            <el-button size="small" @click="goDashboard">回仪表盘</el-button>
          </div>
        </div>
      </section>

      <section class="si-topo-solo__metrics" aria-label="服务延迟摘要">
        <div class="si-topo-solo__metric">
          <span>请求</span>
          <strong>{{ soloLatency.spanCount }}</strong>
        </div>
        <div class="si-topo-solo__metric">
          <span>平均</span>
          <strong>{{ formatMs(soloLatency.avgMs) }}</strong>
        </div>
        <div class="si-topo-solo__metric">
          <span>p95 <PercentileHelp /></span>
          <strong :class="soloLatency.p95Ms >= 1000 ? 'si-topo-text--danger' : soloLatency.p95Ms >= 500 ? 'si-topo-text--warning' : ''">
            {{ formatMs(soloLatency.p95Ms) }}
          </strong>
        </div>
        <div class="si-topo-solo__metric">
          <span>错误</span>
          <strong :class="soloLatency.errorCount > 0 ? 'si-topo-text--danger' : ''">{{ soloLatency.errorCount }}</strong>
        </div>
      </section>

      <section class="si-topo-solo__panel">
        <div class="si-topo-solo__panel-head">
          <h5 class="si-topo-solo__panel-title">最近请求</h5>
          <el-button link type="primary" size="small" @click="goServiceTraces(soloName)">全部</el-button>
        </div>
        <div v-if="recentTraces.length === 0" class="si-topo-solo__empty">{{ emptyRequestsMessage(hours) }}</div>
        <ul v-else class="si-topo-solo__list">
          <li
            v-for="tr in recentTraces"
            :key="tr.traceId"
            @click="goTraceDetail(tr.traceId)"
          >
            <span class="si-http-method" :data-method="traceMethod(tr)">{{ traceMethod(tr) || 'REQ' }}</span>
            <span class="si-topo-solo__path" :title="tracePath(tr)">{{ tracePath(tr) }}</span>
            <em class="si-dur__val">{{ formatMs(tr.durationMs || 0) }}</em>
            <el-tag
              :type="traceFailed(tr) ? 'danger' : 'success'"
              effect="dark"
              size="small"
              round
            >
              {{ traceFailed(tr) ? '失败' : '成功' }}
            </el-tag>
          </li>
        </ul>
      </section>

<!--      <section class="si-topo-solo__hint">
        <h6>什么情况下展示调用拓扑图？</h6>
        <ol>
          <li>业务侧依赖 <code>spring-insight-agent-starter</code>，并配置 <code>server-url</code></li>
          <li>出站走 OpenFeign、<code>WebClient.Builder</code>、Gateway 或 <code>RestTemplateBuilder</code></li>
          <li>Agent 写入 <code>remoteService</code> 后，刷新本页即可看到「调用方 → 被调用方」</li>
        </ol>
      </section>-->
    </div>

    <div v-else class="si-topo-mesh">
      <div class="si-topo-stage">
        <section class="si-topo-stage__main" aria-label="服务依赖拓扑">
          <div class="si-topo-stage__head">
            <div>
              <h3 class="si-topo-stage__title">
                <el-icon class="me-2"><Share /></el-icon>服务依赖拓扑
              </h3>
              <p class="si-topo-legend">
                <span>{{ serviceNames.length }} 个服务</span>
                <span>·</span>
                <span>{{ dependencies.length }} 条调用链路</span>
                <span>·</span>
                <span>箭头 = 调用方向</span>
                <span>·</span>
                <span>线上数字 = 调用次数</span>
                <template v-if="healthAvailable">
                  <span>·</span>
                  <span class="si-topo-legend__swatches" aria-label="健康分图例">
                    <span class="si-topo-swatch si-topo-swatch--healthy">健康</span>
                    <span class="si-topo-swatch si-topo-swatch--warning">警告</span>
                    <span class="si-topo-swatch si-topo-swatch--degraded">降级</span>
                    <span class="si-topo-swatch si-topo-swatch--critical">严重</span>
                  </span>
                </template>
              </p>
            </div>
            <div class="si-topo-stage__toolbar">
              <el-button type="primary" plain size="small" title="重绘" @click="refreshTopology">
                <el-icon><Refresh /></el-icon>
              </el-button>
              <el-button size="small" title="适应窗口" @click="fitToScreen">
                <el-icon><FullScreen /></el-icon>
              </el-button>
            </div>
          </div>
          <div class="si-topo-canvas-wrap">
            <div ref="chartEl" class="si-topo-canvas"></div>

            <!-- 左侧服务列表 -->
            <aside class="si-topo-service-list" :class="{ 'si-topo-service-list--collapsed': serviceListCollapsed }">
              <div class="si-topo-service-list__header">
                <h4 class="si-topo-service-list__title">
                  <el-icon class="me-1"><List /></el-icon>服务列表
                </h4>
                <el-button
                  link
                  size="small"
                  @click="serviceListCollapsed = !serviceListCollapsed"
                  :title="serviceListCollapsed ? '展开' : '收起'"
                >
                  <el-icon><component :is="serviceListCollapsed ? 'Expand' : 'Fold'" /></el-icon>
                </el-button>
              </div>
              <ul v-show="!serviceListCollapsed" class="si-topo-service-list__items">
                <li
                  v-for="svc in sortedServiceNames"
                  :key="svc"
                  class="si-topo-service-list__item"
                  :class="{
                    'si-topo-service-list__item--active': selection.service === svc,
                    'si-topo-service-list__item--healthy': getServiceHealthLevel(svc) === 'healthy',
                    'si-topo-service-list__item--warning': getServiceHealthLevel(svc) === 'warning',
                    'si-topo-service-list__item--degraded': getServiceHealthLevel(svc) === 'degraded',
                    'si-topo-service-list__item--critical': getServiceHealthLevel(svc) === 'critical',
                    'si-topo-service-list__item--stale': getServicePresence(svc) === 'stale'
                  }"
                  @click="highlightService(svc)"
                >
                  <span class="si-topo-service-list__dot"></span>
                  <span class="si-topo-service-list__name">{{ svc }}</span>
                  <span
                    v-if="getServicePresence(svc) === 'stale'"
                    class="si-topo-service-list__stale"
                    :title="presenceTitleFor(svc)"
                  >沉默</span>
                  <span v-if="nodeCallCount(svc)" class="si-topo-service-list__count">{{ nodeCallCount(svc) }}</span>
                </li>
              </ul>
            </aside>
          </div>
        </section>

        <aside class="si-topo-stage__aside" aria-label="选中项">
          <div class="si-topo-rail">
            <h3 class="si-topo-rail__title">
              <el-icon class="me-2"><Pointer /></el-icon>选中
            </h3>

            <template v-if="selection.kind === 'node' && selection.service">
              <p class="si-topo-rail__kicker">服务节点</p>
              <p class="si-topo-rail__name">{{ selection.service }}</p>
              <p
                v-if="getServicePresence(selection.service) === 'stale'"
                class="si-topo-rail__stale"
                :title="presenceTitleFor(selection.service)"
              >
                {{ presenceTitleFor(selection.service) }}
              </p>
              <dl class="si-topo-rail__stats">
                <div><dt>关联调用</dt><dd>{{ nodeCallCount(selection.service) }}</dd></div>
                <div><dt>平均耗时</dt><dd>{{ formatMs(nodeAvgMs(selection.service)) }}</dd></div>
                <div><dt>错误</dt><dd :class="{ 'si-topo-text--danger': nodeErrorCount(selection.service) > 0 }">{{ nodeErrorCount(selection.service) }}</dd></div>
                <div v-if="nodeHealth(selection.service)">
                  <dt>健康分</dt>
                  <dd :class="healthLevelClass(nodeHealth(selection.service)!.level)">
                    {{ nodeHealth(selection.service)!.score }}
                    <span class="si-topo-rail__level">{{ healthLevelText(nodeHealth(selection.service)!.level) }}</span>
                  </dd>
                </div>
              </dl>
              <div class="si-topo-rail__actions">
                <el-button type="primary" size="small" class="si-topo-btn--block" @click="goServiceDetail(selection.service)">
                  服务详情
                </el-button>
                <el-button type="primary" plain size="small" class="si-topo-btn--block" @click="goServiceTraces(selection.service)">
                  查看该服务链路
                </el-button>
                <el-button size="small" class="si-topo-btn--block" @click="clearSelection">
                  取消选中
                </el-button>
              </div>
            </template>

            <template v-else-if="selection.kind === 'edge' && selection.service && selection.peer">
              <p class="si-topo-rail__kicker">调用链路</p>
              <p class="si-topo-rail__edge">
                <span>{{ selection.service }}</span>
                <el-icon class="si-topo-rail__arrow"><ArrowRight /></el-icon>
                <span>{{ selection.peer }}</span>
              </p>
              <dl class="si-topo-rail__stats">
                <div><dt>调用次数</dt><dd>{{ edgeCallCount(selection.service, selection.peer) }}</dd></div>
                <div><dt>平均耗时</dt><dd>{{ formatMs(edgeAvgMs(selection.service, selection.peer)) }}</dd></div>
              </dl>
              <div class="si-topo-rail__actions">
                <el-button type="primary" size="small" class="si-topo-btn--block" @click="goServiceTraces(selection.service)">
                  查看调用方链路
                </el-button>
                <el-button size="small" class="si-topo-btn--block" @click="goServiceTraces(selection.peer)">
                  查看被调方链路
                </el-button>
                <el-button
                  type="info"
                  plain
                  size="small"
                  class="si-topo-btn--block"
                  :loading="explaining"
                  :disabled="explaining || !aiReady"
                  :title="aiReady ? (aiStatusReason || '解释当前调用边') : (aiStatusReason || '请先在设置中启用 AI')"
                  @click="explainEdge(selection.service, selection.peer)"
                >
                  <el-icon v-if="!explaining" class="me-1"><MagicStick /></el-icon>
                  {{ explaining ? '解读中…' : 'AI 解释此链路' }}
                </el-button>
                <el-button link type="primary" size="small" @click="clearSelection">取消选中</el-button>
              </div>
              <AiExplainPanel
                v-if="edgeExplain"
                :result="edgeExplain"
                title="链路解读"
                :meta="[edgeExplain.provider, edgeExplain.model].filter(Boolean).join(' · ')"
                @close="edgeExplain = null"
              />
            </template>

            <p v-else class="si-topo-rail__empty">
              点击图上的节点或调用链路，在此查看摘要并下钻。
            </p>
          </div>
        </aside>
      </div>

      <details class="si-topo-fold">
        <summary class="si-topo-fold__summary">
          <el-icon class="me-2"><List /></el-icon>依赖关系列表（{{ dependencies.length }}）
        </summary>
        <SiDataTable
          :rows="sortedDependencies"
          :columns="depColumns"
          :row-key="depRowKey"
          clickable
          :row-class="depRowClass"
          empty-text=""
          @row-click="onDepClick"
        >
          <template #cell-source="{ row }">
            <code class="si-svc">{{ row.sourceService }}</code>
          </template>
          <template #cell-arrow>
            <el-icon class="si-topo-table-arrow"><ArrowRight /></el-icon>
          </template>
          <template #cell-target="{ row }">
            <code class="si-svc">{{ row.targetService }}</code>
          </template>
          <template #cell-action="{ row }">
            <el-button type="primary" plain size="small" @click="goServiceTraces(row.sourceService)">
              链路
            </el-button>
          </template>
        </SiDataTable>
      </details>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { Download, Clock, Share, Refresh, FullScreen, Pointer, ArrowRight, MagicStick, List, Expand, Fold } from '@element-plus/icons-vue'
import PercentileHelp from '../components/PercentileHelp.vue'
import AiExplainPanel from '../components/AiExplainPanel.vue'
import SiAlert from '../components/SiAlert.vue'
import SiOnboardingCard from '../components/SiOnboardingCard.vue'
import SiFilterBar from '../components/SiFilterBar.vue'
import SiLoading from '../components/SiLoading.vue'
import SiPageHeader from '../components/SiPageHeader.vue'
import SiDataTable from '../components/SiDataTable.vue'
import type { SiColumn } from '../components/siTable'
import { ApiService, isQueryTooLargeError, queryTooLargeDetail, type AiExplainResult } from '../services/ApiService'
import { useTheme } from '../composables/useTheme'
import {
  buildTopologyOption,
  resolveTopologyClick,
  type ServiceHealthInfo
} from '../utils/topologyGraph'
import { useChartTheme } from '../composables/useChartTheme'
import { formatDuration } from '../utils/traceTimeline'
import { presenceTitle } from '../utils/servicePresence'
import {
  emptyRequestsMessage,
  deeplinkMissMessage,
  loadFailedMessage,
  queryTooLargeMessage
} from '../utils/timeRange'
import {
  mergeQuery,
  persistHours,
  persistService,
  resolveHours
} from '../utils/insightQuery'

const router = useRouter()
const route = useRoute()
const { effectiveTheme } = useTheme()
const loading = ref(true)
const loadError = ref('')
const queryBudgetHint = ref('')
const deeplinkHint = ref('')
const currentTime = ref('')
const hours = ref(72)
let syncingSelection = false
const dependencies = ref<any[]>([])
const serviceNames = ref<string[]>([])
const serviceLatency = ref<any[]>([])
const recentTraces = ref<any[]>([])
/** null = 健康 API 不可用，回退错误数着色；对象 = 按健康分着色 */
const healthByService = ref<Record<string, ServiceHealthInfo> | null>(null)
const presenceByService = ref<
  Record<
    string,
    {
      presence: string
      quietMinutes: number | null
      staleThresholdMinutes: number
    }
  >
>({})
const chartEl = ref<HTMLElement | null>(null)
const explaining = ref(false)
const edgeExplain = ref<AiExplainResult | null>(null)
const aiReady = ref(false)
const aiStatusReason = ref('')
const selection = ref<{ kind: 'node' | 'edge' | null; service: string; peer?: string }>({
  kind: null,
  service: ''
})
const serviceListCollapsed = ref(false)

let topologyChart: echarts.ECharts | null = null
let timeInterval: number | null = null
let dashAnimationTimer: number | null = null

const { colors: chartColors } = useChartTheme()

const healthAvailable = computed(() => healthByService.value != null)

/** 当前时间窗无任何服务与调用边：首跑接入引导 */
const isEmpty = computed(
  () => !loadError.value && serviceNames.value.length === 0 && dependencies.value.length === 0
)
const isSolo = computed(() => dependencies.value.length === 0 && serviceNames.value.length === 1)
const soloName = computed(() => serviceNames.value[0] || '未命名服务')
const soloLatency = computed(() => {
  const row = serviceLatency.value.find((item: any) => item.serviceName === soloName.value) || serviceLatency.value[0]
  return {
    spanCount: row?.spanCount || 0,
    avgMs: row?.avgMs || 0,
    p95Ms: row?.p95Ms || 0,
    errorCount: row?.errorCount || 0
  }
})

const sortedDependencies = computed(() =>
  [...dependencies.value].sort((a, b) => (b.callCount || 0) - (a.callCount || 0))
)

// 排序后的服务名列表（按调用次数降序）
const sortedServiceNames = computed(() => {
  const callCounts = new Map<string, number>()
  dependencies.value.forEach((d: any) => {
    callCounts.set(d.sourceService, (callCounts.get(d.sourceService) || 0) + (d.callCount || 0))
    callCounts.set(d.targetService, (callCounts.get(d.targetService) || 0) + (d.callCount || 0))
  })
  return [...serviceNames.value].sort((a, b) => (callCounts.get(b) || 0) - (callCounts.get(a) || 0))
})

const getServiceHealthLevel = (serviceName: string): string | undefined => {
  return healthByService.value?.[serviceName]?.level
}

const getServicePresence = (serviceName: string): string => {
  return presenceByService.value?.[serviceName]?.presence || 'unknown'
}

const presenceTitleFor = (serviceName: string): string => {
  const row = presenceByService.value?.[serviceName]
  return presenceTitle(row?.presence, row?.quietMinutes ?? null, row?.staleThresholdMinutes ?? 5)
}

const formatMs = (ms: number) => formatDuration(Number(ms) || 0)

type DepRow = {
  sourceService: string
  targetService: string
  callCount?: number
  avgDuration?: number
}

const depColumns: SiColumn<DepRow>[] = [
  { key: 'source', label: '源服务' },
  { key: 'arrow', label: '', cellClass: 'si-topo-cell--center' },
  { key: 'target', label: '目标服务' },
  { key: 'count', label: '调用次数', value: (row) => row.callCount ?? 0 },
  {
    key: 'avg',
    label: '平均耗时',
    value: (row) => formatMs(row.avgDuration || 0),
    cellClass: (row) => {
      const ms = Number(row.avgDuration) || 0
      if (ms > 1000) return 'si-topo-text--danger'
      if (ms > 500) return 'si-topo-text--warning'
      return 'si-topo-text--success'
    }
  },
  { key: 'action', label: '', stopRowClick: true }
]

const depRowKey = (row: DepRow) => `${row.sourceService}-${row.targetService}`

const depRowClass = (row: DepRow) => {
  const current = selection.value
  if (
    current.kind === 'edge' &&
    current.service === row.sourceService &&
    current.peer === row.targetService
  ) {
    return 'si-topo-row--active'
  }
  return ''
}

const onDepClick = (row: DepRow) => {
  selectEdge(String(row.sourceService || ''), String(row.targetService || ''))
}
const traceFailed = (tr: any) => !!(tr?.hasError || tr?.statusCode === 'ERROR')
const traceMethod = (tr: any) => {
  const op = String(tr?.operationName || '')
  const matched = /^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\b/i.exec(op)
  return matched ? matched[1].toUpperCase() : ''
}
const tracePath = (tr: any) => {
  const op = String(tr?.operationName || tr?.traceId || '—')
  return op.replace(/^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\s+/i, '')
}

const highlightOpts = () => {
  if (selection.value.kind === 'node' && selection.value.service) {
    return { highlight: { node: selection.value.service } }
  }
  if (selection.value.kind === 'edge' && selection.value.service && selection.value.peer) {
    return {
      highlight: {
        edge: { source: selection.value.service, target: selection.value.peer }
      }
    }
  }
  return {}
}

const errorCountByService = () => {
  const map: Record<string, number> = {}
  serviceLatency.value.forEach((row: any) => {
    const name = String(row?.serviceName || '')
    if (name) map[name] = Number(row?.errorCount) || 0
  })
  return map
}

const topologyOpts = () => ({
  standaloneServices: serviceNames.value,
  theme: chartColors.value,
  healthByService: healthByService.value,
  errorCountByService: errorCountByService(),
  darkTechStyle: effectiveTheme.value === 'dark',
  ...highlightOpts()
})

const nodeHealth = (name: string): ServiceHealthInfo | null =>
  healthByService.value?.[name] || null

const healthLevelText = (level: string) => {
  switch (level) {
    case 'healthy':
      return '健康'
    case 'warning':
      return '警告'
    case 'degraded':
      return '降级'
    case 'critical':
      return '严重'
    default:
      return '未知'
  }
}

const healthLevelClass = (level: string) => {
  switch (level) {
    case 'healthy':
      return 'si-topo-text--success'
    case 'warning':
      return 'si-topo-text--warning'
    case 'degraded':
      return 'si-topo-text--degraded'
    case 'critical':
      return 'si-topo-text--danger'
    default:
      return 'si-topo-text--muted'
  }
}

const findEdge = (source: string, target: string) =>
  dependencies.value.find((d) => d.sourceService === source && d.targetService === target)

const nodeCallCount = (name: string) => {
  let n = 0
  dependencies.value.forEach((d) => {
    if (d.sourceService === name || d.targetService === name) n += Number(d.callCount) || 0
  })
  if (n > 0) return n
  const row = serviceLatency.value.find((item: any) => item.serviceName === name)
  return row?.spanCount || 0
}

const nodeAvgMs = (name: string) => {
  const row = serviceLatency.value.find((item: any) => item.serviceName === name)
  if (row?.avgMs != null) return Number(row.avgMs) || 0
  const related = dependencies.value.filter((d) => d.sourceService === name || d.targetService === name)
  if (!related.length) return 0
  const sum = related.reduce((a, d) => a + (Number(d.avgDuration) || 0), 0)
  return Math.round(sum / related.length)
}

const nodeErrorCount = (name: string) => {
  const row = serviceLatency.value.find((item: any) => item.serviceName === name)
  return Number(row?.errorCount) || 0
}

const edgeCallCount = (source: string, target: string) => Number(findEdge(source, target)?.callCount) || 0
const edgeAvgMs = (source: string, target: string) => Number(findEdge(source, target)?.avgDuration) || 0

const updateCurrentTime = () => {
  currentTime.value = new Date().toTimeString().split(' ')[0]
}

const goDashboard = () => router.push({ path: '/', query: { hours: String(hours.value) } })
const goServiceTraces = (serviceName: string) => {
  if (!serviceName) return
  persistService(serviceName)
  router.push({ path: '/traces', query: { service: serviceName, hours: String(hours.value) } })
}
const goServiceDetail = (serviceName: string) => {
  const name = String(serviceName || '').trim()
  if (!name) return
  persistService(name)
  router.push({
    path: `/services/${encodeURIComponent(name)}`,
    query: { hours: String(hours.value) }
  })
}
const goTraceDetail = (traceId: string) => {
  if (!traceId) return
  router.push({ name: 'trace-detail', params: { traceId }, query: { hours: String(hours.value) } })
}

const syncContextToRoute = async () => {
  if (syncingSelection) return
  const patch: Record<string, string | null> = {
    hours: String(hours.value)
  }
  if (selection.value.kind === 'node' && selection.value.service) {
    persistService(selection.value.service)
    patch.service = selection.value.service
    patch.source = null
    patch.target = null
  } else if (selection.value.kind === 'edge' && selection.value.service && selection.value.peer) {
    persistService(selection.value.service)
    patch.service = selection.value.service
    patch.source = selection.value.service
    patch.target = selection.value.peer
  } else {
    patch.service = null
    patch.source = null
    patch.target = null
  }
  persistHours(hours.value)
  await router.replace({ path: route.path, query: mergeQuery(route.query, patch) })
}

const clearSelection = () => {
  selection.value = { kind: null, service: '' }
  edgeExplain.value = null
  updateChart()
  void syncContextToRoute()
}

// 从服务列表点击高亮节点
const highlightService = (service: string) => {
  selection.value = { kind: 'node', service }
  edgeExplain.value = null
  updateChart()
  void syncContextToRoute()
}

const selectNode = (service: string) => {
  selection.value = { kind: 'node', service }
  edgeExplain.value = null
  updateChart()
  void syncContextToRoute()
}

const selectEdge = (source: string, target: string) => {
  selection.value = { kind: 'edge', service: source, peer: target }
  edgeExplain.value = null
  updateChart()
  void syncContextToRoute()
}

const onHoursChange = async () => {
  persistHours(hours.value)
  await router.replace({
    path: route.path,
    query: mergeQuery(route.query, { hours: String(hours.value) })
  })
  await loadData()
}

async function explainEdge(source: string, target: string) {
  explaining.value = true
  try {
    const result = await ApiService.explainDependency(source, target, Number(hours.value))
    edgeExplain.value = result
  } finally {
    explaining.value = false
  }
}

const bindTopologyClick = () => {
  if (!topologyChart) return
  topologyChart.off('click')
  topologyChart.on('click', (params: any) => {
    const hit = resolveTopologyClick(params)
    if (!hit?.service) return
    if (hit.kind === 'edge' && hit.peer) {
      selectEdge(hit.service, hit.peer)
    } else {
      selectNode(hit.service)
    }
  })
}

const mountChart = () => {
  if (!chartEl.value || topologyChart) return
  topologyChart = echarts.init(chartEl.value)
  topologyChart.setOption(buildTopologyOption([], topologyOpts()))
  bindTopologyClick()
  startDashAnimation()
}

// 连线数据流动画（虚线偏移）- 暂时禁用，避免渲染问题
const startDashAnimation = () => {
  // 虚线流动动画会导致图表渲染问题，暂时禁用
  // 如需启用，需要更稳健的实现方式
}

const stopDashAnimation = () => {
  if (dashAnimationTimer) {
    clearInterval(dashAnimationTimer)
    dashAnimationTimer = null
  }
}

const disposeChart = () => {
  stopDashAnimation()
  topologyChart?.dispose()
  topologyChart = null
}

const updateChart = () => {
  if (!topologyChart) return
  topologyChart.setOption(buildTopologyOption(dependencies.value, topologyOpts()), { notMerge: true })
  nextTick(() => topologyChart?.resize())
}

const refreshTopology = () => {
  updateChart()
  topologyChart?.resize()
}

watch(() => chartColors.value, () => {
  updateChart()
})

watch(() => effectiveTheme.value, () => {
  updateChart()
})

const fitToScreen = () => {
  if (!topologyChart) return
  topologyChart.setOption(buildTopologyOption(dependencies.value, topologyOpts()), { notMerge: true })
  topologyChart.resize()
}

const downloadTopology = () => {
  const dataStr = JSON.stringify(dependencies.value, null, 2)
  const dataBlob = new Blob([dataStr], { type: 'application/json' })
  const url = URL.createObjectURL(dataBlob)
  const link = document.createElement('a')
  link.href = url
  link.download = `topology-${new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')}.json`
  link.click()
  URL.revokeObjectURL(url)
}

const syncChart = async () => {
  await nextTick()
  if (isSolo.value) {
    disposeChart()
    return
  }
  mountChart()
  updateChart()
  topologyChart?.resize()
}

const loadData = async () => {
  try {
    loading.value = true
    loadError.value = ''
    queryBudgetHint.value = ''
    deeplinkHint.value = ''
    selection.value = { kind: null, service: '' }
    edgeExplain.value = null
    const h = hours.value
    const capacity = await ApiService.getStorageCapacity()
    if (capacity.queryOverBudget) {
      queryBudgetHint.value = queryTooLargeMessage({
        storedSpans: capacity.storedSpans,
        snapshotQueryMaxSpans: capacity.snapshotQueryMaxSpans
      })
    }
    const [deps, names, latency, recent, healthResult, presenceRows] = await Promise.all([
      ApiService.getServiceDependencies(h).catch((err) => {
        if (isQueryTooLargeError(err)) {
          queryBudgetHint.value = queryTooLargeMessage(queryTooLargeDetail(err))
          return []
        }
        throw err
      }),
      ApiService.getServiceNames(),
      ApiService.getServiceLatency(h, 200),
      ApiService.getRecentTraces({ hours: h === 0 ? 168 : h, limit: 12 }),
      ApiService.getServiceHealth(h).then(
        (rows) => {
          const map: Record<string, ServiceHealthInfo> = {}
          rows.forEach((row) => {
            if (row.service) map[row.service] = row
          })
          return map
        },
        () => null
      ),
      ApiService.getServicePresence()
    ])
    dependencies.value = deps
    serviceNames.value = names
    serviceLatency.value = latency
    recentTraces.value = Array.isArray(recent) ? recent : []
    healthByService.value = healthResult
    const presenceMap: typeof presenceByService.value = {}
    presenceRows.forEach((row) => {
      presenceMap[row.serviceName] = {
        presence: row.presence,
        quietMinutes: row.quietMinutes,
        staleThresholdMinutes: row.staleThresholdMinutes
      }
    })
    // latency 行也可能已带 presence（兼容旧接口无 /presence）
    latency.forEach((row: any) => {
      const name = String(row?.serviceName || '').trim()
      if (!name || presenceMap[name]) return
      presenceMap[name] = {
        presence: row.presence || 'unknown',
        quietMinutes: row.quietMinutes ?? null,
        staleThresholdMinutes: Number(row.staleThresholdMinutes || 5)
      }
    })
    presenceByService.value = presenceMap
  } catch (error) {
    console.error('加载拓扑数据失败:', error)
    if (isQueryTooLargeError(error)) {
      queryBudgetHint.value = queryTooLargeMessage(queryTooLargeDetail(error))
      loadError.value = ''
    } else {
      loadError.value = loadFailedMessage('服务拓扑')
    }
  } finally {
    loading.value = false
    await syncChart()
    applyRouteSelection()
  }
}

/** 支持深链：?service= 或 AI 证据 ?source=&target= */
const applyRouteSelection = () => {
  syncingSelection = true
  try {
    const source = String(route.query.source || '')
    const target = String(route.query.target || '')
    const service = String(route.query.service || '')
    if (source && target) {
      if (findEdge(source, target)) {
        selection.value = { kind: 'edge', service: source, peer: target }
        edgeExplain.value = null
        updateChart()
      } else {
        deeplinkHint.value = deeplinkMissMessage('edge', `${source} → ${target}`)
      }
    } else if (service) {
      if (serviceNames.value.includes(service)) {
        selection.value = { kind: 'node', service }
        edgeExplain.value = null
        updateChart()
      } else {
        deeplinkHint.value = deeplinkMissMessage('service', service)
      }
    } else if (source) {
      if (serviceNames.value.includes(source)) {
        selection.value = { kind: 'node', service: source }
        edgeExplain.value = null
        updateChart()
      } else {
        deeplinkHint.value = deeplinkMissMessage('service', source)
      }
    }
  } finally {
    syncingSelection = false
  }
}

const handleResize = () => topologyChart?.resize()

onMounted(async () => {
  updateCurrentTime()
  timeInterval = window.setInterval(updateCurrentTime, 1000)
  window.addEventListener('resize', handleResize)
  hours.value = resolveHours(route.query, 72)
  persistHours(hours.value)
  if (route.query.hours == null || route.query.hours === '') {
    await router.replace({
      path: route.path,
      query: mergeQuery(route.query, { hours: String(hours.value) })
    })
  }
  try {
    const st = await ApiService.getAiStatus()
    aiReady.value = !!st.invokeReady
    aiStatusReason.value = st.reason || ''
  } catch {
    aiReady.value = false
    aiStatusReason.value = ''
  }
  await loadData()
})

onUnmounted(() => {
  disposeChart()
  if (timeInterval) clearInterval(timeInterval)
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.si-topo-page {
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.si-topo-alert {
  margin-bottom: 0.75rem;
}

.si-topo-onboard {
  flex: 1;
  min-height: 0;
  margin-top: 0.25rem;
  margin-bottom: 0;
}

.si-topo-text--danger { color: var(--danger-color) !important; }
.si-topo-text--warning { color: var(--warning-color) !important; }
.si-topo-text--success { color: var(--success-color) !important; }
.si-topo-text--muted { color: var(--si-muted) !important; }
.si-topo-cell--center { text-align: center; color: var(--si-muted); }
.si-topo-btn--block { width: 100%; }

.si-topo-stage__toolbar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
}

.si-topo-table-arrow {
  color: var(--si-brand);
  font-size: 0.9rem;
}

.si-topo-rail__arrow {
  color: var(--si-brand);
}

.si-topo-mesh {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.si-topo-stage {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 28%);
  gap: 0.85rem;
  align-items: stretch;
}

@media (max-width: 991px) {
  .si-topo-stage {
    grid-template-columns: 1fr;
  }
}

.si-topo-stage__main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 0;
  min-height: clamp(420px, 58vh, 680px);
}

.si-topo-stage__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.85rem 0.95rem 0;
  margin-bottom: 0.55rem;
  flex-shrink: 0;
}

.si-topo-stage__title {
  margin: 0 0 0.2rem;
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-topo-legend {
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 0.4rem;
  font-size: 0.72rem;
  color: var(--si-muted);
}

.si-topo-legend__swatches {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  align-items: center;
}

.si-topo-swatch {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}

.si-topo-swatch::before {
  content: '';
  width: 0.55rem;
  height: 0.55rem;
  border-radius: 50%;
  flex-shrink: 0;
}

.si-topo-swatch--healthy::before { background: var(--success-color); }
.si-topo-swatch--warning::before { background: var(--warning-color); }
.si-topo-swatch--degraded::before { background: #c2410c; }
.si-topo-swatch--critical::before { background: var(--danger-color); }

.si-topo-text--degraded {
  color: #c2410c;
}

.si-topo-rail__level {
  margin-left: 0.25rem;
  font-size: 0.85em;
  font-weight: 500;
  opacity: 0.85;
}

.si-topo-canvas-wrap {
  flex: 1;
  min-height: 360px;
  width: 100%;
  position: relative;
  overflow: hidden;
  background: var(--si-topo-canvas-bg, #0a0e27);
  /* 网格背景 */
  background-image:
    linear-gradient(rgba(56, 189, 248, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(56, 189, 248, 0.06) 1px, transparent 1px);
  background-size: 40px 40px;
}

/* 背景粒子/星点效果 */
.si-topo-canvas-wrap::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(1px 1px at 10% 20%, rgba(255, 255, 255, 0.4), transparent),
    radial-gradient(1px 1px at 30% 60%, rgba(255, 255, 255, 0.3), transparent),
    radial-gradient(1px 1px at 50% 10%, rgba(255, 255, 255, 0.5), transparent),
    radial-gradient(1px 1px at 70% 80%, rgba(255, 255, 255, 0.3), transparent),
    radial-gradient(1px 1px at 90% 40%, rgba(255, 255, 255, 0.4), transparent),
    radial-gradient(1.5px 1.5px at 15% 85%, rgba(56, 189, 248, 0.5), transparent),
    radial-gradient(1.5px 1.5px at 45% 45%, rgba(56, 189, 248, 0.4), transparent),
    radial-gradient(1.5px 1.5px at 75% 25%, rgba(56, 189, 248, 0.5), transparent),
    radial-gradient(1px 1px at 25% 35%, rgba(255, 255, 255, 0.3), transparent),
    radial-gradient(1px 1px at 55% 75%, rgba(255, 255, 255, 0.4), transparent),
    radial-gradient(1px 1px at 85% 55%, rgba(255, 255, 255, 0.3), transparent),
    radial-gradient(1px 1px at 5% 50%, rgba(255, 255, 255, 0.4), transparent),
    radial-gradient(1px 1px at 35% 15%, rgba(255, 255, 255, 0.3), transparent),
    radial-gradient(1px 1px at 65% 95%, rgba(255, 255, 255, 0.4), transparent),
    radial-gradient(1px 1px at 95% 5%, rgba(255, 255, 255, 0.3), transparent);
  background-size: 200px 200px;
  pointer-events: none;
  z-index: 0;
}

.si-topo-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

/* 左侧服务列表 */
.si-topo-service-list {
  position: absolute;
  left: 0.75rem;
  top: 0.75rem;
  width: 200px;
  max-height: calc(100% - 1.5rem);
  background: rgba(15, 23, 42, 0.85);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-radius: 8px;
  overflow: hidden;
  z-index: 10;
  transition: all 0.2s ease;
}

.si-topo-service-list--collapsed {
  width: auto;
}

.si-topo-service-list__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 0.75rem;
  border-bottom: 1px solid rgba(56, 189, 248, 0.15);
}

.si-topo-service-list__title {
  margin: 0;
  font-size: 0.8rem;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.85);
  display: flex;
  align-items: center;
}

.si-topo-service-list__items {
  margin: 0;
  padding: 0.25rem 0;
  list-style: none;
  max-height: 300px;
  overflow-y: auto;
}

.si-topo-service-list__item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
  font-size: 0.78rem;
  color: rgba(255, 255, 255, 0.75);
  transition: background 0.15s ease;
}

.si-topo-service-list__item:hover {
  background: rgba(56, 189, 248, 0.1);
}

.si-topo-service-list__item--active {
  background: rgba(56, 189, 248, 0.15);
  color: #fff;
}

.si-topo-service-list__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.4);
  flex-shrink: 0;
}

.si-topo-service-list__item--healthy .si-topo-service-list__dot { background: var(--success-color); }
.si-topo-service-list__item--warning .si-topo-service-list__dot { background: var(--warning-color); }
.si-topo-service-list__item--degraded .si-topo-service-list__dot { background: #c2410c; }
.si-topo-service-list__item--critical .si-topo-service-list__dot { background: var(--danger-color); }

.si-topo-service-list__item--stale {
  opacity: 0.72;
}

.si-topo-service-list__stale {
  margin-left: auto;
  margin-right: 0.35rem;
  font-size: 0.68rem;
  font-weight: 700;
  color: var(--warning-color, #f59e0b);
}

.si-topo-rail__stale {
  margin: 0 0 0.55rem;
  font-size: 0.78rem;
  color: var(--warning-color, #f59e0b);
}

.si-topo-service-list__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.si-topo-service-list__count {
  font-size: 0.7rem;
  color: rgba(255, 255, 255, 0.5);
  flex-shrink: 0;
}

.si-topo-stage__aside {
  min-width: 0;
}

@media (min-width: 992px) {
  .si-topo-rail {
    position: sticky;
    top: calc(var(--si-nav-offset, 4.5rem) + 0.75rem);
    max-height: calc(100vh - var(--si-nav-offset, 4.5rem) - 1.5rem);
    overflow: auto;
  }
}

.si-topo-rail {
  padding: 0.85rem 0.95rem 1rem;
  border-radius: 10px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  height: 100%;
}

.si-topo-rail__title {
  margin: 0 0 0.75rem;
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-topo-rail__kicker {
  margin: 0;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--si-brand);
}

.si-topo-rail__name {
  margin: 0.2rem 0 0.75rem;
  font-family: var(--font-display);
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--si-ink);
  word-break: break-all;
}

.si-topo-rail__edge {
  margin: 0.25rem 0 0.75rem;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
  font-weight: 700;
  color: var(--si-ink);
  word-break: break-all;
}

.si-topo-rail__edge i {
  color: var(--si-brand);
}

.si-topo-rail__stats {
  margin: 0 0 0.85rem;
  display: grid;
  gap: 0.4rem;
}

.si-topo-rail__stats > div {
  display: grid;
  grid-template-columns: 4.5rem 1fr;
  gap: 0.35rem;
  font-size: 0.85rem;
}

.si-topo-rail__stats dt {
  margin: 0;
  font-size: 0.68rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--si-muted);
}

.si-topo-rail__stats dd {
  margin: 0;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.si-topo-rail__actions {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.si-topo-rail__empty {
  margin: 1.5rem 0 0;
  text-align: center;
  font-size: 0.88rem;
  color: var(--si-muted);
  line-height: 1.5;
}

.si-topo-rail__explain {
  margin-top: 0.85rem;
  padding-top: 0.75rem;
  border-top: 1px dashed var(--card-border);
  text-align: left;
}

.si-topo-rail__explain pre {
  margin: 0;
  white-space: pre-wrap;
  font-family: var(--font-body);
  font-size: 0.82rem;
  line-height: 1.5;
  max-height: 14rem;
  overflow: auto;
}

.si-topo-fold {
  border-radius: 10px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  padding: 0 0.95rem 0.85rem;
}

.si-topo-fold__summary {
  cursor: pointer;
  list-style: none;
  padding: 0.75rem 0;
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--si-ink);
  user-select: none;
}

.si-topo-fold__summary::-webkit-details-marker {
  display: none;
}

.si-topo-fold__summary::before {
  content: '';
  display: inline-block;
  width: 0;
  height: 0;
  border-left: 0.4rem solid var(--si-muted);
  border-top: 0.3rem solid transparent;
  border-bottom: 0.3rem solid transparent;
  margin-right: 0.45rem;
  transition: transform 0.15s ease;
  vertical-align: middle;
}

.si-topo-fold[open] > .si-topo-fold__summary::before {
  transform: rotate(90deg);
}

:deep(.si-topo-row--active) {
  background: rgba(0, 184, 212, 0.08) !important;
}

.si-svc {
  color: var(--si-brand);
  font-size: 0.85rem;
  background: transparent;
}

.si-topo-solo {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.si-topo-solo__hero {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 1.1rem;
  align-items: center;
  padding: 1.15rem 1.25rem;
  border: 1px solid var(--card-border);
  border-radius: 12px;
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
}

.si-topo-solo__node {
  width: 4.5rem;
  height: 4.5rem;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--si-brand);
  color: var(--si-avatar-text);
  font-family: var(--font-display);
  font-size: 1.6rem;
  font-weight: 700;
  box-shadow: 0 0 0 8px var(--si-hover-brand);
}

.si-topo-solo__copy h3 {
  margin: 0 0 0.45rem;
  font-family: var(--font-display);
  font-size: clamp(1.45rem, 2.4vw, 1.9rem);
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--si-ink);
}


.si-topo-solo__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.si-topo-solo__metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.65rem;
}

.si-topo-solo__metric {
  padding: 0.75rem 0.9rem;
  border-radius: 12px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
}

.si-topo-solo__metric span {
  display: block;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--si-muted);
}

.si-topo-solo__metric strong {
  display: block;
  margin-top: 0.2rem;
  font-family: var(--font-numeric);
  font-variant-numeric: tabular-nums;
  font-size: 1.4rem;
  font-weight: 700;
  color: var(--si-ink);
  line-height: 1.15;
}

.si-topo-solo__panel {
  border: 1px solid var(--card-border);
  border-radius: 12px;
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
  overflow: hidden;
}

.si-topo-solo__panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.7rem 0.95rem;
  border-bottom: 1px solid var(--card-border);
}

.si-topo-solo__panel-head h5,
.si-topo-solo__panel-title {
  font-size: 0.92rem;
  font-weight: 700;
  margin: 0;
}

.si-topo-solo__empty {
  padding: 1.4rem;
  text-align: center;
  color: var(--si-muted);
}

.si-topo-solo__list {
  list-style: none;
  margin: 0;
  padding: 0.25rem 0;
  max-height: 18rem;
  overflow-y: auto;
}

.si-topo-solo__list li {
  display: grid;
  grid-template-columns: 3.5rem minmax(0, 1fr) 3.5rem 3.2rem;
  gap: 0.65rem;
  align-items: center;
  padding: 0.58rem 0.95rem;
  cursor: pointer;
  border-bottom: 1px solid var(--card-border);
}

.si-topo-solo__list li:last-child {
  border-bottom: none;
}

.si-topo-solo__list li:hover {
  background: rgba(0, 184, 212, 0.06);
}

.si-topo-solo__path {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 650;
  color: var(--si-ink);
}

.si-topo-solo__list .si-dur__val {
  width: 3.5rem;
  flex: none;
}

.si-topo-solo__hint {
  padding: 0.9rem 1rem;
  border-radius: 12px;
  border: 1px dashed rgba(0, 184, 212, 0.28);
  background: rgba(0, 184, 212, 0.04);
}

.si-topo-solo__hint h6 {
  margin: 0 0 0.45rem;
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-topo-solo__hint ol {
  margin: 0;
  padding-left: 1.15rem;
  font-size: 0.82rem;
  color: var(--si-ink-soft);
  line-height: 1.6;
}

.si-topo-solo__hint code {
  font-size: 0.78rem;
  color: var(--si-brand);
}

@media (max-width: 900px) {
  .si-topo-solo__hero {
    grid-template-columns: 1fr;
  }

  .si-topo-solo__metrics {
    grid-template-columns: repeat(2, 1fr);
  }

  .si-topo-solo__list li {
    grid-template-columns: 3.5rem minmax(0, 1fr) 3.2rem;
  }

  .si-topo-solo__list .si-dur__val {
    display: none;
  }
}

/* 节点选中脉冲动画 */
@keyframes si-topo-pulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(56, 189, 248, 0.6);
  }
  50% {
    box-shadow: 0 0 0 12px rgba(56, 189, 248, 0);
  }
}

/* 错误节点闪烁动画 */
@keyframes si-topo-error-flash {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.6;
  }
}

.si-topo-node--pulse {
  animation: si-topo-pulse 1.5s ease-in-out infinite;
}

.si-topo-node--error-flash {
  animation: si-topo-error-flash 1s ease-in-out infinite;
}
</style>
