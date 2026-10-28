<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  /** Element Plus 图标组件名，如 'BellFilled' */
  icon?: string
}>()
</script>

<template>
  <section class="si-settings__block">
    <header class="si-settings__block-head">
      <div>
        <h4 class="si-settings__block-title">
          <el-icon v-if="icon" class="me-1"><component :is="icon" /></el-icon>{{ title }}
        </h4>
        <p v-if="description" class="si-settings__block-desc">{{ description }}</p>
      </div>
      <slot name="switch" />
    </header>
    <el-form class="si-settings__fields" label-position="top" @submit.prevent>
      <slot />
    </el-form>
  </section>
</template>
