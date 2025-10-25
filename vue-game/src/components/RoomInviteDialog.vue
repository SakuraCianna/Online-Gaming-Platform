<script setup>
import { ref, computed } from 'vue'
import { Icon } from '@iconify/vue'
import { useRouter } from 'vue-router'
import request from '../config/api'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../config/user'
import { useRoomStore } from '../config/room'

const userStore = useUserStore()
const roomStore = useRoomStore()
const router = useRouter()

const visible = ref(false)
const inviteData = ref(null)
const processing = ref(false)

// 显示邀请
function showInvite(data) {
  inviteData.value = data
  visible.value = true
}

// 接受邀请
async function acceptInvite() {
  if (processing.value) return
  processing.value = true

  try {
    const response = await request.post('/room/acceptInvite', {
      roomCode: inviteData.value.roomCode,
      userId: userStore.user?.id,
      inviterId: inviteData.value.inviterId
    })

    if (response.data.success) {
      // 保存房间信息
      roomStore.setCurrentRoom(response.data.room)
      ElMessage.success('已接受邀请')
      visible.value = false

      // 跳转到对应游戏房间
      router.push(`/game/${inviteData.value.gameName}`)
    } else {
      ElMessage.error(response.data.message || '加入房间失败')
    }
  } catch (error) {
    console.error('接受邀请失败:', error)
    ElMessage.error('加入房间失败，请稍后重试')
  } finally {
    processing.value = false
  }
}

// 拒绝邀请
async function rejectInvite() {
  if (processing.value) return
  processing.value = true

  try {
    await request.post('/room/rejectInvite', {
      roomCode: inviteData.value.roomCode,
      userId: userStore.user?.id,
      inviterId: inviteData.value.inviterId
    })

    ElMessage.info('已拒绝邀请')
    visible.value = false
  } catch (error) {
    console.error('拒绝邀请失败:', error)
    ElMessage.error('操作失败，请稍后重试')
  } finally {
    processing.value = false
  }
}

// 游戏名称映射
const gameNameMap = {
  'gomoku': '五子棋',
  'tank-battle': '坦克大战',
  'minesweeper': '扫雷',
  '2048': '2048'
}

const gameName = computed(() => {
  return gameNameMap[inviteData.value?.gameName] || inviteData.value?.gameName || '游戏'
})

// 暴露方法给父组件
defineExpose({
  showInvite
})
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="visible" class="invite-overlay" @click.self="visible = false">
        <div class="invite-dialog">
          <div class="invite-header">
            <Icon icon="mdi:email-open" width="48" class="invite-icon" />
            <h3>房间邀请</h3>
          </div>

          <div class="invite-body">
            <div class="inviter-info">
              <img :src="inviteData?.inviterAvatar || '/image/default-avatar.jpg'" :alt="inviteData?.inviterName"
                class="inviter-avatar" />
              <div class="invite-message">
                <strong>{{ inviteData?.inviterName }}</strong> 邀请你进入
                <span class="game-badge">{{ gameName }}</span> 游戏房间
              </div>
            </div>

            <div class="room-info">
              <div class="info-item">
                <Icon icon="mdi:key" width="20" />
                <span>房间码: <strong>{{ inviteData?.roomCode }}</strong></span>
              </div>
            </div>
          </div>

          <div class="invite-actions">
            <button class="btn btn-reject" @click="rejectInvite" :disabled="processing">
              <Icon icon="mdi:close" width="20" />
              拒绝
            </button>
            <button class="btn btn-accept" @click="acceptInvite" :disabled="processing">
              <Icon icon="mdi:check" width="20" />
              接受邀请
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.invite-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  -webkit-backdrop-filter: blur(4px);
  backdrop-filter: blur(4px);
}

.invite-dialog {
  background: white;
  border-radius: 20px;
  width: 90%;
  max-width: 480px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  overflow: hidden;
  animation: slideUp 0.3s ease-out;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(30px) scale(0.95);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.invite-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 30px 24px;
  text-align: center;
  color: white;
}

.invite-icon {
  margin-bottom: 12px;
  animation: bounce 0.6s ease-out;
}

@keyframes bounce {

  0%,
  100% {
    transform: translateY(0);
  }

  50% {
    transform: translateY(-10px);
  }
}

.invite-header h3 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 700;
}

.invite-body {
  padding: 32px 24px;
}

.inviter-info {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.inviter-avatar {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  object-fit: cover;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  border: 3px solid #fff;
}

.invite-message {
  flex: 1;
  font-size: 1.05rem;
  line-height: 1.6;
  color: #333;
}

.invite-message strong {
  color: #667eea;
  font-weight: 700;
}

.game-badge {
  display: inline-block;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 0.9rem;
  font-weight: 600;
  margin: 0 4px;
}

.room-info {
  background: #f8f9fa;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid #e9ecef;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #555;
  font-size: 0.95rem;
}

.info-item strong {
  color: #667eea;
  font-size: 1.1rem;
  letter-spacing: 1px;
}

.invite-actions {
  display: flex;
  gap: 12px;
  padding: 0 24px 24px 24px;
}

.btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 20px;
  border: none;
  border-radius: 12px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-reject {
  background: #f1f3f5;
  color: #666;
}

.btn-reject:hover:not(:disabled) {
  background: #e9ecef;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.btn-accept {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.btn-accept:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.4);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
