<template>
  <section class="si-empty-state" aria-label="暂无观测数据">
    <div class="si-empty-state__visual" aria-hidden="true">
      <svg class="si-empty-state__mark" viewBox="0 0 120 72" fill="none">
        <circle cx="22" cy="36" r="8" class="si-empty-state__node" />
        <circle cx="60" cy="20" r="7" class="si-empty-state__node si-empty-state__node--soft" />
        <circle cx="60" cy="52" r="7" class="si-empty-state__node si-empty-state__node--soft" />
        <circle cx="98" cy="36" r="9" class="si-empty-state__node si-empty-state__node--core" />
        <path
          class="si-empty-state__edge"
          d="M30 36 H48 M52 24 L90 34 M52 48 L90 38"
          stroke-dasharray="3 4"
        />
      </svg>
    </div>

    <h3 class="si-empty-state__title">{{ title }}</h3>
    <p class="si-empty-state__lead">{{ lead }}</p>

    <ul class="si-empty-state__hints" aria-label="接入要点">
      <li v-for="hint in hints" :key="hint">{{ hint }}</li>
    </ul>

    <div class="si-empty-state__actions">
      <el-button type="primary" size="small" @click="goAbout">
        查看接入说明
      </el-button>
      <el-button size="small" :loading="refreshing" @click="emit('refresh')">
        <el-icon v-if="!refreshing" class="me-1"><Refresh /></el-icon>
        刷新
      </el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

withDefaults(
  defineProps<{
    /** 父页刷新中 */
    refreshing?: boolean
    title?: string
    lead?: string
  }>(),
  {
    refreshing: false,
    title: '暂无观测数据',
    lead: '监测中心已就绪。业务服务接入并产生请求后，这里会显示链路、拓扑与错误。'
  }
)

const emit = defineEmits<{
  refresh: []
}>()

const router = useRouter()

const hints = ['引入 Agent Starter', '配置 server-url', '访问接口后刷新']

function goAbout() {
  void router.push({ path: '/about', hash: '#setup' })
}
</script>

<style scoped>
.si-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  margin-bottom: 1rem;
  padding: 2.25rem 1.5rem 1.85rem;
  border: 1px dashed var(--card-border);
  border-radius: 12px;
  background:
    radial-gradient(420px 160px at 50% 0%, var(--si-brand-soft), transparent 70%),
    var(--card-bg);
}

.si-empty-state__visual {
  margin-bottom: 1rem;
}

.si-empty-state__mark {
  width: 7.5rem;
  height: 4.5rem;
}

.si-empty-state__node {
  fill: var(--si-brand-soft);
  stroke: var(--si-brand);
  stroke-width: 1.5;
}

.si-empty-state__node--soft {
  opacity: 0.72;
}

.si-empty-state__node--core {
  fill: var(--si-brand);
  stroke: var(--si-brand-bright, var(--si-brand));
}

.si-empty-state__edge {
  stroke: var(--si-brand);
  stroke-width: 1.4;
  fill: none;
  opacity: 0.45;
}

.si-empty-state__title {
  margin: 0 0 0.4rem;
  font-size: 1.15rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--si-ink);
}

.si-empty-state__lead {
  margin: 0;
  max-width: 28rem;
  font-size: 0.9rem;
  line-height: 1.55;
  color: var(--si-muted);
}

.si-empty-state__hints {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.4rem;
  margin: 1.15rem 0 0;
  padding: 0;
  list-style: none;
}

.si-empty-state__hints li {
  padding: 0.28rem 0.7rem;
  border-radius: 999px;
  border: 1px solid var(--si-brand-divider, rgba(0, 184, 212, 0.18));
  background: var(--si-surface-2, transparent);
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--si-ink-soft);
}

.si-empty-state__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.55rem;
  margin-top: 1.25rem;
}

@media (max-width: 640px) {
  .si-empty-state {
    padding: 1.75rem 1rem 1.5rem;
  }

  .si-empty-state__actions {
    width: 100%;
  }

  .si-empty-state__actions :deep(.el-button) {
    flex: 1 1 auto;
  }
}
</style>
