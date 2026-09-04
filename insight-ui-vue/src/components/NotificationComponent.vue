<template>
  <div class="notification-container">
    <!-- 通知按钮 -->
    <button class="notification-button" @click="toggleNotifications">
      <i class="fa fa-bell"></i>
      <span v-if="unreadCount > 0" class="notification-badge">{{ unreadCount }}</span>
    </button>

    <!-- 通知面板 -->
    <div v-if="showNotifications" class="notification-panel">
      <div class="notification-header">
        <h5>通知中心</h5>
        <button class="btn btn-sm btn-outline-secondary" @click="markAllAsRead" v-if="unreadCount > 0">
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
          :class="{ 'unread': !notification.read }"
          @click="markAsRead(notification.id)"
        >
          <div class="notification-icon" :class="notification.type">
            <i :class="notification.icon"></i>
          </div>
          <div class="notification-content">
            <h6 class="notification-title">{{ notification.title }}</h6>
            <p class="notification-message">{{ notification.message }}</p>
            <span class="notification-time">{{ formatTime(notification.timestamp) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'

// 通知类型定义
interface Notification {
  id: string
  title: string
  message: string
  type: 'success' | 'warning' | 'error' | 'info'
  icon: string
  timestamp: number
  read: boolean
}

// 响应式数据
const showNotifications = ref(false)
const notifications = ref<Notification[]>([])

// 计算属性：未读通知数量
const unreadCount = computed(() => {
  return notifications.value.filter(n => !n.read).length
})

// 切换通知面板显示
const toggleNotifications = () => {
  showNotifications.value = !showNotifications.value
}

// 标记通知为已读
const markAsRead = (id: string) => {
  const notification = notifications.value.find(n => n.id === id)
  if (notification) {
    notification.read = true
  }
}

// 标记所有通知为已读
const markAllAsRead = () => {
  notifications.value.forEach(notification => {
    notification.read = true
  })
}

// 格式化时间
const formatTime = (timestamp: number) => {
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN')
}

// 添加通知
const addNotification = (notification: Omit<Notification, 'id' | 'timestamp' | 'read'>) => {
  const newNotification: Notification = {
    ...notification,
    id: `notification-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    timestamp: Date.now(),
    read: false
  }
  notifications.value.unshift(newNotification)
  
  // 限制通知数量
  if (notifications.value.length > 50) {
    notifications.value = notifications.value.slice(0, 50)
  }
}

// 模拟告警通知
const simulateAlerts = () => {
  const alertTypes = [
    {
      title: '服务异常',
      message: '用户服务 (user-service) 错误率超过 10%，请及时处理',
      type: 'error' as const,
      icon: 'fa-exclamation-circle'
    },
    {
      title: '性能警告',
      message: '订单服务 (order-service) 平均响应时间超过 200ms',
      type: 'warning' as const,
      icon: 'fa-exclamation-triangle'
    },
    {
      title: '服务恢复',
      message: '用户服务 (user-service) 已恢复正常运行',
      type: 'success' as const,
      icon: 'fa-check-circle'
    },
    {
      title: '系统通知',
      message: 'Spring Insight 已更新到最新版本',
      type: 'info' as const,
      icon: 'fa-info-circle'
    }
  ]
  
  // 随机添加一个告警
  const randomAlert = alertTypes[Math.floor(Math.random() * alertTypes.length)]
  addNotification(randomAlert)
}

// 模拟实时告警
let alertInterval: number | null = null

// 组件挂载时初始化
onMounted(() => {
  // 添加一些初始通知
  addNotification({
    title: '系统启动',
    message: 'Spring Insight 监控系统已成功启动',
    type: 'success',
    icon: 'fa-check-circle'
  })
  
  // 每30秒模拟一个告警
  alertInterval = window.setInterval(simulateAlerts, 30000)
})

// 组件卸载时清理
onUnmounted(() => {
  if (alertInterval) {
    clearInterval(alertInterval)
  }
})

// 暴露方法
defineExpose({
  addNotification
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
  font-size: 1.25rem;
  color: rgba(255, 255, 255, 0.9);
  cursor: pointer;
  padding: 0.5rem;
  border-radius: 50%;
  transition: all 0.3s ease;
}

.notification-button:hover {
  background-color: rgba(255, 255, 255, 0.1);
  color: white;
}

.notification-badge {
  position: absolute;
  top: 0;
  right: 0;
  background-color: #ef4444;
  color: white;
  font-size: 0.75rem;
  font-weight: bold;
  padding: 0.2rem 0.5rem;
  border-radius: 9999px;
  min-width: 1.5rem;
  text-align: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.notification-panel {
  position: absolute;
  top: 100%;
  right: 0;
  width: 350px;
  max-height: 400px;
  background: white;
  border-radius: 0.5rem;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
  z-index: 1000;
  margin-top: 0.5rem;
  overflow: hidden;
}

.notification-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  border-bottom: 1px solid #e2e8f0;
  background-color: #f8fafc;
}

.notification-header h5 {
  margin: 0;
  font-weight: 600;
  color: #1e293b;
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
  margin-bottom: 1rem;
}

.notification-item {
  display: flex;
  padding: 1rem;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: all 0.2s ease;
}

.notification-item:hover {
  background-color: #f8fafc;
}

.notification-item.unread {
  background-color: #f0f9ff;
  border-left: 4px solid #3b82f6;
}

.notification-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  margin-right: 1rem;
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
  font-size: 1.25rem;
}

.notification-content {
  flex: 1;
  min-width: 0;
}

.notification-title {
  margin: 0 0 0.25rem 0;
  font-size: 0.875rem;
  font-weight: 600;
  color: #1e293b;
}

.notification-message {
  margin: 0 0 0.5rem 0;
  font-size: 0.75rem;
  color: #64748b;
  line-height: 1.4;
}

.notification-time {
  font-size: 0.6875rem;
  color: #94a3b8;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .notification-panel {
    width: 300px;
  }
}

@media (max-width: 480px) {
  .notification-panel {
    width: 280px;
    right: -20px;
  }
}
</style>