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
  <div class="alert py-2" :class="`alert-${tone}`" :role="alertRole">
    <slot />
  </div>
</template>
