<script setup lang="ts">
/**
 * 品牌标记：服务节点 + 链路脉冲，表达分布式追踪与监控。
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
    <rect width="32" height="32" rx="6" fill="#042f2e" />
    <circle cx="16" cy="16" r="9.25" fill="#2dd4bf" />
    <path
      d="M 4.25 16 L 10.5 16 C 11.75 16, 12.25 12.5, 13.25 12.5 C 14.125 12.5, 14.625 19.5, 16 19.5 C 17.375 19.5, 17.875 12.5, 18.75 12.5 C 19.75 12.5, 20.25 16, 21.5 16 L 27.75 16"
      stroke="#99f6e4"
      stroke-width="1.4"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <circle cx="4.25" cy="16" r="1.625" fill="#99f6e4" />
    <circle cx="27.75" cy="16" r="1.625" fill="#99f6e4" />
  </svg>
</template>
