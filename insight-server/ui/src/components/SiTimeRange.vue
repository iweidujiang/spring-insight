<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ObservationWindow } from '../utils/insightQuery'
import {
  CUSTOM_TIME_RANGE_VALUE,
  TIME_RANGE_OPTIONS
} from '../utils/timeRange'

const props = withDefaults(
  defineProps<{
    modelValue: ObservationWindow
    size?: 'small' | 'default'
    hoursId?: string
    label?: string
    /** compact：标签对读屏隐藏；fields：可见标签 */
    layout?: 'compact' | 'fields'
  }>(),
  {
    size: 'small',
    hoursId: 'si-time-range',
    label: '时间范围',
    layout: 'compact'
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: ObservationWindow]
  change: [value: ObservationWindow]
}>()

const CUSTOM = CUSTOM_TIME_RANGE_VALUE

const selectValue = computed({
  get: () => (props.modelValue.kind === 'absolute' ? CUSTOM : props.modelValue.hours),
  set: (value: number) => {
    if (value === CUSTOM) {
      const now = Date.now()
      const fromMs =
        props.modelValue.kind === 'absolute'
          ? props.modelValue.fromMs
          : now - 24 * 3_600_000
      const toMs =
        props.modelValue.kind === 'absolute' ? props.modelValue.toMs : now
      const next: ObservationWindow = { kind: 'absolute', fromMs, toMs }
      emit('update:modelValue', next)
      emit('change', next)
      return
    }
    const next: ObservationWindow = { kind: 'rolling', hours: value }
    emit('update:modelValue', next)
    emit('change', next)
  }
})

const rangeModel = ref<[Date, Date] | null>(null)

watch(
  () => props.modelValue,
  (w) => {
    if (w.kind === 'absolute') {
      rangeModel.value = [new Date(w.fromMs), new Date(w.toMs)]
    } else {
      rangeModel.value = null
    }
  },
  { immediate: true }
)

const onRangeChange = (val: [Date, Date] | null) => {
  if (!val || !val[0] || !val[1]) return
  const fromMs = val[0].getTime()
  const toMs = val[1].getTime()
  if (!(toMs > fromMs)) return
  const next: ObservationWindow = { kind: 'absolute', fromMs, toMs }
  emit('update:modelValue', next)
  emit('change', next)
}
</script>

<template>
  <div
    class="si-time-range"
    :class="{
      'si-time-range--compact': layout === 'compact',
      'si-time-range--fields': layout === 'fields'
    }"
  >
    <div class="si-time-range__field">
      <label
        :class="layout === 'fields' ? 'si-time-range__label' : 'visually-hidden'"
        :for="hoursId"
      >{{ label }}</label>
      <el-select
        :id="hoursId"
        v-model="selectValue"
        class="si-time-range__select"
        :size="size"
      >
        <el-option
          v-for="opt in TIME_RANGE_OPTIONS"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </div>

    <div v-if="modelValue.kind === 'absolute'" class="si-time-range__field si-time-range__field--wide">
      <label
        v-if="layout === 'fields'"
        class="si-time-range__label"
        :for="hoursId + '-abs'"
      >起止时间</label>
      <el-date-picker
        :id="hoursId + '-abs'"
        v-model="rangeModel"
        class="si-time-range__picker"
        type="datetimerange"
        :size="size"
        range-separator="至"
        start-placeholder="开始"
        end-placeholder="结束"
        format="MM-DD HH:mm"
        value-format=""
        :clearable="false"
        @change="onRangeChange"
      />
    </div>
  </div>
</template>

<style scoped>
.si-time-range--compact {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.45rem;
}

.si-time-range--fields {
  display: contents;
}

.si-time-range--fields .si-time-range__field {
  display: flex;
  flex-direction: column;
  flex: 1 1 9.5rem;
  min-width: 8.5rem;
  max-width: 14rem;
}

.si-time-range--fields .si-time-range__field--wide {
  flex: 2 1 18rem;
  max-width: 28rem;
}

.si-time-range__label {
  margin-bottom: 0.2rem;
  font-size: 0.75rem;
  color: var(--si-muted);
}

.si-time-range__select {
  width: 100%;
  min-width: 7.5rem;
}

.si-time-range--compact .si-time-range__select {
  width: auto;
}

.si-time-range--compact .si-time-range__select :deep(.el-select__wrapper) {
  min-width: 7.5rem;
}

.si-time-range__picker {
  width: 100%;
}

.si-time-range--compact .si-time-range__picker {
  width: 17.5rem;
  max-width: min(17.5rem, 72vw);
}
</style>
