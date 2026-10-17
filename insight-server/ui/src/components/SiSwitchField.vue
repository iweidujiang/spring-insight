<script setup lang="ts">
const props = defineProps<{
  id: string
  label: string
  modelValue: boolean
  /** 放在区块标题右侧时去掉下边距 */
  flush?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

function onChange(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).checked)
}
</script>

<template>
  <div class="form-check form-switch" :class="{ 'mb-0': props.flush }">
    <input
      :id="id"
      class="form-check-input"
      type="checkbox"
      :checked="modelValue"
      @change="onChange"
    />
    <label class="form-check-label" :for="id">{{ label }}</label>
  </div>
</template>
