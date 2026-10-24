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

function onUpdate(value: string | number | boolean) {
  emit('update:modelValue', Boolean(value))
}
</script>

<template>
  <div class="si-switch-field" :class="{ 'si-switch-field--flush': props.flush }">
    <el-switch
      :id="id"
      :model-value="modelValue"
      :aria-label="label"
      @update:model-value="onUpdate"
    />
    <label class="si-switch-field__label" :for="id">{{ label }}</label>
  </div>
</template>

<style scoped>
.si-switch-field {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}

.si-switch-field--flush {
  margin-bottom: 0;
}

.si-switch-field__label {
  margin: 0;
  font-size: 0.9rem;
  color: var(--si-ink-soft, #3d524a);
  cursor: pointer;
  user-select: none;
}
</style>
