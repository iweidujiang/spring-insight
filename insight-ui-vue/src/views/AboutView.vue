<template>
  <div>
    <!-- 页面标题 -->
    <div class="d-flex justify-content-between align-items-center mb-4">
      <div>
        <h2 class="page-title">
          <i class="fa fa-info-circle me-2 text-primary"></i>关于
        </h2>
        <p class="page-description">了解Spring Insight的基本信息和版本详情</p>
      </div>
      <div>
        <span class="badge bg-info">
          <i class="fa fa-clock me-1"></i>
          <span>{{ currentTime }}</span>
        </span>
      </div>
    </div>

    <!-- 项目信息卡片 -->
    <div class="row">
      <div class="col-md-6">
        <div class="card stat-card">
          <div class="card-body">
            <h5 class="card-title">
              <i class="fa fa-cube me-2"></i>项目信息
            </h5>
            <div class="mb-3">
              <strong>项目名称:</strong> Spring Insight
            </div>
            <div class="mb-3">
              <strong>版本:</strong> 0.1.0-SNAPSHOT
            </div>
            <div class="mb-3">
              <strong>描述:</strong> 一个基于Spring Boot的分布式应用监控系统
            </div>
            <div class="mb-3">
              <strong>技术栈:</strong>
              <ul class="list-inline mt-2">
                <li class="list-inline-item badge bg-primary me-2 mb-2">Spring Boot 3.5.9</li>
                <li class="list-inline-item badge bg-success me-2 mb-2">Java 21</li>
                <li class="list-inline-item badge bg-info me-2 mb-2">Vue 3</li>
                <li class="list-inline-item badge bg-warning me-2 mb-2">TypeScript</li>
                <li class="list-inline-item badge bg-danger me-2 mb-2">ECharts</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
      <div class="col-md-6">
        <div class="card stat-card">
          <div class="card-body">
            <h5 class="card-title">
              <i class="fa fa-server me-2"></i>系统信息
            </h5>
            <div class="mb-3">
              <strong>UI模块:</strong> Vue 3 + TypeScript + Vite
            </div>
            <div class="mb-3">
              <strong>服务端口:</strong> 8088
            </div>
            <div class="mb-3">
              <strong>构建时间:</strong> {{ buildTime }}
            </div>
            <div class="mb-3">
              <strong>GitHub:</strong> 
              <a href="https://github.com/iweidujiang/spring-insight" target="_blank" rel="noopener noreferrer" class="text-decoration-none">
                <i class="fa fa-github me-1"></i>spring-insight
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 功能特性 -->
    <div class="row mt-4">
      <div class="col-12">
        <div class="card stat-card">
          <div class="card-body">
            <h5 class="card-title">
              <i class="fa fa-star me-2"></i>功能特性
            </h5>
            <div class="row g-4">
              <div class="col-md-4">
                <div class="feature-item">
                  <i class="fa fa-tachometer-alt feature-icon text-primary"></i>
                  <h6>实时监控</h6>
                  <p class="text-muted">实时监控服务运行状态和性能指标</p>
                </div>
              </div>
              <div class="col-md-4">
                <div class="feature-item">
                  <i class="fa fa-project-diagram feature-icon text-success"></i>
                  <h6>服务拓扑</h6>
                  <p class="text-muted">可视化展示服务间的依赖关系</p>
                </div>
              </div>
              <div class="col-md-4">
                <div class="feature-item">
                  <i class="fa fa-stream feature-icon text-info"></i>
                  <h6>链路追踪</h6>
                  <p class="text-muted">详细的请求链路追踪和耗时分析</p>
                </div>
              </div>
              <div class="col-md-4">
                <div class="feature-item">
                  <i class="fa fa-exclamation-triangle feature-icon text-danger"></i>
                  <h6>错误分析</h6>
                  <p class="text-muted">自动识别和分析高错误率服务</p>
                </div>
              </div>
              <div class="col-md-4">
                <div class="feature-item">
                  <i class="fa fa-database feature-icon text-warning"></i>
                  <h6>数据持久化</h6>
                  <p class="text-muted">使用H2数据库存储监控数据</p>
                </div>
              </div>
              <div class="col-md-4">
                <div class="feature-item">
                  <i class="fa fa-chart-line feature-icon text-purple"></i>
                  <h6>性能分析</h6>
                  <p class="text-muted">服务调用性能和响应时间分析</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

// 响应式数据
const currentTime = ref('')
const buildTime = ref('')

// 时间更新定时器
let timeInterval: number | null = null

// 更新当前时间
const updateCurrentTime = () => {
  const now = new Date()
  currentTime.value = now.toTimeString().split(' ')[0]
}

// 组件挂载时初始化
onMounted(() => {
  // 设置构建时间
  buildTime.value = new Date().toLocaleString('zh-CN')
  
  // 启动时间更新
  updateCurrentTime()
  timeInterval = window.setInterval(updateCurrentTime, 1000)
})

// 组件卸载时清理
onUnmounted(() => {
  // 清理定时器
  if (timeInterval) {
    clearInterval(timeInterval)
  }
})
</script>

<style scoped>
/* 功能特性样式 */
.feature-item {
  text-align: center;
  padding: 20px;
  border-radius: 10px;
  background-color: #f8f9fc;
  transition: all 0.3s ease;
}

.feature-item:hover {
  transform: translateY(-5px);
  box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.15);
}

.feature-icon {
  font-size: 2rem;
  margin-bottom: 1rem;
}

.feature-item h6 {
  font-weight: 600;
  margin-bottom: 0.5rem;
  color: #333;
}

/* 紫色样式 */
.text-purple {
  color: #6f42c1;
}
</style>
