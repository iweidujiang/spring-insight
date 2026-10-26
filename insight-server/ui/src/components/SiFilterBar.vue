<script setup lang="ts">
import { computed } from 'vue'
import { TIME_RANGE_OPTIONS } from '../utils/timeRange'

const props = withDefaults(
  defineProps<{
    service?: string
    hours: number
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

    <label class="visually-hidden" :for="hoursId">{{ hoursLabel }}</label>
    <el-select
      :id="hoursId"
      v-model="hoursModel"
      class="si-filter-bar__control"
      size="small"
    >
      <el-option
        v-for="opt in TIME_RANGE_OPTIONS"
        :key="opt.value"
        :label="opt.label"
        :value="opt.value"
      />
    </el-select>

    <slot name="leading" />

    <el-button
      v-if="showRefresh"
      type="primary"
      size="small"
      :loading="loading"
      @click="emit('refresh')"
    >
      <i v-if="!loading" class="fa fa-refresh me-1" aria-hidden="true"></i>
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

    <div class="si-filter-bar__field">
      <label class="si-filter-bar__label" :for="hoursId">{{ hoursLabel }}</label>
      <el-select :id="hoursId" v-model="hoursModel" size="default" class="si-filter-bar__select">
        <el-option
          v-for="opt in TIME_RANGE_OPTIONS"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </div>

    <slot name="fields" />

    <slot name="leading" />

    <el-button
      v-if="showRefresh"
      type="primary"
      size="small"
      :loading="loading"
      @click="emit('refresh')"
    >
      <i v-if="!loading" class="fa fa-refresh me-1" aria-hidden="true"></i>
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
