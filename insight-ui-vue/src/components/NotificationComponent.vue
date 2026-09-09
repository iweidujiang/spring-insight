<template>
  <div class="notification-container" ref="containerRef">
    <button
      ref="buttonRef"
      type="button"
      class="notification-button"
      :aria-expanded="showNotifications"
      aria-label="打开状态摘要"
      @click.stop="toggleNotifications"
    >
      <i class="fa fa-bell"></i>
      <span v-if="unreadCount > 0" class="notification-badge">{{ badgeText }}</span>
    </button>

    <Teleport to="body">
      <div
        v-if="showNotifications"
        class="notification-panel"
        :style="panelStyle"
        role="dialog"
        aria-label="状态摘要"
        @click.stop
      >
        <div class="notification-header">
          <div>
            <h5>状态摘要</h5>
            <p class="notification-header-desc">基于 Server 实时统计，非模拟告警</p>
          </div>
          <button
            v-if="unreadCount > 0"
            type="button"
            class="btn btn-sm btn-outline-secondary"
            @click="markAllAsRead"
          >
            全部已读
          </button>
        </div>
        <div class="notification-list">
          <div v-if="loading && notifications.length === 0" class="notification-empty">
            <i class="fa fa-spinner fa-spin"></i>
            <p>加载中…</p>
          </div>
          <div v-else-if="notifications.length === 0" class="notification-empty">
            <i class="fa fa-check-circle"></i>
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
              <i class="fa" :class="notification.icon"></i>
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
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, type CSSProperties } from 'vue'
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
const buttonRef = ref<HTMLButtonElement | null>(null)
const containerRef = ref<HTMLElement | null>(null)
const panelStyle = ref<CSSProperties>({})

let pollTimer: number | null = null

const unreadCount = computed(() => notifications.value.filter((n) => !n.read).length)
const badgeText = computed(() => (unreadCount.value > 99 ? '99+' : String(unreadCount.value)))

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

const updatePanelPosition = () => {
  const btn = buttonRef.value
  if (!btn) return
  const rect = btn.getBoundingClientRect()
  const panelWidth = Math.min(350, window.innerWidth - 16)
  const gap = 8
  let left = rect.left
  if (left + panelWidth > window.innerWidth - 8) {
    left = Math.max(8, window.innerWidth - panelWidth - 8)
  }
  panelStyle.value = {
    position: 'fixed',
    left: `${left}px`,
    bottom: `${window.innerHeight - rect.top + gap}px`,
    width: `${panelWidth}px`,
    zIndex: 5000
  }
}

const toggleNotifications = async () => {
  showNotifications.value = !showNotifications.value
  if (showNotifications.value) {
    await nextTick()
    updatePanelPosition()
    void refreshFromServer()
  }
}

const closeNotifications = () => {
  showNotifications.value = false
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

const onDocPointerDown = (event: MouseEvent | TouchEvent) => {
  if (!showNotifications.value) return
  const target = event.target as Node | null
  if (!target) return
  if (containerRef.value?.contains(target)) return
  const panel = document.querySelector('.notification-panel')
  if (panel?.contains(target)) return
  closeNotifications()
}

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') closeNotifications()
}

onMounted(() => {
  void refreshFromServer()
  pollTimer = window.setInterval(() => {
    void refreshFromServer()
  }, POLL_MS)
  document.addEventListener('mousedown', onDocPointerDown)
  document.addEventListener('touchstart', onDocPointerDown)
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', updatePanelPosition)
  window.addEventListener('scroll', updatePanelPosition, true)
})

onUnmounted(() => {
  if (pollTimer != null) clearInterval(pollTimer)
  document.removeEventListener('mousedown', onDocPointerDown)
  document.removeEventListener('touchstart', onDocPointerDown)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', updatePanelPosition)
  window.removeEventListener('scroll', updatePanelPosition, true)
})
</script>

<style scoped>
.notification-container {
  position: relative;
}

.notification-button {
  position: relative;
  background: none;
  border: none;
  font-size: 1.15rem;
  color: var(--si-ink-soft, #3d524a);
  cursor: pointer;
  padding: 0.5rem;
  border-radius: 50%;
  transition: background 0.2s ease, color 0.2s ease;
}

.notification-button:hover {
  background-color: rgba(15, 118, 110, 0.1);
  color: var(--si-teal, #0f766e);
}

.notification-badge {
  position: absolute;
  top: 0;
  right: 0;
  background-color: #ef4444;
  color: white;
  font-size: 0.7rem;
  font-weight: 700;
  padding: 0.15rem 0.4rem;
  border-radius: 9999px;
  min-width: 1.35rem;
  text-align: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.15);
  line-height: 1.2;
}
</style>

<style>
.notification-panel {
  max-height: min(420px, calc(100vh - 1.5rem));
  background: #fffcfa;
  border-radius: 0.65rem;
  border: 1px solid rgba(20, 83, 45, 0.14);
  box-shadow: 0 16px 40px rgba(21, 36, 31, 0.18);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.notification-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.85rem 1rem;
  border-bottom: 1px solid rgba(20, 83, 45, 0.1);
  background-color: rgba(15, 118, 110, 0.05);
  flex-shrink: 0;
}

.notification-header h5 {
  margin: 0;
  font-weight: 700;
  font-size: 0.95rem;
  color: #15241f;
}

.notification-header-desc {
  margin: 0.2rem 0 0;
  font-size: 0.7rem;
  color: #6b7f76;
  font-weight: 500;
}

.notification-list {
  max-height: 280px;
  overflow-y: auto;
  flex: 1;
}

.notification-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 2rem 1rem;
  color: #94a3b8;
}

.notification-empty i {
  font-size: 2rem;
  margin-bottom: 0.75rem;
  color: #0f766e;
}

.notification-item {
  display: flex;
  padding: 0.85rem 1rem;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: background 0.15s ease;
}

.notification-item:hover {
  background-color: rgba(15, 118, 110, 0.05);
}

.notification-item.unread {
  background-color: #f0f9ff;
  border-left: 4px solid #0f766e;
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
  background-color: #d1fae5;
  color: #10b981;
}

.notification-icon.warning {
  background-color: #fef3c7;
  color: #f59e0b;
}

.notification-icon.error {
  background-color: #fee2e2;
  color: #ef4444;
}

.notification-icon.info {
  background-color: #dbeafe;
  color: #3b82f6;
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
  color: #1e293b;
}

.notification-message {
  margin: 0 0 0.4rem;
  font-size: 0.75rem;
  color: #64748b;
  line-height: 1.4;
}

.notification-time {
  font-size: 0.6875rem;
  color: #94a3b8;
}

.notification-footer {
  flex-shrink: 0;
  padding: 0.65rem 1rem;
  font-size: 0.68rem;
  line-height: 1.45;
  color: #6b7f76;
  background: rgba(15, 118, 110, 0.04);
  border-top: 1px solid rgba(20, 83, 45, 0.1);
}
</style>
