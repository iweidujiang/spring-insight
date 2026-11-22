<script setup lang="ts">
import { computed } from 'vue'
import type { TagProps } from 'element-plus'

export type SiStatusTone = 'success' | 'danger' | 'warn' | 'neutral'

const props = withDefaults(
  defineProps<{
    tone: SiStatusTone
    /** 默认 dark；列表旁计数等可用 plain */
    effect?: TagProps['effect']
  }>(),
  {
    effect: 'dark'
  }
)

/** tone → el-tag type；neutral 用 info */
const tagType = computed<TagProps['type']>(() => {
  if (props.tone === 'warn') return 'warning'
  if (props.tone === 'neutral') return 'info'
  return props.tone
})
</script>

<template>
  <el-tag
    class="si-status-badge"
    :type="tagType"
    :effect="effect"
    size="small"
    round
    disable-transitions
  >
    <slot />
  </el-tag>
</template>

<style scoped>
/*
 * 中等胶囊：介于「偏高按钮感」(36px) 与「过小」(22px) 之间，
 * 与表格行文字对齐，仍明显矮于工具栏按钮。
 */
.si-status-badge.el-tag {
  height: 28px;
  min-height: 28px;
  padding: 0 0.7rem;
  line-height: 26px;
  font-size: 0.8125rem;
  font-weight: 650;
  border-width: 0;
  vertical-align: middle;
}
</style>
