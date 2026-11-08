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
      <el-icon :size="18"><component :is="navOpen ? 'Close' : 'Expand'" /></el-icon>
    </button>

    <div v-if="navOpen" class="si-nav-backdrop" @click="navOpen = false"></div>

    <aside class="si-sidebar" aria-label="主导航">
      <router-link class="si-sidebar__brand" to="/" @click="closeNav">
        <InsightMark :size="28" />
        <span>Spring Insight</span>
      </router-link>

      <nav class="si-sidebar__nav">
        <div v-for="group in navGroups" :key="group.label" class="si-sidebar__group">
          <p class="si-sidebar__group-label">{{ group.label }}</p>
          <router-link
            v-for="item in group.items"
            :key="item.to"
            class="si-sidebar__link"
            :class="{ active: isActive(item) }"
            :to="navLocation(item.to)"
            @click="closeNav"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </router-link>
        </div>
      </nav>
    </aside>

    <div class="si-workspace">
      <header class="si-topbar">
        <div class="si-topbar__end">
          <ThemeToggle />
          <NotificationComponent />
          <div v-if="showUserMenu" class="si-user">
            <el-dropdown trigger="click" @command="onUserCommand">
              <span class="si-user__avatar-wrap">
                <el-avatar :size="30" :style="avatarStyle">{{ userInitial }}</el-avatar>
                <span class="si-user__name">{{ displayName }}</span>
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="logout">
                    <el-icon><SwitchButton /></el-icon>
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <router-link
            v-else-if="authEnabled"
            class="si-user__login"
            to="/login"
          >
            登录
          </router-link>
        </div>
      </header>

      <main
        class="si-main"
        :class="$route.path === '/' ? 'si-main--dashboard' : 'si-main--page'"
      >
        <router-view v-slot="{ Component }">
          <transition name="si-page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import InsightMark from './components/InsightMark.vue'
import NotificationComponent from './components/NotificationComponent.vue'
import ThemeToggle from './components/ThemeToggle.vue'
import { fetchAuthStatus, getUiToken, getUiUsername, logout } from './services/AuthService'
import { buildNavLocation } from './utils/insightQuery'

const route = useRoute()
const router = useRouter()
const navOpen = ref(false)

const isLoginRoute = computed(() => route.path === '/login')
/** Server 是否开启控制台登录 */
const authEnabled = ref(false)
const signedIn = ref(false)
const displayName = ref('管理员')
const showUserMenu = computed(() => authEnabled.value && signedIn.value)
const userInitial = computed(() => {
  const name = displayName.value.trim()
  return name ? name.slice(0, 1).toUpperCase() : 'A'
})

const avatarStyle = computed(() => ({
  background: 'linear-gradient(145deg, var(--si-avatar-from), var(--si-avatar-to))',
  color: 'var(--si-avatar-text)',
  fontWeight: '700',
  fontSize: '0.85rem'
}))

async function refreshAuthAndUser() {
  try {
    const status = await fetchAuthStatus()
    authEnabled.value = !!status.uiAuthEnabled
  } catch {
    // 探测失败时：有本地 token 仍展示退出，避免无法登出
    authEnabled.value = !!getUiToken()
  }
  signedIn.value = !!getUiToken()
  displayName.value = getUiUsername() || '管理员'
}

const navGroups = [
  {
    label: '观测',
    items: [
      { to: '/', label: '仪表盘', icon: 'Odometer', match: (p: string) => p === '/' },
      { to: '/topology', label: '拓扑图', icon: 'Share', match: (p: string) => p === '/topology' },
      { to: '/traces', label: '链路追踪', icon: 'List', match: (p: string) => p === '/traces' || p.startsWith('/traces/') },
      { to: '/jdbc', label: '数据库', icon: 'Coin', match: (p: string) => p === '/jdbc' },
      { to: '/redis', label: '缓存', icon: 'Box', match: (p: string) => p === '/redis' },
      { to: '/error-analysis', label: '错误分析', icon: 'WarningFilled', match: (p: string) => p === '/error-analysis' }
    ]
  },
  {
    label: '运维',
    items: [
      { to: '/settings', label: '设置', icon: 'Setting', match: (p: string) => p === '/settings' }
    ]
  },
  {
    label: '关于',
    items: [
      { to: '/about', label: '关于', icon: 'InfoFilled', match: (p: string) => p === '/about' }
    ]
  }
]

const isActive = (item: { match: (p: string) => boolean }) => item.match(route.path)
const navLocation = (path: string) => buildNavLocation(path)
const closeNav = () => {
  navOpen.value = false
}

async function onUserCommand(command: string) {
  if (command === 'logout') {
    await logout()
    signedIn.value = false
    await router.push('/login')
  }
}

const onStorage = (event: StorageEvent) => {
  if (event.key === 'spring-insight-ui-token' || event.key === 'spring-insight-ui-username') {
    refreshAuthAndUser()
  }
}

onMounted(() => {
  window.addEventListener('storage', onStorage)
  refreshAuthAndUser()
})

onUnmounted(() => {
  window.removeEventListener('storage', onStorage)
})

watch(
  () => route.fullPath,
  () => {
    navOpen.value = false
    refreshAuthAndUser()
  },
  { immediate: true }
)
</script>
