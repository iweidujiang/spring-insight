<script setup lang="ts">
import { computed } from 'vue'
import type { TagProps } from 'element-plus'

export type SiStatusTone = 'success' | 'danger' | 'warn' | 'neutral'

const props = defineProps<{
  tone: SiStatusTone
}>()

/** tone → el-tag type；neutral 用 info */
const tagType = computed<TagProps['type']>(() => {
  if (props.tone === 'warn') return 'warning'
  if (props.tone === 'neutral') return 'info'
  return props.tone
})
</script>

<template>
  <el-tag class="si-status-badge" :type="tagType" size="small" effect="dark" disable-transitions>
    <slot />
  </el-tag>
</template>
