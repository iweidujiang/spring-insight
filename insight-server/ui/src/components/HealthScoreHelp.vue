<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { QuestionFilled } from '@element-plus/icons-vue'

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
  <span ref="rootEl" class="si-hs-help">
    <button
      type="button"
      class="si-hs-help__btn"
      :aria-expanded="open"
      aria-label="健康分如何计算"
      @click.stop="toggle"
    >
      <el-icon><QuestionFilled /></el-icon>
    </button>
    <div v-if="open" class="si-hs-help__panel" role="dialog" aria-label="健康分说明">
      <p class="si-hs-help__title">健康分怎么来的？</p>
      <ul>
        <li><strong>错误率 50%</strong>：0%→100 分，1%→80，5%→40，≥10%→0（中间线性插值）。</li>
        <li><strong>p95 延迟 30%</strong>：&lt;100ms→100，&lt;500→70，&lt;1s→40，≥1s→0。</li>
        <li><strong>数据可用 20%</strong>：窗口内有 Span→100；无数据→20，总分标为「未知」。</li>
      </ul>
      <p class="si-hs-help__tip">
        等级：≥80 健康 · ≥60 关注 · ≥40 降级 · 更低危急。悬停节点或选中服务可看当前窗口的实测值与加权拆解。
      </p>
    </div>
  </span>
</template>

<style scoped>
.si-hs-help {
  position: relative;
  display: inline-flex;
  vertical-align: middle;
}

.si-hs-help__btn {
  border: 0;
  padding: 0;
  margin-left: 0.25rem;
  background: transparent;
  color: var(--si-muted);
  font-size: 0.85rem;
  line-height: 1;
  cursor: pointer;
}

.si-hs-help__btn:hover,
.si-hs-help__btn[aria-expanded='true'] {
  color: var(--si-brand);
}

.si-hs-help__panel {
  position: absolute;
  z-index: 40;
  top: calc(100% + 0.4rem);
  left: 0;
  width: min(24rem, 82vw);
  padding: 0.85rem 0.95rem;
  border-radius: 10px;
  border: 1px solid var(--card-border);
  background: var(--si-dropdown-bg);
  box-shadow: 0 12px 28px rgba(21, 36, 31, 0.14);
  color: var(--si-ink);
  text-align: left;
}

.si-hs-help__title {
  margin: 0 0 0.45rem;
  font-size: 0.82rem;
  font-weight: 700;
}

.si-hs-help__panel ul {
  margin: 0;
  padding-left: 1.05rem;
  font-size: 0.78rem;
  line-height: 1.55;
  color: var(--si-ink-soft);
}

.si-hs-help__panel li + li {
  margin-top: 0.35rem;
}

.si-hs-help__tip {
  margin: 0.55rem 0 0;
  font-size: 0.74rem;
  line-height: 1.5;
  color: var(--si-muted);
}
</style>
