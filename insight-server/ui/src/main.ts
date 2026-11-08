import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import DashboardView from './views/DashboardView.vue'
import TopologyView from './views/TopologyView.vue'
import TracesView from './views/TracesView.vue'
import TraceDetailView from './views/TraceDetailView.vue'
import TraceCompareView from './views/TraceCompareView.vue'
import ErrorAnalysisView from './views/ErrorAnalysisView.vue'
import JdbcView from './views/JdbcView.vue'
import RedisView from './views/RedisView.vue'
import ServiceDetailView from './views/ServiceDetailView.vue'
import SettingsView from './views/SettingsView.vue'
import AboutView from './views/AboutView.vue'
import LoginView from './views/LoginView.vue'
import { fetchAuthStatus, getUiToken } from './services/AuthService'
import { initTheme } from './composables/useTheme'

// 导入样式（Element Plus 主题变量在品牌色之后覆盖）
import 'font-awesome/css/font-awesome.min.css'
import 'element-plus/dist/index.css'
import './assets/css/styles.css'
import './assets/css/element-plus-theme.css'
import './assets/css/utilities.css'

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
      path: '/traces/compare',
      name: 'trace-compare',
      component: TraceCompareView,
      meta: { title: 'Trace 对比' }
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
      path: '/jdbc',
      name: 'jdbc',
      component: JdbcView,
      meta: { title: '数据库' }
    },
    {
      path: '/redis',
      name: 'redis',
      component: RedisView,
      meta: { title: '缓存' }
    },
    {
      path: '/services/:serviceName',
      name: 'service-detail',
      component: ServiceDetailView,
      meta: { title: '服务详情' }
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

initTheme()

const app = createApp(App)
app.use(router)
app.use(ElementPlus, { locale: zhCn })
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.mount('#app')
