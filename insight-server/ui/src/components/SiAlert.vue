<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  tone: 'danger' | 'warning' | 'success'
  /** 不传时：危险为 alert，其余为 status */
  role?: 'alert' | 'status'
}>()

const alertRole = computed(() => props.role ?? (props.tone === 'danger' ? 'alert' : 'status'))
</script>

<template>
  <el-alert
    class="si-alert"
    :type="tone"
    :closable="false"
    :role="alertRole"
    show-icon
  >
    <slot />
  </el-alert>
</template>

<style scoped>
.si-alert {
  margin: 0;
}
.si-alert :deep(.el-alert__description),
.si-alert :deep(.el-alert__content) {
  font-size: inherit;
  line-height: 1.45;
}
</style>
