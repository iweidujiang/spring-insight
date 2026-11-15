<script setup lang="ts">
import { computed } from 'vue'
import type { ObservationWindow } from '../utils/insightQuery'
import SiTimeRange from './SiTimeRange.vue'

const props = withDefaults(
  defineProps<{
    service?: string
    /** @deprecated 请用 window；保留以兼容旧绑定 */
    hours?: number
    window?: ObservationWindow
    services?: readonly string[]
    loading?: boolean
    showService?: boolean
    showRefresh?: boolean
    /** compact：页头右侧，标签对读屏隐藏。fields：多条件贴在内容上方，标签可见 */
    layout?: 'compact' | 'fields'
    serviceId?: string
    hoursId?: string
    serviceLabel?: string
    hoursLabel?: string
    allServicesLabel?: string
  }>(),
  {
    service: '',
    services: () => [],
    loading: false,
    showService: true,
    showRefresh: true,
    layout: 'compact',
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
  'update:window': [value: ObservationWindow]
  serviceChange: [value: string]
  hoursChange: [value: number]
  windowChange: [value: ObservationWindow]
  refresh: []
}>()

const serviceModel = computed({
  get: () => props.service,
  set: (value: string) => {
    emit('update:service', value)
    emit('serviceChange', value)
  }
})

const windowModel = computed({
  get: (): ObservationWindow => {
    if (props.window) return props.window
    return { kind: 'rolling', hours: props.hours ?? 24 }
  },
  set: (value: ObservationWindow) => {
    emit('update:window', value)
    emit('windowChange', value)
    if (value.kind === 'rolling') {
      emit('update:hours', value.hours)
      emit('hoursChange', value.hours)
    }
  }
})

const onWindowChange = (value: ObservationWindow) => {
  windowModel.value = value
}

const selectSize = computed(() => (props.layout === 'fields' ? 'default' : 'small'))
</script>

<template>
  <div v-if="layout === 'compact'" class="si-page__toolbar si-filter-bar">
    <template v-if="showService">
      <label class="visually-hidden" :for="serviceId">{{ serviceLabel }}</label>
      <el-select
        :id="serviceId"
        v-model="serviceModel"
        class="si-filter-bar__control"
        size="small"
        filterable
        :placeholder="allServicesLabel"
      >
        <el-option :label="allServicesLabel" value="" />
        <el-option v-for="name in services" :key="name" :label="name" :value="name" />
      </el-select>
    </template>

    <SiTimeRange
      v-model="windowModel"
      :hours-id="hoursId"
      :label="hoursLabel"
      layout="compact"
      size="small"
      @change="onWindowChange"
    />

    <slot name="leading" />

    <el-button
      v-if="showRefresh"
      type="primary"
      size="small"
      :loading="loading"
      @click="emit('refresh')"
    >
      <el-icon v-if="!loading" class="me-1"><Refresh /></el-icon>
      刷新
    </el-button>

    <slot />
  </div>

  <div v-else class="si-filter-bar si-filter-bar--fields">
    <div v-if="showService" class="si-filter-bar__field">
      <label class="si-filter-bar__label" :for="serviceId">{{ serviceLabel }}</label>
      <el-select
        :id="serviceId"
        v-model="serviceModel"
        size="default"
        filterable
        :placeholder="allServicesLabel"
        class="si-filter-bar__select"
      >
        <el-option :label="allServicesLabel" value="" />
        <el-option v-for="name in services" :key="name" :label="name" :value="name" />
      </el-select>
    </div>

    <SiTimeRange
      v-model="windowModel"
      :hours-id="hoursId"
      :label="hoursLabel"
      layout="fields"
      :size="selectSize"
      @change="onWindowChange"
    />

    <slot name="fields" />

    <slot name="leading" />

    <el-button
      v-if="showRefresh"
      type="primary"
      size="small"
      :loading="loading"
      @click="emit('refresh')"
    >
      <el-icon v-if="!loading" class="me-1"><Refresh /></el-icon>
      刷新
    </el-button>

    <slot />
  </div>
</template>

<style scoped>
.si-filter-bar__control {
  width: auto;
  min-width: 7.5rem;
}

.si-filter-bar__control :deep(.el-select__wrapper) {
  min-width: 7.5rem;
}

.si-filter-bar--fields {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 0.75rem 1rem;
  padding: 1rem 1.25rem;
  background: var(--card-bg);
  border-radius: 10px;
  box-shadow: var(--box-shadow);
}

.si-filter-bar__field,
.si-filter-bar--fields :deep(.si-filter-bar__field) {
  display: flex;
  flex-direction: column;
  flex: 1 1 9.5rem;
  min-width: 8.5rem;
  max-width: 14rem;
}

.si-filter-bar--fields :deep(.si-filter-bar__field--narrow) {
  flex: 0 1 6.5rem;
  min-width: 5.5rem;
  max-width: 7rem;
}

.si-filter-bar--fields :deep(.si-filter-bar__field--search) {
  flex: 2 1 18rem;
  max-width: 32rem;
}

.si-filter-bar__label,
.si-filter-bar--fields :deep(.si-filter-bar__label) {
  margin-bottom: 0.2rem;
  font-size: 0.75rem;
  color: var(--si-muted);
}

.si-filter-bar__select {
  width: 100%;
}
</style>
