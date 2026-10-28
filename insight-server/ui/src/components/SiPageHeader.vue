<script setup lang="ts">
defineProps<{
  title: string
  /** Element Plus 图标组件名，如 'Database' */
  icon?: string
}>()
</script>

<template>
  <div class="si-page__header">
    <div>
      <h2 class="page-title mb-1">
        <el-icon v-if="icon" class="me-2 si-page__header-icon"><component :is="icon" /></el-icon>{{ title }}
      </h2>
      <p v-if="$slots.description" class="page-description mb-0">
        <slot name="description" />
      </p>
    </div>
    <slot />
  </div>
</template>

<style scoped>
.si-page__header-icon {
  color: var(--si-brand);
}
</style>
