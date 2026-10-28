<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { AiCompareResult, AiEvidence, AiExplainResult } from '../services/ApiService'

const props = withDefaults(
  defineProps<{
    result: AiExplainResult | AiCompareResult
    title?: string
    meta?: string
  }>(),
  {
    title: 'AI 解读',
    meta: ''
  }
)

const emit = defineEmits<{
  close: []
  /** 当前页内选中 Span（Trace 详情） */
  'select-span': [spanId: string]
}>()

const router = useRouter()

const compareResult = computed(() =>
  props.result.kind === 'compare-traces' ? (props.result as AiCompareResult) : null
)
const isCompare = computed(() => !!compareResult.value)
const summary = computed(() => props.result.summary || props.result.message || '')
const evidence = computed(() => props.result.evidence || [])
const suggestions = computed(() => props.result.suggestions || [])
const displayMeta = computed(() => {
  if (props.meta) return props.meta
  return [props.result.provider, props.result.model].filter(Boolean).join(' · ')
})
const modelLine = computed(() => {
  const parts: string[] = []
  if (props.result.provider) parts.push(String(props.result.provider))
  if (props.result.model) parts.push(String(props.result.model))
  if (props.result.statusLabel) parts.push(String(props.result.statusLabel))
  else if (props.result.degraded) parts.push('调用失败或已降级')
  return parts.join(' · ')
})
const showRawMarkdown = computed(
  () =>
    !props.result.structured &&
    !!props.result.markdown &&
    (!summary.value || props.result.markdown.trim() !== summary.value.trim())
)
const evidenceTitle = computed(() => (isCompare.value ? '显著差异' : '可疑证据'))

const evidenceLabel = (item: AiEvidence) => {
  if (item.label) return item.label
  if (item.span) {
    const svc = item.service ? ` @ ${item.service}` : ''
    return `${item.span}${svc}`
  }
  return item.ref || '—'
}

const evidenceReason = (item: AiEvidence) => {
  if (item.reason) return item.reason
  const bits: string[] = []
  if (item.durationA != null || item.durationB != null) {
    const a = item.durationA != null ? `${item.durationA}ms` : '—'
    const b = item.durationB != null ? `${item.durationB}ms` : '—'
    const d =
      item.delta != null
        ? `Δ ${item.delta > 0 ? '+' : ''}${item.delta}ms`
        : ''
    bits.push(`A ${a} → B ${b}${d ? `（${d}）` : ''}`)
  }
  if (item.analysis) bits.push(item.analysis)
  return bits.join(' · ')
}

const onEvidence = (item: AiEvidence) => {
  const nav = item.nav
  if (!nav?.kind) {
    // 对比证据无 spanId 时：跳到 Trace B（变慢侧常更值得看），否则 A
    const tid =
      compareResult.value?.traceIdB || compareResult.value?.traceIdA || props.result.traceId
    if (tid) {
      router.push({ path: `/traces/${encodeURIComponent(tid)}` })
    }
    return
  }
  if (nav.kind === 'span' && nav.spanId) {
    if (nav.traceId && props.result.traceId && nav.traceId !== props.result.traceId) {
      router.push({ path: `/traces/${encodeURIComponent(nav.traceId)}`, query: { span: nav.spanId } })
      return
    }
    emit('select-span', nav.spanId)
    return
  }
  if (nav.kind === 'trace' && nav.traceId) {
    router.push({ path: `/traces/${encodeURIComponent(nav.traceId)}` })
    return
  }
  if (nav.kind === 'traces') {
    const query: Record<string, string> = {}
    if (nav.service) query.service = nav.service
    if (nav.q) query.q = nav.q
    if (nav.status) query.status = nav.status
    if (nav.hours != null) query.hours = String(nav.hours)
    router.push({ path: '/traces', query })
    return
  }
  if (nav.kind === 'topology') {
    const query: Record<string, string> = {}
    if (nav.source) query.source = nav.source
    if (nav.target) query.target = nav.target
    if (nav.hours != null) query.hours = String(nav.hours)
    router.push({ path: '/topology', query })
    return
  }
  if (nav.kind === 'error-analysis') {
    const query: Record<string, string> = {}
    if (nav.hours != null) query.hours = String(nav.hours)
    router.push({ path: '/error-analysis', query })
  }
}

const canClick = (item: AiEvidence) => {
  if (item.nav?.kind) return true
  if (isCompare.value) {
    return !!(
      compareResult.value?.traceIdA ||
      compareResult.value?.traceIdB ||
      props.result.traceId
    )
  }
  return false
}

const openTrace = (id?: string) => {
  if (!id) return
  router.push({ path: `/traces/${encodeURIComponent(id)}` })
}
</script>

<template>
  <section class="si-ai-panel" aria-label="AI 解读">
    <div class="si-ai-panel__head">
      <h3 class="si-ai-panel__title">
        <el-icon class="me-2 si-ai-panel__icon"><Lightbulb /></el-icon>{{ title }}
        <small v-if="displayMeta" class="si-ai-panel__meta">{{ displayMeta }}</small>
        <el-tag v-if="result.degraded" type="warning" size="small" effect="plain" class="ms-2">已降级</el-tag>
      </h3>
      <el-button size="small" @click="emit('close')">关闭</el-button>
    </div>

    <div
      v-if="compareResult && (compareResult.traceIdA || compareResult.traceIdB)"
      class="si-ai-panel__trace-links"
    >
      <el-button
        v-if="compareResult.traceIdA"
        type="primary"
        link
        size="small"
        @click="openTrace(compareResult.traceIdA)"
      >
        打开 Trace A
      </el-button>
      <span v-if="compareResult.traceIdA && compareResult.traceIdB" class="si-ai-panel__sep">·</span>
      <el-button
        v-if="compareResult.traceIdB"
        type="primary"
        link
        size="small"
        @click="openTrace(compareResult.traceIdB)"
      >
        打开 Trace B
      </el-button>
    </div>

    <div v-if="result.degraded" class="si-ai-panel__fail" role="status">
      <el-icon class="me-1"><WarningFilled /></el-icon>
      <span>{{ result.message || result.reason || summary || 'AI 调用失败或未就绪，已降级为提示信息。' }}</span>
    </div>

    <p
      v-if="summary && (!result.degraded || summary !== (result.message || result.reason || ''))"
      class="si-ai-panel__summary"
    >{{ summary }}</p>

    <div v-if="evidence.length" class="si-ai-panel__block">
      <h4 class="si-ai-panel__sub">{{ evidenceTitle }}</h4>
      <ul class="si-ai-panel__list">
        <li v-for="(item, i) in evidence" :key="i">
          <button
            v-if="canClick(item)"
            type="button"
            class="si-ai-panel__ev-btn"
            @click="onEvidence(item)"
          >
            <strong>{{ evidenceLabel(item) }}</strong>
            <span v-if="evidenceReason(item)" class="si-ai-panel__reason">{{ evidenceReason(item) }}</span>
            <span class="si-ai-panel__go">查看</span>
          </button>
          <div v-else class="si-ai-panel__ev-static">
            <strong>{{ evidenceLabel(item) }}</strong>
            <span v-if="evidenceReason(item)" class="si-ai-panel__reason">{{ evidenceReason(item) }}</span>
          </div>
        </li>
      </ul>
    </div>

    <div v-if="suggestions.length" class="si-ai-panel__block">
      <h4 class="si-ai-panel__sub">建议</h4>
      <ol class="si-ai-panel__suggestions">
        <li v-for="(s, i) in suggestions" :key="i">{{ s }}</li>
      </ol>
    </div>

    <details v-if="showRawMarkdown" class="si-ai-panel__raw">
      <summary>原始说明</summary>
      <pre class="si-ai-panel__md">{{ result.markdown }}</pre>
    </details>

    <p v-if="modelLine" class="si-ai-panel__model">{{ modelLine }}</p>
    <p class="si-ai-panel__note">AI 建议，请以 Span 为准；失败时请核对设置页模型与密钥，或改用「复制 Context」。</p>
  </section>
</template>

<style scoped>
.si-ai-panel {
  margin-top: 1rem;
  padding: 1rem 1.1rem;
  border: 1px solid var(--card-border);
  border-radius: 10px;
  background: var(--card-bg);
  color: var(--si-ink);
}
.si-ai-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.65rem;
}
.si-ai-panel__title {
  margin: 0;
  font-size: 1rem;
  font-weight: 650;
  color: var(--si-ink);
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.25rem;
}
.si-ai-panel__icon {
  color: var(--si-brand);
}
.si-ai-panel__meta {
  font-size: 0.82rem;
  color: var(--si-muted);
  font-weight: 400;
  margin-left: 0.5rem;
}
.si-ai-panel__trace-links {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.15rem;
  margin: -0.25rem 0 0.65rem;
  font-size: 0.85rem;
}
.si-ai-panel__sep {
  color: var(--si-muted);
  padding: 0 0.25rem;
}
.si-ai-panel__summary {
  margin: 0 0 0.75rem;
  font-size: 0.95rem;
  line-height: 1.5;
  color: var(--si-ink);
}
.si-ai-panel__fail {
  display: flex;
  align-items: flex-start;
  gap: 0.35rem;
  margin: 0 0 0.75rem;
  padding: 0.55rem 0.7rem;
  border-radius: 8px;
  border: 1px solid var(--si-warn-bullet-border);
  background: var(--si-amber-bg);
  font-size: 0.85rem;
  line-height: 1.45;
  color: var(--si-warn-bullet-color);
}
.si-ai-panel__model {
  margin: 0.65rem 0 0;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--si-muted);
}
.si-ai-panel__block {
  margin-bottom: 0.75rem;
}
.si-ai-panel__sub {
  margin: 0 0 0.35rem;
  font-size: 0.8rem;
  font-weight: 650;
  letter-spacing: 0.02em;
  color: var(--si-muted);
  text-transform: uppercase;
}
.si-ai-panel__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.si-ai-panel__ev-btn,
.si-ai-panel__ev-static {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.35rem 0.65rem;
  width: 100%;
  text-align: left;
  padding: 0.45rem 0.6rem;
  border-radius: 8px;
  border: 1px solid transparent;
  background: var(--si-brand-soft);
  color: var(--si-ink);
}
.si-ai-panel__ev-btn {
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}
.si-ai-panel__ev-btn:hover {
  border-color: var(--si-form-border);
  background: var(--si-hover-brand);
}
.si-ai-panel__reason {
  flex: 1 1 12rem;
  font-size: 0.85rem;
  color: var(--si-muted);
}
.si-ai-panel__go {
  font-size: 0.75rem;
  font-weight: 650;
  color: var(--si-brand);
}
.si-ai-panel__suggestions {
  margin: 0;
  padding-left: 1.2rem;
  font-size: 0.9rem;
  line-height: 1.5;
  color: var(--si-ink);
}
.si-ai-panel__raw {
  margin-top: 0.5rem;
  font-size: 0.85rem;
  color: var(--si-ink-soft);
}
.si-ai-panel__md {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font-mono);
  font-size: 0.8rem;
  margin: 0.4rem 0 0;
  padding: 0.5rem 0.65rem;
  background: var(--si-surface-2);
  border-radius: 6px;
  color: var(--si-ink-soft);
}
.si-ai-panel__note {
  margin: 0.5rem 0 0;
  font-size: 0.75rem;
  color: var(--si-muted);
}
</style>
