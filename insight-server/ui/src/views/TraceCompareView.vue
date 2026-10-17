<template>
  <div class="si-compare fade-in">
    <header class="si-compare__top">
      <div>
        <button type="button" class="btn btn-link btn-sm px-0 si-compare__back" @click="goBack">
          <i class="fa fa-arrow-left me-1"></i>返回列表
        </button>
        <h2 class="si-compare__title">
          <i class="fa fa-columns me-2"></i>Trace 对比
        </h2>
        <p class="si-compare__sub">并排瀑布（各自时间轴）· 操作差分可展开查看</p>
      </div>
    </header>

    <SiAlert v-if="hint" tone="warning" role="alert" class="mb-3">{{ hint }}</SiAlert>
    <SiAlert v-if="loadError" tone="danger" class="mb-3">{{ loadError }}</SiAlert>

    <div v-if="loading" class="si-compare__loading">
      <i class="fa fa-spinner fa-spin"></i><span class="ms-2">加载对比数据…</span>
    </div>

    <template v-else-if="leftSpans && rightSpans">
      <div class="si-compare__list-bar mb-2">
        <span class="si-compare__list-bar-label">并排瀑布</span>
        <div class="si-compare__actions">
          <button type="button" class="btn btn-outline-secondary btn-sm" :disabled="!canSwap" @click="swapSides">
            <i class="fa fa-exchange"></i> 交换两侧
          </button>
          <button type="button" class="btn btn-primary btn-sm" :disabled="loading" @click="load">
            <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i>
            <span>刷新</span>
          </button>
        </div>
      </div>

      <div class="row g-3 si-compare__panels">
        <div class="col-lg-6">
          <section class="si-compare__panel" aria-label="Trace A">
            <header class="si-compare__panel-head">
              <div>
                <span class="si-compare__side">A</span>
                <code class="si-compare__id" :title="traceA">{{ shortId(traceA) }}</code>
              </div>
              <button type="button" class="btn btn-link btn-sm py-0" @click="openDetail(traceA)">详情</button>
            </header>
            <p class="si-compare__meta">
              {{ leftTimeline.serviceCount }} 服务 · {{ leftTimeline.rows.length }} Span ·
              总耗时 {{ formatDuration(leftTimeline.totalDurationMs) }}
              <span v-if="leftTimeline.errorCount" class="text-danger"> · {{ leftTimeline.errorCount }} 错</span>
            </p>
            <CompactWaterfall :timeline="leftTimeline" />
          </section>
        </div>
        <div class="col-lg-6">
          <section class="si-compare__panel" aria-label="Trace B">
            <header class="si-compare__panel-head">
              <div>
                <span class="si-compare__side si-compare__side--b">B</span>
                <code class="si-compare__id" :title="traceB">{{ shortId(traceB) }}</code>
              </div>
              <button type="button" class="btn btn-link btn-sm py-0" @click="openDetail(traceB)">详情</button>
            </header>
            <p class="si-compare__meta">
              {{ rightTimeline.serviceCount }} 服务 · {{ rightTimeline.rows.length }} Span ·
              总耗时 {{ formatDuration(rightTimeline.totalDurationMs) }}
              <span v-if="rightTimeline.errorCount" class="text-danger"> · {{ rightTimeline.errorCount }} 错</span>
            </p>
            <CompactWaterfall :timeline="rightTimeline" />
          </section>
        </div>
      </div>

      <section class="card si-compare__diff mt-3" aria-label="操作耗时差分">
        <div class="card-header py-2 d-flex justify-content-between align-items-center flex-wrap gap-2">
          <button
            type="button"
            class="btn btn-link btn-sm px-0 text-decoration-none si-compare__diff-toggle"
            :aria-expanded="diffExpanded"
            @click="diffExpanded = !diffExpanded"
          >
            <i class="fa" :class="diffExpanded ? 'fa-chevron-down' : 'fa-chevron-right'"></i>
            <span class="fw-semibold">操作耗时差分（max，B − A）</span>
            <span class="badge bg-secondary ms-1">{{ diffRows.length }}</span>
          </button>
          <span v-if="diffExpanded" class="small text-muted">按 |Δ| 排序 · 绿=变快 · 橙=变慢</span>
        </div>
        <div v-show="diffExpanded" class="card-body p-0">
          <div v-if="diffRows.length === 0" class="p-3 text-muted small">无可对比操作</div>
          <div v-else class="table-responsive">
            <table class="table table-sm table-hover mb-0 si-data-table">
              <thead class="table-light">
                <tr>
                  <th>服务</th>
                  <th>操作</th>
                  <th>A max</th>
                  <th>B max</th>
                  <th>Δ</th>
                  <th>次数 A/B</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, i) in diffRows" :key="i" :class="diffRowClass(row)">
                  <td>{{ row.serviceName }}</td>
                  <td class="text-truncate" style="max-width: 280px" :title="row.operationName">
                    {{ row.operationName }}
                    <span v-if="row.onlyLeft" class="badge bg-secondary ms-1">仅 A</span>
                    <span v-if="row.onlyRight" class="badge bg-secondary ms-1">仅 B</span>
                  </td>
                  <td>{{ row.onlyRight ? '—' : formatDuration(row.leftMaxMs) }}</td>
                  <td>{{ row.onlyLeft ? '—' : formatDuration(row.rightMaxMs) }}</td>
                  <td class="fw-semibold">{{ formatDelta(row.deltaMs, row) }}</td>
                  <td>{{ row.leftCount }} / {{ row.rightCount }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiService } from '../services/ApiService'
import SiAlert from '../components/SiAlert.vue'
import {
  buildOpDurationDiff,
  buildTraceTimeline,
  formatDuration,
  formatWaterfallOpLabel,
  normalizeSpanKind,
  type OpDiffRow,
  type TraceSpanLike,
  type TraceTimelineModel,
  type WaterfallRow
} from '../utils/traceTimeline'
import { loadFailedMessage, TRACE_NOT_FOUND } from '../utils/timeRange'
import { resolveHours } from '../utils/insightQuery'

const CompactWaterfall = defineComponent({
  name: 'CompactWaterfall',
  props: {
    timeline: { type: Object as () => TraceTimelineModel, required: true }
  },
  setup(props) {
    return () => {
      const tl = props.timeline
      const ticks = tl.tickMarks || []
      return h('div', { class: 'si-cwf' }, [
        h('div', { class: 'si-cwf__axis' }, [
          h('div', { class: 'si-cwf__axis-track' },
            ticks.map((t, i) =>
              h('span', {
                key: i,
                class: 'si-cwf__tick',
                style: { left: `${(i / (ticks.length - 1 || 1)) * 100}%` }
              }, formatDuration(t))
            )
          )
        ]),
        ...tl.rows.map((row: WaterfallRow, idx: number) =>
          h('div', {
            key: row.span.spanId || idx,
            class: [
              'si-cwf__row',
              row.isError ? 'is-error' : '',
              row.isOnCriticalPath ? 'is-critical' : ''
            ]
          }, [
            h('div', {
              class: 'si-cwf__meta',
              style: { paddingLeft: `${6 + row.depth * 10}px` }
            }, [
              h('div', { class: 'si-cwf__svc-row' }, [
                h('span', { class: 'si-cwf__svc', style: { color: row.color } }, row.span.serviceName || '-'),
                normalizeSpanKind(row.span.spanKind)
                  ? h('span', {
                      class: ['si-cwf__kind', 'is-' + normalizeSpanKind(row.span.spanKind).toLowerCase()]
                    }, normalizeSpanKind(row.span.spanKind))
                  : null
              ]),
              h('span', { class: 'si-cwf__op', title: row.span.operationName }, formatWaterfallOpLabel(row.span))
            ]),
            h('div', { class: 'si-cwf__track' }, [
              h('div', {
                class: ['si-cwf__bar', row.isError ? 'is-error' : ''],
                style: {
                  left: `${row.offsetPct}%`,
                  width: `${Math.min(row.widthPct, 100 - row.offsetPct)}%`,
                  background: row.isError ? 'var(--danger-color)' : row.color
                },
                title: `${row.span.serviceName} · ${formatWaterfallOpLabel(row.span)}\n${formatDuration(row.durationMs)}`
              })
            ]),
            h('div', { class: 'si-cwf__dur' }, formatDuration(row.durationMs))
          ])
        )
      ])
    }
  }
})

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const loadError = ref('')
const hint = ref('')
const traceA = ref('')
const traceB = ref('')
const leftSpans = ref<TraceSpanLike[] | null>(null)
const rightSpans = ref<TraceSpanLike[] | null>(null)
/** 差分表默认收起，把视口留给瀑布 */
const diffExpanded = ref(false)

const leftTimeline = computed(() => buildTraceTimeline(leftSpans.value || []))
const rightTimeline = computed(() => buildTraceTimeline(rightSpans.value || []))
const diffRows = computed(() =>
  buildOpDurationDiff(leftSpans.value || [], rightSpans.value || [], 40)
)
const canSwap = computed(() => !!(traceA.value && traceB.value && !loading.value))

const shortId = (id: string) => {
  const s = String(id || '')
  return s.length > 14 ? `${s.slice(0, 10)}…` : s || '—'
}

const hoursForBack = () => {
  const h = resolveHours(route.query, 24)
  return String(h)
}

const goBack = () => {
  router.push({ path: '/traces', query: { hours: hoursForBack() } })
}

const openDetail = (id: string) => {
  if (!id) return
  router.push({
    name: 'trace-detail',
    params: { traceId: id },
    query: { hours: hoursForBack() }
  })
}

const swapSides = () => {
  if (!canSwap.value) return
  router.replace({
    path: '/traces/compare',
    query: { a: traceB.value, b: traceA.value, hours: hoursForBack() }
  })
}

const formatDelta = (delta: number, row: OpDiffRow) => {
  if (row.onlyLeft) return '仅 A'
  if (row.onlyRight) return '仅 B'
  if (delta === 0) return '0'
  const sign = delta > 0 ? '+' : '−'
  return `${sign}${formatDuration(Math.abs(delta))}`
}

const diffRowClass = (row: OpDiffRow) => {
  if (row.onlyLeft || row.onlyRight) return 'si-compare__diff-muted'
  if (row.deltaMs > 20) return 'si-compare__diff-slow'
  if (row.deltaMs < -20) return 'si-compare__diff-fast'
  return ''
}

const load = async () => {
  const a = String(route.query.a || '').trim()
  const b = String(route.query.b || '').trim()
  traceA.value = a
  traceB.value = b
  leftSpans.value = null
  rightSpans.value = null
  loadError.value = ''
  hint.value = ''

  if (!a || !b) {
    loading.value = false
    hint.value = '请从链路列表勾选两条 Trace 后点「对比」，或在地址栏提供 a、b 两个 traceId'
    return
  }
  if (a === b) {
    loading.value = false
    hint.value = 'A 与 B 不能是同一条 Trace'
    return
  }

  loading.value = true
  try {
    const [la, lb] = await Promise.all([
      ApiService.getTraceDetail(a),
      ApiService.getTraceDetail(b)
    ])
    if (!la?.length) {
      loadError.value = `${TRACE_NOT_FOUND}（A: ${shortId(a)}）`
      return
    }
    if (!lb?.length) {
      loadError.value = `${TRACE_NOT_FOUND}（B: ${shortId(b)}）`
      return
    }
    leftSpans.value = la
    rightSpans.value = lb
  } catch {
    loadError.value = loadFailedMessage('Trace 对比')
  } finally {
    loading.value = false
  }
}

watch(() => [route.query.a, route.query.b], () => load())
onMounted(() => load())
</script>

<style scoped>
.si-compare__top {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: flex-start;
  gap: 0.75rem 1rem;
  margin-bottom: 1rem;
}

.si-compare__title {
  margin: 0.15rem 0 0.2rem;
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-compare__sub,
.si-compare__back {
  color: var(--si-muted);
  text-decoration: none;
  font-size: 0.85rem;
}

.si-compare__sub { margin: 0; }

.si-compare__list-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.45rem 0.75rem;
}

.si-compare__list-bar-label {
  font-size: 0.9rem;
  font-weight: 650;
  color: var(--si-ink);
}

.si-compare__actions {
  display: flex;
  gap: 0.45rem;
  align-items: center;
  flex-shrink: 0;
}

.si-compare__actions .btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  white-space: nowrap;
}

.si-compare__diff-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--si-ink) !important;
}

.si-compare__loading {
  padding: 2rem;
  text-align: center;
  color: var(--si-muted);
}

.si-compare__panel {
  border: 1px solid var(--card-border);
  border-radius: 10px;
  background: var(--card-bg);
  padding: 0.7rem 0.75rem 0.85rem;
  box-shadow: var(--box-shadow);
  min-height: 100%;
}

.si-compare__panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.25rem;
}

.si-compare__side {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.25rem;
  height: 1.25rem;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: 800;
  color: #fff;
  background: var(--si-teal);
  margin-right: 0.4rem;
}

.si-compare__side--b { background: var(--si-kind-client-color); }

.si-compare__id {
  font-size: 0.78rem;
  color: var(--si-ink);
}

.si-compare__meta {
  margin: 0 0 0.55rem;
  font-size: 0.75rem;
  color: var(--si-muted);
}

.si-compare__diff-slow { background: var(--si-error-row-bg); }
.si-compare__diff-fast { background: var(--si-success-row-bg); }
.si-compare__diff-muted { opacity: 0.85; }

/* compact waterfall */
:deep(.si-cwf) {
  display: flex;
  flex-direction: column;
  gap: 0.12rem;
  border: 1px solid var(--si-table-border);
  border-radius: 6px;
  background: var(--si-surface-1);
  padding: 0.4rem 0.45rem 0.5rem;
  overflow-x: auto;
}

:deep(.si-cwf__axis),
:deep(.si-cwf__row) {
  display: grid;
  grid-template-columns: minmax(148px, 38%) 1fr 3.6rem;
  gap: 0.4rem;
  align-items: center;
  min-width: 460px;
}

:deep(.si-cwf__row) {
  padding: 0.12rem 0;
}

:deep(.si-cwf__axis) {
  padding-bottom: 0.15rem;
  margin-bottom: 0.05rem;
  border-bottom: 1px dashed var(--si-table-border);
}

:deep(.si-cwf__axis-track) {
  position: relative;
  height: 0.9rem;
  grid-column: 2;
}

:deep(.si-cwf__tick) {
  position: absolute;
  transform: translateX(-50%);
  font-size: 0.68rem;
  color: var(--si-muted);
  font-weight: 600;
  white-space: nowrap;
}

:deep(.si-cwf__tick:first-child) { transform: translateX(0); }
:deep(.si-cwf__tick:last-child) { transform: translateX(-100%); }

:deep(.si-cwf__row.is-critical) {
  box-shadow: inset 2px 0 0 var(--si-critical-color);
}

:deep(.si-cwf__meta) {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-width: 0;
}

:deep(.si-cwf__svc-row) {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  min-width: 0;
}

:deep(.si-cwf__svc) {
  font-size: 0.78rem;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.si-cwf__kind) {
  flex: 0 0 auto;
  font-size: 0.62rem;
  font-weight: 700;
  padding: 0.1rem 0.28rem;
  border-radius: 2px;
  border: 1px solid transparent;
  color: var(--si-muted);
}

:deep(.si-cwf__kind.is-server) {
  color: var(--si-kind-server-color);
  border-color: var(--si-kind-server-border);
  background: var(--si-kind-server-bg);
}

:deep(.si-cwf__kind.is-client) {
  color: var(--si-kind-client-color);
  border-color: var(--si-kind-client-border);
  background: var(--si-kind-client-bg);
}

:deep(.si-cwf__op) {
  font-size: 0.82rem;
  color: var(--si-ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

:deep(.si-cwf__track) {
  position: relative;
  height: 0.85rem;
  background: var(--si-track-bg);
  border-radius: 3px;
}

:deep(.si-cwf__bar) {
  position: absolute;
  top: 0;
  bottom: 0;
  border-radius: 3px;
  min-width: 2px;
}

:deep(.si-cwf__dur) {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--si-ink-soft);
  text-align: right;
}
</style>
