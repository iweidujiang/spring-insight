<script setup lang="ts">
defineProps<{
  title: string
  /** Font Awesome 类，如 fa-database。只作标题行内图标 */
  icon?: string
}>()
</script>

<template>
  <div class="si-page__header">
    <div>
      <h2 class="page-title mb-1">
        <i v-if="icon" class="fa me-2" :class="icon" aria-hidden="true"></i>{{ title }}
      </h2>
      <p v-if="$slots.description" class="page-description mb-0">
        <slot name="description" />
      </p>
    </div>
    <slot />
  </div>
</template>
