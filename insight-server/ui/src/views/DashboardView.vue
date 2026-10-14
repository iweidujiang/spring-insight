<template>
  <div class="si-dashboard fade-in">
    <header class="si-dashboard__top">
      <div class="si-dashboard__title-block">
        <h2 class="si-dashboard__title">
          <i class="fa fa-tachometer me-2"></i>监控仪表盘
        </h2>
        <p class="si-dashboard__subtitle">
          {{ isSolo ? '单应用 · 请求、延迟与错误' : '健康度 · 异常 · 依赖一览' }}
        </p>
      </div>
      <div class="si-dashboard__status" v-show="!loading">
        <span v-if="!errorAnalysis || errorAnalysis.length === 0" class="si-dashboard__pill si-dashboard__pill--ok">
          <i class="fa fa-check-circle me-1"></i>无异常服务
        </span>
        <span v-else class="si-dashboard__pill si-dashboard__pill--warn">
          <i class="fa fa-exclamation-triangle me-1"></i>{{ errorAnalysis.length }} 个服务需关注
        </span>
      </div>
      <div class="si-dashboard__actions">
        <label class="si-dashboard__hours visually-hidden" for="dashboard-hours">时间范围</label>
        <select id="dashboard-hours" class="form-select form-select-sm si-dashboard__hours" v-model.number="hours" @change="onHoursChange">
          <option v-for="opt in TIME_RANGE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
        <button class="btn btn-primary btn-sm si-dashboard__btn" @click="loadData" :disabled="loading">
          <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i> 刷新
        </button>
        <span class="badge bg-info si-dashboard__clock">
          <i class="fa fa-clock me-1"></i>{{ currentTime }}
        </span>
      </div>
    </header>

    <div v-if="loading" class="si-dashboard__loading">
      <i class="fa fa-spinner fa-spin"></i>
      <span class="ms-2">加载中…</span>
    </div>

    <div v-if="loadError" class="alert alert-danger py-2 mx-3 mb-0" role="alert">{{ loadError }}</div>

    <div v-show="!loading" class="si-dashboard__content">
      <section class="si-dashboard__kpis" aria-label="关键指标">
        <button
          v-for="(stat, index) in stats"
          :key="index"
          type="button"
          class="card stat-card si-dashboard__kpi"
          :style="{ animationDelay: `${index * 0.06}s` }"
          @click="onKpiClick(stat.to)"
        >
          <div class="card-body">
            <div class="d-flex align-items-center justify-content-between">
              <div class="text-start">
                <div class="text-xs font-weight-bold text-uppercase mb-0 si-dashboard__kpi-label" :class="`text-${stat.color}`">
                  {{ stat.title }}
                </div>
                <div class="si-dashboard__kpi-value">{{ stat.value }}</div>
                <div class="si-dashboard__kpi-hint">{{ stat.hint }}</div>
              </div>
              <i :class="`fa ${stat.icon} si-dashboard__kpi-icon text-${stat.color}`"></i>
            </div>
          </div>
        </button>
      </section>

      <section class="si-period" aria-label="时段摘要">
        <header class="si-period__head">
          <div class="si-period__meta">
            <span class="si-period__kicker">时段摘要</span>
            <span class="si-period__scope">{{ periodScopeText }}</span>
          </div>
          <div class="si-period__actions">
            <button
              type="button"
              class="btn btn-sm btn-outline-secondary si-period__action"
              :disabled="periodExplaining || !aiStatus.invokeReady"
              :title="aiPeriodTitle"
              @click="explainPeriod"
            >
              <i class="fa" :class="periodExplaining ? 'fa-spinner fa-spin' : 'fa-magic'"></i>
              <span>{{ periodExplaining ? '小结中…' : 'AI 小结' }}</span>
            </button>
            <button
              v-for="act in periodActions"
              :key="act.key"
              type="button"
              class="btn btn-sm btn-outline-secondary si-period__action"
              @click="act.run"
            >
              {{ act.label }}
            </button>
          </div>
        </header>
        <p class="si-period__headline">{{ periodHeadline }}</p>
        <ul v-if="periodBullets.length" class="si-period__bullets">
          <li v-for="(b, i) in periodBullets" :key="i" :class="b.tone ? `si-period__bullet--${b.tone}` : undefined">
            <button
              v-if="b.run"
              type="button"
              class="si-period__bullet-btn"
              @click="b.run"
            >{{ b.text }}</button>
            <span v-else>{{ b.text }}</span>
          </li>
        </ul>
        <p v-else class="si-period__empty">{{ periodEmptyHint }}</p>
        <AiExplainPanel
          v-if="periodExplain"
          :result="periodExplain"
          title="时段 AI 小结"
          :meta="periodExplainMeta"
          @close="periodExplain = null"
        />
      </section>

      <section
        v-if="collectorStats"
        class="si-dashboard__collector-strip"
        aria-label="采集器状态"
      >
        <span class="si-dashboard__collector-strip-title"><i class="fa fa-database me-1"></i>Collector</span>
        <div class="si-dashboard__collector-strip-items">
          <span><em>接收</em>{{ collectorStats.totalReceivedRequests ?? 0 }}</span>
          <span><em>Span</em>{{ collectorStats.totalReceivedSpans ?? 0 }}</span>
          <span :class="(collectorStats.successRate ?? 100) < 90 ? 'text-danger' : ''">
            <em>成功</em>{{ collectorStats.successRate ?? 100 }}%
          </span>
          <span><em>运行</em>{{ collectorStats.runningHours ?? 0 }}h</span>
        </div>
        <button type="button" class="btn btn-sm btn-outline-secondary py-0 ms-auto" @click="goTopology()">
          打开拓扑
        </button>
      </section>

      <section class="si-infra" aria-label="库与缓存">
        <button type="button" class="si-infra__card" @click="goJdbc()">
          <div class="si-infra__head">
            <span class="si-infra__title"><i class="fa fa-database me-2"></i>数据库</span>
            <span class="si-infra__link">打开 <i class="fa fa-angle-right"></i></span>
          </div>
          <template v-if="jdbcKpis && jdbcKpis.spanCount > 0">
            <div class="si-infra__metrics">
              <div>
                <em>Span</em>
                <strong>{{ jdbcKpis.spanCount }}</strong>
              </div>
              <div>
                <em>错误</em>
                <strong :class="{ 'is-danger': jdbcKpis.errorCount > 0 }">{{ jdbcKpis.errorCount }}</strong>
              </div>
              <div>
                <em>慢</em>
                <strong :class="{ 'is-warn': jdbcKpis.slowCount > 0 }">{{ jdbcKpis.slowCount }}</strong>
              </div>
              <div>
                <em>p95</em>
                <strong>{{ formatMs(jdbcKpis.p95Ms) }}</strong>
              </div>
            </div>
          </template>
          <p v-else class="si-infra__empty">
            暂无 JDBC 调用。业务侧开启 <code>jdbc-tracing-enabled</code> 后可在此看慢 SQL / 错误。
          </p>
        </button>

        <button type="button" class="si-infra__card" @click="goRedis()">
          <div class="si-infra__head">
            <span class="si-infra__title"><i class="fa fa-cube me-2"></i>缓存</span>
            <span class="si-infra__link">打开 <i class="fa fa-angle-right"></i></span>
          </div>
          <template v-if="redisKpis && redisKpis.spanCount > 0">
            <div class="si-infra__metrics">
              <div>
                <em>Span</em>
                <strong>{{ redisKpis.spanCount }}</strong>
              </div>
              <div>
                <em>错误</em>
                <strong :class="{ 'is-danger': redisKpis.errorCount > 0 }">{{ redisKpis.errorCount }}</strong>
              </div>
              <div>
                <em>慢</em>
                <strong :class="{ 'is-warn': redisKpis.slowCount > 0 }">{{ redisKpis.slowCount }}</strong>
              </div>
              <div>
                <em>p95</em>
                <strong>{{ formatMs(redisKpis.p95Ms) }}</strong>
              </div>
            </div>
          </template>
          <p v-else class="si-infra__empty">
            暂无 Redis 调用。业务侧开启 <code>redis-tracing-enabled</code> 后可在此看慢命令 / 错误。
          </p>
        </button>
      </section>

      <section v-if="isSolo" class="si-app" aria-label="单应用概览">
        <div class="si-app__head">
          <div>
            <p class="si-app__kicker">当前服务</p>
            <h3 class="si-app__name">{{ soloProfile.name }}</h3>
          </div>
          <button type="button" class="btn btn-sm btn-outline-secondary" @click="goTraces()">全部链路</button>
        </div>

        <div class="si-app__metrics">
          <div class="si-app__metric">
            <span>请求</span>
            <strong>{{ soloProfile.spanCount }}</strong>
          </div>
          <div class="si-app__metric">
            <span>平均</span>
            <strong>{{ formatMs(soloProfile.avgMs) }}</strong>
          </div>
          <div class="si-app__metric">
            <span>p95 <PercentileHelp /></span>
            <strong :class="soloProfile.p95Ms >= 1000 ? 'text-danger' : soloProfile.p95Ms >= 500 ? 'text-warning' : ''">
              {{ formatMs(soloProfile.p95Ms) }}
            </strong>
          </div>
          <div class="si-app__metric">
            <span>错误</span>
            <strong :class="soloProfile.errorCount > 0 ? 'text-danger' : ''">{{ soloProfile.errorCount }}</strong>
          </div>
        </div>

        <div class="si-app__traces">
          <div class="si-app__traces-head">
            <h6 class="mb-0">最近请求</h6>
            <span class="si-app__traces-hint">{{ hoursLabel }}</span>
          </div>
          <div v-if="recentTraces.length === 0" class="si-dashboard__diag-empty">{{ emptyRequestsMessage(hours) }}</div>
          <ul v-else class="si-app__list">
            <li
              v-for="tr in recentTraces"
              :key="tr.traceId"
              class="si-app__row"
              @click="goTraceDetail(tr.traceId)"
            >
              <span class="si-http-method" :data-method="traceMethod(tr)">{{ traceMethod(tr) || 'REQ' }}</span>
              <span class="si-app__path" :title="tracePath(tr)">{{ tracePath(tr) }}</span>
              <span class="si-dur si-app__dur">
                <span class="si-dur__track">
                  <i class="si-dur__bar" :style="{ width: durationShare(tr) + '%' }"></i>
                </span>
                <em class="si-dur__val">{{ formatMs(tr.durationMs || 0) }}</em>
              </span>
              <span class="badge" :class="traceFailed(tr) ? 'bg-danger' : 'bg-success'">
                {{ traceFailed(tr) ? '失败' : '成功' }}
              </span>
            </li>
          </ul>
        </div>
      </section>

      <section v-else class="si-overview" aria-label="服务概览">
        <div class="si-overview__grid">
          <div class="si-overview__panel">
            <div class="si-overview__head">
              <span><i class="fa fa-heartbeat me-2"></i>服务健康</span>
              <button type="button" class="btn btn-sm btn-outline-secondary py-0" @click="goSlowTraces()">慢链路</button>
            </div>
            <div v-if="healthSnapshot.length > 0" class="table-responsive si-overview__scroll">
              <table class="table table-hover mb-0 si-dashboard__diag-table si-data-table">
                <thead class="table-light">
                  <tr>
                    <th>服务</th>
                    <th>均耗时</th>
                    <th>p95</th>
                    <th>错误</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="row in healthSnapshot"
                    :key="'health-' + row.serviceName"
                    class="si-dashboard__diag-row"
                    @click="goServiceSlow(row)"
                  >
                    <td class="text-truncate" style="max-width: 9rem" :title="row.serviceName">{{ row.serviceName }}</td>
                    <td>{{ formatMs(row.avgMs) }}</td>
                    <td :class="row.p95Ms >= 1000 ? 'text-danger fw-bold' : row.p95Ms >= 500 ? 'text-warning' : ''">
                      {{ formatMs(row.p95Ms) }}
                    </td>
                    <td :class="(row.errorCount || 0) > 0 ? 'text-danger' : 'text-muted'">
                      {{ row.errorCount || 0 }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else class="si-dashboard__diag-empty si-dashboard__diag-empty--rich">
              <p class="mb-1">{{ emptyLatencySampleMessage(hours) }}</p>
              <button type="button" class="btn btn-sm btn-outline-primary" @click="expandAllStored">扩大到全部已存</button>
            </div>
          </div>

          <div class="si-overview__panel">
            <div class="si-overview__head">
              <span>慢服务 <PercentileHelp /></span>
              <button type="button" class="btn btn-sm btn-link py-0" @click="goSlowTraces()">更多</button>
            </div>
            <ul v-if="slowServices.length" class="si-dashboard__rail-list">
              <li
                v-for="row in slowServices.slice(0, 6)"
                :key="'slow-' + row.serviceName"
                @click="goServiceSlow(row)"
              >
                <span class="si-dashboard__rail-name" :title="row.serviceName">{{ row.serviceName }}</span>
                <em :class="row.p95Ms >= 1000 ? 'text-danger' : row.p95Ms >= 500 ? 'text-warning' : ''">
                  {{ formatMs(row.p95Ms) }}
                </em>
              </li>
            </ul>
            <div v-else class="si-dashboard__rail-empty">{{ emptyInRangeShort(hours, '延迟样本') }}</div>
          </div>

          <div class="si-overview__panel">
            <div class="si-overview__head">
              <span>热点依赖</span>
              <button type="button" class="btn btn-sm btn-link py-0" @click="goTopology()">拓扑图</button>
            </div>
            <ul v-if="hotDependencies.length" class="si-dashboard__rail-list">
              <li
                v-for="(dep, idx) in hotDependencies.slice(0, 6)"
                :key="'hot-' + idx"
                @click="goHotDependency(dep)"
              >
                <span class="si-dashboard__rail-dep" :title="`${dep.sourceService} → ${dep.targetService}`">
                  {{ dep.sourceService }} → {{ dep.targetService }}
                </span>
                <em>{{ dep.callCount }}</em>
              </li>
            </ul>
            <div v-else class="si-dashboard__rail-empty">
              <p class="mb-1">{{ emptyInRangeShort(hours, '依赖') }}</p>
              <p class="small text-muted mb-0">跨服务调用出现后可在此与拓扑图查看</p>
            </div>
          </div>
        </div>

        <div class="si-overview__bottom">
          <div class="si-overview__panel si-overview__panel--traces">
            <div class="si-overview__head">
              <span><i class="fa fa-list me-2"></i>最近请求</span>
              <button type="button" class="btn btn-sm btn-outline-secondary py-0" @click="goTraces()">全部链路</button>
            </div>
            <div v-if="recentTraces.length === 0" class="si-dashboard__diag-empty">{{ emptyRequestsMessage(hours) }}</div>
            <ul v-else class="si-app__list">
              <li
                v-for="tr in recentTraces.slice(0, 10)"
                :key="tr.traceId"
                class="si-app__row"
                @click="goTraceDetail(tr.traceId)"
              >
                <span class="si-http-method" :data-method="traceMethod(tr)">{{ traceMethod(tr) || 'REQ' }}</span>
                <span class="si-app__path" :title="tracePath(tr)">{{ tracePath(tr) }}</span>
                <span class="text-muted small text-truncate" style="max-width: 7rem">{{ tr.serviceName || '—' }}</span>
                <span class="si-dur si-app__dur">
                  <span class="si-dur__track">
                    <i class="si-dur__bar" :style="{ width: durationShare(tr) + '%' }"></i>
                  </span>
                  <em class="si-dur__val">{{ formatMs(tr.durationMs || 0) }}</em>
                </span>
                <span class="badge" :class="traceFailed(tr) ? 'bg-danger' : 'bg-success'">
                  {{ traceFailed(tr) ? '失败' : '成功' }}
                </span>
              </li>
            </ul>
          </div>

          <div class="si-overview__panel si-overview__panel--rank">
            <div class="si-overview__head">
              <span>请求排名</span>
              <button type="button" class="btn btn-sm btn-outline-primary py-0" @click="refreshServiceRankChart">
                <i class="fa fa-refresh"></i>
              </button>
            </div>
            <div id="service-rank-chart" class="si-overview__rank-canvas"></div>
            <button
              v-if="errorAnalysis && errorAnalysis.length > 0"
              type="button"
              class="btn btn-sm btn-outline-danger w-100 mt-2"
              @click="goErrors"
            >
              {{ errorAnalysis.length }} 个异常服务 · 错误分析
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import PercentileHelp from '../components/PercentileHelp.vue'
import AiExplainPanel from '../components/AiExplainPanel.vue'
import { ApiService, type AiExplainResult, type PeriodInsight, type PeriodInsightLink } from '../services/ApiService'
import { formatDuration } from '../utils/traceTimeline'
import { useChartTheme } from '../composables/useChartTheme'
import {
  TIME_RANGE_OPTIONS,
  formatHoursLabel,
  emptyRequestsMessage,
  emptyLatencySampleMessage,
  emptyInRangeShort,
  loadFailedMessage
} from '../utils/timeRange'
import { mergeQuery, persistHours, resolveHours } from '../utils/insightQuery'

const router = useRouter()
const route = useRoute()

const loading = ref(true)
const loadError = ref('')
const hours = ref(72)
const currentTime = ref('')
const services = ref<string[]>([])
const dependencies = ref<any[]>([])
const serviceStats = ref<any[]>([])
const serviceLatency = ref<any[]>([])
const errorAnalysis = ref<any[]>([])
const collectorStats = ref<any>({})
const totalSpans = ref(0)
const recentTraces = ref<any[]>([])
const periodInsight = ref<PeriodInsight | null>(null)
const periodExplain = ref<AiExplainResult | null>(null)
const periodExplaining = ref(false)
const aiStatus = ref({
  enabled: false,
  invokeReady: false,
  provider: '',
  model: '',
  baseUrl: '',
  statusLabel: '未启用',
  reason: ''
})
const periodExplainMeta = computed(() => {
  const r = periodExplain.value
  if (!r) return ''
  return [r.provider, r.model].filter(Boolean).join(' · ')
})
const aiPeriodTitle = computed(() =>
  aiStatus.value.reason || (aiStatus.value.invokeReady ? '用当前时段事实调用 AI' : '请先在设置中启用 AI')
)
type InfraKpis = {
  spanCount: number
  errorCount: number
  slowCount: number
  avgMs: number
  p95Ms: number
  maxMs: number
}
const jdbcKpis = ref<InfraKpis | null>(null)
const redisKpis = ref<InfraKpis | null>(null)

let serviceRankChart: echarts.ECharts | null = null
let timeInterval: number | null = null

const { colors: chartColors } = useChartTheme()

const hasDependencies = computed(() => dependencies.value.length > 0)
/** 只有一个应用且尚无调用边：首页用单应用请求列表 */
const isSolo = computed(() => !hasDependencies.value && services.value.length === 1)

const soloProfile = computed(() => {
  const name = services.value[0] || ''
  const row = serviceLatency.value.find((item: any) => item.serviceName === name) || serviceLatency.value[0]
  return {
    name: name || row?.serviceName || '未命名服务',
    spanCount: row?.spanCount || 0,
    avgMs: row?.avgMs || 0,
    p95Ms: row?.p95Ms || 0,
    errorCount: row?.errorCount || 0
  }
})

const hoursLabel = computed(() => formatHoursLabel(hours.value))

const periodHeadline = computed(() => {
  if (!periodInsight.value) return '暂无时段摘要'
  return periodInsight.value.headline || '暂无时段摘要'
})

/** 口径说明：当前统计段 + 环比对照段（含具体起止时刻） */
const periodScopeText = computed(() => {
  const p = periodInsight.value
  if (!p) return hoursLabel.value
  if (!p.compare || hours.value <= 0) {
    return hours.value <= 0 ? '统计范围：全部已存 Span（不做环比）' : `统计范围：${hoursLabel.value}`
  }
  const cur = formatPeriodClock(p.window?.fromEpochMs, p.window?.toEpochMs)
  const prev = formatPeriodClock(p.previousWindow?.fromEpochMs, p.previousWindow?.toEpochMs)
  const priorLabel = hours.value === 168 ? '此前同等 7 天' : `此前同等 ${hours.value} 小时`
  if (cur && prev) {
    return `统计：${hoursLabel.value}（${cur}）· 环比：${priorLabel}（${prev}）`
  }
  return `统计：${hoursLabel.value} · 环比：${priorLabel}（紧挨当前时段之前）`
})

const periodEmptyHint = computed(() => {
  if (!periodInsight.value) return '暂无法获取时段事实'
  return emptyInRangeShort(hours.value, 'Span 数据')
})

/** 本地时刻短格式，如 9/27 00:00～12:00 */
const formatPeriodClock = (fromMs?: number, toMs?: number) => {
  if (!fromMs || !toMs || fromMs <= 0 || toMs <= 0) return ''
  const fmt = (ms: number) => {
    const d = new Date(ms)
    const mon = d.getMonth() + 1
    const day = d.getDate()
    const hh = String(d.getHours()).padStart(2, '0')
    const mm = String(d.getMinutes()).padStart(2, '0')
    return `${mon}/${day} ${hh}:${mm}`
  }
  return `${fmt(fromMs)}～${fmt(toMs)}`
}

type PeriodBullet = { text: string; tone?: 'warn' | 'ok' | 'muted'; run?: () => void }

const periodBullets = computed((): PeriodBullet[] => {
  const p = periodInsight.value
  const cur = p?.current
  if (!p || !cur || cur.spanCount <= 0) return []

  const max = 5
  const out: PeriodBullet[] = []
  const delta = p.delta
  const links = p.links
  const priorShort = hours.value === 168 ? '此前同等 7 天' : `此前同等 ${hours.value} 小时`

  const err = cur.errorSpanCount
  let errText = `错误 Span ${err} 条`
  if (p.compare && delta) {
    const d = delta.errorSpanCount
    if (d > 0) errText += `（较${priorShort} +${d}）`
    else if (d < 0) errText += `（较${priorShort} ${d}）`
    else errText += `（与${priorShort}持平）`
  }
  out.push({
    text: errText,
    tone: err > 0 ? 'warn' : 'ok',
    run: err > 0 && links.errorTraces ? () => followPeriodLink(links.errorTraces) : undefined
  })

  const topErr = cur.errorServices[0]
  if (topErr?.serviceName && topErr.errorSpans > 0) {
    out.push({
      text: `错误最多：${topErr.serviceName}（${topErr.errorSpans}）`,
      tone: 'warn',
      run: () => goTraces({
        service: topErr.serviceName,
        status: 'error',
        ...(hours.value > 0 ? { hours: String(hours.value) } : {})
      })
    })
  }

  if (delta?.newErrorServices?.length) {
    const names = delta.newErrorServices.slice(0, 3).join('、')
    out.push({
      text: `新增出错服务：${names}`,
      tone: 'warn',
      run: links.errorAnalysis ? () => followPeriodLink(links.errorAnalysis) : undefined
    })
  }

  const slow = cur.slowServices[0]
  if (slow?.serviceName && out.length < max) {
    out.push({
      text: `最慢服务：${slow.serviceName}（p95 ${formatMs(slow.p95Ms)}）`,
      tone: slow.p95Ms >= 1000 ? 'warn' : 'muted',
      run: links.sampleSlowTrace
        ? () => followPeriodLink(links.sampleSlowTrace)
        : () => goServiceSlow({ serviceName: slow.serviceName, p50Ms: slow.avgMs })
    })
  }

  const edge = cur.hotEdges[0]
  if (edge?.sourceService && edge?.targetService && out.length < max) {
    out.push({
      text: `热点依赖：${edge.sourceService} → ${edge.targetService}（${edge.callCount} 次）`,
      tone: 'muted',
      run: links.hotEdge ? () => followPeriodLink(links.hotEdge) : undefined
    })
  }

  return out.slice(0, max)
})

const periodActions = computed(() => {
  const links = periodInsight.value?.links
  if (!links) return [] as Array<{ key: string; label: string; run: () => void }>
  const acts: Array<{ key: string; label: string; run: () => void }> = []
  if (links.errorAnalysis) {
    acts.push({ key: 'errors', label: '错误分析', run: () => followPeriodLink(links.errorAnalysis) })
  }
  if (links.errorTraces && (periodInsight.value?.current?.errorSpanCount ?? 0) > 0) {
    acts.push({ key: 'err-traces', label: '错误链路', run: () => followPeriodLink(links.errorTraces) })
  }
  if (links.sampleSlowTrace) {
    acts.push({ key: 'slow', label: '慢链路样例', run: () => followPeriodLink(links.sampleSlowTrace) })
  }
  if (links.hotEdge) {
    acts.push({ key: 'edge', label: '热点依赖', run: () => followPeriodLink(links.hotEdge) })
  }
  return acts.slice(0, 4)
})

const followPeriodLink = (link?: PeriodInsightLink) => {
  if (!link?.path) return
  if (link.traceId) {
    goTraceDetail(link.traceId)
    return
  }
  const query: Record<string, string> = {}
  if (link.query) {
    for (const [k, v] of Object.entries(link.query)) {
      query[k] = String(v)
    }
  }
  router.push({ path: link.path, query })
}

const explainPeriod = async () => {
  if (periodExplaining.value) return
  periodExplaining.value = true
  try {
    const result = await ApiService.explainPeriod(Number(hours.value))
    periodExplain.value = result
  } finally {
    periodExplaining.value = false
  }
}

const slowServices = computed(() =>
  [...serviceLatency.value]
    .sort((a, b) => (b.p95Ms || 0) - (a.p95Ms || 0))
    .slice(0, 8)
)

const healthSnapshot = computed(() =>
  [...serviceLatency.value]
    .sort((a, b) => (b.spanCount || 0) - (a.spanCount || 0))
    .slice(0, 8)
)

const hotDependencies = computed(() =>
  [...dependencies.value]
    .sort((a, b) => (b.callCount || 0) - (a.callCount || 0))
    .slice(0, 8)
)

const formatMs = (ms: number) => formatDuration(Number(ms) || 0)

const traceFailed = (tr: any) => !!(tr?.hasError || tr?.statusCode === 'ERROR')

const traceMethod = (tr: any) => {
  const op = String(tr?.operationName || tr?.endpoint || '')
  const matched = /^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\b/i.exec(op)
  return matched ? matched[1].toUpperCase() : ''
}

const tracePath = (tr: any) => {
  const op = String(tr?.operationName || tr?.endpoint || tr?.traceId || '—')
  return op.replace(/^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\s+/i, '')
}

const durationShare = (tr: any) => {
  const max = recentTraces.value.reduce((n, item) => Math.max(n, Number(item?.durationMs) || 0), 0)
  if (max <= 0) return 8
  return Math.max(8, Math.round(((Number(tr?.durationMs) || 0) / max) * 100))
}

const stats = computed(() => [
  {
    title: '监控服务',
    value: `${services.value.length} 个`,
    icon: 'fa-server',
    color: 'primary',
    hint: '进入链路筛选',
    to: '/traces'
  },
  {
    title: '链路总数',
    value: `${totalSpans.value} 条`,
    icon: 'fa-list-ul',
    color: 'success',
    hint: '最近上报 Span',
    to: '/traces'
  },
  {
    title: '依赖关系',
    value: `${dependencies.value.length} 条`,
    icon: 'fa-sitemap',
    color: 'info',
    hint: '打开拓扑图',
    to: '/topology'
  },
  {
    title: '异常服务',
    value: `${errorAnalysis.value.length} 个`,
    icon: 'fa-exclamation-triangle',
    color: 'warning',
    hint: '错误分析',
    to: '/error-analysis'
  }
])

const updateCurrentTime = () => {
  currentTime.value = new Date().toTimeString().split(' ')[0]
}

const onKpiClick = (to: string) => {
  router.push(to)
}

const goTopology = () => router.push('/topology')
const goJdbc = () => router.push({ path: '/jdbc', query: { hours: String(hours.value) } })
const goRedis = () => router.push({ path: '/redis', query: { hours: String(hours.value) } })
const goTraces = (query: Record<string, string> = {}) => router.push({ path: '/traces', query })
const goErrors = () => router.push('/error-analysis')
const goTraceDetail = (traceId: string) => {
  if (!traceId) return
  router.push({ name: 'trace-detail', params: { traceId } })
}

const goSlowTraces = () => goTraces({ minDurationMs: '500' })

const goServiceSlow = (row: { serviceName: string; p50Ms?: number }) => {
  const minDur = Math.max(100, Math.floor(Number(row.p50Ms) || 100))
  goTraces({ service: row.serviceName, minDurationMs: String(minDur) })
}

const goHotDependency = (dep: { sourceService: string; targetService?: string }) => {
  if (!dep?.sourceService) return
  goTraces({ service: dep.sourceService })
}

const mountRankChart = () => {
  const serviceRankChartDom = document.getElementById('service-rank-chart')
  if (!serviceRankChartDom || serviceRankChart) return
  serviceRankChart = echarts.init(serviceRankChartDom)
  serviceRankChart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any) => {
        const data = params[0]
        return `${data.name}<br/>请求数量: ${data.value} 条`
      }
    },
    grid: { left: 8, right: 28, bottom: 8, top: 12, containLabel: true },
    xAxis: {
      type: 'value',
      name: '请求'
    },
    yAxis: {
      type: 'category',
      data: [],
      axisLabel: { width: 100, overflow: 'truncate', ellipsis: '…' }
    },
    series: [{
      name: '请求数量',
      type: 'bar',
      data: [],
      barMaxWidth: 18,
      itemStyle: { borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', formatter: '{c}', fontSize: 10 }
    }]
  })
  applyRankChartTheme()
  serviceRankChart.on('click', (params: any) => {
    const name = params?.name
    if (typeof name === 'string' && name) {
      router.push({
        path: `/services/${encodeURIComponent(name)}`,
        query: { hours: String(hours.value) }
      })
    }
  })
}

const applyRankChartTheme = () => {
  if (!serviceRankChart) return
  const c = chartColors.value
  serviceRankChart.setOption({
    textStyle: { color: c.muted },
    tooltip: {
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText }
    },
    xAxis: {
      nameTextStyle: { fontSize: 10, color: c.muted },
      axisLine: { lineStyle: { color: c.axisLine } },
      splitLine: { lineStyle: { color: c.splitLine } },
      axisLabel: { fontSize: 10, color: c.muted }
    },
    yAxis: {
      axisLine: { lineStyle: { color: c.axisLine } },
      axisLabel: { fontSize: 11, color: c.ink }
    },
    series: [{
      itemStyle: {
        color: (params: any) => c.palette[params.dataIndex % c.palette.length]
      },
      label: { color: c.inkSoft }
    }]
  })
}

const updateCharts = () => {
  if (!serviceRankChart) return
  const serviceNames: string[] = []
  const callCounts: number[] = []
  const sorted = [...serviceStats.value].sort((a, b) => (b.totalSpans || 0) - (a.totalSpans || 0)).slice(0, 8)
  sorted.forEach((s: any) => {
    serviceNames.push(s.serviceName)
    callCounts.push(s.totalSpans || 0)
  })
  serviceRankChart.setOption({
    graphic: serviceNames.length === 0
      ? [{
          type: 'text',
          left: 'center',
          top: 'center',
          style: { text: '暂无排名数据', fill: chartColors.value.muted, fontSize: 12, textAlign: 'center' }
        }]
      : [],
    yAxis: { data: serviceNames },
    series: [{ data: callCounts }]
  })
}

const refreshServiceRankChart = () => {
  serviceRankChart?.resize()
  updateCharts()
}

watch(() => chartColors.value, () => {
  applyRankChartTheme()
  updateCharts()
})

const syncHoursToRoute = async () => {
  persistHours(hours.value)
  await router.replace({
    path: route.path,
    query: mergeQuery(route.query, { hours: String(hours.value) })
  })
}

const onHoursChange = async () => {
  await syncHoursToRoute()
  await loadData()
}

const expandAllStored = async () => {
  hours.value = 0
  await onHoursChange()
}

const loadData = async () => {
  try {
    loading.value = true
    loadError.value = ''
    const h = hours.value
    const [
      serviceNames,
      serviceDeps,
      serviceStatsData,
      latencyData,
      errorAnalysisData,
      collectorStatsData,
      recent,
      period,
      jdbcSummary,
      redisSummary
    ] = await Promise.all([
      ApiService.getServiceNames(),
      ApiService.getServiceDependencies(h),
      ApiService.getServiceStats(h),
      ApiService.getServiceLatency(h, 20),
      ApiService.getErrorAnalysis(h),
      ApiService.getCollectorStats(),
      ApiService.getRecentTraces({ hours: h === 0 ? 168 : h, limit: 16 }),
      ApiService.getPeriodInsight(h),
      ApiService.getJdbcSummary(h, undefined, 1).catch(() => null),
      ApiService.getRedisSummary(h, undefined, 1).catch(() => null)
    ])
    services.value = serviceNames
    dependencies.value = serviceDeps
    serviceStats.value = serviceStatsData
    serviceLatency.value = latencyData
    errorAnalysis.value = errorAnalysisData
    collectorStats.value = collectorStatsData
    recentTraces.value = Array.isArray(recent) ? recent : []
    periodInsight.value = period
    periodExplain.value = null
    jdbcKpis.value = jdbcSummary?.kpis ?? null
    redisKpis.value = redisSummary?.kpis ?? null
    totalSpans.value = serviceStatsData.reduce((sum: number, s: any) => sum + (s.totalSpans || 0), 0)
  } catch (error) {
    console.error('加载仪表盘数据失败:', error)
    loadError.value = loadFailedMessage('仪表盘')
  } finally {
    loading.value = false
    await nextTick()
    syncCharts()
  }
}

/** 多服务概览挂请求排名图；单应用不挂。 */
const syncCharts = async () => {
  serviceRankChart?.dispose()
  serviceRankChart = null
  if (isSolo.value) return
  await nextTick()
  mountRankChart()
  updateCharts()
  serviceRankChart?.resize()
}

const handleResize = () => {
  serviceRankChart?.resize()
}

onMounted(async () => {
  updateCurrentTime()
  timeInterval = window.setInterval(updateCurrentTime, 1000)
  window.addEventListener('resize', handleResize)
  hours.value = resolveHours(route.query, 72)
  persistHours(hours.value)
  if (route.query.hours == null || route.query.hours === '') {
    await syncHoursToRoute()
  }
  try {
    aiStatus.value = await ApiService.getAiStatus()
  } catch {
    /* keep defaults */
  }
  await loadData()
})

onUnmounted(() => {
  serviceRankChart?.dispose()
  if (timeInterval) clearInterval(timeInterval)
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.si-dashboard {
  flex: 1;
  width: 100%;
  min-width: 0;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  overflow: hidden;
}

.si-dashboard__content {
  flex: 1;
  width: 100%;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  overflow: auto;
  padding-bottom: 0.75rem;
}

.si-dashboard__top {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: 1fr auto auto;
  align-items: center;
  gap: 0.85rem 1.25rem;
  padding: 0.55rem 0.35rem 0.2rem;
}

.si-dashboard__title {
  font-family: var(--font-display);
  font-size: clamp(1.45rem, 2.2vw, 1.85rem);
  font-weight: 700;
  color: var(--si-ink);
  margin: 0;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.si-dashboard__title i { color: var(--si-teal); }

.si-dashboard__subtitle {
  margin: 0.2rem 0 0;
  font-size: 0.8rem;
  color: var(--si-muted);
  letter-spacing: 0.04em;
}

.si-dashboard__status { justify-self: center; }

.si-dashboard__pill {
  display: inline-flex;
  align-items: center;
  padding: 0.4rem 0.85rem;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 600;
}

.si-dashboard__pill--ok {
  background: #dcfce7;
  color: #14532d;
  border: 1px solid rgba(21, 128, 61, 0.25);
}

.si-dashboard__pill--warn {
  background: #ffedd5;
  color: #9a3412;
  border: 1px solid rgba(180, 83, 9, 0.3);
}

.si-dashboard__actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  justify-self: end;
}

.si-dashboard__hours {
  width: auto;
  min-width: 7.5rem;
  font-size: 0.8rem;
  border-color: rgba(20, 83, 45, 0.2);
  background: #fffcfa;
  color: #15241f;
}

.si-dashboard__btn {
  padding: 0.45rem 0.95rem;
  font-size: 0.85rem;
}

.si-dashboard__clock {
  font-size: 0.8rem;
  font-weight: 700;
  padding: 0.45rem 0.75rem;
}

.si-dashboard__loading {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--si-muted);
  font-size: 0.95rem;
}

.si-dashboard__loading i {
  color: var(--si-teal);
  font-size: 1.75rem;
}

.si-dashboard__content--mesh .si-dashboard__kpis--compact {
  gap: 0.45rem;
}

.si-dashboard__kpis--compact .si-dashboard__kpi {
  min-height: 0;
}

.si-dashboard__kpis--compact .si-dashboard__kpi :deep(.card-body) {
  padding: 0.55rem 0.75rem;
}

.si-dashboard__kpis--compact .si-dashboard__kpi-value {
  font-size: 1.15rem;
}

.si-dashboard__kpis--compact .si-dashboard__kpi-label {
  margin-bottom: 0.1rem !important;
}

.si-dashboard__body--mesh {
  grid-template-columns: minmax(0, 1fr) minmax(220px, 24%);
}

.si-dashboard__inline-meta {
  color: var(--si-muted);
}

.si-dashboard__diag--slim {
  max-height: 11rem;
}

.si-dashboard__edge-tips {
  margin: 0;
  padding: 0.65rem 0.85rem 0.85rem 1.35rem;
  font-size: 0.78rem;
  color: var(--si-ink-soft);
  line-height: 1.55;
}

.si-dashboard__edge-tips code {
  font-size: 0.72rem;
  color: var(--si-teal);
}

.si-dashboard__rail-card {
  flex-shrink: 0;
  border: 1px solid var(--card-border);
  border-radius: 10px;
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
  overflow: hidden;
}

.si-dashboard__rail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.35rem;
  padding: 0.45rem 0.7rem;
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--si-ink);
  border-bottom: 1px solid rgba(20, 83, 45, 0.1);
  background: rgba(15, 118, 110, 0.04);
}

.si-dashboard__rail-list {
  list-style: none;
  margin: 0;
  padding: 0.2rem 0;
  max-height: 9.5rem;
  overflow-y: auto;
}

.si-dashboard__rail-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.4rem 0.7rem;
  cursor: pointer;
  font-size: 0.78rem;
}

.si-dashboard__rail-list li:hover {
  background: rgba(15, 118, 110, 0.06);
}

.si-dashboard__rail-name,
.si-dashboard__rail-dep {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  color: var(--si-ink);
}

.si-dashboard__rail-list em {
  flex: 0 0 auto;
  font-style: normal;
  font-weight: 700;
  color: var(--si-ink-soft);
}

.si-dashboard__rail-empty {
  padding: 0.75rem;
  font-size: 0.78rem;
  color: var(--si-muted);
  text-align: center;
}

.si-dashboard__rail-rank {
  flex: 1;
  min-height: 8rem;
}

@media (max-width: 991px) {
  .si-dashboard__body--mesh {
    grid-template-columns: 1fr;
  }
}

.si-dashboard__kpis {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.75rem;
}

@media (max-width: 991px) {
  .si-dashboard__kpis { grid-template-columns: repeat(2, 1fr); }
}

.si-dashboard__kpi {
  min-height: 5.75rem;
  text-align: left;
  cursor: pointer;
  appearance: none;
  width: 100%;
  padding: 0;
  background: inherit;
}

.si-dashboard__kpi:focus-visible {
  outline: 2px solid var(--si-teal);
  outline-offset: 2px;
}

.si-dashboard__kpi :deep(.card-body) {
  padding: 1rem 1.15rem;
  height: 100%;
  display: flex;
  align-items: center;
}

.si-dashboard__kpi :deep(.card-body > .d-flex) { width: 100%; }

.si-dashboard__kpi-label {
  font-size: 0.72rem !important;
  letter-spacing: 0.08em;
  margin-bottom: 0.25rem !important;
}

.si-dashboard__kpi-value {
  font-family: var(--font-display);
  font-size: clamp(1.45rem, 2.4vw, 1.85rem);
  font-weight: 700;
  color: var(--si-ink);
  line-height: 1.15;
  letter-spacing: -0.02em;
}

.si-dashboard__kpi-hint {
  margin-top: 0.25rem;
  font-size: 0.68rem;
  color: var(--si-muted);
  font-weight: 600;
}

.si-dashboard__kpi-icon {
  font-size: 2rem !important;
  opacity: 0.88;
}

/* 时段摘要：与上方 KPI 卡同系卡片，弱化横幅感 */
.si-period {
  flex-shrink: 0;
  padding: 0.7rem 0.95rem 0.8rem;
  border-radius: 10px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
}

.si-period--compact {
  padding: 0.6rem 0.85rem 0.7rem;
}

.si-period__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.45rem 0.85rem;
  margin-bottom: 0.45rem;
  padding-bottom: 0.45rem;
  border-bottom: 1px solid rgba(20, 83, 45, 0.08);
}

.si-period__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.35rem 0.65rem;
  min-width: 0;
}

.si-period__kicker {
  display: inline-flex;
  align-items: center;
  margin: 0;
  padding: 0.12rem 0.45rem;
  border-radius: 999px;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--si-teal);
  background: rgba(15, 118, 110, 0.08);
  border: 1px solid rgba(15, 118, 110, 0.18);
}

.si-period__scope {
  margin: 0;
  font-size: 0.72rem;
  font-weight: 500;
  color: var(--si-muted);
  line-height: 1.35;
}

.si-period__headline {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 650;
  color: var(--si-ink);
  line-height: 1.45;
  letter-spacing: -0.01em;
}

.si-period--compact .si-period__headline {
  font-size: 0.9rem;
}

.si-period__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  align-items: center;
  flex-shrink: 0;
}

.si-period__action {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.75rem;
  font-weight: 600;
  padding: 0.22rem 0.55rem;
  white-space: nowrap;
  border-color: rgba(20, 83, 45, 0.18);
  color: var(--si-ink-soft);
  background: transparent;
}

.si-period__action:hover {
  border-color: rgba(15, 118, 110, 0.4);
  color: var(--si-teal);
  background: rgba(15, 118, 110, 0.04);
}

.si-period__bullets {
  list-style: none;
  margin: 0.55rem 0 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.si-period__bullets li {
  margin: 0;
  padding: 0.28rem 0.6rem;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 550;
  color: var(--si-ink-soft);
  line-height: 1.35;
  background: rgba(15, 23, 42, 0.035);
  border: 1px solid rgba(20, 83, 45, 0.1);
}

.si-period__bullet--warn {
  color: #9a3412;
  background: rgba(194, 65, 12, 0.08);
  border-color: rgba(194, 65, 12, 0.22);
}

.si-period__bullet--ok {
  color: #14532d;
  background: rgba(21, 128, 61, 0.08);
  border-color: rgba(21, 128, 61, 0.2);
}

.si-period__bullet--muted {
  color: var(--si-muted);
  font-weight: 500;
}

.si-period__bullet-btn {
  border: 0;
  background: transparent;
  padding: 0;
  margin: 0;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.si-period__bullet-btn:hover {
  color: var(--si-teal);
}

.si-period__empty {
  margin: 0.45rem 0 0;
  font-size: 0.82rem;
  color: var(--si-muted);
}

.si-period :deep(.si-ai-panel) {
  margin-top: 0.65rem;
}

.si-dashboard__collector-strip {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem 1.25rem;
  padding: 0.45rem 0.85rem;
  border-radius: 10px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
}

.si-dashboard__collector-strip-title {
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--si-ink-soft);
}

.si-dashboard__collector-strip-items {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem 1.1rem;
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-dashboard__collector-strip-items em {
  font-style: normal;
  font-size: 0.68rem;
  font-weight: 600;
  color: var(--si-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-right: 0.35rem;
}

.si-infra {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
  flex-shrink: 0;
}

.si-infra__card {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  text-align: left;
  padding: 0.75rem 0.95rem;
  border-radius: 12px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
  color: inherit;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.si-infra__card:hover {
  border-color: rgba(15, 118, 110, 0.35);
  box-shadow: 0 4px 14px rgba(21, 36, 31, 0.08);
}

.si-infra__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.si-infra__title {
  font-size: 0.9rem;
  font-weight: 650;
  color: var(--si-ink);
}

.si-infra__link {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--si-teal, #0f766e);
  white-space: nowrap;
}

.si-infra__metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.45rem;
}

.si-infra__metrics em {
  display: block;
  font-style: normal;
  font-size: 0.65rem;
  font-weight: 600;
  color: var(--si-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.si-infra__metrics strong {
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--si-ink);
  line-height: 1.2;
}

.si-infra__metrics strong.is-danger {
  color: #b91c1c;
}

.si-infra__metrics strong.is-warn {
  color: #b45309;
}

.si-infra__empty {
  margin: 0;
  font-size: 0.82rem;
  line-height: 1.45;
  color: var(--si-muted);
}

.si-infra__empty code {
  font-size: 0.78em;
}

@media (max-width: 900px) {
  .si-infra {
    grid-template-columns: 1fr;
  }
}

.si-overview {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-height: 0;
}

.si-overview__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
}

.si-overview__bottom {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(220px, 0.8fr);
  gap: 0.75rem;
  min-height: 14rem;
}

.si-overview__panel {
  border-radius: 12px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
  padding: 0.65rem 0.85rem;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.si-overview__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  font-size: 0.85rem;
  font-weight: 650;
  color: var(--si-ink);
}

.si-overview__scroll {
  max-height: 14rem;
  overflow: auto;
}

.si-overview__panel--traces .si-app__list {
  max-height: 18rem;
  overflow: auto;
}

.si-overview__rank-canvas {
  flex: 1;
  min-height: 12rem;
  width: 100%;
}

@media (max-width: 1100px) {
  .si-overview__grid {
    grid-template-columns: 1fr;
  }

  .si-overview__bottom {
    grid-template-columns: 1fr;
  }
}

/* 拓扑主、排名辅（遗留样式，首页已不再使用拓扑舞台） */
.si-dashboard__body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 1fr minmax(240px, 28%);
  gap: 0.75rem;
  overflow: hidden;
}

@media (max-width: 991px) {
  .si-dashboard__body {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(220px, 1fr) minmax(180px, auto);
  }
}

.si-dashboard__rail {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  min-height: 0;
  overflow: hidden;
}

.si-dashboard__panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  margin-bottom: 0 !important;
}

.si-dashboard__hero {
  border-color: rgba(15, 118, 110, 0.22);
  box-shadow: var(--box-shadow-hover);
}

.si-dashboard__panel-title {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--si-ink);
  text-transform: none;
  letter-spacing: 0.01em;
  margin-bottom: 0.15rem;
}

.si-dashboard__panel-desc {
  font-size: 0.7rem;
  color: var(--si-muted);
  font-weight: 500;
}

.si-dashboard__chart-rank,
.si-dashboard__chart-topology {
  flex: 1;
  min-height: 0;
  height: auto !important;
  margin-bottom: 0 !important;
  padding: 0.65rem 0.85rem !important;
}

.si-dashboard__chart-head {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.45rem;
}

.si-dashboard__chart-canvas {
  flex: 1;
  min-height: 0;
  width: 100%;
}

.si-dashboard__diag {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.65rem;
  max-height: 13.5rem;
}

@media (max-width: 991px) {
  .si-dashboard__diag {
    grid-template-columns: 1fr;
    max-height: none;
  }
}

.si-dashboard__diag-panel {
  min-height: 0;
  border: 1px solid var(--card-border);
  border-radius: 10px;
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.si-dashboard__diag-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.5rem 0.85rem;
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--si-ink);
  background: rgba(15, 118, 110, 0.05);
  border-bottom: 1px solid rgba(20, 83, 45, 0.1);
}

.si-dashboard__diag-empty {
  padding: 1.1rem 0.85rem;
  font-size: 0.95rem;
  color: var(--si-muted);
  text-align: center;
}

.si-dashboard__diag-empty--rich {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.55rem;
}

.si-dashboard__diag-scroll {
  overflow-y: auto;
  max-height: 10rem;
}

.si-dashboard__diag-table :deep(th),
.si-dashboard__diag-table :deep(td) {
  padding: 0.5rem 0.75rem;
  font-size: 0.92rem;
  vertical-align: middle;
}

.si-dashboard__diag-table :deep(th) {
  font-size: 0.82rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--si-muted);
}

.si-dashboard__diag-row {
  cursor: pointer;
}

.si-dashboard__dep-cell {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  min-width: 0;
  max-width: 16rem;
  font-size: 0.9rem;
}

.si-dashboard__dep-src,
.si-dashboard__dep-tgt {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 6.5rem;
}

.si-dashboard__dep-arrow {
  flex: 0 0 auto;
  color: var(--si-teal);
  font-size: 0.85rem;
}

.si-dashboard__diag-foot {
  flex-shrink: 0;
  padding: 0.4rem 0.75rem 0.55rem;
  border-top: 1px dashed rgba(20, 83, 45, 0.12);
  text-align: right;
}

.si-dashboard__hero-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.si-dashboard__hero-body--solo {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(240px, 42%);
  gap: 0.65rem;
  align-items: stretch;
}

.si-dashboard__hero-body--solo .si-dashboard__chart-canvas {
  min-height: 12rem;
}

.si-dashboard__solo {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  overflow: hidden;
}

.si-dashboard__solo-card {
  border: 1px solid rgba(20, 83, 45, 0.12);
  border-radius: 10px;
  background: rgba(15, 118, 110, 0.04);
  padding: 0.7rem 0.8rem;
}

.si-dashboard__solo-card--traces {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--card-bg);
  overflow: hidden;
}

.si-dashboard__solo-title {
  margin: 0 0 0.45rem;
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-dashboard__solo-tips {
  margin: 0 0 0.65rem;
  padding-left: 1.1rem;
  font-size: 0.75rem;
  color: var(--si-ink-soft);
  line-height: 1.55;
}

.si-dashboard__solo-tips code {
  font-size: 0.7rem;
  color: var(--si-teal);
}

.si-dashboard__solo-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.si-dashboard__solo-traces-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.35rem;
  flex-shrink: 0;
}

.si-dashboard__solo-traces {
  list-style: none;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  min-height: 0;
  flex: 1;
}

.si-dashboard__solo-trace {
  padding: 0.45rem 0.35rem;
  border-bottom: 1px solid rgba(20, 83, 45, 0.08);
  cursor: pointer;
  border-radius: 6px;
}

.si-dashboard__solo-trace:hover {
  background: rgba(15, 118, 110, 0.06);
}

.si-dashboard__solo-trace-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.si-dashboard__solo-trace-op {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--si-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.si-dashboard__solo-trace-meta {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  margin-top: 0.15rem;
  font-size: 0.7rem;
  color: var(--si-muted);
}

@media (max-width: 991px) {
  .si-dashboard__hero-body--solo {
    grid-template-columns: 1fr;
  }

  .si-app__metrics {
    grid-template-columns: repeat(2, 1fr);
  }

  .si-app__dur {
    display: none;
  }
}

.si-app {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  overflow: hidden;
}

.si-app__head {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.si-app__kicker {
  margin: 0;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--si-teal);
}

.si-app__name {
  margin: 0.15rem 0 0.25rem;
  font-family: var(--font-display);
  font-size: clamp(1.6rem, 2.6vw, 2.1rem);
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--si-ink);
}

.si-app__desc {
  margin: 0;
  max-width: 36rem;
  font-size: 0.88rem;
  color: var(--si-muted);
}

.si-app__metrics {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.65rem;
}

.si-app__metric {
  padding: 0.75rem 0.9rem;
  border-radius: 12px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
}

.si-app__metric span {
  display: inline-flex;
  align-items: center;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--si-muted);
}

.si-app__metric strong {
  display: block;
  margin-top: 0.2rem;
  font-family: var(--font-display);
  font-size: 1.45rem;
  font-weight: 700;
  color: var(--si-ink);
  line-height: 1.15;
}

.si-app__traces {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--card-border);
  border-radius: 12px;
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
  overflow: hidden;
}

.si-app__traces-head {
  flex-shrink: 0;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.7rem 0.95rem;
  border-bottom: 1px solid rgba(20, 83, 45, 0.1);
}

.si-app__traces-head h6 {
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-app__traces-hint {
  font-size: 0.75rem;
  color: var(--si-muted);
}

.si-app__list {
  list-style: none;
  margin: 0;
  padding: 0.25rem 0;
  overflow-y: auto;
  min-height: 0;
}

.si-app__row {
  display: grid;
  grid-template-columns: 3.5rem minmax(0, 1fr) 8.5rem 3.2rem;
  align-items: center;
  gap: 0.75rem;
  padding: 0.62rem 0.95rem;
  cursor: pointer;
  border-bottom: 1px solid rgba(20, 83, 45, 0.08);
}

.si-app__row:last-child {
  border-bottom: none;
}

.si-app__row:hover {
  background: rgba(15, 118, 110, 0.06);
}

.si-app__path {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--si-ink);
}

.si-app__dur {
  min-width: 0;
  max-width: none;
}
</style>
