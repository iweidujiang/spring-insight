<script setup lang="ts">
defineProps<{
  title: string
  /** 不传则不显示计数 */
  count?: number
  /** Element Plus 图标组件名，如 'Server' */
  icon?: string
  /** 无障碍名称；缺省用标题 */
  label?: string
}>()
</script>

<template>
  <section class="si-section" :aria-label="label || title">
    <div class="si-section__head">
      <h3 class="si-section__title">
        <el-icon v-if="icon" class="me-2"><component :is="icon" /></el-icon>{{ title }}
      </h3>
      <el-tag v-if="count != null" type="info" effect="plain" size="small" round>{{ count }}</el-tag>
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
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--si-ink);
  padding-left: 0.75rem;
  border-left: 3px solid var(--si-brand);
}
</style>
