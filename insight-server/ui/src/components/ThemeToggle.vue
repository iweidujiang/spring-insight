<template>
  <button
    type="button"
    class="si-theme-toggle"
    :title="tooltip"
    aria-label="切换主题"
    @click="cycleTheme"
  >
    <span class="si-theme-toggle__icon">
      <i v-if="effectiveTheme === 'light'" class="fa fa-sun-o"></i>
      <i v-else class="fa fa-moon-o"></i>
    </span>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useTheme } from '../composables/useTheme'

const { theme, effectiveTheme, cycleTheme } = useTheme()

const modeLabel: Record<string, string> = {
  light: '亮色',
  dark: '暗色',
  system: '跟随系统'
}

const tooltip = computed(() => `当前：${modeLabel[theme.value]}（点击切换）`)
</script>
