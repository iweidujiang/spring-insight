<script setup lang="ts">
defineProps<{
  /** Font Awesome 类，如 fa-database。行内图标，不放进方块 */
  icon?: string
}>()
</script>

<template>
  <div class="si-empty">
    <i v-if="icon" class="fa" :class="icon" aria-hidden="true"></i>
    <slot />
  </div>
</template>

<style scoped>
.si-empty {
  text-align: center;
  padding: 2.5rem 1rem;
  color: var(--si-muted, #6b7f76);
  border: 1px dashed var(--card-border);
  border-radius: 0.5rem;
}

.si-empty .fa {
  font-size: 1.75rem;
  margin-bottom: 0.75rem;
  opacity: 0.55;
}
</style>
