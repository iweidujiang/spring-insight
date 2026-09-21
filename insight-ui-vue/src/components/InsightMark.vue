<script setup lang="ts">
/**
 * 品牌标记：调用弧线汇入被观察的服务节点。
 */
withDefaults(defineProps<{
  /** 边长，单位 px */
  size?: number
}>(), {
  size: 32
})
</script>

<template>
  <svg
    class="si-mark"
    :width="size"
    :height="size"
    viewBox="0 0 32 32"
    fill="none"
    aria-hidden="true"
  >
    <rect width="32" height="32" rx="8" fill="#06221f" />
    <path
      d="M6.5 23.5C9.2 15.2 13.4 12.2 18.2 13"
      stroke="#5eead4"
      stroke-width="1.7"
      stroke-linecap="round"
    />
    <circle cx="7.2" cy="22.6" r="1.7" fill="#5eead4" />
    <circle cx="21.2" cy="14.2" r="6.15" stroke="#2dd4bf" stroke-width="1.7" />
    <circle cx="21.2" cy="14.2" r="2.25" fill="#ccfbf1" />
  </svg>
</template>
