<script setup lang="ts">
export type SiStatusTone = 'success' | 'danger' | 'warn' | 'neutral'

const props = defineProps<{
  tone: SiStatusTone
}>()

const toneClass: Record<SiStatusTone, string> = {
  success: 'bg-success',
  danger: 'bg-danger',
  warn: 'bg-warning text-dark',
  neutral: 'bg-secondary'
}
</script>

<template>
  <span class="badge" :class="toneClass[props.tone]">
    <slot />
  </span>
</template>
