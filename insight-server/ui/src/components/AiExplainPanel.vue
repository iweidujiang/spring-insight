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
const displayMeta = computed(() => {
  if (props.meta) return props.meta
  return [props.result.provider, props.result.model].filter(Boolean).join(' · ')
})
const modelLine = computed(() => {
  const parts: string[] = []
  if (props.result.provider) parts.push(String(props.result.provider))
  if (props.result.model) parts.push(String(props.result.model))
  if (props.result.degraded) parts.push('调用失败或已降级')
  return parts.join(' · ')
})
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
    return
  }
  if (nav.kind === 'error-analysis') {
    const query: Record<string, string> = {}
    if (nav.hours != null) query.hours = String(nav.hours)
    router.push({ path: '/error-analysis', query })
  }
}

const canClick = (item: AiEvidence) => !!item.nav?.kind
</script>

<template>
  <section class="si-ai-panel" aria-label="AI 解读">
    <div class="si-ai-panel__head">
      <h3 class="si-ai-panel__title">
        <i class="fa fa-lightbulb-o me-2"></i>{{ title }}
        <small v-if="displayMeta" class="text-muted ms-2">{{ displayMeta }}</small>
        <span v-if="result.degraded" class="badge text-bg-warning ms-2">已降级</span>
      </h3>
      <button type="button" class="btn btn-sm btn-outline-secondary" @click="emit('close')">关闭</button>
    </div>

    <div v-if="result.degraded" class="si-ai-panel__fail" role="status">
      <i class="fa fa-exclamation-triangle me-1"></i>
      <span>{{ result.message || summary || 'AI 调用失败或未就绪，已降级为提示信息。' }}</span>
    </div>

    <p
      v-if="summary && (!result.degraded || summary !== (result.message || ''))"
      class="si-ai-panel__summary"
    >{{ summary }}</p>

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

    <p v-if="modelLine" class="si-ai-panel__model">{{ modelLine }}</p>
    <p class="si-ai-panel__note">AI 建议，请以 Span 为准；失败时请核对设置页模型与密钥，或改用「复制 Context」。</p>
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
.si-ai-panel__fail {
  display: flex;
  align-items: flex-start;
  gap: 0.35rem;
  margin: 0 0 0.75rem;
  padding: 0.55rem 0.7rem;
  border-radius: 8px;
  border: 1px solid rgba(180, 83, 9, 0.35);
  background: var(--si-amber-bg);
  font-size: 0.85rem;
  line-height: 1.45;
  color: var(--si-warn-bullet-color);
}
.si-ai-panel__model {
  margin: 0.65rem 0 0;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--si-muted, #78716c);
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
