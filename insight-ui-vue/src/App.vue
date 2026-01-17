<template>
  <div id="app">
    <!-- 导航栏 -->
    <nav class="navbar navbar-expand-lg navbar-dark bg-dark fixed-top">
      <div class="container-fluid">
        <router-link class="navbar-brand" to="/">
          <i class="fa fa-chart-network me-2"></i>Spring Insight
        </router-link>
        
        <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarNav" aria-controls="navbarNav" aria-expanded="false" aria-label="Toggle navigation">
          <span class="navbar-toggler-icon"></span>
        </button>
        
        <div class="collapse navbar-collapse" id="navbarNav">
          <ul class="navbar-nav me-auto">
            <li class="nav-item">
              <router-link class="nav-link" :class="{ active: $route.path === '/' }" to="/">
                <i class="fa fa-tachometer-alt me-1"></i>仪表盘
              </router-link>
            </li>
            <li class="nav-item">
              <router-link class="nav-link" :class="{ active: $route.path === '/topology' }" to="/topology">
                <i class="fa fa-project-diagram me-1"></i>拓扑图
              </router-link>
            </li>
            <li class="nav-item">
              <router-link class="nav-link" :class="{ active: $route.path === '/traces' }" to="/traces">
                <i class="fa fa-stream me-1"></i>链路追踪
              </router-link>
            </li>
            <li class="nav-item">
              <router-link class="nav-link" :class="{ active: $route.path === '/error-analysis' }" to="/error-analysis">
                <i class="fa fa-exclamation-triangle me-1"></i>错误分析
              </router-link>
            </li>
            <li class="nav-item">
              <router-link class="nav-link" :class="{ active: $route.path === '/about' }" to="/about">
                <i class="fa fa-info-circle me-1"></i>关于
              </router-link>
            </li>
          </ul>
        </div>
      </div>
    </nav>

    <!-- 主内容区域 -->
    <main class="container-fluid mt-4 pt-5">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
// 根组件逻辑
</script>

<style scoped>
/* 组件特定样式 */
</style>
