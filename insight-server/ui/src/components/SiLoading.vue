<script setup lang="ts">
withDefaults(
  defineProps<{
    text?: string
    /** 区块内嵌加载（更矮），用于卡片 / JVM 等局部区域 */
    compact?: boolean
  }>(),
  { compact: false }
)
</script>

<template>
  <!-- 页面级占位：指令常开，由父级 v-if 控制显隐。遮罩透明，避免套上弹层用的暗色蒙层。 -->
  <div
    v-loading="true"
    class="si-loading"
    :class="{ 'si-loading--compact': compact }"
    :element-loading-text="text || ''"
    element-loading-background="transparent"
    role="status"
    :aria-label="text || '加载中'"
  />
</template>

<style scoped>
.si-loading {
  min-height: 200px;
}

.si-loading--compact {
  min-height: 96px;
}

.si-loading :deep(.el-loading-spinner .el-loading-text) {
  color: var(--si-muted, #6b7f76);
  font-weight: 500;
}
</style>
