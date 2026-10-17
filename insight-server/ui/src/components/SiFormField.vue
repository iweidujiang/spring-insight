<script setup lang="ts">
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
</script>

<template>
  <div
    class="si-settings__field si-form-field"
    :class="{
      'si-settings__field--full': span === 'full',
      'si-settings__field--wide': span === 'wide',
      'si-settings__field--narrow': span === 'narrow',
      'si-settings__field--toggles': span === 'toggles'
    }"
  >
    <label v-if="label" class="form-label" :for="fieldId">
      {{ label }}
      <slot name="hint" />
    </label>
    <slot />
    <slot name="note" />
  </div>
</template>

<style scoped>
.form-label {
  font-size: 0.82rem;
  margin-bottom: 0.3rem;
  color: var(--si-ink-soft);
}
</style>
