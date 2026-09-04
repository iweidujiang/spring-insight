<template>
  <div class="si-page fade-in si-about">
    <div class="si-page__header mb-1">
      <div>
        <h2 class="page-title mb-1">
          <i class="fa fa-info-circle me-2"></i>关于
        </h2>
        <p class="page-description mb-0">Spring Insight 基本信息与能力概览</p>
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
            <div><dt>版本</dt><dd>0.1.0-SNAPSHOT</dd></div>
            <div><dt>描述</dt><dd>面向 Spring 微服务的轻量分布式监测中心（Agent + Server）</dd></div>
            <div>
              <dt>技术栈</dt>
              <dd class="si-about__tags">
                <span>Spring Boot 3.5</span>
                <span>Java 21</span>
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
            <div><dt>构建时间</dt><dd>{{ buildTime }}</dd></div>
            <div>
              <dt>GitHub</dt>
              <dd>
                <a href="https://github.com/iweidujiang/spring-insight" target="_blank" rel="noopener noreferrer">
                  <i class="fa fa-github me-1"></i>iweidujiang/spring-insight
                </a>
              </dd>
            </div>
          </dl>
        </div>
      </div>
    </div>

    <section class="si-about__features">
      <div class="si-about__features-head">
        <h5 class="mb-0"><i class="fa fa-star me-2"></i>功能特性</h5>
        <span class="si-about__features-sub">开箱即用的监测能力</span>
      </div>
      <ul class="si-about__feature-list">
        <li v-for="f in features" :key="f.title" class="si-about__feature">
          <span class="si-about__feature-icon" :style="{ color: f.color }">
            <i :class="['fa', f.icon]"></i>
          </span>
          <div class="si-about__feature-text">
            <strong>{{ f.title }}</strong>
            <span>{{ f.desc }}</span>
          </div>
        </li>
      </ul>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const currentTime = ref('')
const buildTime = ref('')
let timeInterval: number | null = null

const features = [
  { title: '实时监控', desc: '汇总服务、Span 与 Collector 上报状态', icon: 'fa-tachometer-alt', color: '#0f766e' },
  { title: '服务拓扑', desc: '可视化调用方向与依赖强度', icon: 'fa-project-diagram', color: '#15803d' },
  { title: '链路追踪', desc: '按 Trace / Span 排查单次请求耗时', icon: 'fa-stream', color: '#0d9488' },
  { title: '错误分析', desc: '识别高错误率服务并跳转相关链路', icon: 'fa-exclamation-triangle', color: '#b91c1c' },
  { title: '独立 Server', desc: '业务侧仅依赖 Agent，监测中心进程隔离', icon: 'fa-server', color: '#b45309' },
  { title: '性能洞察', desc: '平均耗时、调用次数等指标辅助定位瓶颈', icon: 'fa-chart-line', color: '#1d4ed8' }
]

const updateCurrentTime = () => {
  currentTime.value = new Date().toTimeString().split(' ')[0]
}

onMounted(() => {
  buildTime.value = new Date().toLocaleString('zh-CN')
  updateCurrentTime()
  timeInterval = window.setInterval(updateCurrentTime, 1000)
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

.si-about__features {
  margin-top: 0.25rem;
  padding: 1rem 1.1rem 1.15rem;
  border-radius: 14px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  box-shadow: var(--box-shadow);
}

.si-about__features-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.85rem;
  color: var(--si-ink);
}

.si-about__features-sub {
  font-size: 0.75rem;
  color: var(--si-muted);
}

.si-about__feature-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.55rem;
}

@media (max-width: 991px) {
  .si-about__feature-list {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 575px) {
  .si-about__feature-list {
    grid-template-columns: 1fr;
  }
}

.si-about__feature {
  display: flex;
  gap: 0.7rem;
  align-items: flex-start;
  padding: 0.7rem 0.8rem;
  border-radius: 10px;
  background: var(--si-paper);
  border: 1px solid var(--card-border);
  transition: border-color 0.2s ease, background 0.2s ease;
}

.si-about__feature:hover {
  border-color: rgba(15, 118, 110, 0.35);
  background: #fff;
}

.si-about__feature-icon {
  flex-shrink: 0;
  width: 2rem;
  height: 2rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #fff;
  font-size: 0.95rem;
  border: 1px solid var(--card-border);
}

.si-about__feature-text {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.si-about__feature-text strong {
  color: var(--si-ink);
  font-size: 0.88rem;
}

.si-about__feature-text span {
  color: var(--si-muted);
  font-size: 0.78rem;
  line-height: 1.4;
}
</style>
