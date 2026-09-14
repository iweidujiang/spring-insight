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
    </aside>

    <div class="si-workspace">
      <header class="si-topbar">
        <div class="si-topbar__end">
          <NotificationComponent />
          <div v-if="signedIn" class="si-user" ref="userMenuRef">
            <button
              type="button"
              class="si-user__trigger"
              :aria-expanded="userMenuOpen"
              aria-haspopup="menu"
              @click.stop="userMenuOpen = !userMenuOpen"
            >
              <span class="si-user__avatar" aria-hidden="true">{{ userInitial }}</span>
              <span class="si-user__name">{{ displayName }}</span>
              <i class="fa fa-chevron-down si-user__caret"></i>
            </button>
            <div v-if="userMenuOpen" class="si-user__menu" role="menu">
              <button type="button" role="menuitem" @click="onLogout">退出登录</button>
            </div>
          </div>
        </div>
      </header>

      <main
        class="si-main"
        :class="$route.path === '/' ? 'si-main--dashboard' : 'si-main--page'"
      >
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NotificationComponent from './components/NotificationComponent.vue'
import { getUiToken, getUiUsername, logout } from './services/AuthService'

const route = useRoute()
const router = useRouter()
const navOpen = ref(false)
const userMenuOpen = ref(false)
const userMenuRef = ref<HTMLElement | null>(null)

const isLoginRoute = computed(() => route.path === '/login')
const signedIn = ref(false)
const displayName = ref('管理员')
const userInitial = computed(() => {
  const name = displayName.value.trim()
  return name ? name.slice(0, 1).toUpperCase() : 'A'
})

function refreshUser() {
  signedIn.value = !!getUiToken()
  displayName.value = getUiUsername() || '管理员'
}

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
  userMenuOpen.value = false
  await logout()
  await router.push('/login')
}

const onDocPointerDown = (event: MouseEvent) => {
  if (!userMenuOpen.value) return
  const target = event.target as Node | null
  if (target && userMenuRef.value?.contains(target)) return
  userMenuOpen.value = false
}

onMounted(() => {
  document.addEventListener('mousedown', onDocPointerDown)
})

onUnmounted(() => {
  document.removeEventListener('mousedown', onDocPointerDown)
})

watch(
  () => route.fullPath,
  () => {
    navOpen.value = false
    userMenuOpen.value = false
    refreshUser()
  },
  { immediate: true }
)
</script>
