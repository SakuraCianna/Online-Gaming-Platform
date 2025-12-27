<script setup>
import { ref, onMounted, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import gsap from 'gsap'
import request from '../../config/api.js'
import { useUserStore } from '../../config/user.js'

const userStore = useUserStore()
// 1. 获取游戏名
const route = useRoute()
const router = useRouter()
const gameName = computed(() => route.params.game)

// 2. 配置模板
const roomConfigTemplates = {
  gomoku: {
    title: '五子棋房间',
    boardSize: '17x17', // 五子棋独有
    isPrivate: false,
  },
  'tank_battle': {
    title: '坦克大战房间',
    isPrivate: false,
  }
  // 其他游戏...
}

// 生成4位随机码
function genRoomCode() {
  return Math.floor(1000 + Math.random() * 9000).toString()
}

// 异步生成唯一房间码（不限制尝试次数）
async function genUniqueRoomCode() {
  while (true) {
    const code = genRoomCode()
    // 调用后端校验
    const res = await request.get('/room/checkRoomCode', { params: { roomCode: code } })
    if (!res.data.exists) {
      return code
    }
  }
}

// 4. 当前房间配置
const roomConfig = ref({})
const isLoading = ref(true)

onMounted(async () => {
  await nextTick()

  // 加载配置
  if (roomConfigTemplates[gameName.value]) {
    roomConfig.value = {
      ...roomConfigTemplates[gameName.value],
      roomCode: await genUniqueRoomCode()
    }
  } else {
    // 未知游戏类型
    roomConfig.value = {
      title: '自定义房间',
      isPrivate: false,
      roomCode: await genUniqueRoomCode()
    }
  }

  isLoading.value = false

  // 动画 - 使用更稳定的动画方式
  setTimeout(() => {
    gsap.to('.room-create-container', {
      opacity: 1,
      y: 0,
      duration: 0.8,
      ease: 'power2.out'
    })
  }, 50)
})

// 5. 创建房间事件
import { useRoomStore } from '../../config/room'
const roomStore = useRoomStore()

async function createRoom() {
  try {
    const response = await request.post('/room/create', {
      roomCode: roomConfig.value.roomCode,
      gameName: gameName.value,
      status: 0,
      creatorId: userStore.user?.id,
      isPrivate: roomConfig.value.isPrivate ? 1 : 0
    })

    if (response.data.success) {
      // 保存room到全局store
      roomStore.setCurrentRoom(response.data.room)

      // 跳转到房间页面
      router.push(`/game/${gameName.value}`)
    } else {
      alert('创建房间失败: ' + response.data.message)
    }
  } catch (error) {
    console.error('创建房间时出错:', error)
    alert('创建房间时发生错误，请稍后重试')
  }
}

// 刷新房间码
async function refreshRoomCode() {
  roomConfig.value.roomCode = await genUniqueRoomCode()
}

// 返回游戏中心
function goBackToGameCenter() {
  router.push('/user')
}
</script>

<template>
  <div class="room-create-wrapper">
    <div class="room-create-container" :style="{ opacity: isLoading ? 0 : 1 }">
      <!-- 返回按钮 -->
      <button class="back-btn" @click="goBackToGameCenter">
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none"
          stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M19 12H5M12 19l-7-7 7-7" />
        </svg>
        返回游戏中心
      </button>

      <h2>{{ roomConfig.title }}</h2>

      <div class="form-group">
        <label class="checkbox-container">
          <span class="label-text">私密房间</span>
          <input type="checkbox" v-model="roomConfig.isPrivate" />
          <span class="checkmark"></span>
        </label>
        <div class="hint">私密房间需要输入房间码才能加入</div>
      </div>

      <div class="form-group">
        <label class="label-text" for="room-code-input">房间码</label>
        <div class="code-input-group">
          <input id="room-code-input" type="text" v-model="roomConfig.roomCode" readonly class="code-input" />
          <button @click="refreshRoomCode" type="button" class="refresh-btn" title="重新生成房间码">
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M23 4v6h-6M1 20v-6h6" />
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
            </svg>
          </button>
        </div>
        <div class="hint">4位数字房间码,可点击刷新按钮重新生成</div>
      </div>

      <!-- 五子棋独有配置 -->
      <div class="form-group" v-if="gameName === 'gomoku'">
        <span class="label-text">棋盘大小</span>
        <div class="board-size-container">
          <div class="board-size-display">{{ roomConfig.boardSize }}</div>
        </div>
      </div>

      <!-- 其他游戏独有配置可在此扩展 -->

      <div class="form-group">
        <button class="create-btn" @click="createRoom">
          <span>创建房间</span>
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none"
            stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M9 12h6m-3-3v6m5-13H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V8z" />
            <polyline points="16 2 16 8 22 8" />
          </svg>
        </button>
      </div>
    </div>

    <!-- 加载指示器 -->
    <div v-if="isLoading" class="loading-overlay">
      <div class="loading-spinner"></div>
      <p>加载中...</p>
    </div>
  </div>
</template>

<style scoped>
.room-create-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
  overflow: hidden;
}

.room-create-container {
  max-width: 480px;
  width: 100%;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.25);
  padding: 40px 36px;
  text-align: left;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
  position: relative;
  transform: translateY(20px);
  opacity: 0;
}

.back-btn {
  position: absolute;
  top: 20px;
  left: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f8f9fa;
  border: 2px solid #e6e9ef;
  border-radius: 10px;
  padding: 8px 16px;
  font-size: 0.9rem;
  color: #232946;
  cursor: pointer;
  transition: all 0.3s;
  z-index: 10;
}

.back-btn:hover {
  background: #4ecdc4;
  border-color: #4ecdc4;
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 4px 10px rgba(78, 205, 196, 0.3);
}

h2 {
  margin: 20px 0 32px;
  color: #232946;
  font-size: 1.8rem;
  font-weight: 700;
  text-align: center;
  position: relative;
  padding-bottom: 12px;
}

h2::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 60px;
  height: 4px;
  background: linear-gradient(90deg, #4ecdc4, #38b2ac);
  border-radius: 2px;
}

.form-group {
  margin-bottom: 28px;
}

.label-text {
  display: block;
  font-size: 1rem;
  color: #232946;
  font-weight: 600;
  margin-bottom: 10px;
}

.checkbox-container {
  display: flex;
  /* 或 inline-flex */
  align-items: center;
  position: relative;
  padding-left: 35px;
  padding-top: 0;
  padding-bottom: 0;
  min-height: 32px;
  /* 保证高度 */
  cursor: pointer;
  font-size: 16px;
  -webkit-user-select: none;
  user-select: none;
  border-radius: 10px;
  transition: background-color 0.3s;
}

.checkbox-container:hover {
  background-color: #f8f9fa;
}

.checkbox-container input {
  position: absolute;
  opacity: 0;
  cursor: pointer;
  height: 0;
  width: 0;
}

.checkmark {
  position: absolute;
  top: 50%;
  left: 0;
  transform: translateY(-50%);
  height: 24px;
  width: 24px;
  background-color: #f8f9fa;
  border: 2px solid #ddd;
  border-radius: 6px;
  transition: all 0.2s;
}

.checkbox-container:hover input~.checkmark {
  border-color: #4ecdc4;
}

.checkbox-container input:checked~.checkmark {
  background-color: #4ecdc4;
  border-color: #4ecdc4;
}

.checkmark:after {
  content: "";
  position: absolute;
  display: none;
}

.checkbox-container input:checked~.checkmark:after {
  display: block;
}

.checkbox-container .checkmark:after {
  left: 7px;
  top: 3px;
  width: 6px;
  height: 11px;
  border: solid white;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}

.room-code-container {
  margin-top: 10px;
}

.code-input-group {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.code-input {
  width: 120px;
  padding: 14px 18px;
  border: 2px solid #e6e9ef;
  border-radius: 12px;
  font-size: 1.4rem;
  font-weight: 700;
  letter-spacing: 2px;
  text-align: center;
  color: #232946;
  background-color: #f8f9fa;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.1);
}

.code-input:focus {
  outline: none;
  border-color: #4ecdc4;
  box-shadow: 0 0 0 3px rgba(78, 205, 196, 0.2);
}

.refresh-btn {
  margin-left: 16px;
  padding: 12px;
  background: #f8f9fa;
  border: 2px solid #e6e9ef;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.05);
}

.refresh-btn:hover {
  background: #4ecdc4;
  border-color: #4ecdc4;
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 4px 10px rgba(78, 205, 196, 0.3);
}

.board-size-container {
  display: inline-block;
}

.board-size-display {
  padding: 12px 24px;
  background: #f8f9fa;
  border: 2px solid #e6e9ef;
  border-radius: 12px;
  font-weight: 600;
  color: #232946;
  text-align: center;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.05);
}

.hint {
  font-size: 0.85rem;
  color: #7b89a8;
  margin-top: 8px;
  font-style: italic;
}

.create-btn {
  width: 100%;
  padding: 16px 24px;
  background: linear-gradient(90deg, #4ecdc4, #38b2ac);
  color: #fff;
  border: none;
  border-radius: 12px;
  font-size: 1.1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 10px;
  box-shadow: 0 4px 15px rgba(78, 205, 196, 0.4);
}

.create-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 7px 20px rgba(78, 205, 196, 0.5);
}

.create-btn:active {
  transform: translateY(-1px);
}

.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background: rgba(255, 255, 255, 0.8);
  z-index: 100;
}

.loading-spinner {
  width: 50px;
  height: 50px;
  border: 5px solid #f3f3f3;
  border-top: 5px solid #4ecdc4;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 15px;
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }

  100% {
    transform: rotate(360deg);
  }
}

@media (max-width: 480px) {
  .room-create-wrapper {
    padding: 16px;
    min-height: 100vh;
    align-items: flex-start;
    padding-top: 40px;
  }

  .room-create-container {
    padding: 24px 20px;
    border-radius: 16px;
  }

  .back-btn {
    position: relative;
    top: 0;
    left: 0;
    margin-bottom: 16px;
    width: 100%;
    justify-content: center;
    padding: 10px 16px;
    font-size: 0.85rem;
  }

  .back-btn svg {
    width: 20px;
    height: 20px;
  }

  h2 {
    font-size: 1.4rem;
    margin-top: 0;
    margin-bottom: 24px;
    padding-bottom: 10px;
  }

  h2::after {
    width: 50px;
    height: 3px;
  }

  .form-group {
    margin-bottom: 20px;
  }

  .label-text {
    font-size: 0.9rem;
    margin-bottom: 8px;
  }

  .checkbox-container {
    padding-left: 32px;
    font-size: 14px;
  }

  .checkmark {
    width: 22px;
    height: 22px;
  }

  .checkbox-container .checkmark:after {
    left: 6px;
    top: 2px;
    width: 5px;
    height: 10px;
  }

  .code-input-group {
    flex-direction: row;
    gap: 10px;
  }

  .code-input {
    flex: 1;
    width: auto;
    min-width: 0;
    padding: 12px 14px;
    font-size: 1.2rem;
    letter-spacing: 3px;
  }

  .refresh-btn {
    margin-left: 0;
    padding: 12px;
    flex-shrink: 0;
  }

  .refresh-btn svg {
    width: 18px;
    height: 18px;
  }

  .board-size-display {
    padding: 10px 20px;
    font-size: 0.95rem;
  }

  .hint {
    font-size: 0.8rem;
    margin-top: 6px;
  }

  .create-btn {
    padding: 14px 20px;
    font-size: 1rem;
    gap: 10px;
    margin-top: 8px;
  }

  .create-btn svg {
    width: 18px;
    height: 18px;
  }

  .loading-spinner {
    width: 40px;
    height: 40px;
    border-width: 4px;
  }

  .loading-overlay p {
    font-size: 14px;
  }
}
</style>