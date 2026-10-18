<template>
  <div class="si-page fade-in si-about">
    <SiPageHeader class="mb-1" title="关于" icon="fa-info-circle">
      <span class="badge bg-info">
        <i class="fa fa-clock me-1"></i>{{ currentTime }}
      </span>
    </SiPageHeader>

    <section class="si-about__sheet" aria-label="项目与运行">
      <div class="si-about__identity">
        <div class="si-about__lead">
          <h3 class="si-about__name">Spring Insight</h3>
          <p class="si-about__desc">面向 Spring 微服务的轻量分布式监测中心（Agent + Server）</p>
        </div>
        <div class="si-about__versions">
          <p class="si-about__ver si-about__ver--lead">
            <span class="si-about__ver-value">{{ serverVersion }}</span>
            <span class="si-about__ver-name">Server</span>
          </p>
          <p class="si-about__ver">
            <span class="si-about__ver-value">{{ agentVersion }}</span>
            <span class="si-about__ver-name">Agent</span>
          </p>
        </div>
      </div>
      <dl class="si-about__facts">
        <div>
          <dd class="si-about__num">9966</dd>
          <dt>默认端口</dt>
        </div>
        <div>
          <dd>{{ storageMode || '—' }}</dd>
          <dt>存储模式</dt>
        </div>
        <div>
          <dd>Vue 3 + TypeScript + Vite</dd>
          <dt>界面</dt>
        </div>
        <div>
          <dd>{{ buildTime }}</dd>
          <dt>构建/运行</dt>
        </div>
      </dl>
      <div class="si-about__links">
        <a href="https://github.com/iweidujiang/spring-insight" target="_blank" rel="noopener noreferrer">
          <i class="fa fa-github me-1"></i>iweidujiang/spring-insight
        </a>
        <a href="/v3/api-docs" target="_blank" rel="noopener noreferrer">/v3/api-docs</a>
        <a href="/swagger-ui.html" target="_blank" rel="noopener noreferrer">Swagger UI</a>
      </div>
      <div class="si-about__tags">
        <span>Spring Boot 3.5</span>
        <span>Java 17+</span>
        <span>Vue 3</span>
        <span>TypeScript</span>
        <span>ECharts</span>
      </div>
    </section>

    <section class="si-about__demo" aria-label="演示路径">
      <div class="si-about__demo-head">
        <h5 class="mb-0"><i class="fa fa-play-circle me-2"></i>三分钟演示路径</h5>
        <span class="si-about__demo-sub">配套 Agent 开启 JDBC / Redis 追踪更完整</span>
      </div>
      <ol class="si-about__demo-list">
        <li v-for="(step, i) in demoSteps" :key="i">
          <RouterLink v-if="step.to" :to="step.to" class="si-about__demo-link">{{ step.label }}</RouterLink>
          <strong v-else>{{ step.label }}</strong>
          <span>{{ step.hint }}</span>
        </li>
      </ol>
<!--      <p class="si-about__demo-note">
        冒烟建议：首页 KPI 有数 → 拓扑有服务边 → 数据库 / 缓存页有 Span（或空态提示开关）→
        打开一条 Trace 可「排障包」下载 → 设置页 AI 状态条可看就绪 / 原因。
      </p>-->
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { RouterLink } from 'vue-router'
import axios from 'axios'
import SiPageHeader from '../components/SiPageHeader.vue'

const currentTime = ref('')
const buildTime = ref('')
const serverVersion = ref('…')
const agentVersion = ref('…')
const storageMode = ref('')
let timeInterval: number | null = null

const demoSteps = [
  { to: '/', label: '仪表盘', hint: '看 KPI、时段摘要、库/缓存卡片与最近请求' },
  { to: '/topology', label: '拓扑图', hint: '确认服务依赖边' },
  { to: '/jdbc', label: '数据库', hint: '开启 jdbc-tracing-enabled 后看慢 SQL / 错误' },
  { to: '/redis', label: '缓存', hint: '开启 redis-tracing-enabled 后看慢命令 / 错误' },
  { to: '/traces', label: '链路详情', hint: '打开一条 Trace →「排障包」下载脱敏 JSON' },
  { to: '/settings', label: '设置 · AI', hint: '看就绪态 / 模型 / 失败原因；未配齐时按钮灰显有提示' }
]

const updateCurrentTime = () => {
  currentTime.value = new Date().toTimeString().split(' ')[0]
}

/**
 * 从 health 读取 Server / 配套 Agent 版本（避免前端硬编码）。
 */
async function loadServerMeta() {
  try {
    const { data } = await axios.get('/api/v1/health', { timeout: 8000 })
    serverVersion.value = data?.serverVersion || data?.version || 'unknown'
    agentVersion.value = data?.agentVersion || 'unknown'
    storageMode.value = data?.storageMode || ''
  } catch {
    serverVersion.value = 'unknown'
    agentVersion.value = 'unknown'
  }
}

onMounted(() => {
  buildTime.value = new Date().toLocaleString('zh-CN')
  updateCurrentTime()
  timeInterval = window.setInterval(updateCurrentTime, 1000)
  loadServerMeta()
})

onUnmounted(() => {
  if (timeInterval) clearInterval(timeInterval)
})
</script>

<style scoped>
.si-about__sheet {
  padding: 0.95rem 1.1rem 1rem;
  border-radius: 10px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
}

.si-about__identity {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem 1.75rem;
}

.si-about__lead {
  min-width: 0;
}

.si-about__name {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.45rem;
  font-weight: 600;
  color: var(--si-ink);
  line-height: 1.15;
}

.si-about__desc {
  margin: 0.35rem 0 0;
  max-width: 36rem;
  font-size: 0.92rem;
  line-height: 1.45;
  color: var(--si-ink-soft);
}

.si-about__versions {
  display: flex;
  align-items: baseline;
  gap: 1.35rem;
  flex: 0 0 auto;
}

.si-about__ver {
  display: inline-flex;
  align-items: baseline;
  gap: 0.4rem;
  margin: 0;
}

.si-about__ver-value {
  font-family: var(--font-numeric);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  font-size: 1.15rem;
  line-height: 1;
  letter-spacing: -0.03em;
  color: var(--si-ink);
}

.si-about__ver--lead .si-about__ver-value {
  font-size: clamp(1.7rem, 2.4vw, 2.15rem);
}

.si-about__ver-name {
  font-size: 0.84rem;
  font-weight: 600;
  color: var(--si-ink-soft);
}

.si-about__ver--lead .si-about__ver-name {
  font-size: 0.95rem;
  color: var(--si-ink);
}

.si-about__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem 1.5rem;
  margin: 0.9rem 0 0;
  padding-top: 0.8rem;
  border-top: 1px solid var(--card-border);
}

.si-about__facts > div {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
}

.si-about__facts dd {
  margin: 0;
  font-size: 0.92rem;
  font-weight: 600;
  color: var(--si-ink);
}

.si-about__num {
  font-family: var(--font-numeric);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.si-about__facts dt {
  margin: 0;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--si-ink-soft);
}

.si-about__links {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 1rem;
  margin-top: 0.8rem;
}

.si-about__links a,
.si-about__demo-link {
  color: var(--si-teal);
  text-decoration: none;
}

.si-about__links a:hover,
.si-about__demo-link:hover {
  text-decoration: underline;
}

.si-about__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin-top: 0.75rem;
}

.si-about__tags span {
  display: inline-block;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--si-forest);
  background: var(--si-teal-soft);
  border: 1px solid rgba(15, 118, 110, 0.25);
}

@media (max-width: 767px) {
  .si-about__identity {
    flex-direction: column;
    align-items: flex-start;
  }
}

.si-about__demo {
  margin-top: 0.75rem;
  padding: 1rem 1.1rem 1.15rem;
  border-radius: 14px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
}

.si-about__demo-list {
  margin: 0;
  padding-left: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  color: var(--si-ink);
  font-size: 0.9rem;
  line-height: 1.45;
}

.si-about__demo-list li {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 0.65rem;
  align-items: baseline;
}

.si-about__demo-list li > span {
  flex: 1 1 12rem;
  color: var(--si-muted);
  font-size: 0.82rem;
}

.si-about__demo-link {
  font-weight: 650;
  color: var(--si-teal);
  text-decoration: none;
}

.si-about__demo-link:hover {
  text-decoration: underline;
}

.si-about__demo-note {
  margin: 0;
  font-size: 0.8rem;
  line-height: 1.5;
  color: var(--si-muted);
}

.si-about__demo-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.85rem;
  color: var(--si-ink);
}

.si-about__demo-sub {
  font-size: 0.75rem;
  color: var(--si-muted);
}
</style>
