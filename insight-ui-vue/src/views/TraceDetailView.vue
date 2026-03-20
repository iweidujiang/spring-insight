<template>
  <div class="fade-in trace-detail-page">
    <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
      <div>
        <h2 class="page-title">
          <i class="fa fa-sitemap me-2"></i>链路详情
        </h2>
        <p class="page-description text-truncate" style="max-width: 90vw">
          <code class="text-cyan">{{ traceId }}</code>
        </p>
      </div>
      <div class="d-flex gap-2">
        <button class="btn btn-outline-secondary" type="button" @click="goBack">
          <i class="fa fa-arrow-left me-1"></i>返回
        </button>
        <button class="btn btn-primary" type="button" @click="load" :disabled="loading">
          <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i> 刷新
        </button>
      </div>
    </div>

    <div v-if="loading" class="loading-spinner">
      <i class="fa fa-spinner fa-spin"></i>
      <span class="ms-2">加载中...</span>
    </div>

    <div v-else class="card stat-card">
      <div class="card-body">
        <h5 class="card-title mb-3"><i class="fa fa-list me-2"></i>Span 列表（{{ spans.length }}）</h5>
        <div class="table-responsive">
          <table class="table table-hover table-dark-glass mb-0">
            <thead>
              <tr>
                <th>spanId</th>
                <th>operation</th>
                <th>kind</th>
                <th>耗时(ms)</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(s, i) in spans" :key="s.spanId || i">
                <td><code>{{ s.spanId }}</code></td>
                <td class="text-truncate" style="max-width: 240px">{{ s.operationName }}</td>
                <td>{{ s.spanKind }}</td>
                <td>{{ s.durationMs }}</td>
                <td>
                  <span class="badge" :class="s.statusCode === 'OK' ? 'bg-success' : 'bg-danger'">{{ s.statusCode }}</span>
                </td>
              </tr>
              <tr v-if="spans.length === 0">
                <td colspan="5" class="text-center text-muted py-4">暂无数据</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiService } from '../services/ApiService'

const route = useRoute()
const router = useRouter()
const traceId = ref('')
const spans = ref<any[]>([])
const loading = ref(true)

const load = async () => {
  const id = String(route.params.traceId || '')
  traceId.value = id
  if (!id) {
    spans.value = []
    loading.value = false
    return
  }
  loading.value = true
  try {
    spans.value = await ApiService.getTraceDetail(id)
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  router.push('/traces')
}

watch(() => route.params.traceId, () => load())

onMounted(() => load())
</script>

<style scoped>
.text-cyan {
  color: var(--si-cyan, #22d3ee);
}
.table-dark-glass {
  --bs-table-bg: rgba(15, 23, 42, 0.45);
  color: #e2e8f0;
}
</style>
