<script setup lang="ts">
defineProps<{
  /** Font Awesome 类，如 fa-database。行内图标，不放进方块 */
  icon?: string
}>()
</script>

<template>
  <!-- description 置空：正文与操作按钮走默认插槽，避免按钮落在 description 区 -->
  <el-empty class="si-empty" description=" " :image-size="56">
    <template v-if="icon" #image>
      <i class="fa si-empty__icon" :class="icon" aria-hidden="true"></i>
    </template>
    <div class="si-empty__body">
      <slot />
    </div>
  </el-empty>
</template>

<style scoped>
.si-empty {
  padding: 2.5rem 1rem;
  color: var(--si-muted, #6b7f76);
  border: 1px dashed var(--card-border);
  border-radius: 0.5rem;
  background: transparent;
}

.si-empty :deep(.el-empty__image) {
  width: auto;
  height: auto;
}

.si-empty :deep(.el-empty__description) {
  display: none;
}

.si-empty__icon {
  font-size: 1.75rem;
  opacity: 0.55;
  color: var(--si-muted, #6b7f76);
}

.si-empty__body {
  margin: 0;
  font-size: 0.95rem;
  line-height: 1.5;
  color: var(--si-muted, #6b7f76);
  max-width: 36rem;
}

.si-empty :deep(.el-empty__bottom) {
  margin-top: 0.75rem;
}
</style>
