<template>
  <div class="si-page fade-in si-about">
    <SiPageHeader class="si-about__header" title="关于" icon="InfoFilled">
      <el-tag type="info" effect="plain" size="small" round>
        <el-icon class="si-about__icon--me-1"><Clock /></el-icon>{{ currentTime }}
      </el-tag>
    </SiPageHeader>

    <div class="si-about__info">
      <el-card class="si-about__card" shadow="never" aria-label="项目信息">
        <template #header>
          <h3 class="si-about__card-title"><el-icon><Box /></el-icon>项目信息</h3>
        </template>
        <dl class="si-about__dl">
          <div>
            <dt>项目名称</dt>
            <dd>Spring Insight</dd>
          </div>
          <div>
            <dt>版本</dt>
            <dd class="si-about__versions">
              <span class="si-about__ver"><b>{{ serverVersion }}</b><em>Server</em></span>
              <span class="si-about__ver"><b>{{ agentVersion }}</b><em>Agent</em></span>
            </dd>
          </div>
          <div>
            <dt>描述</dt>
            <dd>面向 Spring 微服务的轻量分布式监测中心（Agent + Server）</dd>
          </div>
          <div>
            <dt>技术栈</dt>
            <dd>
              <ul class="si-about__tags">
                <li v-for="tag in techTags" :key="tag">
                  <el-tag size="small" effect="plain" type="success" round>{{ tag }}</el-tag>
                </li>
              </ul>
            </dd>
          </div>
        </dl>
      </el-card>
      <el-card class="si-about__card" shadow="never" aria-label="系统信息">
        <template #header>
          <h3 class="si-about__card-title"><el-icon><Monitor /></el-icon>系统信息</h3>
        </template>
        <dl class="si-about__dl">
          <div>
            <dt>界面</dt>
            <dd>Vue 3 + TypeScript + Vite</dd>
          </div>
          <div>
            <dt>默认端口</dt>
            <dd class="si-about__num">9966</dd>
          </div>
          <div>
            <dt>存储模式</dt>
            <dd>{{ storageMode || '—' }}</dd>
          </div>
          <div>
            <dt>构建时间</dt>
            <dd>{{ buildTime }}</dd>
          </div>
          <div>
            <dt>GitHub</dt>
            <dd>
              <a href="https://github.com/iweidujiang/spring-insight" target="_blank" rel="noopener noreferrer">
                <el-icon class="si-about__icon--me-1"><Link /></el-icon>iweidujiang/spring-insight
              </a>
            </dd>
          </div>
          <div>
            <dt>接口</dt>
            <dd class="si-about__links">
              <a href="/v3/api-docs" target="_blank" rel="noopener noreferrer">/v3/api-docs</a>
              <a href="/swagger-ui.html" target="_blank" rel="noopener noreferrer">Swagger UI</a>
            </dd>
          </div>
        </dl>
      </el-card>
    </div>

    <el-card class="si-about__demo" shadow="never" aria-label="演示路径">
      <template #header>
        <div class="si-about__demo-head">
          <h5 class="si-about__demo-title"><el-icon class="si-about__icon--me-2"><VideoPlay /></el-icon>三分钟演示路径</h5>
          <span class="si-about__demo-sub">配套 Agent 开启 JDBC / Redis 追踪更完整</span>
        </div>
      </template>
      <ol class="si-about__demo-list">
        <li v-for="(step, i) in demoSteps" :key="i">
          <RouterLink v-if="step.to" :to="step.to" class="si-about__demo-link">{{ step.label }}</RouterLink>
          <strong v-else>{{ step.label }}</strong>
          <span>{{ step.hint }}</span>
        </li>
      </ol>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { RouterLink } from 'vue-router'
import { Clock, Box, Monitor, Link, VideoPlay } from '@element-plus/icons-vue'
import axios from 'axios'
import SiPageHeader from '../components/SiPageHeader.vue'

const currentTime = ref('')
const buildTime = ref('')
const serverVersion = ref('…')
const agentVersion = ref('…')
const storageMode = ref('')
let timeInterval: number | null = null

const techTags = ['Spring Boot 3.5', 'Java 17+', 'Vue 3', 'TypeScript', 'ECharts']

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
.si-about__header {
  margin-bottom: 0.5rem;
}

.si-about__icon--me-1 {
  margin-right: 0.25rem;
}

.si-about__icon--me-2 {
  margin-right: 0.5rem;
}

.si-about__info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
  align-items: stretch;
}

.si-about__card {
  margin: 0;
  border-radius: 10px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
  --el-card-border-color: var(--card-border);
  --el-card-bg-color: var(--card-bg);
  --el-card-padding: 1.05rem 1.15rem 1.15rem;
}

.si-about__card :deep(.el-card__header) {
  padding: 1.05rem 1.15rem 0;
  border-bottom: none;
}

.si-about__card :deep(.el-card__body) {
  padding: 0.85rem 1.15rem 1.15rem;
}

.si-about__card-title {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  line-height: 1.3;
  color: var(--si-ink);
}

.si-about__card-title .el-icon {
  color: var(--si-brand);
  font-size: 0.95rem;
}

.si-about__dl {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.72rem;
}

.si-about__dl > div {
  display: grid;
  grid-template-columns: 4.75rem minmax(0, 1fr);
  gap: 0.75rem;
  align-items: start;
}

.si-about__dl dt {
  margin: 0;
  padding-top: 0.08rem;
  font-size: 0.84rem;
  font-weight: 600;
  line-height: 1.45;
  color: var(--si-ink-soft);
}

.si-about__dl dd {
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.45;
  color: var(--si-ink);
}

.si-about__versions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem 0.9rem;
}

.si-about__ver {
  display: inline-flex;
  align-items: baseline;
  gap: 0.3rem;
}

.si-about__ver b {
  font-family: var(--font-numeric);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.si-about__ver em {
  font-style: normal;
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--si-ink-soft);
}

.si-about__num {
  font-family: var(--font-numeric);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.si-about__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.si-about__tags li {
  display: inline-flex;
  line-height: 1;
}

.si-about__links {
  display: flex;
  flex-wrap: wrap;
  gap: 0.2rem 0.75rem;
}

.si-about__dl a {
  color: var(--si-brand);
  text-decoration: none;
}

.si-about__dl a:hover {
  text-decoration: underline;
}

@media (max-width: 800px) {
  .si-about__info {
    grid-template-columns: 1fr;
  }
}

.si-about__demo {
  margin-top: 0.75rem;
  border-radius: 14px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
  --el-card-border-color: var(--card-border);
  --el-card-bg-color: var(--card-bg);
}

.si-about__demo :deep(.el-card__header) {
  padding: 1rem 1.1rem 0;
  border-bottom: none;
}

.si-about__demo :deep(.el-card__body) {
  padding: 0.75rem 1.1rem 1.15rem;
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
  color: var(--si-brand);
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
  color: var(--si-ink);
}

.si-about__demo-title {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 700;
  display: flex;
  align-items: center;
}

.si-about__demo-sub {
  font-size: 0.75rem;
  color: var(--si-muted);
}
</style>
