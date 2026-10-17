<script setup lang="ts">
defineProps<{
  text?: string
}>()
</script>

<template>
  <div class="loading-spinner">
    <i class="fa fa-spinner fa-spin" aria-hidden="true"></i>
    <span v-if="text" class="ms-2">{{ text }}</span>
  </div>
</template>
