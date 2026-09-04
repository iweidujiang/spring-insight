import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import DashboardView from './views/DashboardView.vue'
import TopologyView from './views/TopologyView.vue'
import TracesView from './views/TracesView.vue'
import TraceDetailView from './views/TraceDetailView.vue'
import ErrorAnalysisView from './views/ErrorAnalysisView.vue'
import AboutView from './views/AboutView.vue'

// 导入样式
import 'bootstrap/dist/css/bootstrap.min.css'
import 'font-awesome/css/font-awesome.min.css'
import './assets/css/styles.css'

// 创建路由
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
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
      path: '/about',
      name: 'about',
      component: AboutView,
      meta: { title: '关于' }
    }
  ]
})

// 路由守卫，设置页面标题
router.beforeEach((to, from, next) => {
  document.title = `Spring Insight - ${to.meta.title || '监控系统'}`
  next()
})

// 创建应用
const app = createApp(App)
app.use(router)
app.mount('#app')
