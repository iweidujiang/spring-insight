<template>
  <div v-if="isLoginRoute" class="si-shell si-shell--login">
    <router-view />
  </div>
  <div v-else class="si-shell" :class="{ 'si-shell--nav-open': navOpen }">
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
        <button v-if="showLogout" type="button" class="si-sidebar__logout" @click="onLogout">
          退出登录
        </button>
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
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NotificationComponent from './components/NotificationComponent.vue'
import { getUiToken, logout } from './services/AuthService'

const route = useRoute()
const router = useRouter()
const navOpen = ref(false)

const isLoginRoute = computed(() => route.path === '/login')
const showLogout = computed(() => !!getUiToken())

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

async function onLogout() {
  await logout()
  await router.push('/login')
}

watch(
  () => route.fullPath,
  () => {
    navOpen.value = false
  }
)
</script>

<style scoped>
.si-sidebar__logout {
  width: 100%;
  margin-bottom: 0.5rem;
  border: 1px solid rgba(255, 255, 255, 0.25);
  background: transparent;
  color: inherit;
  border-radius: 8px;
  padding: 0.4rem 0.6rem;
  font-size: 0.85rem;
  cursor: pointer;
}
</style>
