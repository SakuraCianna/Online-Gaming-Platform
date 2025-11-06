<script setup>
import { ref, computed, onMounted, onBeforeUnmount, inject } from 'vue'
import { useRouter } from 'vue-router'
import { gsap } from 'gsap'
import { Icon } from '@iconify/vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request, { aiRequest } from '../../config/api'
import { useUserStore } from '../../config/user'
import { useRoomStore } from '../../config/room'
import { storeToRefs } from 'pinia'

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

// ===== 工具函数区 =====

// 坐标验证
function isValidPosition(row, col) {
  return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE
}

function canPlaceStone(row, col) {
  return isValidPosition(row, col) && board.value[row]?.[col] === null
}

// 临时落子并执行回调（用于AI评估）- 性能优化版本
function withTemporaryStone(row, col, color, callback) {
  if (!board.value[row]) return null

  // 保存原值
  const originalValue = board.value[row][col]

  // 临时修改（避免创建新数组）
  board.value[row][col] = color

  const result = callback()

  // 恢复原值
  board.value[row][col] = originalValue

  return result
}

// 遍历所有空位并执行回调
function findEmptyPosition(callback) {
  for (let r = 0; r < BOARD_SIZE; r++) {
    if (!board.value[r]) continue
    for (let c = 0; c < BOARD_SIZE; c++) {
      if (board.value[r][c] !== null) continue
      const result = callback(r, c)
      if (result) return result
    }
  }
  return null
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

// 通用棋型查找函数
function findPatternMove(color, validator) {
  return findEmptyPosition((r, c) => {
    return withTemporaryStone(r, c, color, () => {
      return validator(r, c, color) ? { row: r, col: c } : null
    })
  })
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

// 落子逻辑（悲观更新：先验证，后更新）
async function handleCellClick(row, col) {
  // 防止重复点击（防抖）
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
    // 先发送到服务器验证（悲观更新）
    const success = await sendMove(row, col)

    if (!success) {
      return
    }

    // 验证成功，立即本地更新（不等待WebSocket，提升体验）
    placeStone(row, col, myColor.value, true)

    // 发送成功后，如果是AI对战且游戏未结束，触发AI落子
    if (isAIGame.value && gameState.value === STATE_PLAYING) {
      createTrackedTimeout(() => {
        // 再次检查游戏状态，确保游戏还在进行中
        if (gameState.value === STATE_PLAYING) {
          aiMove()
        }
      }, AI_THINK_TIME)
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

  // 检查游戏状态：只有游戏进行中才接受落子
  if (gameState.value !== STATE_PLAYING) {
    return
  }

  // 忽略自己的落子（自己的落子已经在handleCellClick中处理）
  if (data.id === user.value?.id) {
    return
  }

  const row = data.y
  const col = data.x

  // 验证坐标有效性和位置是否可落子
  if (!canPlaceStone(row, col)) {
    return
  }

  // 验证是否是对手的回合（应该是对手的颜色）
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


// 选择AI落子策略
async function selectAIStrategy() {
  switch (aiDifficulty.value) {
    case 'easy':
      return getRandomMove()
    case 'medium':
      return getMediumMove()
    case 'hard':
      if (gameState.value !== STATE_PLAYING) {
        return { row: -1, col: -1 }
      }
      return await getHardMove()
    default:
      return getMediumMove()
  }
}

// 验证并执行AI落子
async function executeAIMove(row, col, pointVO) {
  if (canPlaceStone(row, col)) {
    await sendAIMove(row, col, pointVO)
    return true
  }

  return false
}

// 执行降级AI落子
async function executeFallbackAIMove() {
  const { row, col } = getRandomMove()
  if (row !== -1 && col !== -1) {
    await sendAIMove(row, col, null)
  }
}

// AI 落子主函数
async function aiMove() {
  if (gameState.value !== STATE_PLAYING) return

  try {
    const { row, col, pointVO } = await selectAIStrategy()

    if (gameState.value !== STATE_PLAYING) return
    if (row === -1 || col === -1) return

    const success = await executeAIMove(row, col, pointVO)

    // 如果失败，尝试降级到简单算法
    if (!success && gameState.value === STATE_PLAYING) {
      await executeFallbackAIMove()
    }
  } catch {
    // AI策略失败，降级到随机算法
    if (gameState.value === STATE_PLAYING) {
      await executeFallbackAIMove().catch(() => {
        ElMessage.error('AI落子失败,请刷新页面重试')
      })
    }
  }
}

// 发送AI落子到后端记录
async function sendAIMove(row, col, pointVO = null) {
  // 防御性检查
  if (!roomInfo.value?.roomCode) {
    return
  }

  // 检查坐标有效性
  if (!isValidPosition(row, col)) {
    return
  }

  const moveData = pointVO || {
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

// 简单难度：随机落子 + 基础防守 + 简单攻击
function getRandomMove() {
  const aiColor = opponentColor.value
  const playerColor = myColor.value

  // 1. 优先检查AI是否能获胜（一步制胜）
  const winMove = findWinningMove(aiColor)
  if (winMove) return winMove

  // 2. 检查是否需要阻止玩家获胜（玩家有4连）
  const blockMove = findWinningMove(playerColor)
  if (blockMove) return blockMove

  // 3. 检查玩家是否有3连威胁（双端开放的3连）
  const blockThreeMove = findThreatenMove(playerColor)
  if (blockThreeMove) return blockThreeMove

  // 4. 检查AI是否能形成3连（进攻）
  const attackThreeMove = findThreatenMove(aiColor)
  if (attackThreeMove) return attackThreeMove

  // 5. 优先在已有棋子附近落子（智能随机）
  const nearbyMove = findNearbyMove()
  if (nearbyMove) return nearbyMove

  // 6. 完全随机选择空位（作为最后的兜底）
  const emptyCells = []
  for (let r = 0; r < BOARD_SIZE; r++) {
    // 防御性检查：确保board.value[r]存在
    if (!board.value[r]) continue
    for (let c = 0; c < BOARD_SIZE; c++) {
      if (board.value[r][c] === null) {
        emptyCells.push({ row: r, col: c })
      }
    }
  }

  if (emptyCells.length === 0) {
    return { row: -1, col: -1 }
  }

  const randomIndex = Math.floor(Math.random() * emptyCells.length)
  return emptyCells[randomIndex]
}

// 查找能够获胜的位置（检查是否能形成五连）
function findWinningMove(color) {
  return findPatternMove(color, (r, c, color) => checkWinner(r, c, color))
}

// 查找3连威胁位置（双端开放的3连）
function findThreatenMove(color) {
  return findPatternMove(color, (r, c, color) => {
    const { count, openEnds } = countMaxLine(r, c, color)
    // 3连且双端开放，或者4连（无论是否开放）
    return (count >= 3 && openEnds >= 2) || count >= 4
  })
}

// 统计某个位置的最大连子数和开放端点
function countMaxLine(row, col, color) {
  const directions = [
    { dr: 0, dc: 1 },
    { dr: 1, dc: 0 },
    { dr: 1, dc: 1 },
    { dr: 1, dc: -1 }
  ]

  let maxCount = 0
  let maxOpenEnds = 0

  for (const { dr, dc } of directions) {
    const result = countLineStones(row, col, dr, dc, color)
    if (result.count > maxCount || (result.count === maxCount && result.openEnds > maxOpenEnds)) {
      maxCount = result.count
      maxOpenEnds = result.openEnds
    }
  }

  return { count: maxCount, openEnds: maxOpenEnds }
}

// 在已有棋子附近找位置（智能随机）
function findNearbyMove() {
  const nearbyCells = []
  const maxDistance = 2

  for (let r = 0; r < BOARD_SIZE; r++) {
    // 防御性检查：确保board.value[r]存在
    if (!board.value[r]) continue
    for (let c = 0; c < BOARD_SIZE; c++) {
      if (board.value[r][c] === null && hasStoneNearby(r, c, maxDistance)) {
        nearbyCells.push({ row: r, col: c })
      }
    }
  }

  if (nearbyCells.length === 0) return null
  const randomIndex = Math.floor(Math.random() * nearbyCells.length)
  return nearbyCells[randomIndex]
}

// 检查某位置附近是否有棋子
function hasStoneNearby(row, col, maxDistance) {
  for (let dr = -maxDistance; dr <= maxDistance; dr++) {
    for (let dc = -maxDistance; dc <= maxDistance; dc++) {
      if (dr === 0 && dc === 0) continue
      const nr = row + dr
      const nc = col + dc
      // 防御性检查：确保board.value[nr]存在
      if (isValidPosition(nr, nc) && board.value[nr] && board.value[nr][nc] !== null) {
        return true
      }
    }
  }
  return false
}

// 统计威胁数量（活三和活四）
function countThreats(row, col, color) {
  let openThreeCount = 0
  let openFourCount = 0
  const directions = [
    { dr: 0, dc: 1 },
    { dr: 1, dc: 0 },
    { dr: 1, dc: 1 },
    { dr: 1, dc: -1 }
  ]

  for (const { dr, dc } of directions) {
    const { count, openEnds } = countLineStones(row, col, dr, dc, color)
    if (count === 3 && openEnds === 2) openThreeCount++
    else if (count === 4 && openEnds >= 1) openFourCount++
  }

  return { openThreeCount, openFourCount }
}

// 判断是否为双重威胁
function isDoubleThreat(openThreeCount, openFourCount) {
  return openThreeCount >= 2 || openFourCount >= 2 || (openThreeCount >= 1 && openFourCount >= 1)
}

// 查找双三位置（两个或以上的活三交叉）
function findDoubleThreatMove(color) {
  return findPatternMove(color, (r, c, color) => {
    const { openThreeCount, openFourCount } = countThreats(r, c, color)
    return isDoubleThreat(openThreeCount, openFourCount)
  })
}

// 检查是否有冲四
function hasRushFourAtPosition(row, col, color) {
  const directions = [
    { dr: 0, dc: 1 },
    { dr: 1, dc: 0 },
    { dr: 1, dc: 1 },
    { dr: 1, dc: -1 }
  ]

  for (const { dr, dc } of directions) {
    const { count, openEnds } = countLineStones(row, col, dr, dc, color)
    if (count === 4 && openEnds >= 1) return true
  }
  return false
}

// 查找冲四位置（下一步能形成活四的位置）
function findRushFourMove(color) {
  return findPatternMove(color, (r, c, color) => hasRushFourAtPosition(r, c, color))
}

// 中等难度：评分算法 + 战术识别
function getMediumMove() {
  const aiColor = opponentColor.value
  const playerColor = myColor.value

  // 【关键战术1】检查是否能形成双三（必胜棋形）
  const myDoubleThreat = findDoubleThreatMove(aiColor)
  if (myDoubleThreat) return myDoubleThreat

  // 【关键战术2】阻止对方双三
  const opponentDoubleThreat = findDoubleThreatMove(playerColor)
  if (opponentDoubleThreat) return opponentDoubleThreat

  // 检查是否能形成冲四（一步成活四）
  const myRushFour = findRushFourMove(aiColor)
  if (myRushFour) return myRushFour

  // 阻止对方冲四
  const opponentRushFour = findRushFourMove(playerColor)
  if (opponentRushFour) return opponentRushFour

  // 原有的评分算法
  const candidates = calculateAllCandidates()

  // 15%概率从前5个较好位置随机选一个（降低随机性，提高水平）
  if (Math.random() < 0.15) {
    return selectRandomFromTopN(candidates, 5)
  }

  // 正常选择最佳位置
  return candidates.length > 0
    ? { row: candidates[0].row, col: candidates[0].col }
    : { row: -1, col: -1 }
}

// 计算所有候选位置并排序（性能优化：只评估有棋子附近的位置）
function calculateAllCandidates() {
  const candidates = []
  const aiColor = opponentColor.value
  const playerColor = myColor.value
  const searchRadius = 2 // 只搜索棋子周围2格内的位置

  // 性能优化：只评估有棋子附近的位置
  for (let r = 0; r < BOARD_SIZE; r++) {
    if (!board.value[r]) continue
    for (let c = 0; c < BOARD_SIZE; c++) {
      if (board.value[r][c] !== null) continue

      // 检查附近是否有棋子，没有则跳过（除非是第一步）
      if (moveHistory.value.length > 0 && !hasStoneNearby(r, c, searchRadius)) {
        continue
      }

      const attackScore = evaluatePosition(r, c, aiColor)
      const defenseScore = evaluatePosition(r, c, playerColor)
      const totalScore = attackScore + defenseScore * 0.9

      candidates.push({ row: r, col: c, score: totalScore })
    }
  }

  candidates.sort((a, b) => b.score - a.score)
  return candidates
}

// 从前N个候选中随机选择
function selectRandomFromTopN(candidates, n) {
  const topCandidates = candidates.slice(0, Math.min(n, candidates.length))
  if (topCandidates.length === 0) return { row: -1, col: -1 }

  const randomIndex = Math.floor(Math.random() * topCandidates.length)
  return { row: topCandidates[randomIndex].row, col: topCandidates[randomIndex].col }
}

// 评估某个位置的分数
function evaluatePosition(row, col, color) {
  let score = 0

  // 四个方向：横、竖、主对角线、副对角线
  const directions = [
    { dr: 0, dc: 1 },   // 横向
    { dr: 1, dc: 0 },   // 竖向
    { dr: 1, dc: 1 },   // 主对角线
    { dr: 1, dc: -1 }   // 副对角线
  ]

  for (const { dr, dc } of directions) {
    const lineScore = evaluateLine(row, col, dr, dc, color)
    score += lineScore
  }

  return score
}

// 评估某个方向上的连子情况
function evaluateLine(row, col, dr, dc, color) {
  return withTemporaryStone(row, col, color, () => {
    // 统计连续棋子数和开放端点数
    const { count, openEnds } = countLineStones(row, col, dr, dc, color)
    // 根据连子数和开放端点数评分
    return calculateLineScore(count, openEnds)
  }) || 0
}

// 统计某个方向上的连子数和开放端点
function countLineStones(row, col, dr, dc, color) {
  let count = 1
  let openEnds = 0

  // 正向统计
  openEnds += countSingleDirection(row, col, dr, dc, color, (c) => { count += c })

  // 反向统计
  openEnds += countSingleDirection(row, col, -dr, -dc, color, (c) => { count += c })

  return { count, openEnds }
}

// 统计单个方向
function countSingleDirection(row, col, dr, dc, color, addCount) {
  let r = row + dr
  let c = col + dc
  let localCount = 0

  while (isValidPosition(r, c)) {
    // 防御性检查：确保board.value[r]存在
    if (!board.value[r]) {
      addCount(localCount)
      return 0
    }

    if (board.value[r][c] === color) {
      localCount++
      r += dr
      c += dc
    } else if (board.value[r][c] === null) {
      addCount(localCount)
      return 1 // 开放端点
    } else {
      addCount(localCount)
      return 0 // 被阻挡
    }
  }

  addCount(localCount)
  return 0 // 边界阻挡
}

// 计算分数（优化权重，提升到75分水平）
function calculateLineScore(count, openEnds) {
  if (count >= 5) return 100000

  // 【优化】提升关键棋型的分数
  const scoreMap = {
    4: [0, 5000, 50000],    // 活四是必胜，权重极高
    3: [0, 200, 3000],      // 活三是强威胁，权重提升
    2: [0, 20, 200],        // 双端开放的2连
    1: [1, 10, 10]          // 单子
  }

  const scores = scoreMap[count]
  if (!scores) return 0

  return scores[openEnds] || 0
}

// 转换棋盘状态为AI格式
function convertBoardToAIFormat() {
  const cellToNumber = (cell) => {
    if (cell === null) return 0
    return cell === COLOR_BLACK ? 1 : 2
  }

  return board.value.map(row => row.map(cellToNumber))
}

// 构建最后一步落子信息
function buildLastMoveVO() {
  if (!lastMove.value) return null

  const { row, col } = lastMove.value
  const color = board.value[row]?.[col]

  return color ? { x: col, y: row, color, id: user.value?.id || 0 } : null
}

// 处理AI错误并降级
function handleAIError(errorCode) {
  const message = errorCode === 4001 ? 'AI出现异常，已切换到普通难度' : 'AI服务暂时不可用，使用普通难度'
  ElMessage.warning(message)
  return getMediumMove()
}

// 困难难度：调用后端大模型API
async function getHardMove() {
  try {
    const response = await aiRequest.post('/gomoku/ai/move', {
      board: convertBoardToAIFormat(),
      aiColor: opponentColor.value === COLOR_BLACK ? 1 : 2,
      lastMove: buildLastMoveVO()
    })

    const { data } = response

    if (data.code === 4001 || !data.success || !data.data) {
      return handleAIError(data.code)
    }

    return {
      row: data.data.y,
      col: data.data.x,
      pointVO: data.data
    }
  } catch (error) {
    const errorCode = error?.response?.data?.code
    return handleAIError(errorCode)
  }
}

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
          receiveMove(data)
          break
        case 'playerLeft':
          handleOpponentLeft(data)
          break
        case 'gameEnd':
          handleGameEndNotification(data)
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
            ElMessage.warning('无法恢复游戏数据：' + (data.message || '未知错误'))
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
        ElMessage.warning('恢复游戏数据超时')
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

  // 先取消旧的订阅（如果存在）
  wsService.unsubscribe(topic)

  // 等待一小段时间确保取消完成
  await new Promise(resolve => setTimeout(resolve, 100))

  // 强制重新订阅游戏界面的handler，覆盖等待界面的handler
  // 这样可以确保游戏界面的消息处理器（createMessageHandler）被正确注册
  wsService.subscribe(topic, createMessageHandler())

  if (await waitForSubscription(topic)) {
    // 订阅成功后，尝试恢复游戏数据（处理断线重连情况）
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
          {{ isMyTurn ? '你的回合' : (isAIGame ? 'AI思考中' : '对手回合') }}
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
  width: 32px;
  height: 32px;
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
  width: 36px;
  height: 36px;
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
  width: 32px;
  height: 32px;
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
</style>
