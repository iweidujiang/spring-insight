<script setup lang="ts">
import { computed, useSlots } from 'vue'

withDefaults(
  defineProps<{
    label?: string
    fieldId?: string
    /** 在设置页四列网格里占几列 */
    span?: 'default' | 'full' | 'wide' | 'narrow' | 'toggles'
  }>(),
  {
    span: 'default'
  }
)

const slots = useSlots()
const hasHint = computed(() => !!slots.hint)
</script>

<template>
  <el-form-item
    class="si-settings__field si-form-field"
    :class="{
      'si-settings__field--full': span === 'full',
      'si-settings__field--wide': span === 'wide',
      'si-settings__field--narrow': span === 'narrow',
      'si-settings__field--toggles': span === 'toggles'
    }"
    :label="label && !hasHint ? label : undefined"
  >
    <template v-if="label && hasHint" #label>
      <label v-if="fieldId" class="si-form-field__label" :for="fieldId">
        {{ label }}
        <slot name="hint" />
      </label>
      <span v-else class="si-form-field__label">
        {{ label }}
        <slot name="hint" />
      </span>
    </template>
    <slot />
    <div v-if="$slots.note" class="si-form-field__note">
      <slot name="note" />
    </div>
  </el-form-item>
</template>

<style scoped>
.si-form-field {
  margin-bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

.si-form-field :deep(.el-form-item__label) {
  justify-content: flex-start;
  align-items: center;
  height: auto;
  line-height: 1.3;
  margin-bottom: 0.3rem;
  padding: 0;
  font-size: 0.82rem;
  color: var(--si-ink-soft);
}

.si-form-field :deep(.el-form-item__content) {
  margin-left: 0 !important;
  line-height: normal;
}

.si-form-field__label {
  font-size: inherit;
  font-weight: inherit;
  color: inherit;
  cursor: pointer;
}

.si-form-field__note {
  margin-top: 0.35rem;
  font-size: 0.78rem;
  line-height: 1.4;
  color: var(--si-muted);
}
</style>
