<template>
  <div class="si-page fade-in si-about">
    <div class="si-page__header mb-1">
      <div>
        <h2 class="page-title mb-1">
          <i class="fa fa-info-circle me-2"></i>关于
        </h2>
        <p class="page-description mb-0">
          基本信息与演示路径 · 推荐配套 Agent {{ agentVersion }}
        </p>
      </div>
      <span class="badge bg-info">
        <i class="fa fa-clock me-1"></i>{{ currentTime }}
      </span>
    </div>

    <div class="si-about__info">
      <div class="card stat-card si-about__card">
        <div class="card-body">
          <h5 class="card-title"><i class="fa fa-cube me-2"></i>项目信息</h5>
          <dl class="si-about__dl">
            <div><dt>项目名称</dt><dd>Spring Insight</dd></div>
            <div>
              <dt>版本</dt>
              <dd class="si-about__versions">
                <span class="si-about__ver"><em>Server</em>{{ serverVersion }}</span>
                <span class="si-about__ver"><em>Agent</em>{{ agentVersion }}</span>
              </dd>
            </div>
            <div><dt>描述</dt><dd>面向 Spring 微服务的轻量分布式监测中心（Agent + Server）</dd></div>
            <div>
              <dt>技术栈</dt>
              <dd class="si-about__tags">
                <span>Spring Boot 3.5</span>
                <span>Java 17+</span>
                <span>Vue 3</span>
                <span>TypeScript</span>
                <span>ECharts</span>
              </dd>
            </div>
          </dl>
        </div>
      </div>
      <div class="card stat-card si-about__card">
        <div class="card-body">
          <h5 class="card-title"><i class="fa fa-server me-2"></i>系统信息</h5>
          <dl class="si-about__dl">
            <div><dt>UI</dt><dd>Vue 3 + TypeScript + Vite</dd></div>
            <div><dt>默认端口</dt><dd>9966</dd></div>
            <div><dt>存储模式</dt><dd>{{ storageMode || '—' }}</dd></div>
            <div><dt>构建/运行</dt><dd>{{ buildTime }}</dd></div>
            <div>
              <dt>GitHub</dt>
              <dd>
                <a href="https://github.com/iweidujiang/spring-insight" target="_blank" rel="noopener noreferrer">
                  <i class="fa fa-github me-1"></i>iweidujiang/spring-insight
                </a>
              </dd>
            </div>
            <div>
              <dt>OpenAPI</dt>
              <dd class="si-about__openapi">
                <a href="/v3/api-docs" target="_blank" rel="noopener noreferrer">/v3/api-docs</a>
                <span class="text-muted">·</span>
                <a href="/swagger-ui.html" target="_blank" rel="noopener noreferrer">Swagger UI</a>
              </dd>
            </div>
          </dl>
        </div>
      </div>
    </div>

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
.si-about__info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}

@media (max-width: 767px) {
  .si-about__info {
    grid-template-columns: 1fr;
  }
}

.si-about__card {
  margin-bottom: 0 !important;
  height: 100%;
}

.si-about__dl {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.si-about__dl > div {
  display: grid;
  grid-template-columns: 5.5rem 1fr;
  gap: 0.5rem;
  align-items: start;
}

.si-about__dl dt {
  margin: 0;
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--si-muted);
}

.si-about__dl dd {
  margin: 0;
  color: var(--si-ink);
  font-size: 0.9rem;
  line-height: 1.45;
}

.si-about__versions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.si-about__ver {
  display: inline-flex;
  align-items: baseline;
  gap: 0.35rem;
  padding: 0.2rem 0.55rem;
  border-radius: 8px;
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--si-ink);
  background: var(--si-paper);
  border: 1px solid var(--card-border);
}

.si-about__ver em {
  font-style: normal;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--si-teal);
}

.si-about__dl a {
  color: var(--si-teal);
  text-decoration: none;
}

.si-about__dl a:hover {
  text-decoration: underline;
}

.si-about__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
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
