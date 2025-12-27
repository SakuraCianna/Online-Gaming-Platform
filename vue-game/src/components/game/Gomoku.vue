<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, inject, watch } from 'vue'
import { useRouter } from 'vue-router'
import { gsap } from 'gsap'
import request from '../../config/api'
import { useUserStore } from '../../config/user'
import { storeToRefs } from 'pinia'
import { Icon } from '@iconify/vue'
import { ElMessage } from 'element-plus'
import { useRoomStore } from '../../config/room'

const router = useRouter()
const userStore = useUserStore()
const { user, friends } = storeToRefs(userStore)

const roomStore = useRoomStore()
const roomInfo = computed(() => roomStore.currentRoom)

const wsService = inject('wsService')

// UI 状态
const boardRef = ref(null)
const inviting = ref({})
const inviteCooldown = ref({}) // 邀请冷却时间戳
const cooldownTimer = ref(null) // 冷却定时器

// 对手占位（后续对接后端/WS）
const opponent = ref(null)

// 新增：执黑执白状态
const isBlackFirst = ref(true)

// 辅助函数：更新对手颜色
function updateOpponentColor(blackFirst) {
  if (opponent.value?.isAI) {
    opponent.value.color = blackFirst ? 'white' : 'black'
  }
}

async function swapColor() {
  const originalValue = isBlackFirst.value
  isBlackFirst.value = !isBlackFirst.value
  updateOpponentColor(isBlackFirst.value)

  // AI 模式或无房间码，直接返回
  if (isAIGame.value || !roomInfo.value?.roomCode) return

  try {
    await request.get(`/gomoku/${roomInfo.value.roomCode}/swapColor`, {
      params: {
        isBlackFirst: isBlackFirst.value,
        userId: user.value?.id
      }
    })
  } catch (e) {
    // 回滚状态
    isBlackFirst.value = originalValue
    updateOpponentColor(originalValue)
    console.error('发送交换颜色请求失败:', e)
    ElMessage.error('交换颜色失败，请重试')
  }
}

function runStructureAnimation() {
  gsap.from('.gomoku-waiting', { opacity: 0, scale: 0.98, duration: 0.6, ease: 'power2.out' })
  gsap.from(boardRef.value, { scale: 0.92, opacity: 0, duration: 0.8, ease: 'power2.out', delay: 0.1 })
  gsap.from('.seat', { scale: 0.9, opacity: 0, duration: 0.6, ease: 'back.out(1.6)', stagger: 0.1, delay: 0.2 })
}

function runFriendListAnimation() {
  gsap.from('.friend-list .friend-item', { opacity: 0, scale: 0.95, duration: 0.4, ease: 'power2.out', stagger: 0.04, delay: 0.1 })
}

// 加载好友
async function loadFriends() {
  if (!user.value?.id) return
  try {
    const res = await request.get('/friend/getAllFriend', {
      params: { id: user.value.id, page: 1, pageSize: 9999 }
    })
    userStore.setFriends(res?.data?.data || [], res?.data?.total || 0)
  } catch (e) {
    userStore.setFriends([], 0)
    console.error('加载好友列表失败:', e)
  }
}

// 排序：在线优先，其次在玩，最后离线
const sortedFriends = computed(() => {
  const list = friends.value || []
  const score = (f) => {
    if (f?.online && (!f?.currentGame || f?.currentGame === '')) return 0
    if (f?.online) return 1
    return 2
  }
  return [...list].sort((a, b) => {
    const sa = score(a), sb = score(b)
    if (sa !== sb) return sa - sb
    const an = a.name || a.username || ''
    const bn = b.name || b.username || ''
    return an.localeCompare(bn, 'zh-CN')
  })
})

function statusText(f) {
  if (f?.online) {
    return f?.currentGame ? `在玩 ${f.currentGame}` : '在线'
  }
  return '离线'
}

// 强制更新冷却状态（用于触发响应式更新）
const cooldownTrigger = ref(0)

// 检查邀请冷却
function isInviteCooldown(friendId) {
  // 访问 cooldownTrigger 以触发响应式更新
  // eslint-disable-next-line no-unused-vars
  const _ = cooldownTrigger.value

  const lastInviteTime = inviteCooldown.value[friendId]
  if (!lastInviteTime) return false

  const now = Date.now()
  const cooldownTime = 5000 // 5秒冷却
  return (now - lastInviteTime) < cooldownTime
}

// 获取剩余冷却时间（秒）
function getRemainingCooldown(friendId) {
  // 访问 cooldownTrigger 以触发响应式更新
  // eslint-disable-next-line no-unused-vars
  const _ = cooldownTrigger.value

  const lastInviteTime = inviteCooldown.value[friendId]
  if (!lastInviteTime) return 0

  const now = Date.now()
  const cooldownTime = 5000
  const remaining = cooldownTime - (now - lastInviteTime)
  return Math.max(0, Math.ceil(remaining / 1000))
}

// 启动冷却倒计时更新器
function startCooldownTimer() {
  if (cooldownTimer.value) return

  cooldownTimer.value = setInterval(() => {
    // 递增触发器以强制重新计算
    cooldownTrigger.value++

    // 清理已过期的冷却记录
    const now = Date.now()
    for (const friendId of Object.keys(inviteCooldown.value)) {
      if (now - inviteCooldown.value[friendId] >= 5000) {
        delete inviteCooldown.value[friendId]
      }
    }

    // 如果没有活跃的冷却，停止定时器
    if (Object.keys(inviteCooldown.value).length === 0) {
      stopCooldownTimer()
    }
  }, 100) // 每100ms更新一次
}

// 停止冷却倒计时更新器
function stopCooldownTimer() {
  if (cooldownTimer.value) {
    clearInterval(cooldownTimer.value)
    cooldownTimer.value = null
  }
}

async function inviteFriend(friend) {
  if (!friend?.id || !user.value?.id) return

  // 检查冷却时间
  if (isInviteCooldown(friend.id)) {
    const remaining = getRemainingCooldown(friend.id)
    ElMessage.warning(`请等待 ${remaining} 秒后再邀请该好友`)
    return
  }

  // 检查好友是否在线且空闲
  if (!friend.online) {
    ElMessage.warning('该好友当前不在线')
    return
  }

  if (friend.currentGame && friend.currentGame !== '') {
    ElMessage.warning('该好友正在游戏中，无法邀请')
    return
  }

  inviting.value[friend.id] = true
  try {
    const resp = await request.post('/room/invite', {
      userId: user.value.id,
      friendId: friend.id,
      roomCode: roomInfo.value?.roomCode,
      gameName: 'gomoku'
    })

    if (resp?.data?.success || resp?.data?.code === 200) {
      ElMessage.success('邀请已发送，等待好友响应')
      // 设置冷却时间
      inviteCooldown.value[friend.id] = Date.now()
      // 启动冷却定时器
      startCooldownTimer()
    } else {
      ElMessage.error(resp?.data?.message || '邀请发送失败')
    }
  } catch (e) {
    ElMessage.error('邀请失败，请稍后重试')
    console.error('invite failed', e)
  } finally {
    inviting.value[friend.id] = false
  }
}

const isAIGame = ref(false)
const aiDifficulty = ref('medium') // 默认中等难度：easy, medium, hard

async function startAIGame() {
  if (!roomInfo.value?.roomCode) {
    ElMessage.error('房间信息不存在')
    return
  }

  try {
    // 调用后端接口，将AI玩家信息存入Redis
    const response = await request.post('/room/addAI', {
      roomCode: roomInfo.value.roomCode,
      userId: user.value?.id,
      aiDifficulty: aiDifficulty.value
    })

    if (response.data.success || response.data.code === 200) {
      isAIGame.value = true
      opponent.value = {
        id: 0, // AI的userId为0
        name: 'AI Player',
        avatar: '/image/default-avatar.jpg',
        isAI: true,
        color: isBlackFirst.value ? 'white' : 'black',
        difficulty: aiDifficulty.value
      }
      ElMessage.success(`已进入AI对战模式 (${getDifficultyText(aiDifficulty.value)})`)
    } else {
      ElMessage.error(response.data.message || '添加AI失败')
    }
  } catch (error) {
    console.error('添加AI失败:', error)
    ElMessage.error('添加AI失败，请重试')
  }
}

function getDifficultyText(difficulty) {
  const map = {
    easy: '简单',
    medium: '中等',
    hard: '困难'
  }
  return map[difficulty] || '中等'
}

function updateAIDifficulty() {
  if (opponent.value && opponent.value.isAI) {
    opponent.value.difficulty = aiDifficulty.value
    ElMessage.info(`已切换至${getDifficultyText(aiDifficulty.value)}难度`)
  }
}

async function exitRoom() {
  if (isAIGame.value) {
    // AI对战，直接本地清理
    await userStore.clearCurrentGame()
    roomStore.clearCurrentRoom()
    roomStore.clearGameConfig()
    router.push('/user')
  } else {
    // 真人对战，调用后端接口
    try {
      // 检查房间信息是否存在
      if (!roomInfo.value || !roomInfo.value.roomCode) {
        console.warn('房间信息不存在，跳过后端退出')
        await userStore.clearCurrentGame()
        roomStore.clearCurrentRoom()
        roomStore.clearGameConfig()
        router.push('/user')
        return
      }

      await request.post('/room/leave', {
        roomCode: roomInfo.value.roomCode,
        userId: user.value?.id,
        username: user.value?.username
      })
      await userStore.clearCurrentGame()
      roomStore.clearCurrentRoom()
      roomStore.clearGameConfig()
    } catch (error) {
      console.error('退出房间失败:', error)
      // 即使失败也清理本地状态
      await userStore.clearCurrentGame()
      roomStore.clearCurrentRoom()
      roomStore.clearGameConfig()
    } finally {
      router.push('/user')
    }
  }
}

async function startGame() {
  // 检查是否有对手
  if (!opponent.value) {
    ElMessage.warning('请等待对手加入房间')
    return
  }

  // 检查对手是否为AI
  if (opponent.value.isAI) {
    ElMessage.info('即将开始与AI的对战...')
  }

  try {
    // 通知后端游戏开始
    if (roomInfo.value?.roomCode) {
      const isOwnerBlack = isBlackFirst.value
      const opponentId = opponent.value.isAI ? 0 : opponent.value.id
      const player1Id = isOwnerBlack ? user.value?.id : opponentId
      const player2Id = isOwnerBlack ? opponentId : user.value?.id

      await request.post(`/gomoku/${roomInfo.value.roomCode}/start`, {
        userId: user.value?.id,           // 房主ID
        player1Id: player1Id,             // 先手玩家ID（黑棋），AI为0
        player2Id: player2Id,             // 后手玩家ID（白棋），AI为0
        isAIGame: opponent.value.isAI || false  // 是否AI对战
      })
    }

    // 保存游戏配置到 roomStore
    const ownerColor = isBlackFirst.value ? 'black' : 'white'
    const joinerColor = isBlackFirst.value ? 'white' : 'black'

    roomStore.setGameConfig({
      opponent: opponent.value,
      isBlackFirst: isBlackFirst.value,
      myColor: isOwner.value ? ownerColor : joinerColor,
      isAIGame: opponent.value?.isAI || false,
      aiDifficulty: opponent.value?.difficulty || 'medium'
    })

    // 退出动画
    const tl = gsap.timeline({
      onComplete: () => {
        // 动画完成后跳转
        router.push({
          name: 'GomokuGame',
          params: {
            roomCode: roomInfo.value?.roomCode
          }
        })
      }
    })

    // 动画序列
    tl.to('.friend-list .friend-item', {
      x: 100,
      opacity: 0,
      duration: 0.3,
      stagger: 0.05,
      ease: 'power2.in'
    })
      .to('.right', {
        x: 100,
        opacity: 0,
        duration: 0.4,
        ease: 'power2.in'
      }, '-=0.2')
      .to('.top-bar', {
        y: -50,
        opacity: 0,
        duration: 0.3,
        ease: 'power2.in'
      }, '-=0.3')
      .to('.seat', {
        scale: 0.8,
        opacity: 0,
        duration: 0.4,
        stagger: 0.1,
        ease: 'power2.in'
      }, '-=0.2')
      .to('.board', {
        scale: 1.2,
        opacity: 0,
        duration: 0.5,
        ease: 'power2.in'
      }, '-=0.3')

  } catch (e) {
    console.error('开始游戏失败:', e)
    ElMessage.error('无法开始游戏，请重试')
  }
}

const isOwner = computed(() => user.value?.id === roomInfo.value?.creatorId)

// 踢出对手
async function kickOpponent() {
  if (!opponent.value) return

  // 统一调用后端接口（包括AI）
  try {
    await request.post('/room/kick', {
      roomCode: roomInfo.value?.roomCode,
      kickerId: user.value?.id,
      kickerName: user.value?.username,
      kickedUserId: opponent.value.id || 0,
      kickedUserName: opponent.value.name
    })

    opponent.value = null
    isAIGame.value = false
    ElMessage.success('已将对手移出房间')
  } catch (e) {
    console.error('踢出玩家失败:', e)
    ElMessage.error('踢出玩家失败，请重试')
  }
}

// WebSocket 消息处理函数
function handleRoomMessage(data) {
  if (data.type === 'memberJoined') {
    if (data.userId !== user.value?.id) {
      opponent.value = {
        id: data.userId,
        name: data.username,
        avatar: data.avatar || '/image/default-avatar.jpg',
        isAI: data.isAI || false,
        difficulty: data.aiDifficulty || 'medium'
      }
      ElMessage.success(`${data.username} 已加入房间`)
    }
  } else if (data.type === 'memberLeft') {
    ElMessage.info(`${data.username || '某玩家'} 已退出房间`)
    if (data.userId === opponent.value?.id) {
      opponent.value = null
    }
  } else if (data.type === 'roomDestroyed') {
    ElMessage.warning('房主已离开，房间已解散')
    roomStore.clearCurrentRoom()
    roomStore.clearGameConfig()
    router.push('/user')
  } else if (data.type === 'swapColor') {
    if (data.userId !== user.value?.id) {
      isBlackFirst.value = data.isBlackFirst
      ElMessage.info('房主已交换黑白棋')
    }
  } else if (data.type === 'kicked') {
    handleKickedEvent(data)
  } else if (data.type === 'gameStart') {
    handleGameStartEvent(data)
  }
}

function handleKickedEvent(data) {
  if (data.kickedUserId === user.value?.id) {
    ElMessage.warning(`你已被房主 ${data.kickerName || ''} 移出房间`)
      ; (async () => {
        await userStore.clearCurrentGame()
        roomStore.clearCurrentRoom()
        roomStore.clearGameConfig()
        setTimeout(() => router.push('/user'), 1500)
      })()
  } else if (data.kickedUserId === opponent.value?.id) {
    opponent.value = null
    ElMessage.info(`${data.kickedUserName || '玩家'} 已被移出房间`)
  }
}

function handleGameStartEvent(data) {
  if (data.player1Id && data.player2Id) {
    const amIPlayer1 = user.value?.id === data.player1Id
    const myGameColor = amIPlayer1 ? 'black' : 'white'
    const isBlackFirst = data.isBlackFirst === undefined ? true : data.isBlackFirst

    ElMessage.success('游戏即将开始...')

    // 保存游戏配置到 roomStore（非房主玩家）
    roomStore.setGameConfig({
      opponent: opponent.value,
      isBlackFirst: isBlackFirst,
      myColor: myGameColor,
      isAIGame: opponent.value?.isAI || false,
      aiDifficulty: opponent.value?.difficulty || 'medium'
    })

    setTimeout(() => {
      router.push({
        name: 'GomokuGame',
        params: { roomCode: roomInfo.value?.roomCode }
      })
    }, 800)
  }
}

// 监听好友状态变化（可用于添加上线/离线提醒等功能）
watch(friends, () => {
  // 可以在这里添加好友状态变化的UI提示
}, { deep: true })

// 加载房间玩家列表
async function loadRoomPlayers() {
  if (!roomInfo.value?.roomCode) return

  try {
    const res = await request.get('/room/players', {
      params: { roomCode: roomInfo.value.roomCode }
    })

    if (res.data.success && res.data.players) {
      const players = res.data.players
      // 找到对手（不是自己的玩家）
      const opponentData = players.find(p => p.userId !== user.value?.id)

      if (opponentData) {
        opponent.value = {
          id: opponentData.userId,
          name: opponentData.username,
          avatar: opponentData.avatar || '/image/default-avatar.jpg',
          isAI: opponentData.isAi === 1,
          difficulty: opponentData.aiType || 'medium'
        }
      }
    }
  } catch (e) {
    console.error('加载房间玩家失败:', e)
  }
}

// 添加：检查房间状态
onMounted(async () => {
  await nextTick()

  // 1. 立即执行结构动画，避免等待数据加载导致的页面闪烁
  runStructureAnimation()

  // 检查房间信息
  if (!roomInfo.value || !roomInfo.value.roomCode) {
    ElMessage.warning('房间信息不存在，请重新创建房间')
    router.push('/user')
    return
  }

  // 2. 并行加载数据，提高速度
  await Promise.all([
    loadFriends(),
    loadRoomPlayers()
  ])

  // 3. 数据加载完成后，执行好友列表的入场动画
  await nextTick()
  runFriendListAnimation()

  // 进入等待页时先标记当前游戏（好友可见），离开时清除
  try {
    await userStore.setCurrentGame('五子棋')
  } catch (e) {
    console.error('设置游戏状态失败:', e)
  }

  // 注册 WebSocket 订阅
  if (wsService && roomInfo.value?.roomCode) {
    wsService.subscribe(`/topic/room/${roomInfo.value.roomCode}`, (msg) => {
      try {
        const data = JSON.parse(msg.body)
        handleRoomMessage(data)
      } catch (e) {
        console.error('处理房间消息失败:', e, msg)
      }
    })
  }
})

onBeforeUnmount(async () => {
  try { await userStore.clearCurrentGame() } catch (e) { console.error('清除游戏状态失败:', e) }
  // 清理冷却定时器
  stopCooldownTimer()
  // 取消 WebSocket 订阅
  if (wsService && roomInfo.value?.roomCode) {
    wsService.unsubscribe(`/topic/room/${roomInfo.value.roomCode}`)
  }
})

// 修改：基于房主身份和设置计算实际的棋子颜色
const myColor = computed(() => {
  if (isOwner.value) {
    // 房主：按照 isBlackFirst 设置
    return isBlackFirst.value ? 'black' : 'white'
  } else {
    // 加入者：与房主相反
    return isBlackFirst.value ? 'white' : 'black'
  }
})

const opponentColor = computed(() => {
  return myColor.value === 'black' ? 'white' : 'black'
})

const myRole = computed(() => {
  return myColor.value === 'black' ? '黑棋' : '白棋'
})

const opponentRole = computed(() => {
  return opponentColor.value === 'black' ? '黑棋' : '白棋'
})

// 添加一个计算属性
const canSwapColor = computed(() => {
  // 只有房主可以交换
  if (!isOwner.value) return false
  // AI 模式下可以随时交换
  if (isAIGame.value) return true
  // 真人模式下，如果没有对手可以交换
  if (!opponent.value) return true
  // 如果有对手但还没开始游戏，可以交换（根据你的需求调整）
  return true
})
</script>

<template>
  <div class="gomoku-waiting">
    <div class="left">
      <div class="top-bar">
        <div class="title">
          <Icon icon="mdi:chess-king" width="22" />
          <span>五子棋对战房间</span>
          <div class="room-code" v-if="roomInfo?.roomCode">
            <Icon icon="mdi:key-variant" width="16" />
            <span>房间码{{ roomInfo.roomCode }}</span>
          </div>
        </div>
        <!-- 新增：中间提示 -->
        <div class="top-hint">
          可从右侧邀请在线好友加入一起对战
        </div>
        <div class="actions">
          <button class="btn exit" @click="exitRoom">
            <Icon icon="lucide:log-out" width="18" />
            <span>退出房间</span>
          </button>
        </div>
      </div>

      <div class="stage">
        <div class="board" ref="boardRef">
          <div class="grid"></div>
          <div class="seats">
            <div class="seat">
              <div class="avatar">
                <img :src="user?.avatar || '/image/default-avatar.jpg'" alt="me" />
                <!-- 修改：使用计算属性 -->
                <div class="piece" :class="myColor"></div>
              </div>
              <div class="meta">
                <div class="name">{{ user?.username || user?.name || '我' }}</div>
                <!-- 修改：使用计算属性 -->
                <div class="role">{{ myRole }}</div>
              </div>
            </div>

            <div class="vs-area">
              <!-- 交换黑白棋按钮：仅房主可用 -->
              <button class="swap-btn" @click="swapColor" :disabled="!canSwapColor">
                <Icon icon="mdi:swap-horizontal" width="20" />
                交换黑白棋
              </button>
              <div class="vs">VS</div>
              <!-- 开始游戏按钮：仅房主可用 -->
              <button class="btn start" @click="startGame" :disabled="!isOwner">
                <Icon icon="lucide:play" width="18" />
                <span>开始游戏</span>
              </button>
            </div>

            <div class="seat opponent-seat">
              <div class="avatar">
                <img :src="opponent?.avatar || '/image/default-avatar.jpg'" alt="opponent" />
                <!-- 修改：使用计算属性 -->
                <div class="piece" :class="opponentColor"></div>
              </div>
              <div class="meta">
                <div class="name">{{ opponent?.name || '等待加入...' }}</div>
                <!-- 修改：使用计算属性 -->
                <div class="role">{{ opponentRole }}</div>
              </div>
              <!-- AI对战按钮：仅房主可用且无对手时显示 -->
              <button v-if="!opponent && isOwner" class="ai-btn" @click="startAIGame">
                <Icon icon="mdi:robot" width="16" />
                与AI对战
              </button>
              <!-- 踢人按钮：仅房主且有对手时显示 -->
              <button v-if="opponent && isOwner" class="kick-btn" @click="kickOpponent">
                <Icon icon="mdi:close" width="16" />
                踢出
              </button>
              <!-- AI难度选择：仅在AI对战时显示 -->
              <div v-if="opponent && opponent.isAI && isOwner" class="ai-difficulty-selector">
                <label class="difficulty-label" for="ai-difficulty-select">
                  <Icon icon="mdi:tune" width="14" />
                  <span>难度</span>
                </label>
                <select id="ai-difficulty-select" v-model="aiDifficulty" class="difficulty-select"
                  @change="updateAIDifficulty">
                  <option value="easy">简单</option>
                  <option value="medium">中等</option>
                  <option value="hard">困难</option>
                </select>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="right">
      <div class="friends-header">
        <Icon icon="lucide:users" width="18" />
        <span>好友</span>
      </div>
      <div class="friend-list">
        <div class="friend-item" v-for="f in sortedFriends" :key="f.id">
          <div class="friend-info">
            <div class="avatar">
              <img :src="f.avatar || '/image/default-avatar.jpg'" :alt="f.name || f.username" />
              <span class="dot" :class="{ online: f.online }"></span>
            </div>
            <div class="meta">
              <div class="name">{{ f.name || f.username }}</div>
              <div class="status" :class="{ online: f.online, offline: !f.online }">
                <Icon icon="lucide:circle" width="10" />
                <span>{{ statusText(f) }}</span>
              </div>
            </div>
          </div>
          <button v-if="f.online" class="invite-btn" :class="{ cooldown: isInviteCooldown(f.id) }"
            :disabled="!!inviting[String(f.id)] || isInviteCooldown(f.id)" @click="inviteFriend(f)"
            :title="isInviteCooldown(f.id) ? `冷却中，还需 ${getRemainingCooldown(f.id)} 秒` : '邀请加入房间'">
            <Icon icon="lucide:user-plus" width="16" />
            <span>{{ inviting[String(f.id)] ? '邀请中...' : (isInviteCooldown(f.id) ? `${getRemainingCooldown(f.id)}秒` :
              '邀请') }}</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.gomoku-waiting {
  display: flex;
  gap: 16px;
  width: 100%;
  height: 100vh;
  /* 改为视口高度，确保固定高度 */
  padding: 16px;
  box-sizing: border-box;
  background: linear-gradient(135deg, #f6f7fb 0%, #eef1f8 100%);
  /* 强制显示垂直滚动条，避免出现/消失 */
  overflow-y: scroll;
  overflow-x: hidden;
  /* 移除 scrollbar-gutter，直接显示滚动条更稳定 */
}

.left {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
  position: relative;
}

.top-hint {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  color: #888;
  font-size: 14px;
  font-weight: 500;
  pointer-events: none;
}

.title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: #333;
}

.room-code {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 12px;
  padding: 4px 10px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.25);
  letter-spacing: 1px;
}

.actions {
  display: flex;
  gap: 8px;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  border-radius: 10px;
  padding: 10px 14px;
  cursor: pointer;
  font-size: 14px;
  transition: all .2s ease;
}

.btn.exit {
  background: #f1f3f5;
  color: #333;
}

.btn.exit:hover {
  background: #e9ecef;
}

.btn.start {
  background: #667eea;
  color: #fff;
}

.btn.start:hover {
  background: #5a67d8;
}

.stage {
  flex: 1;
  margin-top: 16px;
  background: #ffffff;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
}

.board {
  position: relative;
  flex: 1;
  border-radius: 12px;
  overflow: hidden;
  background: #eac488;
  /* 木色底 */
  display: flex;
  align-items: center;
  justify-content: center;
}

.board .grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(#0000001a 1px, transparent 1px),
    linear-gradient(90deg, #0000001a 1px, transparent 1px);
  background-size: calc(100%/17) calc(100%/17), calc(100%/17) calc(100%/17);
  background-position: center;
  pointer-events: none;
}

.seats {
  position: relative;
  width: 100%;
  max-width: 860px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 20px;
  align-items: center;
  padding: 10px;
}

.vs-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.vs {
  font-weight: 800;
  color: #333;
  background: #ffffff80;
  padding: 8px 14px;
  border-radius: 999px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.swap-btn {
  background: #f1f3f5;
  color: #333;
  border: none;
  border-radius: 8px;
  padding: 6px 12px;
  margin-bottom: 4px;
  cursor: pointer;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: background .2s;
}

.swap-btn:hover:not(:disabled) {
  background: #e9ecef;
}

.swap-btn:disabled {
  background: #e9ecef;
  color: #999;
  cursor: not-allowed;
  opacity: 0.6;
}

.btn.start:hover:not(:disabled) {
  background: #5a67d8;
}

.btn.start:disabled {
  background: #ccc;
  color: #999;
  cursor: not-allowed;
  opacity: 0.6;
}

.seat {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #ffffffc9;
  padding: 16px 20px;
  border-radius: 14px;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.08);
  position: relative;
  min-height: 100px;
}

.seat .avatar {
  position: relative;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  overflow: hidden;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.15);
}

.seat .avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.piece {
  position: absolute;
  right: -6px;
  bottom: -6px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 2px solid #fff;
}

.piece.black {
  background: #222;
}

.piece.white {
  background: #fff;
}

.seat .meta .name {
  font-weight: 700;
  color: #222;
}

.seat .meta .role {
  font-size: 12px;
  color: #666;
}

.hint {
  margin-top: 14px;
  font-size: 13px;
  color: #666;
  text-align: center;
}

.right {
  /* 使用更严格的固定宽度控制 */
  flex: none;
  width: 340px;
  min-width: 340px;
  max-width: 340px;
  box-sizing: border-box;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  padding: 12px;
  height: calc(100vh - 32px);
  /* 固定高度，减去外层 padding */
  overflow: hidden;
  /* 添加 transform 创建新的层叠上下文，避免动画影响 */
  transform: translateZ(0);
}

.friends-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 6px 12px 6px;
  border-bottom: 1px solid #f0f0f0;
  font-weight: 700;
  color: #333;
  flex-shrink: 0;
}

.friend-list {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: none;
  -ms-overflow-style: none;
  min-height: 0;
  /* 添加 contain 属性，限制布局影响范围 */
  contain: layout style;
}

.friend-list::-webkit-scrollbar {
  display: none;
}

.friend-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 12px;
  padding: 10px;
  margin-bottom: 8px;
  transition: background .2s ease, transform .2s ease;
  min-width: 0;
  /* 添加 transform 避免影响父容器布局 */
  transform: translateZ(0);
}

.friend-item:last-child {
  margin-bottom: 0;
}

.friend-item:hover {
  background: #f1f3f4;
  transform: translateY(-1px);
}

.friend-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.friend-info .avatar {
  position: relative;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  overflow: hidden;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
}

.friend-info .avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.dot {
  position: absolute;
  right: 2px;
  bottom: 2px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #bbb;
  border: 2px solid #fff;
  transition: background-color 0.3s ease, box-shadow 0.3s ease;
}

.dot.online {
  background: #28a745;
  animation: pulse 2s infinite;
}

@keyframes pulse {

  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(40, 167, 69, 0.7);
  }

  50% {
    box-shadow: 0 0 0 4px rgba(40, 167, 69, 0);
  }
}

.friend-info .meta .name {
  font-weight: 600;
  color: #333;
}

.friend-info .meta .status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #666;
  transition: color 0.3s ease;
}

.friend-info .meta .status.online {
  color: #28a745;
}

.friend-info .meta .status.offline {
  color: #999;
}

.invite-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #667eea;
  color: #fff;
  border: none;
  border-radius: 10px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background .2s ease;
  font-size: 12px;
}

.invite-btn:hover {
  background: #5a67d8;
}

.invite-btn:disabled {
  opacity: .7;
  cursor: not-allowed;
}

.invite-btn.cooldown {
  background: #9ca3af;
  cursor: not-allowed;
  opacity: 0.6;
}

.invite-btn.cooldown:hover {
  background: #9ca3af;
}

.ai-btn {
  margin-top: 8px;
  background: #ffd600;
  color: #222;
  border: none;
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  transition: background .2s;
}

.ai-btn:hover {
  background: #ffe066;
}

.opponent-seat .ai-btn {
  position: absolute;
  right: 10px;
  bottom: 10px;
  margin-top: 0;
}

.kick-btn {
  position: absolute;
  right: 14px;
  top: 14px;
  background: #ff4d4f;
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 5px 12px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  transition: background .2s;
}

.kick-btn:hover {
  background: #ff7875;
}

.ai-difficulty-selector {
  position: absolute;
  right: 14px;
  bottom: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
  background: #f8f9fa;
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid #e0e0e0;
}

.difficulty-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #666;
  font-weight: 500;
  margin: 0;
  cursor: default;
}

.difficulty-select {
  border: 1px solid #d0d0d0;
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 12px;
  color: #333;
  background: #fff;
  cursor: pointer;
  outline: none;
  transition: all .2s;
}

.difficulty-select:hover {
  border-color: #667eea;
}

.difficulty-select:focus {
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.1);
}

@media (max-width: 1024px) {
  .gomoku-waiting {
    flex-direction: column;
    height: 100vh;
    overflow-y: scroll;
    overflow-x: hidden;
  }

  .right {
    width: 100%;
    max-width: 100%;
    min-width: 0;
    flex: none;
    order: -1;
    height: auto;
    max-height: 40vh;
  }
}

@media (max-width: 768px) {
  .gomoku-waiting {
    padding: 12px;
    gap: 12px;
    height: auto;
    min-height: 100vh;
    padding-bottom: 80px;
  }

  .left {
    min-height: auto;
  }

  .top-bar {
    flex-wrap: wrap;
    gap: 8px;
    padding: 10px 12px;
  }

  .top-hint {
    position: static;
    transform: none;
    order: 3;
    width: 100%;
    text-align: center;
    font-size: 12px;
    margin-top: 4px;
  }

  .title {
    font-size: 14px;
  }

  .room-code {
    margin-left: 8px;
    padding: 3px 8px;
    font-size: 11px;
  }

  .btn {
    padding: 8px 12px;
    font-size: 13px;
  }

  .right {
    padding: 8px;
    height: auto;
    max-height: 35vh;
  }

  .friends-header {
    padding: 6px;
    font-size: 14px;
  }

  .friend-item {
    padding: 8px;
    margin-bottom: 6px;
  }

  .friend-info .avatar {
    width: 36px;
    height: 36px;
  }

  .friend-info .meta .name {
    font-size: 13px;
  }

  .friend-info .meta .status {
    font-size: 11px;
  }

  .invite-btn {
    padding: 6px 10px;
    font-size: 11px;
  }

  .stage {
    margin-top: 12px;
    padding: 12px;
  }

  .seats {
    grid-template-columns: 1fr;
    gap: 12px;
    padding: 8px;
  }

  .seat {
    padding: 12px 16px;
    min-height: 80px;
  }

  .seat .avatar {
    width: 48px;
    height: 48px;
  }

  .piece {
    width: 18px;
    height: 18px;
    right: -4px;
    bottom: -4px;
  }

  .seat .meta .name {
    font-size: 14px;
  }

  .seat .meta .role {
    font-size: 11px;
  }

  .vs-area {
    gap: 12px;
    padding: 8px 0;
  }

  .vs {
    font-size: 14px;
    padding: 6px 12px;
  }

  .swap-btn {
    font-size: 12px;
    padding: 5px 10px;
  }

  .btn.start {
    padding: 8px 16px;
    font-size: 13px;
  }

  .ai-btn {
    padding: 5px 10px;
    font-size: 12px;
  }

  .kick-btn {
    padding: 4px 10px;
    font-size: 12px;
    right: 10px;
    top: 10px;
  }

  .ai-difficulty-selector {
    right: 10px;
    bottom: 10px;
    padding: 4px 8px;
  }

  .difficulty-label {
    font-size: 11px;
  }

  .difficulty-select {
    padding: 3px 6px;
    font-size: 11px;
  }
}

@media (max-width: 480px) {
  .gomoku-waiting {
    padding: 8px;
    padding-bottom: 80px;
  }

  .top-bar {
    padding: 8px 10px;
  }

  .title {
    font-size: 13px;
    gap: 4px;
  }

  .title .iconify {
    width: 18px;
  }

  .room-code {
    margin-left: 6px;
    padding: 2px 6px;
    font-size: 10px;
    letter-spacing: 0.5px;
  }

  .btn {
    padding: 6px 10px;
    font-size: 12px;
    gap: 4px;
  }

  .btn .iconify {
    width: 16px;
  }

  .right {
    max-height: 30vh;
    padding: 6px;
  }

  .friends-header {
    font-size: 13px;
    padding: 4px;
  }

  .friend-item {
    padding: 6px;
  }

  .friend-info .avatar {
    width: 32px;
    height: 32px;
  }

  .dot {
    width: 8px;
    height: 8px;
  }

  .friend-info .meta .name {
    font-size: 12px;
  }

  .friend-info .meta .status {
    font-size: 10px;
  }

  .invite-btn {
    padding: 5px 8px;
    font-size: 10px;
    border-radius: 8px;
  }

  .stage {
    padding: 8px;
    margin-top: 8px;
  }

  .board {
    border-radius: 8px;
  }

  .seats {
    gap: 10px;
    padding: 6px;
  }

  .seat {
    padding: 10px 12px;
    border-radius: 10px;
    min-height: 70px;
    gap: 10px;
  }

  .seat .avatar {
    width: 42px;
    height: 42px;
  }

  .piece {
    width: 16px;
    height: 16px;
  }

  .seat .meta .name {
    font-size: 13px;
  }

  .seat .meta .role {
    font-size: 10px;
  }

  .vs-area {
    gap: 8px;
  }

  .vs {
    font-size: 12px;
    padding: 4px 10px;
  }

  .swap-btn {
    font-size: 11px;
    padding: 4px 8px;
  }

  .btn.start {
    padding: 6px 12px;
    font-size: 12px;
  }

  .ai-btn {
    padding: 4px 8px;
    font-size: 11px;
  }

  .kick-btn {
    padding: 3px 8px;
    font-size: 11px;
  }

  .opponent-seat .ai-btn {
    right: 8px;
    bottom: 8px;
  }

  .ai-difficulty-selector {
    padding: 3px 6px;
    gap: 4px;
  }

  .difficulty-label {
    font-size: 10px;
    gap: 2px;
  }

  .difficulty-select {
    padding: 2px 4px;
    font-size: 10px;
  }
}

/* 触摸设备优化 */
@media (hover: none) and (pointer: coarse) {
  .friend-item:hover {
    background: #f8f9fa;
    transform: none;
  }

  .invite-btn:hover {
    background: #667eea;
  }

  .btn.exit:hover {
    background: #f1f3f5;
  }

  .btn.start:hover:not(:disabled) {
    background: #667eea;
  }

  .swap-btn:hover:not(:disabled) {
    background: #f1f3f5;
  }

  .ai-btn:hover {
    background: #ffd600;
  }

  .kick-btn:hover {
    background: #ff4d4f;
  }
}
</style>
