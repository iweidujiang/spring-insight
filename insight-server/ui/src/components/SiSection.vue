<script setup lang="ts">
defineProps<{
  title: string
  /** 不传则不显示计数 */
  count?: number
  /** Font Awesome 图标类，如 fa-server。只作行内图标，不包方块 */
  icon?: string
  /** 无障碍名称；缺省用标题 */
  label?: string
}>()
</script>

<template>
  <section class="si-section" :aria-label="label || title">
    <div class="si-section__head">
      <h3 class="si-section__title">
        <i v-if="icon" class="fa me-2" :class="icon" aria-hidden="true"></i>{{ title }}
      </h3>
      <span v-if="count != null" class="badge bg-secondary">{{ count }}</span>
    </div>
    <slot />
  </section>
</template>

<style scoped>
.si-section {
  margin-bottom: 1.25rem;
  border: 1px solid var(--card-border);
  border-radius: 0.5rem;
  overflow: hidden;
  background: var(--card-bg);
}

.si-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.65rem 1rem;
  border-bottom: 1px solid var(--card-border);
}

.si-section__title {
  margin: 0;
  font-family: var(--font-body);
  font-size: 0.95rem;
  font-weight: 650;
  color: var(--si-ink);
}
</style>
