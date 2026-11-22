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
/* 覆盖历史全局大高度，保持紧凑胶囊 */
.si-status-badge.el-tag {
  height: 22px;
  min-height: 22px;
  padding: 0 0.55rem;
  line-height: 20px;
  font-size: 0.75rem;
  font-weight: 650;
  border-width: 0;
  vertical-align: middle;
}
</style>
