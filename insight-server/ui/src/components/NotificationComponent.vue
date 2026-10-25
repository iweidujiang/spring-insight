<template>
  <el-popover
    v-model:visible="showNotifications"
    trigger="click"
    placement="bottom-end"
    :width="panelWidth"
    :offset="8"
    :show-arrow="false"
    role="dialog"
    popper-class="si-notification-popper"
    @show="onPanelShow"
  >
    <template #reference>
      <el-badge
        class="notification-badge"
        :value="unreadCount"
        :max="99"
        :hidden="unreadCount === 0"
      >
        <button
          type="button"
          class="notification-button"
          :aria-expanded="showNotifications"
          aria-haspopup="dialog"
          aria-label="打开状态摘要"
        >
          <i class="fa fa-bell" aria-hidden="true"></i>
        </button>
      </el-badge>
    </template>

    <div class="notification-panel" aria-label="状态摘要">
      <div class="notification-header">
        <h5>状态摘要</h5>
        <el-button v-if="unreadCount > 0" size="small" plain @click="markAllAsRead">
          全部已读
        </el-button>
      </div>
      <div
        class="notification-list"
        :class="{ 'is-loading': loading && notifications.length === 0 }"
        v-loading="loading && notifications.length === 0"
        element-loading-text="加载中…"
        element-loading-background="transparent"
      >
        <div v-if="!loading && notifications.length === 0" class="notification-empty">
          <i class="fa fa-check-circle" aria-hidden="true"></i>
          <p>暂无需要关注的事项</p>
        </div>
        <div
          v-for="notification in notifications"
          :key="notification.id"
          class="notification-item"
          :class="{ unread: !notification.read }"
          @click="onItemClick(notification)"
        >
          <div class="notification-icon" :class="notification.type">
            <i class="fa" :class="notification.icon" aria-hidden="true"></i>
          </div>
          <div class="notification-content">
            <h6 class="notification-title">{{ notification.title }}</h6>
            <p class="notification-message">{{ notification.message }}</p>
            <span class="notification-time">{{ formatTime(notification.timestamp) }}</span>
          </div>
        </div>
      </div>
      <div class="notification-footer">
        完整告警规则（阈值/Webhook）将在后续版本提供；此处只反映已采集到的错误与上报成功率。
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ApiService } from '../services/ApiService'

interface Notification {
  id: string
  title: string
  message: string
  type: 'success' | 'warning' | 'error' | 'info'
  icon: string
  timestamp: number
  read: boolean
  route?: string
}

const READ_KEY = 'si.notify.read.v1'
const POLL_MS = 60_000

const router = useRouter()
const showNotifications = ref(false)
const notifications = ref<Notification[]>([])
const loading = ref(false)
const panelWidth = ref(350)

let pollTimer: number | null = null

const unreadCount = computed(() => notifications.value.filter((n) => !n.read).length)

const syncPanelWidth = () => {
  panelWidth.value = Math.min(350, Math.max(0, window.innerWidth - 16))
}

const loadReadIds = (): Set<string> => {
  try {
    const raw = localStorage.getItem(READ_KEY)
    if (!raw) return new Set()
    const arr = JSON.parse(raw)
    return new Set(Array.isArray(arr) ? arr.map(String) : [])
  } catch {
    return new Set()
  }
}

const saveReadIds = (ids: Set<string>) => {
  try {
    localStorage.setItem(READ_KEY, JSON.stringify([...ids]))
  } catch {
    /* ignore quota */
  }
}

const closeNotifications = () => {
  showNotifications.value = false
}

const onPanelShow = () => {
  syncPanelWidth()
  void refreshFromServer()
}

const markAsRead = (id: string) => {
  const notification = notifications.value.find((n) => n.id === id)
  if (!notification || notification.read) return
  notification.read = true
  const ids = loadReadIds()
  ids.add(id)
  saveReadIds(ids)
}

const markAllAsRead = () => {
  const ids = loadReadIds()
  notifications.value.forEach((n) => {
    n.read = true
    ids.add(n.id)
  })
  saveReadIds(ids)
}

const onItemClick = (notification: Notification) => {
  markAsRead(notification.id)
  if (notification.route) {
    closeNotifications()
    void router.push(notification.route)
  }
}

const formatTime = (timestamp: number) => new Date(timestamp).toLocaleString('zh-CN')

/**
 * 仅根据 Server 真实统计生成条目；无数据则列表为空（不造假告警）。
 */
const buildFromStats = (stats: any, errorRows: any[]): Notification[] => {
  const now = Date.now()
  const items: Notification[] = []
  const errorCount = Array.isArray(errorRows) ? errorRows.length : 0
  const failedSpans = Number(stats?.totalFailedSpans ?? stats?.total_failed_spans ?? 0)
  const receivedSpans = Number(stats?.totalReceivedSpans ?? stats?.total_received_spans ?? 0)
  const successRate = Number(stats?.successRate ?? stats?.success_rate ?? 100)

  if (errorCount > 0) {
    const names = errorRows
      .slice(0, 3)
      .map((r) => r.serviceName || r.service_name || '?')
      .filter(Boolean)
    const more = errorCount > 3 ? ` 等 ${errorCount} 个` : ''
    items.push({
      id: 'fact-error-services',
      title: '存在异常服务',
      message: `${errorCount} 个服务在近 24h 有错误调用：${names.join('、')}${more}。点击查看错误分析。`,
      type: 'error',
      icon: 'fa-exclamation-circle',
      timestamp: now,
      read: false,
      route: '/error-analysis'
    })
  }

  if (receivedSpans > 0 && successRate < 90) {
    items.push({
      id: 'fact-collector-success-rate',
      title: '上报成功率偏低',
      message: `Collector 成功率约 ${successRate.toFixed(1)}%（接收 ${receivedSpans} 条 Span）。请检查 Agent 上报与 Server 日志。`,
      type: 'warning',
      icon: 'fa-exclamation-triangle',
      timestamp: now,
      read: false,
      route: '/'
    })
  } else if (failedSpans > 0) {
    items.push({
      id: 'fact-collector-failed-spans',
      title: '存在失败 Span',
      message: `Collector 累计失败 Span：${failedSpans}。可在仪表盘查看 Collector 条与链路筛选。`,
      type: 'warning',
      icon: 'fa-exclamation-triangle',
      timestamp: now,
      read: false,
      route: '/traces'
    })
  }

  return items
}

const refreshFromServer = async () => {
  loading.value = true
  try {
    const [stats, errorRows] = await Promise.all([
      ApiService.getCollectorStats(),
      ApiService.getErrorAnalysis(24)
    ])
    const readIds = loadReadIds()
    const next = buildFromStats(stats, errorRows).map((n) => ({
      ...n,
      read: readIds.has(n.id)
    }))
    notifications.value = next
  } catch (e) {
    console.error('刷新状态摘要失败:', e)
  } finally {
    loading.value = false
  }
}

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') closeNotifications()
}

onMounted(() => {
  syncPanelWidth()
  void refreshFromServer()
  pollTimer = window.setInterval(() => {
    void refreshFromServer()
  }, POLL_MS)
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', syncPanelWidth)
})

onUnmounted(() => {
  if (pollTimer != null) clearInterval(pollTimer)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', syncPanelWidth)
})
</script>

<style scoped>
.notification-badge {
  display: inline-flex;
  line-height: 1;
}

.notification-badge :deep(.el-badge__content) {
  border: none;
  font-weight: 700;
  background-color: var(--danger-color);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.15);
}

.notification-button {
  background: none;
  border: none;
  font-size: 1.15rem;
  color: var(--si-ink-soft);
  cursor: pointer;
  padding: 0.5rem;
  min-width: 44px;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: background 0.2s ease, color 0.2s ease;
}

.notification-button:hover {
  background-color: var(--si-hover-teal);
  color: var(--si-teal);
}

.notification-panel {
  display: flex;
  flex-direction: column;
  max-height: min(420px, calc(100vh - 1.5rem));
  overflow: hidden;
}

.notification-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
  padding: 0.85rem 1rem;
  border-bottom: 1px solid var(--si-brand-divider);
  background-color: var(--si-notification-header-bg);
  flex-shrink: 0;
}

.notification-header h5 {
  margin: 0;
  font-weight: 700;
  font-size: 0.95rem;
  color: var(--si-ink);
}

.notification-list {
  max-height: 280px;
  overflow-y: auto;
  flex: 1;
}

.notification-list.is-loading {
  min-height: 140px;
}

.notification-list :deep(.el-loading-text) {
  color: var(--si-muted);
}

.notification-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 2rem 1rem;
  color: var(--si-muted);
}

.notification-empty i {
  font-size: 2rem;
  margin-bottom: 0.75rem;
  color: var(--si-teal);
}

.notification-empty p {
  margin: 0;
}

.notification-item {
  display: flex;
  padding: 0.85rem 1rem;
  border-bottom: 1px solid var(--si-table-border);
  cursor: pointer;
  transition: background 0.15s ease;
}

.notification-item:hover {
  background-color: var(--si-hover-teal);
}

.notification-item.unread {
  background-color: var(--si-notification-unread-bg);
  border-left: 4px solid var(--si-teal);
}

.notification-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  margin-right: 0.85rem;
  flex-shrink: 0;
}

.notification-icon.success {
  background-color: var(--si-icon-success-bg);
  color: var(--si-icon-success-color);
}

.notification-icon.warning {
  background-color: var(--si-icon-warning-bg);
  color: var(--si-icon-warning-color);
}

.notification-icon.error {
  background-color: var(--si-icon-error-bg);
  color: var(--si-icon-error-color);
}

.notification-icon.info {
  background-color: var(--si-icon-info-bg);
  color: var(--si-icon-info-color);
}

.notification-icon i {
  font-size: 1.15rem;
}

.notification-content {
  flex: 1;
  min-width: 0;
}

.notification-title {
  margin: 0 0 0.2rem;
  font-size: 0.875rem;
  font-weight: 700;
  color: var(--si-ink);
}

.notification-message {
  margin: 0 0 0.4rem;
  font-size: 0.75rem;
  color: var(--si-muted);
  line-height: 1.4;
}

.notification-time {
  font-size: 0.6875rem;
  color: var(--si-muted);
}

.notification-footer {
  flex-shrink: 0;
  padding: 0.65rem 1rem;
  font-size: 0.68rem;
  line-height: 1.45;
  color: var(--si-muted);
  background: var(--si-notification-footer-bg);
  border-top: 1px solid var(--si-brand-divider);
}
</style>

<style>
.si-notification-popper.el-popover.el-popper {
  padding: 0;
  background: var(--si-notification-bg);
  border: 1px solid var(--card-border);
  border-radius: 0.65rem;
  box-shadow: var(--si-notification-shadow);
}
</style>
