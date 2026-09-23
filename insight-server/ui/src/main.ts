import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import DashboardView from './views/DashboardView.vue'
import TopologyView from './views/TopologyView.vue'
import TracesView from './views/TracesView.vue'
import TraceDetailView from './views/TraceDetailView.vue'
import ErrorAnalysisView from './views/ErrorAnalysisView.vue'
import SettingsView from './views/SettingsView.vue'
import AboutView from './views/AboutView.vue'
import LoginView from './views/LoginView.vue'
import { fetchAuthStatus, getUiToken } from './services/AuthService'

// 导入样式
import 'bootstrap/dist/css/bootstrap.min.css'
import 'font-awesome/css/font-awesome.min.css'
import './assets/css/styles.css'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { title: '登录', public: true }
    },
    {
      path: '/',
      name: 'dashboard',
      component: DashboardView,
      meta: { title: '仪表盘' }
    },
    {
      path: '/topology',
      name: 'topology',
      component: TopologyView,
      meta: { title: '服务拓扑' }
    },
    {
      path: '/traces',
      name: 'traces',
      component: TracesView,
      meta: { title: '链路追踪' }
    },
    {
      path: '/traces/:traceId',
      name: 'trace-detail',
      component: TraceDetailView,
      meta: { title: '链路详情' }
    },
    {
      path: '/error-analysis',
      name: 'error-analysis',
      component: ErrorAnalysisView,
      meta: { title: '错误分析' }
    },
    {
      path: '/settings',
      name: 'settings',
      component: SettingsView,
      meta: { title: '设置' }
    },
    {
      path: '/about',
      name: 'about',
      component: AboutView,
      meta: { title: '关于' }
    }
  ]
})

let uiAuthEnabled: boolean | null = null

async function ensureAuthStatus() {
  if (uiAuthEnabled !== null) {
    return uiAuthEnabled
  }
  try {
    const status = await fetchAuthStatus()
    uiAuthEnabled = status.uiAuthEnabled
  } catch {
    uiAuthEnabled = false
  }
  return uiAuthEnabled
}

router.beforeEach(async (to, _from, next) => {
  document.title = `Spring Insight - ${String(to.meta.title || '监控系统')}`
  if (to.meta.public) {
    next()
    return
  }
  const enabled = await ensureAuthStatus()
  if (enabled && !getUiToken()) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  next()
})

const app = createApp(App)
app.use(router)
app.mount('#app')
