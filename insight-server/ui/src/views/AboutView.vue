<template>
  <div class="si-page fade-in si-about">
    <SiPageHeader class="mb-1" title="关于" icon="fa-info-circle">
      <span class="badge bg-info">
        <i class="fa fa-clock me-1"></i>{{ currentTime }}
      </span>
    </SiPageHeader>

    <section class="si-about__sheet" aria-label="项目与运行">
      <div class="si-about__project">
        <h3 class="si-about__name">Spring Insight</h3>
        <p class="si-about__desc">面向 Spring 微服务的轻量分布式监测中心（Agent + Server）</p>
        <ul class="si-about__tags" aria-label="技术栈">
          <li>Spring Boot 3.5</li>
          <li>Java 17+</li>
          <li>Vue 3</li>
          <li>TypeScript</li>
          <li>ECharts</li>
        </ul>
        <a
          class="si-about__github"
          href="https://github.com/iweidujiang/spring-insight"
          target="_blank"
          rel="noopener noreferrer"
        >
          <i class="fa fa-github" aria-hidden="true"></i>
          iweidujiang/spring-insight
        </a>
      </div>
      <aside class="si-about__runtime" aria-label="运行">
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
        <dl class="si-about__facts">
          <div>
            <dt>默认端口</dt>
            <dd class="si-about__num">9966</dd>
          </div>
          <div>
            <dt>存储模式</dt>
            <dd>{{ storageMode || '—' }}</dd>
          </div>
          <div>
            <dt>界面</dt>
            <dd>Vue 3 + TypeScript + Vite</dd>
          </div>
          <div>
            <dt>构建/运行</dt>
            <dd>{{ buildTime }}</dd>
          </div>
        </dl>
        <div class="si-about__links">
          <a href="/v3/api-docs" target="_blank" rel="noopener noreferrer">/v3/api-docs</a>
          <a href="/swagger-ui.html" target="_blank" rel="noopener noreferrer">Swagger UI</a>
        </div>
      </aside>
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
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(16.5rem, 0.9fr);
  border-radius: 10px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
  overflow: hidden;
}

.si-about__project {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 1.45rem 1.6rem 1.4rem;
}

.si-about__name {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.75rem, 2.5vw, 2.1rem);
  font-weight: 600;
  color: var(--si-ink);
  line-height: 1.1;
}

.si-about__desc {
  margin: 0.7rem 0 0;
  max-width: 32rem;
  font-size: 1rem;
  line-height: 1.55;
  color: var(--si-ink-soft);
}

.si-about__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin: 1.2rem 0 0;
  padding: 0;
  list-style: none;
}

.si-about__tags li {
  padding: 0.18rem 0.55rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--si-forest);
  background: var(--si-teal-soft);
  border: 1px solid rgba(15, 118, 110, 0.25);
}

.si-about__github {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: auto;
  padding-top: 1.35rem;
  font-weight: 600;
  color: var(--si-teal);
  text-decoration: none;
}

.si-about__github:hover {
  text-decoration: underline;
}

.si-about__runtime {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  min-width: 0;
  padding: 1.3rem 1.35rem 1.2rem;
  border-left: 1px solid var(--card-border);
  background: var(--si-paper);
}

.si-about__versions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.4rem;
}

.si-about__ver {
  display: inline-flex;
  align-items: baseline;
  gap: 0.45rem;
  margin: 0;
}

.si-about__ver-value {
  font-family: var(--font-numeric);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  font-size: 1.2rem;
  line-height: 1;
  letter-spacing: -0.03em;
  color: var(--si-ink);
}

.si-about__ver--lead .si-about__ver-value {
  font-size: clamp(1.85rem, 2.6vw, 2.25rem);
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
  flex-direction: column;
  margin: 0;
}

.si-about__facts > div {
  display: grid;
  grid-template-columns: 5.25rem minmax(0, 1fr);
  gap: 0.6rem;
  align-items: baseline;
  padding: 0.45rem 0;
  border-top: 1px solid var(--card-border);
}

.si-about__facts dt {
  margin: 0;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--si-ink-soft);
}

.si-about__facts dd {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
  line-height: 1.35;
  text-align: right;
  color: var(--si-ink);
}

.si-about__num {
  font-family: var(--font-numeric);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.si-about__links {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 0.9rem;
  margin-top: auto;
  padding-top: 0.15rem;
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

@media (max-width: 800px) {
  .si-about__sheet {
    grid-template-columns: 1fr;
  }

  .si-about__runtime {
    border-left: 0;
    border-top: 1px solid var(--card-border);
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
