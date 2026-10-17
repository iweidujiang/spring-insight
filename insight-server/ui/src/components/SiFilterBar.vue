<script setup lang="ts">
import { computed } from 'vue'
import { TIME_RANGE_OPTIONS } from '../utils/timeRange'

const props = withDefaults(
  defineProps<{
    service: string
    hours: number
    services?: readonly string[]
    loading?: boolean
    showService?: boolean
    serviceId?: string
    hoursId?: string
    serviceLabel?: string
    hoursLabel?: string
    allServicesLabel?: string
  }>(),
  {
    services: () => [],
    loading: false,
    showService: true,
    serviceId: 'si-filter-service',
    hoursId: 'si-filter-hours',
    serviceLabel: '业务服务',
    hoursLabel: '时间范围',
    allServicesLabel: '全部服务'
  }
)

const emit = defineEmits<{
  'update:service': [value: string]
  'update:hours': [value: number]
  serviceChange: [value: string]
  hoursChange: [value: number]
  refresh: []
}>()

const serviceModel = computed({
  get: () => props.service,
  set: (value: string) => {
    emit('update:service', value)
    emit('serviceChange', value)
  }
})

const hoursModel = computed({
  get: () => props.hours,
  set: (value: number) => {
    emit('update:hours', value)
    emit('hoursChange', value)
  }
})
</script>

<template>
  <div class="si-page__toolbar si-filter-bar">
    <template v-if="showService">
      <label class="form-label mb-0 visually-hidden" :for="serviceId">{{ serviceLabel }}</label>
      <select
        :id="serviceId"
        v-model="serviceModel"
        class="form-select form-select-sm si-filter-bar__control"
      >
        <option value="">{{ allServicesLabel }}</option>
        <option v-for="name in services" :key="name" :value="name">{{ name }}</option>
      </select>
    </template>

    <label class="form-label mb-0 visually-hidden" :for="hoursId">{{ hoursLabel }}</label>
    <select
      :id="hoursId"
      v-model.number="hoursModel"
      class="form-select form-select-sm si-filter-bar__control"
    >
      <option v-for="opt in TIME_RANGE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
    </select>

    <slot name="leading" />

    <button type="button" class="btn btn-sm btn-primary" :disabled="loading" @click="emit('refresh')">
      <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i>
      刷新
    </button>

    <slot />
  </div>
</template>

<style scoped>
.si-filter-bar__control {
  width: auto;
  min-width: 7.5rem;
}
</style>
