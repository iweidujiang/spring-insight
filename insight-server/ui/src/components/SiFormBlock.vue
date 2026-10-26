<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  /** Font Awesome 图标类，如 fa-envelope。只作行内图标 */
  icon?: string
}>()
</script>

<template>
  <section class="si-settings__block">
    <header class="si-settings__block-head">
      <div>
        <h4 class="si-settings__block-title">
          <i v-if="icon" class="fa me-1" :class="icon" aria-hidden="true"></i>{{ title }}
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
