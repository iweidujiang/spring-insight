<template>
  <div class="notification-container" ref="containerRef">
    <button
      ref="buttonRef"
      type="button"
      class="notification-button"
      :aria-expanded="showNotifications"
      aria-label="打开通知中心"
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
        aria-label="通知中心"
        @click.stop
      >
        <div class="notification-header">
          <h5>通知中心</h5>
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
          <div v-if="notifications.length === 0" class="notification-empty">
            <i class="fa fa-bell-slash"></i>
            <p>暂无通知</p>
          </div>
          <div
            v-for="notification in notifications"
            :key="notification.id"
            class="notification-item"
            :class="{ unread: !notification.read }"
            @click="markAsRead(notification.id)"
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
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, type CSSProperties } from 'vue'

interface Notification {
  id: string
  title: string
  message: string
  type: 'success' | 'warning' | 'error' | 'info'
  icon: string
  timestamp: number
  read: boolean
}

const showNotifications = ref(false)
const notifications = ref<Notification[]>([])
const buttonRef = ref<HTMLButtonElement | null>(null)
const containerRef = ref<HTMLElement | null>(null)
const panelStyle = ref<CSSProperties>({})

const unreadCount = computed(() => notifications.value.filter((n) => !n.read).length)
const badgeText = computed(() => (unreadCount.value > 99 ? '99+' : String(unreadCount.value)))

const updatePanelPosition = () => {
  const btn = buttonRef.value
  if (!btn) return
  const rect = btn.getBoundingClientRect()
  const panelWidth = Math.min(350, window.innerWidth - 16)
  const gap = 8
  // 从按钮上方展开，避免落在视口外被「挡住」
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
  }
}

const closeNotifications = () => {
  showNotifications.value = false
}

const markAsRead = (id: string) => {
  const notification = notifications.value.find((n) => n.id === id)
  if (notification) notification.read = true
}

const markAllAsRead = () => {
  notifications.value.forEach((n) => {
    n.read = true
  })
}

const formatTime = (timestamp: number) => new Date(timestamp).toLocaleString('zh-CN')

const addNotification = (notification: Omit<Notification, 'id' | 'timestamp' | 'read'>) => {
  const newNotification: Notification = {
    ...notification,
    id: `notification-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    timestamp: Date.now(),
    read: false
  }
  notifications.value.unshift(newNotification)
  if (notifications.value.length > 50) {
    notifications.value = notifications.value.slice(0, 50)
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
  addNotification({
    title: '系统就绪',
    message: 'Spring Insight 已启动。有异常服务或慢请求时，可在错误分析与链路页下钻。',
    type: 'info',
    icon: 'fa-info-circle'
  })
  document.addEventListener('mousedown', onDocPointerDown)
  document.addEventListener('touchstart', onDocPointerDown)
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', updatePanelPosition)
  window.addEventListener('scroll', updatePanelPosition, true)
})

onUnmounted(() => {
  document.removeEventListener('mousedown', onDocPointerDown)
  document.removeEventListener('touchstart', onDocPointerDown)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', updatePanelPosition)
  window.removeEventListener('scroll', updatePanelPosition, true)
})

defineExpose({ addNotification })
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
/* Teleport 到 body，需非 scoped */
.notification-panel {
  max-height: min(400px, calc(100vh - 1.5rem));
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
  align-items: center;
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

.notification-list {
  max-height: 320px;
  overflow-y: auto;
}

.notification-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  color: #94a3b8;
}

.notification-empty i {
  font-size: 2rem;
  margin-bottom: 0.75rem;
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
</style>
