<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const open = ref(false)
const rootEl = ref<HTMLElement | null>(null)

function toggle() {
  open.value = !open.value
}

function onDocClick(e: MouseEvent) {
  if (!open.value || !rootEl.value) return
  if (!rootEl.value.contains(e.target as Node)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('click', onDocClick))
onUnmounted(() => document.removeEventListener('click', onDocClick))
</script>

<template>
  <span ref="rootEl" class="si-px-help">
    <button
      type="button"
      class="si-px-help__btn"
      :aria-expanded="open"
      aria-label="什么是 p50 / p95"
      @click.stop="toggle"
    >
      <i class="fa fa-question-circle"></i>
    </button>
    <div v-if="open" class="si-px-help__panel" role="dialog" aria-label="延迟百分位说明">
      <p class="si-px-help__title">p50 / p95 是什么？</p>
      <ul>
        <li><strong>平均</strong>：所有请求耗时的算术平均，容易被少数极慢请求拉高。</li>
        <li><strong>p50</strong>：一半请求比这个值更快。最接近「多数人实际体感」。</li>
        <li><strong>p95</strong>：95% 的请求不慢于这个值。用来抓偶发变慢，比平均值更敏感。</li>
      </ul>
      <p class="si-px-help__tip">例如 p95=246ms，表示这个时间窗口里，大约每 20 次请求里会有 1 次达到或超过 246ms。</p>
    </div>
  </span>
</template>

<style scoped>
.si-px-help {
  position: relative;
  display: inline-flex;
  vertical-align: middle;
}

.si-px-help__btn {
  border: 0;
  padding: 0;
  margin-left: 0.25rem;
  background: transparent;
  color: var(--si-muted, #6b7f76);
  font-size: 0.85rem;
  line-height: 1;
  cursor: pointer;
}

.si-px-help__btn:hover,
.si-px-help__btn[aria-expanded='true'] {
  color: var(--si-teal, #0f766e);
}

.si-px-help__panel {
  position: absolute;
  z-index: 40;
  top: calc(100% + 0.4rem);
  left: 50%;
  transform: translateX(-50%);
  width: min(22rem, 78vw);
  padding: 0.85rem 0.95rem;
  border-radius: 10px;
  border: 1px solid rgba(20, 83, 45, 0.16);
  background: #fffcfa;
  box-shadow: 0 12px 28px rgba(21, 36, 31, 0.14);
  color: var(--si-ink, #15241f);
  text-align: left;
}

.si-px-help__title {
  margin: 0 0 0.45rem;
  font-size: 0.82rem;
  font-weight: 700;
}

.si-px-help__panel ul {
  margin: 0;
  padding-left: 1.05rem;
  font-size: 0.78rem;
  line-height: 1.55;
  color: var(--si-ink-soft, #3d524a);
}

.si-px-help__panel li + li {
  margin-top: 0.35rem;
}

.si-px-help__tip {
  margin: 0.55rem 0 0;
  font-size: 0.74rem;
  line-height: 1.5;
  color: var(--si-muted, #6b7f76);
}
</style>
