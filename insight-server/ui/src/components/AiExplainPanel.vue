<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { AiEvidence, AiExplainResult } from '../services/ApiService'

const props = withDefaults(
  defineProps<{
    result: AiExplainResult
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

const summary = computed(() => props.result.summary || props.result.message || '')
const evidence = computed(() => props.result.evidence || [])
const suggestions = computed(() => props.result.suggestions || [])
const showRawMarkdown = computed(
  () =>
    !props.result.structured &&
    !!props.result.markdown &&
    (!summary.value || props.result.markdown.trim() !== summary.value.trim())
)

const onEvidence = (item: AiEvidence) => {
  const nav = item.nav
  if (!nav?.kind) return
  if (nav.kind === 'span' && nav.spanId) {
    // 同页：交给父组件选中；跨 Trace 则跳转
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
  }
}

const canClick = (item: AiEvidence) => !!item.nav?.kind
</script>

<template>
  <section class="si-ai-panel" aria-label="AI 解读">
    <div class="si-ai-panel__head">
      <h3 class="si-ai-panel__title">
        <i class="fa fa-lightbulb-o me-2"></i>{{ title }}
        <small v-if="meta" class="text-muted ms-2">{{ meta }}</small>
        <span v-if="result.degraded" class="badge text-bg-warning ms-2">已降级</span>
      </h3>
      <button type="button" class="btn btn-sm btn-outline-secondary" @click="emit('close')">关闭</button>
    </div>

    <p v-if="summary" class="si-ai-panel__summary">{{ summary }}</p>

    <div v-if="evidence.length" class="si-ai-panel__block">
      <h4 class="si-ai-panel__sub">可疑证据</h4>
      <ul class="si-ai-panel__list">
        <li v-for="(item, i) in evidence" :key="i">
          <button
            v-if="canClick(item)"
            type="button"
            class="si-ai-panel__ev-btn"
            @click="onEvidence(item)"
          >
            <strong>{{ item.label || item.ref }}</strong>
            <span v-if="item.reason" class="si-ai-panel__reason">{{ item.reason }}</span>
            <span class="si-ai-panel__go">查看</span>
          </button>
          <div v-else class="si-ai-panel__ev-static">
            <strong>{{ item.label || item.ref }}</strong>
            <span v-if="item.reason" class="si-ai-panel__reason">{{ item.reason }}</span>
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

    <p class="si-ai-panel__note">AI 建议，请以 Span 为准。</p>
  </section>
</template>

<style scoped>
.si-ai-panel {
  margin-top: 1rem;
  padding: 1rem 1.1rem;
  border: 1px solid var(--si-border, #d6d3d1);
  border-radius: 10px;
  background: var(--si-paper, #fafaf9);
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
}
.si-ai-panel__summary {
  margin: 0 0 0.75rem;
  font-size: 0.95rem;
  line-height: 1.5;
  color: var(--si-ink, #1c1917);
}
.si-ai-panel__block {
  margin-bottom: 0.75rem;
}
.si-ai-panel__sub {
  margin: 0 0 0.35rem;
  font-size: 0.8rem;
  font-weight: 650;
  letter-spacing: 0.02em;
  color: var(--si-muted, #78716c);
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
  background: rgba(15, 118, 110, 0.06);
}
.si-ai-panel__ev-btn {
  cursor: pointer;
  color: inherit;
  transition: border-color 0.15s ease, background 0.15s ease;
}
.si-ai-panel__ev-btn:hover {
  border-color: rgba(15, 118, 110, 0.35);
  background: rgba(15, 118, 110, 0.1);
}
.si-ai-panel__reason {
  flex: 1 1 12rem;
  font-size: 0.85rem;
  color: var(--si-muted, #57534e);
}
.si-ai-panel__go {
  font-size: 0.75rem;
  font-weight: 650;
  color: #0f766e;
}
.si-ai-panel__suggestions {
  margin: 0;
  padding-left: 1.2rem;
  font-size: 0.9rem;
  line-height: 1.5;
}
.si-ai-panel__raw {
  margin-top: 0.5rem;
  font-size: 0.85rem;
}
.si-ai-panel__md {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font-mono, ui-monospace, monospace);
  font-size: 0.8rem;
  margin: 0.4rem 0 0;
  padding: 0.5rem 0.65rem;
  background: rgba(0, 0, 0, 0.03);
  border-radius: 6px;
}
.si-ai-panel__note {
  margin: 0.5rem 0 0;
  font-size: 0.75rem;
  color: var(--si-muted, #a8a29e);
}
</style>
