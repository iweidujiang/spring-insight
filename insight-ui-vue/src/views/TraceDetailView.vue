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
          class="btn btn-sm btn-outline-danger"
          type="button"
          @click="jumpError(-1)"
          title="上一个异常 Span"
        >
          <i class="fa fa-chevron-up"></i> 异常
        </button>
        <button
          v-if="timeline.errorCount > 0"
          class="btn btn-sm btn-outline-danger"
          type="button"
          @click="jumpError(1)"
          title="下一个异常 Span"
        >
          异常 <i class="fa fa-chevron-down"></i>
        </button>
        <button class="btn btn-sm btn-outline-secondary" type="button" @click="goBack">
          <i class="fa fa-arrow-left me-1"></i>返回
        </button>
        <button
          class="btn btn-sm btn-outline-secondary"
          type="button"
          @click="copyTraceContext"
          :disabled="loading || spans.length === 0"
          title="复制脱敏 Context JSON，可粘贴到任意 LLM"
        >
          <i class="fa fa-magic me-1"></i>复制 Context
        </button>
        <button
          class="btn btn-sm btn-outline-info"
          type="button"
          @click="runExplain"
          :disabled="loading || explaining || spans.length === 0 || !aiStatus.invokeReady"
          :title="aiExplainTitle"
        >
          <i class="fa" :class="explaining ? 'fa-spinner fa-spin' : 'fa-lightbulb-o'"></i>
          {{ explaining ? '解释中…' : 'AI 解释' }}
        </button>
        <button class="btn btn-sm btn-primary" type="button" @click="load" :disabled="loading">
          <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i> 刷新
        </button>
      </div>
    </div>

    <div v-if="copyHint" class="alert alert-success py-2 mb-3" role="status">{{ copyHint }}</div>
    <div v-if="explainHint" class="alert alert-warning py-2 mb-3" role="status">{{ explainHint }}</div>

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
        <!-- 摘要细条：高度让给瀑布 -->
        <section class="trace-strip" aria-label="链路摘要">
          <div class="trace-strip__item">
            <span class="trace-strip__label">总耗时</span>
            <span class="trace-strip__value">{{ formatDuration(timeline.totalDurationMs) }}</span>
          </div>
          <div class="trace-strip__item">
            <span class="trace-strip__label">Span</span>
            <span class="trace-strip__value">{{ spans.length }}</span>
          </div>
          <div class="trace-strip__item">
            <span class="trace-strip__label">服务</span>
            <span class="trace-strip__value">{{ timeline.serviceCount }}</span>
          </div>
          <div class="trace-strip__item">
            <span class="trace-strip__label">异常</span>
            <span class="trace-strip__value" :class="{ 'is-danger': timeline.errorCount > 0 }">
              {{ timeline.errorCount }}
            </span>
          </div>
        </section>

        <!-- 主舞台：瀑布 + 右栏选中 Span -->
        <div class="trace-stage">
          <section class="trace-stage__main" aria-label="调用时间线">
            <div class="trace-stage__head">
              <h3 class="trace-stage__title">
                <i class="fa fa-align-left me-2"></i>调用时间线
              </h3>
              <span class="trace-waterfall-hint">
                缩进=父子 · 橙边=关键路径 · 红条=异常
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
                <div class="trace-waterfall__axis-dur"></div>
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
                <div class="trace-waterfall__meta" :style="{ paddingLeft: `${10 + row.depth * 14}px` }">
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
          </section>

          <aside class="trace-stage__aside" aria-label="选中 Span">
            <div class="trace-aside">
              <h3 class="trace-aside__title"><i class="fa fa-info-circle me-2"></i>选中 Span</h3>

              <template v-if="selected">
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

                <div v-if="selected.errorCode || selected.errorMessage" class="trace-error-box">
                  <div class="trace-error-box__title"><i class="fa fa-exclamation-triangle me-1"></i>错误信息</div>
                  <div v-if="selected.errorCode"><strong>errorCode</strong>：{{ selected.errorCode }}</div>
                  <div v-if="selected.errorMessage" class="mt-1">{{ selected.errorMessage }}</div>
                </div>

                <div v-if="tagEntries.length" class="trace-aside__tags">
                  <div class="trace-tags-title">Tags</div>
                  <div class="trace-tags">
                    <span v-for="[k, v] in tagEntries" :key="k" class="trace-tag">
                      <em>{{ k }}</em>{{ v }}
                    </span>
                  </div>
                </div>
              </template>

              <p v-else class="trace-aside__empty">点击瀑布行查看详情</p>
            </div>
          </aside>
        </div>

        <!-- AI 结果：不占首屏，有内容时出现在瀑布下方 -->
        <section v-if="explainMarkdown" class="trace-ai-panel" aria-label="AI 解释">
          <div class="trace-ai-panel__head">
            <h3 class="trace-ai-panel__title">
              <i class="fa fa-lightbulb-o me-2"></i>AI 解释
              <small v-if="explainMeta" class="text-muted ms-2">{{ explainMeta }}</small>
            </h3>
            <button class="btn btn-sm btn-outline-secondary" type="button" @click="explainMarkdown = ''">关闭</button>
          </div>
          <pre class="trace-ai-markdown mb-0">{{ explainMarkdown }}</pre>
        </section>

        <!-- Span 表：默认折叠 -->
        <details class="trace-span-fold">
          <summary class="trace-span-fold__summary">
            <i class="fa fa-list me-2"></i>Span 列表（{{ spans.length }}）
          </summary>
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
        </details>
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
const explainHint = ref('')
const explainMarkdown = ref('')
const explainMeta = ref('')
const explaining = ref(false)
const aiStatus = ref({
  enabled: false,
  invokeReady: false,
  provider: '',
  model: '',
  baseUrl: ''
})
const waterfallEl = ref<HTMLElement | null>(null)
let copyTimer: number | null = null

const aiExplainTitle = computed(() => {
  if (aiStatus.value.invokeReady) {
    return `调用 ${aiStatus.value.provider || 'AI'} / ${aiStatus.value.model || 'model'} 解释当前 Trace`
  }
  if (aiStatus.value.enabled) {
    return 'AI 已启用但未配齐 base-url / api-key（见 spring.insight.server.ai）'
  }
  return 'AI 未启用：请配置 spring.insight.server.ai.enabled=true 与 api-key；也可「复制 Context」'
})

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

const copyTraceContext = async () => {
  if (!traceId.value) return
  try {
    const ctx = await ApiService.getTraceContext(traceId.value)
    if (!ctx) {
      copyHint.value = '未找到 Context（Trace 可能不存在）'
      return
    }
    await navigator.clipboard.writeText(JSON.stringify(ctx, null, 2))
    copyHint.value = '已复制 Trace Context（可粘贴到 LLM；AI 建议请以 Span 为准）'
    if (copyTimer) clearTimeout(copyTimer)
    copyTimer = window.setTimeout(() => {
      copyHint.value = ''
    }, 3000)
  } catch {
    copyHint.value = '复制 Context 失败'
  }
}

const runExplain = async () => {
  if (!traceId.value || explaining.value) return
  explaining.value = true
  explainHint.value = ''
  try {
    const res = await ApiService.explainTrace(traceId.value)
    if (!res) {
      explainHint.value = '解释请求失败（网络或鉴权）'
      return
    }
    explainMarkdown.value = res.markdown || ''
    explainMeta.value = [res.provider, res.model].filter(Boolean).join(' · ')
    if (res.degraded) {
      explainHint.value = res.message || '已降级：请检查 AI 配置或复制 Context'
    }
  } finally {
    explaining.value = false
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

watch(() => route.params.traceId, () => {
  explainMarkdown.value = ''
  explainHint.value = ''
  load()
})
onMounted(async () => {
  aiStatus.value = await ApiService.getAiStatus()
  await load()
})
</script>

<style scoped>
.trace-ai-markdown {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font-mono, ui-monospace, monospace);
  font-size: 0.875rem;
  line-height: 1.55;
  margin: 0;
  padding: 0.75rem 1rem;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.04);
  border: 1px solid var(--card-border);
  max-height: 22rem;
  overflow: auto;
}

.text-cyan {
  color: var(--si-teal);
}

/* —— 摘要细条 —— */
.trace-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 1.25rem;
  align-items: baseline;
  padding: 0.55rem 0.9rem;
  margin-bottom: 0.85rem;
  border-radius: 8px;
  border: 1px solid var(--card-border);
  background: rgba(255, 252, 250, 0.65);
}

.trace-strip__item {
  display: inline-flex;
  align-items: baseline;
  gap: 0.4rem;
}

.trace-strip__label {
  font-size: 0.68rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--si-muted);
}

.trace-strip__value {
  font-family: var(--font-display);
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--si-ink);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.trace-strip__value.is-danger {
  color: #b91c1c;
}

/* —— 主舞台：瀑布 + 右栏 —— */
.trace-stage {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(240px, 30%);
  gap: 0.85rem;
  align-items: start;
  margin-bottom: 0.85rem;
}

@media (max-width: 991px) {
  .trace-stage {
    grid-template-columns: 1fr;
  }
}

.trace-stage__main {
  min-width: 0;
  padding: 0.85rem 0.95rem 1rem;
  border-radius: 10px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
}

.trace-stage__head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 0.35rem 0.75rem;
  margin-bottom: 0.65rem;
}

.trace-stage__title {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--si-ink);
}

.trace-waterfall-hint {
  font-size: 0.72rem;
  color: var(--si-muted);
}

.trace-stage__aside {
  min-width: 0;
}

@media (min-width: 992px) {
  .trace-aside {
    position: sticky;
    top: calc(var(--si-nav-offset, 4.5rem) + 0.75rem);
    max-height: calc(100vh - var(--si-nav-offset, 4.5rem) - 1.5rem);
    overflow: auto;
  }
}

.trace-aside {
  padding: 0.85rem 0.95rem 1rem;
  border-radius: 10px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
}

.trace-aside__title {
  margin: 0 0 0.75rem;
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--si-ink);
}

.trace-aside__empty {
  margin: 0;
  padding: 1.25rem 0.25rem;
  text-align: center;
  font-size: 0.88rem;
  color: var(--si-muted);
}

.trace-aside__tags {
  margin-top: 0.85rem;
}

/* —— 瀑布 —— */
.trace-waterfall {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  border: 1px solid var(--card-border);
  border-radius: 8px;
  background: var(--si-paper);
  padding: 0.55rem 0.65rem 0.7rem;
  overflow-x: auto;
}

.trace-waterfall__axis,
.trace-waterfall__row {
  display: grid;
  grid-template-columns: minmax(160px, 26%) 1fr 4.25rem;
  gap: 0.5rem;
  align-items: center;
  min-width: 560px;
}

.trace-waterfall__axis {
  padding-bottom: 0.25rem;
  margin-bottom: 0.1rem;
  border-bottom: 1px dashed rgba(20, 83, 45, 0.15);
}

.trace-waterfall__axis-track {
  position: relative;
  height: 1rem;
}

.trace-waterfall__tick {
  position: absolute;
  transform: translateX(-50%);
  font-size: 0.65rem;
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
  padding: 0.18rem 0;
  border-radius: 5px;
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
  gap: 0.02rem;
  min-width: 0;
}

.trace-waterfall__svc {
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.trace-waterfall__op {
  font-size: 0.78rem;
  color: var(--si-ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.trace-waterfall__track {
  position: relative;
  height: 1.35rem;
  background: rgba(255, 252, 250, 0.8);
  border: 1px solid rgba(20, 83, 45, 0.08);
  border-radius: 5px;
  overflow: hidden;
}

.trace-waterfall__bar {
  position: absolute;
  top: 2px;
  bottom: 2px;
  border-radius: 3px;
  min-width: 4px;
  display: flex;
  align-items: center;
  padding: 0 0.3rem;
  box-shadow: 0 1px 2px rgba(21, 36, 31, 0.12);
}

.trace-waterfall__bar-label {
  font-size: 0.62rem;
  font-weight: 700;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.trace-waterfall__dur {
  text-align: right;
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--si-ink-soft);
  font-variant-numeric: tabular-nums;
}

/* —— 选中 Span —— */
.trace-selected {
  margin: 0;
  display: grid;
  grid-template-columns: 1fr;
  gap: 0.45rem 0;
}

.trace-selected > div {
  display: grid;
  grid-template-columns: 3.75rem 1fr;
  gap: 0.4rem;
  align-items: start;
}

.trace-selected dt {
  margin: 0;
  font-size: 0.68rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--si-muted);
}

.trace-selected dd {
  margin: 0;
  color: var(--si-ink);
  font-size: 0.84rem;
  word-break: break-all;
}

.trace-error-box {
  margin-top: 0.75rem;
  padding: 0.65rem 0.8rem;
  border-radius: 8px;
  background: rgba(185, 28, 28, 0.06);
  border: 1px solid rgba(185, 28, 28, 0.2);
  color: #991b1b;
  font-size: 0.84rem;
  word-break: break-word;
}

.trace-error-box__title {
  font-weight: 700;
  margin-bottom: 0.3rem;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.trace-tags-title {
  font-size: 0.68rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--si-muted);
  margin-bottom: 0.4rem;
}

.trace-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.trace-tag {
  display: inline-flex;
  gap: 0.3rem;
  align-items: baseline;
  max-width: 100%;
  padding: 0.15rem 0.45rem;
  border-radius: 5px;
  background: rgba(15, 118, 110, 0.06);
  border: 1px solid rgba(15, 118, 110, 0.14);
  font-size: 0.74rem;
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

/* —— AI 面板（瀑布下方） —— */
.trace-ai-panel {
  margin-bottom: 0.85rem;
  padding: 0.85rem 0.95rem;
  border-radius: 10px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
}

.trace-ai-panel__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 0.55rem;
}

.trace-ai-panel__title {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 700;
}

/* —— Span 表折叠 —— */
.trace-span-fold {
  border-radius: 10px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  padding: 0 0.95rem 0.85rem;
}

.trace-span-fold__summary {
  cursor: pointer;
  list-style: none;
  padding: 0.75rem 0;
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--si-ink);
  user-select: none;
}

.trace-span-fold__summary::-webkit-details-marker {
  display: none;
}

.trace-span-fold__summary::before {
  content: '\f0da';
  font-family: FontAwesome, 'Font Awesome 5 Free', sans-serif;
  display: inline-block;
  width: 0.9rem;
  margin-right: 0.15rem;
  color: var(--si-muted);
  transition: transform 0.15s ease;
}

.trace-span-fold[open] > .trace-span-fold__summary::before {
  transform: rotate(90deg);
}
</style>
