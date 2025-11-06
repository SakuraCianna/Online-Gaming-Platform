<script setup>
import { ref, onMounted, onBeforeUnmount, watch, provide } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from './config/user'
import { storeToRefs } from 'pinia'
import { wsService } from './config/websocket'
import RoomInviteDialog from './components/RoomInviteDialog.vue'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()
const router = useRouter()
const { isAuthenticated } = storeToRefs(userStore)

const inviteDialogRef = ref(null)

// provide WebSocket 服务给所有子组件
provide('wsService', wsService)

// 初始化
onMounted(() => {
  // 已禁用加载动画，直接初始化
  if (isAuthenticated.value) {
    userStore.markOnline()
    userStore.startPresence()
  }
})

// 订阅全局主题（所有页面都需要的）
function subscribeGlobalTopics() {
  const userId = userStore.user?.id
  if (!userId) return

  // 好友在线状态
  wsService.subscribe('/topic/presence', (msg) => {
    const data = JSON.parse(msg.body)
    userStore.updateFriendStatus(
      data.userId,
      !!data.online,
      data.currentGame || null
    )
  })

  // 房间邀请
  wsService.subscribe(`/topic/roomInvite/${userId}`, (msg) => {
    const data = JSON.parse(msg.body)
    if (inviteDialogRef.value) {
      inviteDialogRef.value.showInvite(data)
    }
  })

  // 用户个人通知
  wsService.subscribe(`/topic/user/${userId}`, (msg) => {
    const data = JSON.parse(msg.body)
    if (data.type === 'inviteRejected') {
      // 显示拒绝通知
      ElMessage.info(data.message || `${data.username} 拒绝了你的邀请`)
    }
  })
}

// 用于防止重复提示的标志
let isWebSocketTokenExpiredMessageShown = false

// 处理Token过期
function handleTokenExpired(source = 'WebSocket') {
  if (isWebSocketTokenExpiredMessageShown) return
  
  console.error(`[${source}] Token过期，跳转登录页`)
  isWebSocketTokenExpiredMessageShown = true
  
  ElMessage.error({
    message: '登录已过期，请重新登录',
    duration: 3000,
    onClose: () => {
      isWebSocketTokenExpiredMessageShown = false
    }
  })
  
  // 清除token和状态
  localStorage.removeItem('token')
  wsService.disconnect()
  userStore.stopPresence()
  userStore.markOffline()
  
  // 跳转登录页
  setTimeout(() => {
    router.push('/')
  }, 1000)
}

// 监听登录状态变化，动态建立/断开 WebSocket 连接
watch(isAuthenticated, (authed) => {
  if (authed) {
    userStore.markOnline()
    userStore.startPresence()

    // 建立 WebSocket 连接
    const token = userStore.token
    if (!token) {
      console.error('[App] Token不存在，无法建立WebSocket连接')
      return
    }

    console.log('[App] 建立WebSocket连接')
    wsService.connect(token, {
      onConnect: () => {
        console.log('[App] WebSocket连接成功')
        // 订阅全局主题
        subscribeGlobalTopics()
      },
      onStompError: (frame) => {
        console.error('[App] WebSocket STOMP错误:', frame)
        // 检查是否是 token 过期
        if (frame.headers && frame.headers.message) {
          const message = frame.headers.message
          if (message.includes('JWT') || message.includes('Token') || message.includes('expired')) {
            handleTokenExpired('WebSocket-STOMP')
          }
        }
      },
      onWebSocketError: (error) => {
        console.error('[App] WebSocket底层错误:', error)
        // WebSocket底层错误，静默处理
      },
      onDisconnect: () => {
        console.log('[App] WebSocket已断开')
      }
    })
  } else {
    console.log('[App] 用户未登录，断开WebSocket')
    // 断开 WebSocket 连接
    wsService.disconnect()
    userStore.stopPresence()
    userStore.markOffline()
  }
}, { immediate: true })

// 页面卸载时断开连接
onBeforeUnmount(() => {
  userStore.stopPresence()
  wsService.disconnect()
})
</script>

<template>
  <div id="app">
    <!-- 主应用内容 -->
    <div class="app-container">
      <router-view />

      <!-- 邀请弹窗组件 -->
      <RoomInviteDialog ref="inviteDialogRef" />
    </div>
  </div>
</template>

<style scoped>
#app {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
}

.app-container {
  width: 100%;
  height: 100%;
}
</style>
