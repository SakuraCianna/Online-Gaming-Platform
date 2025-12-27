<script setup>
import { ref, computed, onMounted, onBeforeUnmount, inject, getTransitionRawChildren } from 'vue'
import { useRouter } from 'vue-router'
import { gsap } from 'gsap'
import { Icon } from '@iconify/vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../config/api'
import { useUserStore } from '../../config/user'
import { useRoomStore } from '../../config/room'
import { storeToRefs } from 'pinia'
import { now } from '@vueuse/core'

const router = useRouter()
const userStore = useUserStore()
const roomStore = useRoomStore()
const { user } = storeToRefs(userStore)
const roomInfo = computed(() => roomStore.currentRoom)

const wsService = inject('wsService')

const BOARD_SIZE = 17 // 棋盘大小（17x17）
const TURN_TIME = 30 // 每回合时间（秒）
const AI_THINK_TIME = 200 // AI思考时间（毫秒）
const AI_PLAYER_ID = 0 // AI玩家的ID标识
const TIMER_RESET_DELAY = 100 // 计时器重置延迟（毫秒）

// 棋子颜色常量
const COLOR_BLACK = 'black'
const COLOR_WHITE = 'white'

// 游戏状态常量
const STATE_PLAYING = 'playing'
const STATE_FINISHED = 'finished'

// 胜利条件
const WIN_COUNT = 5 // 五子连珠

// 游戏状态
const board = ref(new Array(BOARD_SIZE).fill(null).map(() => new Array(BOARD_SIZE).fill(null)))
const currentTurn = ref(COLOR_BLACK)
const myColor = ref(COLOR_BLACK)
const opponentColor = computed(() => myColor.value === COLOR_BLACK ? COLOR_WHITE : COLOR_BLACK)
const gameState = ref(STATE_PLAYING)
const winner = ref(null)
const lastMove = ref(null)
const moveHistory = ref([])

// 对手信息
const opponent = ref(null)

// AI相关
const isAIGame = computed(() => opponent.value?.isAI === true || opponent.value?.id === AI_PLAYER_ID)
const aiDifficulty = computed(() => opponent.value?.difficulty || 'medium')

// 计时器
const myTime = ref(TURN_TIME)
const opponentTime = ref(TURN_TIME)
const timer = ref(null)
const activeTimeouts = ref([]) // 跟踪所有活跃的setTimeout
const timerResetTimeout = ref(null) // 跟踪计时器重置的timeout

// UI 状态
const hoveredCell = ref(null)
const showWinnerDialog = ref(false)
const isPlacingStone = ref(false) // 防止重复点击

// 计算属性
const isMyTurn = computed(() => currentTurn.value === myColor.value)
const myTimeDisplay = computed(() => formatTime(myTime.value))
const opponentTimeDisplay = computed(() => formatTime(opponentTime.value))

// 坐标验证
function isValidPosition(row, col) {
  return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE
}

// 判断是否可以落子
function canPlaceStone(row, col) {
  return isValidPosition(row, col) && board.value[row]?.[col] === null
}

// 创建可跟踪的定时器
function createTrackedTimeout(callback, delay) {
  const timeout = setTimeout(() => {
    callback()
    const index = activeTimeouts.value.indexOf(timeout)
    if (index > -1) activeTimeouts.value.splice(index, 1)
  }, delay)
  activeTimeouts.value.push(timeout)
  return timeout
}

// 时间格式化
function formatTime(seconds) {
  return `${seconds}秒`
}

// 计时器逻辑 - 每回合30秒倒计时
function startTimer() {
  if (timer.value) clearInterval(timer.value)

  timer.value = setInterval(() => {
    if (gameState.value !== STATE_PLAYING) return

    if (isMyTurn.value) {
      myTime.value--
      if (myTime.value <= 0) {
        myTime.value = 0
        handleTimeout('me')
      }
    } else {
      opponentTime.value--
      if (opponentTime.value <= 0) {
        opponentTime.value = 0
        handleTimeout('opponent')
      }
    }
  }, 1000)
}

function stopTimer() {
  if (timer.value) {
    clearInterval(timer.value)
    timer.value = null
  }
}

// 超时处理
async function handleTimeout(who) {
  stopTimer()
  gameState.value = STATE_FINISHED

  if (who === 'me') {
    winner.value = opponentColor.value
    ElMessage.error('超时！你输了')

    // 通知对手玩家超时（仅真人对战）
    if (!isAIGame.value && roomInfo.value?.roomCode) {
      notifyPlayerLeft()
    }
  } else {
    winner.value = myColor.value
    ElMessage.success('对手超时！你赢了')
  }

  // 发送游戏结束数据（超时）
  await sendGameEnd(winner.value)

  showWinnerDialog.value = true
}

// 保存游戏状态快照
function saveGameState() {
  return {
    boardSnapshot: board.value.map(row => [...row]),
    moveHistorySnapshot: [...moveHistory.value],
    lastMoveSnapshot: lastMove.value ? { ...lastMove.value } : null,
    currentTurnSnapshot: currentTurn.value,
    gameStateSnapshot: gameState.value,
    winnerSnapshot: winner.value,
    myTimeSnapshot: myTime.value,
    opponentTimeSnapshot: opponentTime.value
  }
}

// 恢复游戏状态
function restoreGameState(snapshot) {
  board.value = snapshot.boardSnapshot
  moveHistory.value = snapshot.moveHistorySnapshot
  lastMove.value = snapshot.lastMoveSnapshot
  currentTurn.value = snapshot.currentTurnSnapshot
  gameState.value = snapshot.gameStateSnapshot
  winner.value = snapshot.winnerSnapshot
  myTime.value = snapshot.myTimeSnapshot
  opponentTime.value = snapshot.opponentTimeSnapshot
}

// 落子逻辑
async function handleCellClick(row, col) {
  // 防止重复点击
  if (isPlacingStone.value) {
    return
  }

  // 基本检查
  if (!isMyTurn.value) {
    ElMessage.warning('还没轮到你')
    return
  }

  if (gameState.value !== STATE_PLAYING) {
    ElMessage.warning('游戏已结束')
    return
  }

  if (board.value[row][col] !== null) {
    ElMessage.warning('此位置已有棋子')
    return
  }

  // 设置防抖标志
  isPlacingStone.value = true

  try {
    // 先发送到服务器验证
    const success = await sendMove(row, col)

    if (!success) {
      return
    }

    // 验证成功，立即本地更新（不等待WebSocket，提升体验）
    placeStone(row, col, myColor.value, true)

    // 发送成功后，如果是AI对战且游戏未结束，触发AI落子
    if (isAIGame.value && gameState.value === STATE_PLAYING) {
      // 立即触发AI落子，思考时间由Worker控制
      createTrackedTimeout(() => {
        // 再次检查游戏状态，确保游戏还在进行中
        if (gameState.value === STATE_PLAYING) {
          aiMove()
        }
      }, 100) // 短暂延迟，确保UI更新完成
    }
  } finally {
    // 无论成功失败，解除防抖锁
    isPlacingStone.value = false
  }
}

// 放置棋子（本地）
function placeStone(row, col, color, shouldSwitchTurn = true) {
  // 使用数组方法确保响应性
  const newRow = [...board.value[row]]
  newRow[col] = color
  board.value[row] = newRow

  lastMove.value = { row, col }
  moveHistory.value.push({ row, col, color })

  // 落子动画
  animateStone(row, col)

  // 检查胜负
  const result = checkWinner(row, col, color)
  if (result) {
    handleGameEnd(color)
    return
  }

  // 检查平局
  if (isBoardFull()) {
    handleGameEnd('draw')
    return
  }

  // 切换回合
  if (shouldSwitchTurn) {
    currentTurn.value = color === COLOR_BLACK ? COLOR_WHITE : COLOR_BLACK

    // 清除之前的计时器重置timeout（如果存在）
    if (timerResetTimeout.value) {
      clearTimeout(timerResetTimeout.value)
      timerResetTimeout.value = null
    }

    // 重置下一回合的计时器
    timerResetTimeout.value = setTimeout(() => {
      if (currentTurn.value === myColor.value) {
        myTime.value = TURN_TIME
      } else {
        opponentTime.value = TURN_TIME
      }
      timerResetTimeout.value = null
    }, TIMER_RESET_DELAY)
  }
}

// 落子动画
function animateStone(row, col) {
  // 延迟一帧确保 DOM 已更新
  createTrackedTimeout(() => {
    const stoneWrapper = document.querySelector(`[data-cell="${row}-${col}"]`)
    if (!stoneWrapper) return

    const stone = stoneWrapper.querySelector('.stone')
    if (!stone) return

    gsap.from(stone, {
      scale: 0,
      opacity: 0,
      duration: 0.3,
      ease: 'back.out(2)'
    })
  }, 0)
}

// 检查某个方向上的连续棋子数
function countDirection(row, col, dr, dc, color) {
  let count = 0
  for (let i = 1; i < WIN_COUNT; i++) {
    const r = row + dr * i
    const c = col + dc * i
    if (!isValidPosition(r, c)) break
    // 防御性检查：确保board.value[r]存在
    if (!board.value[r] || board.value[r][c] !== color) break
    count++
  }
  return count
}

// 五子连珠判断
function checkWinner(row, col, color) {
  const directions = [
    { dr: 0, dc: 1 },
    { dr: 1, dc: 0 },
    { dr: 1, dc: 1 },
    { dr: 1, dc: -1 }
  ]

  for (const { dr, dc } of directions) {
    const forward = countDirection(row, col, dr, dc, color)
    const backward = countDirection(row, col, -dr, -dc, color)
    const total = forward + backward + 1

    if (total >= WIN_COUNT) return true
  }

  return false
}

// 检查棋盘是否已满（性能优化：找到空位立即返回）
function isBoardFull() {
  for (const row of board.value) {
    if (row.includes(null)) {
      return false
    }
  }
  return true
}

// 游戏结束处理
async function handleGameEnd(result) {
  // 防止重复调用
  if (gameState.value === STATE_FINISHED) {
    return
  }

  gameState.value = STATE_FINISHED
  winner.value = result
  stopTimer()

  // 调用结束接口记录游戏数据
  await sendGameEnd(result)

  // 通过WebSocket通知对手游戏结束（真人对战）
  if (!isAIGame.value && roomInfo.value?.roomCode) {
    try {
      const notification = {
        type: 'gameEnd',
        winnerId: result === myColor.value ? user.value?.id : (opponent.value?.id || 0),
        winnerColor: result === 'draw' ? null : result,
        isDraw: result === 'draw' ? 1 : 0,
        timestamp: Date.now()
      }
      wsService?.send(`/app/room/${roomInfo.value.roomCode}/gameEnd`, notification)
    } catch {
      // 静默处理WebSocket错误
    }
  }

  createTrackedTimeout(() => {
    showWinnerDialog.value = true
  }, AI_THINK_TIME)

  // 胜利动画
  if (result === myColor.value) {
    gsap.to('.game-board', {
      scale: 1.02,
      duration: 0.3,
      yoyo: true,
      repeat: 1,
      ease: 'power2.inOut'
    })
  }
}

// 计算胜负信息
function calculateWinnerInfo(result) {
  if (result === 'draw') {
    return { winnerId: null, loserId: null, isDraw: 1 }
  }

  const isMyWin = result === myColor.value
  const myId = user.value?.id || null
  const opponentId = opponent.value?.id || 0

  return {
    winnerId: isMyWin ? myId : opponentId,
    loserId: isMyWin ? opponentId : myId,
    isDraw: 0
  }
}

// 发送游戏结束数据到后端
async function sendGameEnd(result) {
  if (!roomInfo.value?.roomCode) return

  try {
    const { winnerId, loserId, isDraw } = calculateWinnerInfo(result)

    const endData = {
      winnerId,
      loserId,
      isDraw
    }

    await request.post(`/gomoku/${roomInfo.value.roomCode}/end`, endData)
  } catch {
    // 静默处理错误
  }
}

// WebSocket 发送落子（悲观更新：先验证再更新）
async function sendMove(row, col) {
  // 防御性检查
  if (!roomInfo.value?.roomCode) {
    ElMessage.error('房间信息丢失，请刷新页面')
    return false
  }

  if (!user.value?.id) {
    ElMessage.error('用户信息丢失，请重新登录')
    return false
  }

  const moveData = {
    x: col,
    y: row,
    color: myColor.value,
    id: user.value.id
  }

  try {
    const response = await request.post(`/gomoku/${roomInfo.value.roomCode}/move`, moveData)

    if (!response.data.success) {
      throw new Error(response.data.message || '落子失败')
    }

    return true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '落子失败，请重试')
    return false
  }
}

// WebSocket 接收落子（对手或AI的落子）
function receiveMove(data) {
  // 检查数据完整性
  if (!data || data.x === undefined || data.y === undefined || !data.color) {
    return
  }

  if (gameState.value !== STATE_PLAYING) {
    return
  }

  if (data.id === user.value?.id) {
    return
  }

  const row = data.y
  const col = data.x

  // 验证坐标有效性和位置是否可落子
  if (!canPlaceStone(row, col)) {
    return
  }

  // 验证是否是对手的回合
  if (currentTurn.value !== opponentColor.value) {
    return
  }

  // 验证落子颜色是否与对手颜色一致
  if (data.color !== opponentColor.value) {
    return
  }

  // 对手或AI落子，更新棋盘并切换回合
  placeStone(row, col, data.color, true)
}

// 选择AI落子策略（调用后端API）
async function selectAIStrategy() {
  return await callBackendAI(aiDifficulty.value)
}

// 统一调用后端AI接口
async function callBackendAI(difficulty) {
  const playerColor = myColor.value
  const aiColor = opponentColor.value

  // 转换棋盘格式：将棋子颜色转为数字（1=黑棋, 2=白棋, null=0）
  const boardData = board.value.map(row =>
    row.map(cell => {
      if (cell === 'black') return 1
      if (cell === 'white') return 2
      return 0
    })
  )

  // 转换颜色为数字
  const aiColorNum = aiColor === COLOR_BLACK ? 1 : 2
  const playerColorNum = playerColor === COLOR_BLACK ? 1 : 2

  try {
    // 调用后端AI接口
    const response = await request.post('/gomoku/ai/move', {
      board: boardData,
      aiColor: aiColorNum,
      playerColor: playerColorNum,
      difficulty: difficulty
    })

    if (response.data.success && response.data.move) {
      return {
        row: response.data.move.row,
        col: response.data.move.col
      }
    } else {
      throw new Error(response.data.message || 'AI计算失败')
    }
  } catch (error) {
    console.error('后端AI计算失败:', error)
    // 降级到随机落子
    return getRandomMoveFallback()
  }
}

// 降级方案：简单的随机落子
function getRandomMoveFallback() {
  const validMoves = []
  for (let r = 0; r < BOARD_SIZE; r++) {
    for (let c = 0; c < BOARD_SIZE; c++) {
      if (canPlaceStone(r, c)) {
        validMoves.push({ row: r, col: c })
      }
    }
  }
  if (validMoves.length === 0) {
    // 棋盘已满，返回-1表示无法落子
    console.warn('棋盘已满，无有效落子位置')
    return { row: -1, col: -1 }
  }
  const randomIndex = Math.floor(Math.random() * validMoves.length)
  return validMoves[randomIndex]
}

// 验证并执行AI落子
async function executeAIMove(row, col) {
  if (canPlaceStone(row, col)) {
    await sendAIMove(row, col)
    return true
  }
  return false
}

// 执行降级AI落子
async function executeFallbackAIMove() {
  const { row, col } = getRandomMoveFallback()
  if (row !== -1 && col !== -1) {
    await sendAIMove(row, col)
  }
}

// AI 落子主函数
async function aiMove() {
  if (gameState.value !== STATE_PLAYING) return

  try {
    const { row, col } = await selectAIStrategy()

    if (gameState.value !== STATE_PLAYING) return
    if (row === -1 || col === -1) return

    const success = await executeAIMove(row, col)

    // 如果失败，尝试降级到简单算法
    if (!success && gameState.value === STATE_PLAYING) {
      await executeFallbackAIMove()
    }
  } catch (error) {
    console.error('AI策略失败:', error)
    // AI策略失败，降级到随机算法
    if (gameState.value === STATE_PLAYING) {
      await executeFallbackAIMove().catch(() => {
        ElMessage.error('AI落子失败,请刷新页面重试')
      })
    }
  }
}

// 发送AI落子到后端记录
async function sendAIMove(row, col) {
  // 防御性检查
  if (!roomInfo.value?.roomCode) {
    return
  }

  // 检查坐标有效性
  if (!isValidPosition(row, col)) {
    return
  }

  const moveData = {
    x: col,
    y: row,
    color: opponentColor.value,
    id: AI_PLAYER_ID  // AI的id设为0
  }

  try {
    const response = await request.post(`/gomoku/${roomInfo.value.roomCode}/move`, moveData)

    if (!response.data.success) {
      ElMessage.error('AI落子失败，请刷新重试')
      return
    }

    // AI对战模式：直接本地更新棋盘
    // （因为AI模式下没有WebSocket连接，不会收到广播消息）
    if (isAIGame.value && board.value[row][col] === null) {
      placeStone(row, col, opponentColor.value, true)
    }
  } catch {
    ElMessage.error('AI落子失败，请检查网络连接')
  }
}

// === AI实现已全部迁移到后端 ===

// 认输功能
async function surrender() {
  try {
    await ElMessageBox.confirm('确定要认输吗？', '认输确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    // 通知对手玩家认输（仅真人对战）
    if (!isAIGame.value && roomInfo.value?.roomCode) {
      notifyPlayerLeft()
    }

    // 设置游戏状态为结束，对方获胜
    gameState.value = STATE_FINISHED
    winner.value = opponentColor.value
    stopTimer()

    // 发送游戏结束数据（认输）
    await sendGameEnd(opponentColor.value)

    ElMessage.info('你已认输')
    showWinnerDialog.value = true
  } catch {
    // 用户取消认输
  }
}

// 退出游戏
async function exitGame() {
  try {
    const confirmMessage = gameState.value === STATE_PLAYING
      ? '游戏进行中退出将视为认输，确定要退出吗？'
      : '确定要退出游戏吗？'

    await ElMessageBox.confirm(confirmMessage, '退出确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    // 如果游戏进行中退出，算作认输
    if (gameState.value === STATE_PLAYING) {
      // 通知对手玩家退出（仅真人对战）
      if (!isAIGame.value && roomInfo.value?.roomCode) {
        notifyPlayerLeft()
      }
      await sendGameEnd(opponentColor.value)
    }

    stopTimer()
    await userStore.clearCurrentGame()
    roomStore.clearCurrentRoom()
    roomStore.clearGameConfig() // 清理游戏配置
    router.push('/user')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error instanceof Error ? error.message : '退出失败')
    }
  }
}

// 通知对手玩家退出
function notifyPlayerLeft() {
  if (!roomInfo.value?.roomCode || !user.value?.id) return

  try {
    // 通过 WebSocket 通知对手
    const notification = {
      type: 'playerLeft',
      playerId: user.value.id,
      playerName: user.value.username || '对手',
      timestamp: Date.now()
    }

    // 发送到房间topic
    wsService?.send(`/app/room/${roomInfo.value.roomCode}/leave`, notification)
  } catch {
    // 静默处理WebSocket发送错误，不影响玩家退出流程
  }
}

// 返回大厅（游戏结束后）
function backToLobby() {
  showWinnerDialog.value = false
  stopTimer()
  userStore.clearCurrentGame()
  roomStore.clearCurrentRoom()
  roomStore.clearGameConfig()
  router.push('/user')
}

// 入场动画 - 返回Promise以便等待动画完成
function runEnterAnimation() {
  return new Promise((resolve) => {
    const tl = gsap.timeline({
      onComplete: resolve
    })

    tl.from('.game-header', {
      y: -50,
      opacity: 0,
      duration: 0.6,
      ease: 'power2.out'
    })
      .from('.game-board', {
        scale: 0.8,
        opacity: 0,
        duration: 0.8,
        ease: 'back.out(1.5)'
      }, '-=0.3')
      .from('.player-info', {
        x: (index) => index === 0 ? -50 : 50,
        opacity: 0,
        duration: 0.5,
        stagger: 0.2,
        ease: 'power2.out'
      }, '-=0.5')
  })
}

// 处理对手退出
function handleOpponentLeft(data) {
  // 检查数据完整性
  if (!data || !data.playerId) {
    return
  }

  // 检查是否是对手离开（不是自己）
  if (data.playerId === user.value?.id) {
    return
  }

  // 如果游戏正在进行中，判定对手认输，自己获胜
  if (gameState.value === STATE_PLAYING) {
    gameState.value = STATE_FINISHED
    winner.value = myColor.value
    stopTimer()

    ElMessage.success(`对手 ${data.playerName || '对手'} 已退出，你赢了！`)

    // 不再调用 sendGameEnd，因为退出方已经调用过了
    // 后端的幂等性保护会确保只处理一次
    // 这样避免了重复调用和 404 错误

    showWinnerDialog.value = true
  }
}

// 处理游戏结束通知（接收对手的游戏结束消息）
function handleGameEndNotification(data) {
  // 检查数据完整性
  if (!data || !data.type || data.type !== 'gameEnd') {
    return
  }

  // 防止重复处理
  if (gameState.value === STATE_FINISHED) {
    return
  }

  // 更新游戏状态
  gameState.value = STATE_FINISHED
  stopTimer()

  // 确定赢家
  if (data.isDraw === 1) {
    winner.value = 'draw'
  } else if (data.winnerColor) {
    winner.value = data.winnerColor
  } else if (data.winnerId === user.value?.id) {
    winner.value = myColor.value
  } else {
    winner.value = opponentColor.value
  }

  // 显示结果弹窗
  createTrackedTimeout(() => {
    showWinnerDialog.value = true
  }, 300)
}

// WebSocket消息处理器
function createMessageHandler() {
  return (msg) => {
    try {
      const data = JSON.parse(msg.body)

      // 忽略空消息（后端返回的空 Map）
      if (!data || Object.keys(data).length === 0 || !data.type) {
        return
      }

      // 根据消息类型分发处理
      switch (data.type) {
        case 'move':
          try {
            receiveMove(data)
          } catch (err) {
            console.error('处理对手落子失败:', err)
          }
          break
        case 'playerLeft':
          try {
            handleOpponentLeft(data)
          } catch (err) {
            console.error('处理对手离开失败:', err)
          }
          break
        case 'gameEnd':
          try {
            handleGameEndNotification(data)
          } catch (err) {
            console.error('处理游戏结束通知失败:', err)
          }
          break
        default:
          break
      }
    } catch (error) {
      if (error instanceof Error) {
        ElMessage.error('消息处理失败')
      }
    }
  }
}

// 等待连接
async function waitForConnection() {
  let retryCount = 0
  const maxRetries = 50

  while (!wsService?.connected && retryCount < maxRetries) {
    await new Promise(resolve => setTimeout(resolve, 100))
    retryCount++
  }

  return wsService?.connected
}

// 等待订阅生效
async function waitForSubscription(topic) {
  const maxRetries = 30

  for (let i = 0; i < maxRetries; i++) {
    await new Promise(resolve => setTimeout(resolve, 100))

    if (wsService?.isSubscribed(topic)) {
      return true
    }
  }

  return false
}

// 恢复棋盘数据
function restoreBoardData(gameData) {
  // 清空棋盘
  for (let r = 0; r < BOARD_SIZE; r++) {
    for (let c = 0; c < BOARD_SIZE; c++) {
      board.value[r][c] = null
    }
  }
  moveHistory.value = []

  // 重放棋步
  for (const move of gameData) {
    if (move?.x !== undefined && move?.y !== undefined) {
      board.value[move.y][move.x] = move.color
      moveHistory.value.push({ row: move.y, col: move.x, color: move.color })
    }
  }

  board.value = [...board.value]

  // 恢复最后一步
  const lastMoveData = gameData.at(-1)
  if (lastMoveData) {
    lastMove.value = { row: lastMoveData.y, col: lastMoveData.x }
  }
}

// 恢复游戏状态和回合
function restoreGameStatus(gomoku, gameData) {
  currentTurn.value = gameData.length % 2 === 0 ? COLOR_BLACK : COLOR_WHITE

  if (gomoku.winnerId !== null && gomoku.winnerId !== undefined) {
    gameState.value = STATE_FINISHED
    winner.value = gomoku.winnerId === gomoku.player1Id ? COLOR_BLACK : COLOR_WHITE
  } else if (gomoku.isDraw === 1) {
    gameState.value = STATE_FINISHED
    winner.value = 'draw'
  } else {
    gameState.value = STATE_PLAYING
  }
}

// 清理恢复资源
function cleanupRecovery(topic, timeoutId, resolve, success) {
  if (timeoutId) clearTimeout(timeoutId)
  wsService?.unsubscribe(topic)
  resolve(success)
}

// 游戏数据恢复
async function recoverGameData() {
  if (!roomInfo.value?.roomCode || !user.value?.id) return false

  try {
    const recoveryTopic = '/user/queue/recover'
    let timeoutId = null

    return new Promise((resolve) => {
      wsService?.subscribe(recoveryTopic, (msg) => {
        try {
          const data = JSON.parse(msg.body)

          if (!data.success || !data.gomoku) {
            // 静默处理，新游戏时没有数据可恢复是正常情况
            // ElMessage.warning('无法恢复游戏数据：' + (data.message || '未知错误'))
            cleanupRecovery(recoveryTopic, timeoutId, resolve, false)
            return
          }

          const gameData = data.gomoku.gameData || []
          restoreBoardData(gameData)
          restoreGameStatus(data.gomoku, gameData)

          ElMessage.success('游戏数据已恢复')
          cleanupRecovery(recoveryTopic, timeoutId, resolve, true)
        } catch {
          ElMessage.error('恢复数据解析失败')
          cleanupRecovery(recoveryTopic, timeoutId, resolve, false)
        }
      })

      createTrackedTimeout(() => {
        wsService?.send(`/app/room/${roomInfo.value.roomCode}/recover`, {
          userId: user.value.id
        })
      }, 100)

      timeoutId = setTimeout(() => {
        cleanupRecovery(recoveryTopic, null, resolve, false)
      }, 5000)
    })
  } catch {
    ElMessage.error('恢复游戏数据失败')
    return false
  }
}

// 等待WebSocket连接并订阅
async function ensureWebSocketReady() {
  if (isAIGame.value) {
    return true
  }

  if (!(await waitForConnection())) {
    ElMessage.error('网络连接失败，请刷新页面重试')
    return false
  }

  if (!wsService || !roomInfo.value?.roomCode) {
    return false
  }

  const topic = `/topic/room/${roomInfo.value.roomCode}`

  // 取消旧的订阅
  wsService.unsubscribe(topic)

  // 等待一小段时间
  await new Promise(resolve => setTimeout(resolve, 100))

  wsService.subscribe(topic, createMessageHandler())

  if (await waitForSubscription(topic)) {
    // 尝试恢复游戏数据
    await recoverGameData()
    return true
  }

  // 如果等待超时但订阅仍然成功，继续游戏
  if (wsService.isSubscribed(topic)) {
    await recoverGameData()
    return true
  }

  ElMessage.warning('房间同步可能存在问题，如果无法看到对方落子，请刷新页面')
  return true
}

// 初始化游戏逻辑
async function initializeGame() {
  // 从 roomStore 获取游戏配置
  const gameConfig = roomStore.gameConfig

  if (!gameConfig) {
    ElMessage.warning('游戏配置丢失，请重新开始')
    router.push('/user')
    return
  }

  // 设置游戏配置
  if (gameConfig.opponent) {
    opponent.value = gameConfig.opponent
  }

  if (gameConfig.myColor) {
    myColor.value = gameConfig.myColor
    // 五子棋规则：黑棋永远先手
    currentTurn.value = COLOR_BLACK
  }

  // 同时执行入场动画和WebSocket连接检查
  const [, websocketReady] = await Promise.all([
    runEnterAnimation(),
    ensureWebSocketReady()
  ])

  if (!websocketReady && !isAIGame.value) {
    return
  }

  // 启动计时器
  startTimer()
}

// 组件挂载
onMounted(() => {
  // 检查房间信息
  if (!roomInfo.value?.roomCode) {
    ElMessage.warning('房间信息不存在')
    router.push('/user')
    return
  }

  // 开始初始化游戏
  initializeGame()
})

// 组件卸载
onBeforeUnmount(() => {
  stopTimer()

  // 清理所有活跃的setTimeout
  for (const timeoutId of activeTimeouts.value) {
    clearTimeout(timeoutId)
  }
  activeTimeouts.value = []

  // 清理计时器重置timeout
  if (timerResetTimeout.value) {
    clearTimeout(timerResetTimeout.value)
    timerResetTimeout.value = null
  }

  // 清理房间WebSocket订阅
  if (roomInfo.value?.roomCode && wsService) {
    const topic = `/topic/room/${roomInfo.value.roomCode}`
    wsService.unsubscribe(topic)
  }
})
</script>

<template>
  <div class="gomoku-game">
    <!-- 顶部信息栏 -->
    <div class="game-header">
      <div class="header-left">
        <Icon icon="mdi:chess-king" width="24" />
        <span class="game-title">五子棋对战</span>
        <!-- 显示房间号（用于调试） -->
        <span v-if="roomInfo?.roomCode" class="room-code-badge">
          房间: {{ roomInfo.roomCode }}
        </span>
      </div>
      <div class="header-center">
        <div class="turn-indicator" :class="{ active: isMyTurn }">
          {{ isMyTurn ? '你的回合' : (isAIGame ? 'AI回合' : '对手回合') }}
        </div>
        <!-- 认输按钮 -->
        <button v-if="gameState === STATE_PLAYING" class="btn-surrender" @click="surrender" title="认输">
          <Icon icon="mdi:flag-variant" width="18" />
          <span>认输</span>
        </button>
      </div>
      <div class="header-right">
        <button class="btn-icon" @click="exitGame" title="退出游戏">
          <Icon icon="lucide:log-out" width="20" />
        </button>
      </div>
    </div>

    <!-- 主游戏区域 -->
    <div class="game-container">
      <!-- 左侧：我的信息 -->
      <div class="player-info left-player">
        <div class="player-avatar">
          <img :src="user?.avatar || '/image/default-avatar.jpg'" alt="me" />
          <div class="piece-indicator" :class="myColor"></div>
        </div>
        <div class="player-details">
          <div class="player-name">{{ user?.username || '我' }}</div>
          <div class="player-role">{{ myColor === COLOR_BLACK ? '黑棋' : '白棋' }}</div>
        </div>
        <div class="player-timer" :class="{ warning: myTime < 10, active: isMyTurn }">
          <Icon icon="lucide:clock" width="18" />
          <span>{{ myTimeDisplay }}</span>
        </div>
      </div>

      <!-- 中间：棋盘 -->
      <div class="game-board-wrapper">
        <div class="game-board">
          <div class="board-grid">
            <!-- 绘制棋盘线 -->
            <div class="grid-lines">
              <div v-for="i in BOARD_SIZE" :key="`h-${i}`" class="line horizontal"
                :style="{ top: `${((i - 1) / (BOARD_SIZE - 1)) * 100}%` }"></div>
              <div v-for="i in BOARD_SIZE" :key="`v-${i}`" class="line vertical"
                :style="{ left: `${((i - 1) / (BOARD_SIZE - 1)) * 100}%` }"></div>
            </div>

            <!-- 星位 -->
            <div class="star-points">
              <div class="star" :style="{
                top: `${(3 / (BOARD_SIZE - 1)) * 100}%`,
                left: `${(3 / (BOARD_SIZE - 1)) * 100}%`
              }"></div>
              <div class="star" :style="{
                top: `${(3 / (BOARD_SIZE - 1)) * 100}%`,
                left: `${(13 / (BOARD_SIZE - 1)) * 100}%`
              }"></div>
              <div class="star" :style="{
                top: `${(8 / (BOARD_SIZE - 1)) * 100}%`,
                left: `${(8 / (BOARD_SIZE - 1)) * 100}%`
              }"></div>
              <div class="star" :style="{
                top: `${(13 / (BOARD_SIZE - 1)) * 100}%`,
                left: `${(3 / (BOARD_SIZE - 1)) * 100}%`
              }"></div>
              <div class="star" :style="{
                top: `${(13 / (BOARD_SIZE - 1)) * 100}%`,
                left: `${(13 / (BOARD_SIZE - 1)) * 100}%`
              }"></div>
            </div>

            <!-- 棋子层（绝对定位到交叉点） -->
            <div class="board-stones">
              <template v-for="(row, rowIndex) in board" :key="`row-${rowIndex}`">
                <template v-for="(cell, colIndex) in row" :key="`cell-${rowIndex}-${colIndex}`">
                  <!-- 实际棋子 -->
                  <div v-if="cell" class="stone-wrapper" :class="{
                    'last-move': lastMove?.row === rowIndex && lastMove?.col === colIndex
                  }" :data-cell="`${rowIndex}-${colIndex}`" :style="{
                    top: `${(rowIndex / (BOARD_SIZE - 1)) * 100}%`,
                    left: `${(colIndex / (BOARD_SIZE - 1)) * 100}%`
                  }">
                    <div class="stone" :class="cell"></div>
                  </div>
                  <!-- 预览棋子 -->
                  <div
                    v-else-if="hoveredCell?.row === rowIndex && hoveredCell?.col === colIndex && isMyTurn && gameState === STATE_PLAYING"
                    class="stone-wrapper" :style="{
                      top: `${(rowIndex / (BOARD_SIZE - 1)) * 100}%`,
                      left: `${(colIndex / (BOARD_SIZE - 1)) * 100}%`
                    }">
                    <div class="stone-preview" :class="myColor"></div>
                  </div>
                </template>
              </template>
            </div>

            <!-- 交互层（透明的点击区域） -->
            <div class="board-interaction">
              <template v-for="(row, rowIndex) in board" :key="`interaction-row-${rowIndex}`">
                <div v-for="(cell, colIndex) in row" :key="`interaction-${rowIndex}-${colIndex}`"
                  class="interaction-point"
                  :class="{ 'can-place': isMyTurn && gameState === STATE_PLAYING && cell === null }" :style="{
                    top: `${(rowIndex / (BOARD_SIZE - 1)) * 100}%`,
                    left: `${(colIndex / (BOARD_SIZE - 1)) * 100}%`
                  }" @click="handleCellClick(rowIndex, colIndex)"
                  @mouseenter="hoveredCell = { row: rowIndex, col: colIndex }" @mouseleave="hoveredCell = null">
                </div>
              </template>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：对手信息 -->
      <div class="player-info right-player">
        <div class="player-avatar">
          <img :src="opponent?.avatar || '/image/default-avatar.jpg'" alt="opponent" />
          <div class="piece-indicator" :class="opponentColor"></div>
        </div>
        <div class="player-details">
          <div class="player-name">{{ opponent?.name || (isAIGame ? 'AI' : '对手') }}</div>
          <div class="player-role">{{ opponentColor === COLOR_BLACK ? '黑棋' : '白棋' }}</div>
        </div>
        <div class="player-timer" :class="{ warning: opponentTime < 10, active: !isMyTurn }">
          <Icon icon="lucide:clock" width="18" />
          <span>{{ opponentTimeDisplay }}</span>
        </div>
      </div>
    </div>

    <!-- 胜负弹窗 -->
    <Teleport to="body">
      <div v-if="showWinnerDialog" class="winner-overlay" @click="showWinnerDialog = false">
        <div class="winner-dialog" @click.stop>
          <div class="winner-icon">
            <Icon v-if="winner === myColor" icon="mdi:trophy" width="80" color="#FFD700" />
            <Icon v-else-if="winner === 'draw'" icon="mdi:handshake" width="80" color="#888" />
            <Icon v-else icon="mdi:emoticon-sad" width="80" color="#999" />
          </div>
          <div class="winner-title">
            {{
              winner === myColor ? '恭喜你赢了！' :
                winner === 'draw' ? '平局' :
                  '很遗憾，你输了'
            }}
          </div>
          <div class="winner-subtitle">
            {{ winner === 'draw' ? '棋盘已满，不分胜负' : `${winner === COLOR_BLACK ? '黑棋' : '白棋'}获胜` }}
          </div>
          <div class="winner-actions">
            <!-- 所有对战结束后都只显示返回大厅 -->
            <button class="btn-winner primary" @click="backToLobby">
              <Icon icon="lucide:home" width="18" />
              返回大厅
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.gomoku-game {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 顶部信息栏 */
.game-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.game-title {
  font-size: 20px;
  font-weight: 700;
  color: #333;
}

.room-code-badge {
  margin-left: 12px;
  padding: 4px 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

.header-center {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
}

.turn-indicator {
  padding: 8px 24px;
  background: #f0f0f0;
  border-radius: 20px;
  font-weight: 600;
  color: #666;
  transition: all 0.3s ease;
}

.turn-indicator.active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.btn-icon {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: #f5f5f5;
  color: #666;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.btn-icon:hover {
  background: #e0e0e0;
  color: #333;
}

.btn-surrender {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  border-radius: 10px;
  background: #ff6b6b;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-surrender:hover {
  background: #ff5252;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(255, 107, 107, 0.4);
}

.btn-surrender:active {
  transform: translateY(0);
}

/* 主游戏区域 */
.game-container {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 40px;
  padding: 20px;
  min-height: 0;
}

/* 玩家信息 */
.player-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  background: #fff;
  padding: 24px;
  border-radius: 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  min-width: 180px;
}

.player-avatar {
  position: relative;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.player-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.piece-indicator {
  position: absolute;
  right: -4px;
  bottom: -4px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 3px solid #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

.piece-indicator.black {
  background: linear-gradient(135deg, #2c3e50 0%, #34495e 100%);
}

.piece-indicator.white {
  background: linear-gradient(135deg, #fff 0%, #f0f0f0 100%);
}

.player-details {
  text-align: center;
}

.player-name {
  font-size: 18px;
  font-weight: 700;
  color: #333;
  margin-bottom: 4px;
}

.player-role {
  font-size: 14px;
  color: #666;
}

.player-timer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: #f5f5f5;
  border-radius: 20px;
  font-size: 16px;
  font-weight: 600;
  color: #666;
  transition: all 0.3s ease;
}

.player-timer.active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.player-timer.warning {
  background: #ff6b6b;
  color: #fff;
  animation: pulse-timer 1s infinite;
}

@keyframes pulse-timer {

  0%,
  100% {
    transform: scale(1);
  }

  50% {
    transform: scale(1.05);
  }
}

/* 棋盘区域 */
.game-board-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  position: relative;
}

/* AI思考中提示 */
.ai-thinking-overlay {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 1000;
  pointer-events: none;
}

.thinking-content {
  background: rgba(0, 0, 0, 0.85);
  color: white;
  padding: 24px 40px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(10px);
}

/* Vue过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: all 0.2s ease;
}

.fade-enter-from {
  opacity: 0;
  transform: translate(-50%, -50%) scale(0.9);
}

.fade-enter-to {
  opacity: 1;
  transform: translate(-50%, -50%) scale(1);
}

.fade-leave-from {
  opacity: 1;
  transform: translate(-50%, -50%) scale(1);
}

.fade-leave-to {
  opacity: 0;
  transform: translate(-50%, -50%) scale(0.9);
}

.thinking-icon {
  animation: spin 1s linear infinite;
  color: #4CAF50;
}

.thinking-text {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

.game-board {
  width: min(70vh, 800px);
  height: min(70vh, 800px);
  background: linear-gradient(135deg, #f5deb3 0%, #d2b48c 100%);
  border-radius: 12px;
  padding: 30px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  position: relative;
  border: 3px solid #8b7355;
}

.board-grid {
  width: 100%;
  height: 100%;
  position: relative;
}

.grid-lines {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.line {
  position: absolute;
  background: #8b7355;
}

.line.horizontal {
  height: 2px;
  width: 100%;
  left: 0;
  right: 0;
  transform: translateY(-50%);
}

.line.vertical {
  width: 2px;
  height: 100%;
  top: 0;
  bottom: 0;
  transform: translateX(-50%);
}

.star-points {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.star {
  position: absolute;
  width: 6px;
  height: 6px;
  background: #6b5345;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  box-shadow: 0 0 3px rgba(0, 0, 0, 0.3);
}

/* 棋子层 - 绝对定位到交叉点 */
.board-stones {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 2;
}

.stone-wrapper {
  position: absolute;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.stone {
  /* 改为相对棋盘的比例：17x17棋盘，每个格子约5.88%，棋子占格子的80% */
  width: min(32px, 4.7vmin);
  height: min(32px, 4.7vmin);
  border-radius: 50%;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.4);
  transition: transform 0.2s ease;
}

.stone.black {
  background: #1a1a1a;
  /* 后备纯色背景，确保在渐变失败时也能显示 */
  background: radial-gradient(circle at 30% 30%, #666, #1a1a1a 50%, #000);
  border: 1px solid #000;
  /* 增强黑子的立体感 */
  position: relative;
}

.stone.black::before {
  content: '';
  position: absolute;
  top: 15%;
  left: 20%;
  width: 35%;
  height: 35%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.25), transparent);
  border-radius: 50%;
  pointer-events: none;
}

.stone.white {
  background: #f5f5f5;
  /* 后备纯色背景，确保在渐变失败时也能显示 */
  background: radial-gradient(circle at 30% 30%, #fff, #e8e8e8 50%, #d5d5d5);
  border: 1px solid #bbb;
  /* 增强白子的立体感 */
  position: relative;
}

.stone.white::before {
  content: '';
  position: absolute;
  top: 15%;
  left: 20%;
  width: 40%;
  height: 40%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.9), transparent 70%);
  border-radius: 50%;
  pointer-events: none;
}

/* 最后一步标记 */
.stone-wrapper.last-move::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 10px;
  height: 10px;
  background: #ff4757;
  border-radius: 50%;
  z-index: 10;
  box-shadow: 0 0 10px rgba(255, 71, 87, 0.8);
  animation: pulse-marker 1.5s infinite;
}

/* 交互层 - 透明的点击区域 */
.board-interaction {
  position: absolute;
  inset: 0;
  z-index: 3;
}

.interaction-point {
  position: absolute;
  /* 比棋子稍大，方便点击 */
  width: min(36px, 5vmin);
  height: min(36px, 5vmin);
  transform: translate(-50%, -50%);
  cursor: pointer;
  border-radius: 50%;
  transition: background 0.2s ease;
}

.interaction-point.can-place:hover {
  background: rgba(102, 126, 234, 0.15);
}

@keyframes pulse-marker {

  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }

  50% {
    opacity: 0.7;
    transform: scale(1.2);
  }
}

/* 预览棋子 */
.stone-preview {
  /* 与实际棋子大小保持一致 */
  width: min(32px, 4.7vmin);
  height: min(32px, 4.7vmin);
  border-radius: 50%;
  opacity: 0.4;
  pointer-events: none;
  border: 2px dashed currentColor;
  transition: opacity 0.2s ease;
}

.stone-preview.black {
  background: rgba(0, 0, 0, 0.25);
  color: #333;
}

.stone-preview.white {
  background: rgba(255, 255, 255, 0.7);
  color: #666;
  border-color: #999;
}

/* 胜负弹窗 */
.winner-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

.winner-dialog {
  background: #fff;
  border-radius: 20px;
  padding: 40px;
  max-width: 400px;
  text-align: center;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  animation: slideUp 0.4s ease;
}

@keyframes slideUp {
  from {
    transform: translateY(50px);
    opacity: 0;
  }

  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.winner-icon {
  margin-bottom: 20px;
  animation: bounce 0.6s ease;
}

@keyframes bounce {

  0%,
  100% {
    transform: scale(1);
  }

  50% {
    transform: scale(1.1);
  }
}

.winner-title {
  font-size: 28px;
  font-weight: 700;
  color: #333;
  margin-bottom: 8px;
}

.winner-subtitle {
  font-size: 16px;
  color: #666;
  margin-bottom: 30px;
}

.winner-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.btn-winner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-winner.primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
}

.btn-winner.primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.btn-winner.secondary {
  background: #f5f5f5;
  color: #333;
}

.btn-winner.secondary:hover {
  background: #e0e0e0;
}

/* 响应式 */
@media (max-width: 1024px) {
  .game-container {
    flex-direction: column;
    gap: 20px;
    padding: 15px;
  }

  .player-info {
    flex-direction: row;
    width: 100%;
    max-width: 400px;
    padding: 16px;
  }

  .player-avatar {
    width: 60px;
    height: 60px;
  }

  .piece-indicator {
    width: 22px;
    height: 22px;
  }

  .game-board {
    width: min(70vw, 580px);
    height: min(70vw, 580px);
    padding: 20px;
  }

  .stone,
  .stone-preview {
    width: 28px;
    height: 28px;
  }

  .interaction-point {
    width: 32px;
    height: 32px;
  }
}

@media (max-width: 768px) {
  .game-header {
    padding: 12px 16px;
  }

  .game-title {
    font-size: 16px;
  }

  .turn-indicator {
    padding: 6px 16px;
    font-size: 14px;
  }

  .player-info {
    padding: 12px;
    min-width: 140px;
  }

  .player-avatar {
    width: 50px;
    height: 50px;
  }

  .player-name {
    font-size: 14px;
  }

  .player-role {
    font-size: 12px;
  }

  .game-board {
    width: min(85vw, 460px);
    height: min(85vw, 460px);
    padding: 15px;
  }

  .stone,
  .stone-preview {
    width: 22px;
    height: 22px;
  }

  .interaction-point {
    width: 28px;
    height: 28px;
  }
}

/* 移动端适配 */
@media (max-width: 480px) {
  .gomoku-game {
    padding-bottom: 80px;
  }

  .game-header {
    padding: 8px 12px;
    flex-wrap: wrap;
    gap: 8px;
  }

  .header-left {
    gap: 8px;
  }

  .header-left .iconify {
    width: 20px;
    height: 20px;
  }

  .game-title {
    font-size: 14px;
  }

  .room-code-badge {
    display: none;
  }

  .header-center {
    order: 3;
    width: 100%;
    justify-content: center;
    gap: 10px;
  }

  .turn-indicator {
    padding: 5px 12px;
    font-size: 12px;
    border-radius: 16px;
  }

  .btn-surrender {
    padding: 5px 10px;
    font-size: 12px;
    border-radius: 8px;
  }

  .btn-surrender span {
    display: none;
  }

  .btn-icon {
    width: 32px;
    height: 32px;
  }

  .game-container {
    flex-direction: column;
    gap: 10px;
    padding: 10px;
    justify-content: flex-start;
  }

  /* 玩家信息横向排列 */
  .player-info {
    flex-direction: row;
    width: 100%;
    padding: 8px 12px;
    gap: 10px;
    min-width: unset;
    border-radius: 10px;
  }

  .player-info.left-player {
    order: 1;
  }

  .player-info.right-player {
    order: 3;
  }

  .game-board-wrapper {
    order: 2;
  }

  .player-avatar {
    width: 40px;
    height: 40px;
    flex-shrink: 0;
  }

  .piece-indicator {
    width: 16px;
    height: 16px;
    right: -2px;
    bottom: -2px;
    border-width: 2px;
  }

  .player-details {
    flex: 1;
    text-align: left;
  }

  .player-name {
    font-size: 13px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 80px;
  }

  .player-role {
    font-size: 11px;
  }

  .player-timer {
    padding: 4px 10px;
    font-size: 12px;
    gap: 4px;
    flex-shrink: 0;
  }

  .player-timer .iconify {
    width: 14px;
    height: 14px;
  }

  /* 棋盘适配 */
  .game-board {
    width: min(92vw, 360px);
    height: min(92vw, 360px);
    padding: 10px;
    border-radius: 8px;
    border-width: 2px;
  }

  .stone,
  .stone-preview {
    width: 16px;
    height: 16px;
  }

  .interaction-point {
    width: 20px;
    height: 20px;
  }

  .star {
    width: 4px;
    height: 4px;
  }

  .line {
    background: #9b8365;
  }

  .line.horizontal {
    height: 1px;
  }

  .line.vertical {
    width: 1px;
  }

  /* 最后一步标记缩小 */
  .stone-wrapper.last-move::after {
    width: 6px;
    height: 6px;
  }

  /* 胜负弹窗适配 */
  .winner-dialog {
    padding: 24px 20px;
    margin: 16px;
    max-width: calc(100vw - 32px);
    border-radius: 16px;
  }

  .winner-icon .iconify {
    width: 60px !important;
    height: 60px !important;
  }

  .winner-title {
    font-size: 22px;
  }

  .winner-subtitle {
    font-size: 14px;
    margin-bottom: 20px;
  }

  .winner-actions {
    flex-direction: column;
    gap: 10px;
  }

  .btn-winner {
    width: 100%;
    justify-content: center;
    padding: 12px 20px;
  }

  /* AI思考提示适配 */
  .thinking-content {
    padding: 16px 24px;
    gap: 12px;
  }

  .thinking-text {
    font-size: 14px;
  }
}

/* 超小屏幕适配 */
@media (max-width: 360px) {
  .game-board {
    width: min(95vw, 300px);
    height: min(95vw, 300px);
    padding: 8px;
  }

  .stone,
  .stone-preview {
    width: 14px;
    height: 14px;
  }

  .interaction-point {
    width: 18px;
    height: 18px;
  }

  .player-name {
    max-width: 60px;
  }

  .game-header {
    padding: 6px 10px;
  }

  .game-title {
    font-size: 13px;
  }
}
</style>
