<template>
  <div class="si-page fade-in trace-detail-page">
    <div class="si-page__header">
      <div>
        <h2 class="page-title">
          <i class="fa fa-sitemap me-2"></i>链路详情
        </h2>
        <p class="page-description text-truncate" style="max-width: 90vw">
          Trace ID：<code class="text-cyan">{{ traceId }}</code>
          <button class="btn btn-sm btn-link p-0 ms-2 align-baseline" type="button" @click="copyTraceId" title="复制">
            <i class="fa fa-copy"></i>
          </button>
        </p>
      </div>
      <div class="si-page__toolbar">
        <button
          v-if="timeline.errorCount > 0"
          class="btn btn-outline-danger"
          type="button"
          @click="jumpError(-1)"
          title="上一个异常 Span"
        >
          <i class="fa fa-chevron-up"></i> 异常
        </button>
        <button
          v-if="timeline.errorCount > 0"
          class="btn btn-outline-danger"
          type="button"
          @click="jumpError(1)"
          title="下一个异常 Span"
        >
          异常 <i class="fa fa-chevron-down"></i>
        </button>
        <button class="btn btn-outline-secondary" type="button" @click="goBack">
          <i class="fa fa-arrow-left me-1"></i>返回
        </button>
        <button class="btn btn-primary" type="button" @click="load" :disabled="loading">
          <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i> 刷新
        </button>
      </div>
    </div>

    <div v-if="copyHint" class="alert alert-success py-2 mb-3" role="status">{{ copyHint }}</div>

    <div v-if="loading" class="loading-spinner">
      <i class="fa fa-spinner fa-spin"></i>
      <span class="ms-2">加载中...</span>
    </div>

    <template v-else>
      <div v-if="spans.length === 0" class="card stat-card">
        <div class="card-body text-center text-muted py-5">
          <i class="fa fa-info-circle fa-2x mb-2 d-block"></i>
          暂无该 Trace 的 Span 数据
        </div>
      </div>

      <template v-else>
        <!-- 摘要 -->
        <section class="trace-summary">
          <div class="trace-summary__card">
            <span class="trace-summary__label">总耗时</span>
            <span class="trace-summary__value">{{ formatDuration(timeline.totalDurationMs) }}</span>
          </div>
          <div class="trace-summary__card">
            <span class="trace-summary__label">Span 数</span>
            <span class="trace-summary__value">{{ spans.length }}</span>
          </div>
          <div class="trace-summary__card">
            <span class="trace-summary__label">涉及服务</span>
            <span class="trace-summary__value">{{ timeline.serviceCount }}</span>
          </div>
          <div class="trace-summary__card">
            <span class="trace-summary__label">异常 Span</span>
            <span class="trace-summary__value" :class="{ 'text-danger': timeline.errorCount > 0 }">
              {{ timeline.errorCount }}
            </span>
          </div>
        </section>

        <!-- 瀑布时间线 -->
        <div class="card stat-card trace-waterfall-card">
          <div class="card-body">
            <div class="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
              <h5 class="card-title mb-0">
                <i class="fa fa-align-left me-2"></i>调用时间线（瀑布图）
              </h5>
              <span class="trace-waterfall-hint">
                缩进=父子；橙边=关键路径（最晚结束路径）；红条=异常
              </span>
            </div>

            <div class="trace-waterfall" ref="waterfallEl">
              <div class="trace-waterfall__axis">
                <div class="trace-waterfall__axis-label"></div>
                <div class="trace-waterfall__axis-track">
                  <span
                    v-for="(t, i) in timeline.tickMarks"
                    :key="i"
                    class="trace-waterfall__tick"
                    :style="{ left: `${(i / (timeline.tickMarks.length - 1 || 1)) * 100}%` }"
                  >{{ formatDuration(t) }}</span>
                </div>
              </div>

              <div
                v-for="(row, idx) in timeline.rows"
                :key="row.span.spanId || idx"
                :data-span-id="row.span.spanId"
                class="trace-waterfall__row"
                :class="{
                  'is-error': row.isError,
                  'is-active': selectedSpanId === row.span.spanId,
                  'is-critical': row.isOnCriticalPath
                }"
                @click="selectSpan(row.span.spanId)"
              >
                <div class="trace-waterfall__meta" :style="{ paddingLeft: `${12 + row.depth * 16}px` }">
                  <span class="trace-waterfall__svc" :style="{ color: row.color }">{{ row.span.serviceName || '-' }}</span>
                  <span class="trace-waterfall__op" :title="row.span.operationName">{{ row.span.operationName || '-' }}</span>
                </div>
                <div class="trace-waterfall__track">
                  <div
                    class="trace-waterfall__bar"
                    :class="{ 'is-error': row.isError }"
                    :style="{
                      left: `${row.offsetPct}%`,
                      width: `${Math.min(row.widthPct, 100 - row.offsetPct)}%`,
                      background: row.isError ? '#b91c1c' : row.color
                    }"
                    :title="barTitle(row)"
                  >
                    <span v-if="row.widthPct > 8" class="trace-waterfall__bar-label">{{ formatDuration(row.durationMs) }}</span>
                  </div>
                </div>
                <div class="trace-waterfall__dur">{{ formatDuration(row.durationMs) }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 选中详情 + 列表 -->
        <div v-if="selected" class="card stat-card">
          <div class="card-body">
            <h5 class="card-title mb-3"><i class="fa fa-info-circle me-2"></i>选中 Span</h5>
            <dl class="trace-selected">
              <div><dt>服务</dt><dd>{{ selected.serviceName || '-' }}</dd></div>
              <div><dt>操作</dt><dd>{{ selected.operationName || '-' }}</dd></div>
              <div><dt>类型</dt><dd>{{ selected.spanKind || '-' }}</dd></div>
              <div><dt>组件</dt><dd>{{ selected.component || '-' }}</dd></div>
              <div><dt>端点</dt><dd>{{ selected.endpoint || '-' }}</dd></div>
              <div><dt>耗时</dt><dd>{{ formatDuration(Number(selected.durationMs) || 0) }}</dd></div>
              <div><dt>状态</dt><dd>{{ selected.statusCode || '-' }}</dd></div>
              <div><dt>远端</dt><dd>{{ remoteLabel(selected) }}</dd></div>
              <div><dt>spanId</dt><dd><code>{{ selected.spanId }}</code></dd></div>
              <div><dt>parent</dt><dd><code>{{ selected.parentSpanId || '(root)' }}</code></dd></div>
            </dl>

            <div v-if="selected.errorCode || selected.errorMessage" class="trace-error-box mt-3">
              <div class="trace-error-box__title"><i class="fa fa-exclamation-triangle me-1"></i>错误信息</div>
              <div v-if="selected.errorCode"><strong>errorCode</strong>：{{ selected.errorCode }}</div>
              <div v-if="selected.errorMessage" class="mt-1">{{ selected.errorMessage }}</div>
            </div>

            <div v-if="tagEntries.length" class="mt-3">
              <div class="trace-tags-title">Tags</div>
              <div class="trace-tags">
                <span v-for="[k, v] in tagEntries" :key="k" class="trace-tag">
                  <em>{{ k }}</em>{{ v }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <div class="card stat-card si-table-panel">
          <div class="card-body">
            <h5 class="card-title mb-3"><i class="fa fa-list me-2"></i>Span 列表（{{ spans.length }}）</h5>
            <div class="table-responsive">
              <table class="table table-hover mb-0">
                <thead class="table-light">
                  <tr>
                    <th>服务</th>
                    <th>操作</th>
                    <th>类型</th>
                    <th>偏移</th>
                    <th>耗时</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="(row, i) in timeline.rows"
                    :key="row.span.spanId || i"
                    :class="{ 'table-active': selectedSpanId === row.span.spanId, 'table-danger': row.isError }"
                    style="cursor: pointer"
                    @click="selectSpan(row.span.spanId)"
                  >
                    <td>{{ row.span.serviceName }}</td>
                    <td class="text-truncate" style="max-width: 280px">{{ row.span.operationName }}</td>
                    <td>{{ row.span.spanKind }}</td>
                    <td>+{{ formatDuration(row.offsetMs) }}</td>
                    <td>{{ formatDuration(row.durationMs) }}</td>
                    <td>
                      <span class="badge" :class="row.isError ? 'bg-danger' : 'bg-success'">
                        {{ row.span.statusCode || (row.isError ? 'ERR' : 'OK') }}
                      </span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiService } from '../services/ApiService'
import {
  buildTraceTimeline,
  formatDuration,
  type TraceSpanLike,
  type WaterfallRow
} from '../utils/traceTimeline'

const route = useRoute()
const router = useRouter()
const traceId = ref('')
const spans = ref<TraceSpanLike[]>([])
const loading = ref(true)
const selectedSpanId = ref<string | null>(null)
const copyHint = ref('')
const waterfallEl = ref<HTMLElement | null>(null)
let copyTimer: number | null = null

const timeline = computed(() => buildTraceTimeline(spans.value))

const errorSpanIds = computed(() =>
  timeline.value.rows.filter((r) => r.isError && r.span.spanId).map((r) => r.span.spanId as string)
)

const selected = computed(() =>
  spans.value.find((s) => s.spanId === selectedSpanId.value) || null
)

const tagEntries = computed(() => {
  const tags = selected.value?.tags
  if (!tags || typeof tags !== 'object') return [] as [string, string][]
  return Object.entries(tags)
    .filter(([, v]) => v != null && String(v).length > 0)
    .map(([k, v]) => [k, String(v)] as [string, string])
    .sort((a, b) => a[0].localeCompare(b[0]))
})

const remoteLabel = (span: TraceSpanLike) => {
  const svc = span.remoteService || ''
  const ep = span.remoteEndpoint || ''
  if (!svc && !ep) return '-'
  if (svc && ep) return `${svc} · ${ep}`
  return svc || ep
}

const scrollSelectedIntoView = async () => {
  await nextTick()
  const id = selectedSpanId.value
  if (!id || !waterfallEl.value) return
  const el = waterfallEl.value.querySelector(`[data-span-id="${CSS.escape(id)}"]`) as HTMLElement | null
  el?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
}

const selectSpan = (id?: string | null) => {
  selectedSpanId.value = id || null
  scrollSelectedIntoView()
}

const jumpError = (dir: 1 | -1) => {
  const ids = errorSpanIds.value
  if (!ids.length) return
  const cur = selectedSpanId.value
  let idx = ids.indexOf(cur || '')
  if (idx < 0) {
    idx = dir > 0 ? -1 : 0
  }
  const next = ids[(idx + dir + ids.length) % ids.length]
  selectSpan(next)
}

const copyTraceId = async () => {
  if (!traceId.value) return
  try {
    await navigator.clipboard.writeText(traceId.value)
    copyHint.value = `已复制 Trace ID：${traceId.value}`
    if (copyTimer) clearTimeout(copyTimer)
    copyTimer = window.setTimeout(() => {
      copyHint.value = ''
    }, 2000)
  } catch {
    copyHint.value = '复制失败'
  }
}

const barTitle = (row: WaterfallRow) => {
  const flags = [
    row.isOnCriticalPath ? '关键路径' : '',
    row.isError ? '异常' : ''
  ].filter(Boolean).join(' · ')
  return `${row.span.serviceName} · ${row.span.operationName}\n偏移 +${formatDuration(row.offsetMs)} · 耗时 ${formatDuration(row.durationMs)}${flags ? `\n${flags}` : ''}`
}

const load = async () => {
  const id = String(route.params.traceId || '')
  traceId.value = id
  selectedSpanId.value = null
  if (!id) {
    spans.value = []
    loading.value = false
    return
  }
  loading.value = true
  try {
    spans.value = await ApiService.getTraceDetail(id)
    const firstError = spans.value.find((s) =>
      s.success === false || (s.statusCode && !['OK', '0', '200'].includes(String(s.statusCode).toUpperCase()))
    )
    const root = spans.value.find((s) => !s.parentSpanId) || spans.value[0]
    selectedSpanId.value = (firstError || root)?.spanId || null
    await scrollSelectedIntoView()
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  router.push({ path: '/traces', query: route.query })
}

watch(() => route.params.traceId, () => load())
onMounted(() => load())
</script>

<style scoped>
.text-cyan {
  color: var(--si-teal);
}

.trace-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.75rem;
}

@media (max-width: 767px) {
  .trace-summary {
    grid-template-columns: 1fr 1fr;
  }
}

.trace-summary__card {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 1.05rem 1.2rem;
  min-height: 5.25rem;
  border-radius: 10px;
  background: var(--card-bg);
  border: 1px solid var(--card-border);
  box-shadow: var(--box-shadow);
}

.trace-summary__label {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--si-muted);
  font-weight: 600;
}

.trace-summary__value {
  font-family: var(--font-display);
  font-size: clamp(1.45rem, 2.2vw, 1.75rem);
  font-weight: 700;
  color: var(--si-ink);
  line-height: 1.15;
}

.trace-waterfall-card {
  margin-bottom: 0 !important;
}

.trace-waterfall-hint {
  font-size: 0.78rem;
  color: var(--si-muted);
  max-width: 28rem;
  text-align: right;
}

.trace-waterfall {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  border: 1px solid var(--card-border);
  border-radius: 10px;
  background: var(--si-paper);
  padding: 0.75rem 0.85rem 0.9rem;
  overflow-x: auto;
}

.trace-waterfall__axis,
.trace-waterfall__row {
  display: grid;
  grid-template-columns: minmax(180px, 28%) 1fr 4.5rem;
  gap: 0.65rem;
  align-items: center;
  min-width: 640px;
}

.trace-waterfall__axis {
  padding-bottom: 0.35rem;
  margin-bottom: 0.15rem;
  border-bottom: 1px dashed rgba(20, 83, 45, 0.15);
}

.trace-waterfall__axis-track {
  position: relative;
  height: 1.1rem;
}

.trace-waterfall__tick {
  position: absolute;
  transform: translateX(-50%);
  font-size: 0.68rem;
  color: var(--si-muted);
  font-weight: 600;
  white-space: nowrap;
}

.trace-waterfall__tick:first-child {
  transform: translateX(0);
}

.trace-waterfall__tick:last-child {
  transform: translateX(-100%);
}

.trace-waterfall__row {
  padding: 0.28rem 0;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.trace-waterfall__row:hover,
.trace-waterfall__row.is-active {
  background: rgba(15, 118, 110, 0.06);
}

.trace-waterfall__row.is-critical {
  box-shadow: inset 3px 0 0 #c2410c;
}

.trace-waterfall__row.is-error:hover,
.trace-waterfall__row.is-error.is-active {
  background: rgba(185, 28, 28, 0.06);
}

.trace-waterfall__meta {
  display: flex;
  flex-direction: column;
  gap: 0.05rem;
  min-width: 0;
}

.trace-waterfall__svc {
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.trace-waterfall__op {
  font-size: 0.82rem;
  color: var(--si-ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.trace-waterfall__track {
  position: relative;
  height: 1.55rem;
  background: rgba(255, 252, 250, 0.8);
  border: 1px solid rgba(20, 83, 45, 0.08);
  border-radius: 6px;
  overflow: hidden;
}

.trace-waterfall__bar {
  position: absolute;
  top: 3px;
  bottom: 3px;
  border-radius: 4px;
  min-width: 4px;
  display: flex;
  align-items: center;
  padding: 0 0.35rem;
  box-shadow: 0 1px 2px rgba(21, 36, 31, 0.12);
}

.trace-waterfall__bar-label {
  font-size: 0.65rem;
  font-weight: 700;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.trace-waterfall__dur {
  text-align: right;
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--si-ink-soft);
  font-variant-numeric: tabular-nums;
}

.trace-selected {
  margin: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.65rem 1.25rem;
}

@media (max-width: 767px) {
  .trace-selected {
    grid-template-columns: 1fr;
  }
}

.trace-selected > div {
  display: grid;
  grid-template-columns: 4.5rem 1fr;
  gap: 0.5rem;
  align-items: start;
}

.trace-selected dt {
  margin: 0;
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--si-muted);
}

.trace-selected dd {
  margin: 0;
  color: var(--si-ink);
  font-size: 0.9rem;
  word-break: break-all;
}

.trace-error-box {
  padding: 0.75rem 0.9rem;
  border-radius: 8px;
  background: rgba(185, 28, 28, 0.06);
  border: 1px solid rgba(185, 28, 28, 0.2);
  color: #991b1b;
  font-size: 0.88rem;
  word-break: break-word;
}

.trace-error-box__title {
  font-weight: 700;
  margin-bottom: 0.35rem;
  font-size: 0.8rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.trace-tags-title {
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--si-muted);
  margin-bottom: 0.45rem;
}

.trace-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.trace-tag {
  display: inline-flex;
  gap: 0.35rem;
  align-items: baseline;
  max-width: 100%;
  padding: 0.2rem 0.55rem;
  border-radius: 6px;
  background: rgba(15, 118, 110, 0.06);
  border: 1px solid rgba(15, 118, 110, 0.14);
  font-size: 0.78rem;
  color: var(--si-ink);
  word-break: break-all;
}

.trace-tag em {
  font-style: normal;
  font-weight: 700;
  color: var(--si-teal);
}

.trace-tag em::after {
  content: ':';
}
</style>
