<template>
  <div class="si-shell" :class="{ 'si-shell--nav-open': navOpen }">
    <button
      type="button"
      class="si-nav-toggle"
      aria-label="打开或关闭导航"
      @click="navOpen = !navOpen"
    >
      <i class="fa" :class="navOpen ? 'fa-times' : 'fa-bars'"></i>
    </button>

    <div v-if="navOpen" class="si-nav-backdrop" @click="navOpen = false"></div>

    <aside class="si-sidebar" aria-label="主导航">
      <router-link class="si-sidebar__brand" to="/" @click="closeNav">
        <i class="fa fa-chart-line"></i>
        <span>Spring Insight</span>
      </router-link>

      <nav class="si-sidebar__nav">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          class="si-sidebar__link"
          :class="{ active: isActive(item) }"
          :to="item.to"
          @click="closeNav"
        >
          <i class="fa" :class="item.icon"></i>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="si-sidebar__foot">
        <NotificationComponent />
      </div>
    </aside>

    <main
      class="si-main"
      :class="$route.path === '/' ? 'si-main--dashboard' : 'si-main--page'"
    >
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import NotificationComponent from './components/NotificationComponent.vue'

const route = useRoute()
const navOpen = ref(false)

const navItems = [
  { to: '/', label: '仪表盘', icon: 'fa-tachometer-alt', match: (p: string) => p === '/' },
  { to: '/topology', label: '拓扑图', icon: 'fa-project-diagram', match: (p: string) => p === '/topology' },
  { to: '/traces', label: '链路追踪', icon: 'fa-stream', match: (p: string) => p === '/traces' || p.startsWith('/traces/') },
  { to: '/error-analysis', label: '错误分析', icon: 'fa-exclamation-triangle', match: (p: string) => p === '/error-analysis' },
  { to: '/about', label: '关于', icon: 'fa-info-circle', match: (p: string) => p === '/about' }
]

const isActive = (item: (typeof navItems)[number]) => item.match(route.path)
const closeNav = () => {
  navOpen.value = false
}

watch(
  () => route.fullPath,
  () => {
    navOpen.value = false
  }
)
</script>
