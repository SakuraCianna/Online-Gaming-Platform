<template>
  <div class="minesweeper-root">
    <!-- 难度选择界面 -->
    <div v-if="stage === 'select'" class="difficulty-select">
      <h1>选择难度</h1>
      <div class="difficulty-btns">
        <button v-for="option in difficultyOptions" :key="option.level" @click="showRules(option)">
          {{ option.label }}
        </button>
      </div>
      <button class="back-btn" @click="goBack">返回主界面</button>
    </div>

    <!-- 规则展示界面 -->
    <div v-if="stage === 'rules'" class="rules-display">
      <h2>{{ selectedDifficulty.label }} - 游戏规则</h2>
      <div class="rules-content">
        <div class="rule-item">
          <span class="rule-label">棋盘大小：</span>
          <span class="rule-value">{{ selectedDifficulty.boardWidth }}x{{ selectedDifficulty.boardHeight }}</span>
        </div>
        <div class="rule-item">
          <span class="rule-label">地雷数量：</span>
          <span class="rule-value">{{ selectedDifficulty.mineCount }}个</span>
        </div>
        <div class="rule-item">
          <span class="rule-label">游戏目标：</span>
          <span class="rule-value">找出所有地雷，标记所有安全格子</span>
        </div>
        <div class="rule-item">
          <span class="rule-label">操作说明：</span>
          <div class="control-list">
            <div class="control-item">
              <span class="control-key">左键点击</span>
              <span class="control-desc">揭开格子</span>
            </div>
            <div class="control-item">
              <span class="control-key">右键点击</span>
              <span class="control-desc">标记地雷</span>
            </div>
            <div class="control-item">
              <span class="control-key">双击</span>
              <span class="control-desc">快速展开</span>
            </div>
          </div>
        </div>
        <div class="rule-item">
          <span class="rule-label">胜利条件：</span>
          <span class="rule-value">成功标记所有地雷位置</span>
        </div>
        <div class="rule-item">
          <span class="rule-label">失败条件：</span>
          <span class="rule-value">点击到地雷时游戏结束</span>
        </div>
      </div>
      <div class="action-btns">
        <button class="start-btn" @click="startGame">开始游戏</button>
        <button class="back-btn" @click="backToSelect">返回选择</button>
      </div>
    </div>

    <!-- 爆炸动画界面 -->
    <div v-if="stage === 'explosion'" class="explosion-anim" :class="{ 'anim-active': explosionStarted }">
      <!-- 粒子背景 -->
      <div class="particles">
        <div class="particle" v-for="i in 30" :key="i" :style="getParticleStyle(i)"></div>
      </div>

      <!-- 巨大地雷 -->
      <div class="giant-mine">💣</div>

      <!-- 爆炸火光 -->
      <div class="explosion-fire-burst">
        <div class="fire-core"></div>
        <div class="fire-ring fire-ring-1"></div>
        <div class="fire-ring fire-ring-2"></div>
        <div class="fire-ring fire-ring-3"></div>
      </div>

      <!-- 冲击波 -->
      <div class="shockwave shockwave-1"></div>
      <div class="shockwave shockwave-2"></div>

      <!-- 白光晕眩层 -->
      <div class="white-flash-overlay"></div>
    </div>

    <!-- 游戏主界面 -->
    <div v-if="stage === 'game'" class="game-main">
      <!-- 标题上移 -->
      <h2 class="game-title">扫雷 - {{ currentDifficulty.label }}</h2>
      <!-- 信息表格 -->
      <div class="game-info-table">
        <div class="info-cell">用时：{{ formattedDuration }}</div>
        <div class="info-cell">剩余地雷：{{ remainingMines }}</div>
        <div class="info-cell">已标记：{{ flaggedCount }}</div>
      </div>
      <!-- 按钮行 -->
      <div class="game-btn-row">
        <button class="save-btn" @click="saveAndExit">保存并退出</button>
        <button class="save-btn" @click="restartGame">重新开始</button>
        <button class="save-btn" @click="exitWithoutSave">不保存并退出</button>
      </div>

      <!-- 游戏棋盘 -->
      <div class="game-board" :style="boardStyle">
        <div v-for="(cell, idx) in boardCells" :key="idx" class="cell" :class="getCellClass(cell)"
          @click="handleLeftClick(idx)" @contextmenu.prevent="handleRightClick(idx)" @dblclick="handleDoubleClick(idx)">
          <span v-if="cell.isRevealed && cell.mineCount > 0 && !cell.isMine" class="mine-count">
            {{ cell.mineCount }}
          </span>
          <span v-else-if="cell.isFlagged" class="flag">🚩</span>
          <span v-else-if="cell.isRevealed && cell.isMine" class="mine">💣</span>
          <span v-else-if="cell.isRevealed && cell.isExploded" class="exploded">💥</span>
        </div>
      </div>

      <!-- 游戏胜利弹窗 -->
      <div v-if="gameWin" class="game-result">
        <div class="result-text">🎉 恭喜通关！</div>
        <div class="result-time">用时：{{ formattedDuration }}</div>
        <button class="save-btn" @click="restartGame">再来一局</button>
      </div>

      <!-- 游戏失败弹窗 -->
      <div v-if="gameOver && !gameWin" class="game-result enhanced-result">
        <div class="result-title">游戏结束</div>
        <div class="result-desc">很遗憾,踩到地雷了!要不要再试一次?</div>
        <div class="result-actions">
          <button class="result-btn retry-btn" @click="restartGame">再来一局</button>
          <button class="result-btn exit-btn" @click="exitWithoutSave">退出游戏</button>
        </div>
      </div>
    </div>

    <!-- 自定义弹窗 -->
    <div v-if="showCustomDialog" class="custom-dialog-mask">
      <div class="custom-dialog">
        <div class="custom-dialog-title">{{ dialogTitle }}</div>
        <div class="custom-dialog-content">{{ dialogMessage }}</div>
        <div class="custom-dialog-actions">
          <button @click="onDialogConfirm">继续</button>
          <button @click="onDialogCancel">新游戏</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import request from '../../config/api'
import { ElMessage } from 'element-plus'
import { storeToRefs } from 'pinia'
import { useUserStore } from '../../config/user'

const userStore = useUserStore()
const { user } = storeToRefs(userStore)

const router = useRouter()

// 难度选项
const difficultyOptions = [
  {
    label: '初级',
    level: 'beginner',
    boardWidth: 9,
    boardHeight: 9,
    mineCount: 10
  },
  {
    label: '中级',
    level: 'intermediate',
    boardWidth: 16,
    boardHeight: 16,
    mineCount: 40
  },
  {
    label: '高级',
    level: 'expert',
    boardWidth: 30,
    boardHeight: 16,
    mineCount: 99
  }
]

const stage = ref('select') // select | rules | explosion | game
const selectedDifficulty = ref(null)
const currentDifficulty = ref(null)
const explosionStarted = ref(false)

// 游戏状态
const board = ref([]) // 二维数组
const duration = ref(0)
const timer = ref(null)
const isTiming = ref(false)
const gameOver = ref(false)
const gameWin = ref(false)
const isFirstClick = ref(true)

// 弹窗相关变量
const showCustomDialog = ref(false)
const dialogTitle = ref('')
const dialogMessage = ref('')
let dialogResolve = null
let dialogReject = null
const inProgressGame = ref(null)

// 添加一个标志变量来区分是新建游戏还是恢复存档
const isRestoringGame = ref(false)

// 格式化用时
const formattedDuration = computed(() => {
  const min = Math.floor(duration.value / 60)
  const sec = duration.value % 60
  return `${min}:${sec.toString().padStart(2, '0')}`
})

// 计算剩余地雷数
const remainingMines = computed(() => {
  const flaggedCount = board.value.flat().filter(cell => cell.isFlagged).length
  return currentDifficulty.value.mineCount - flaggedCount
})

// 计算已标记数
const flaggedCount = computed(() => {
  return board.value.flat().filter(cell => cell.isFlagged).length
})

// 返回主界面
function goBack() {
  router.push('/user')
}

// 显示规则
function showRules(option) {
  selectedDifficulty.value = option
  stage.value = 'rules'
}

// 返回选择界面
function backToSelect() {
  stage.value = 'select'
  selectedDifficulty.value = null
}

// 开始游戏
function startGame() {
  currentDifficulty.value = selectedDifficulty.value
  stage.value = 'explosion'
  explosionStarted.value = false
  // 触发动画
  setTimeout(() => {
    explosionStarted.value = true
  }, 50)
  // 2.5秒后切换到游戏界面
  setTimeout(() => {
    stage.value = 'game'
    initBoard()
  }, 2500)
}

// 生成粒子样式
function getParticleStyle(index) {
  const angle = (index / 30) * 360
  const distance = 40 + Math.random() * 60
  const size = 4 + Math.random() * 8
  const duration = 0.8 + Math.random() * 0.6
  const delay = Math.random() * 0.3

  return {
    '--angle': `${angle}deg`,
    '--distance': `${distance}vw`,
    '--size': `${size}px`,
    '--duration': `${duration}s`,
    '--delay': `${delay}s`,
    '--hue': `${Math.random() * 60}`
  }
}

// 计时器
function startTimer() {
  timer.value = setInterval(() => {
    duration.value++
  }, 1000)
}
function stopTimer() {
  clearInterval(timer.value)
  timer.value = null
  isTiming.value = false
}

// 初始化棋盘
function initBoard() {
  const { boardWidth, boardHeight } = currentDifficulty.value
  board.value = Array.from({ length: boardHeight }, (_, row) =>
    Array.from({ length: boardWidth }, (_, col) => ({
      row,
      col,
      isMine: false,
      isRevealed: false,
      isFlagged: false,
      isExploded: false,
      mineCount: 0
    }))
  )
  duration.value = 0
  isTiming.value = false
  gameOver.value = false
  gameWin.value = false
  isFirstClick.value = true
  clearInterval(timer.value)
}

// 生成地雷
function generateMines(firstRow, firstCol) {
  const { boardWidth, boardHeight, mineCount } = currentDifficulty.value
  const mines = []

  // 确保第一次点击的位置及其周围没有地雷
  const safeZone = []
  for (let r = Math.max(0, firstRow - 1); r <= Math.min(boardHeight - 1, firstRow + 1); r++) {
    for (let c = Math.max(0, firstCol - 1); c <= Math.min(boardWidth - 1, firstCol + 1); c++) {
      safeZone.push(`${r},${c}`)
    }
  }

  // 随机生成地雷位置
  while (mines.length < mineCount) {
    const row = Math.floor(Math.random() * boardHeight)
    const col = Math.floor(Math.random() * boardWidth)
    const key = `${row},${col}`

    if (!mines.includes(key) && !safeZone.includes(key)) {
      mines.push(key)
      board.value[row][col].isMine = true
    }
  }

  // 计算每个格子周围的地雷数
  for (let r = 0; r < boardHeight; r++) {
    for (let c = 0; c < boardWidth; c++) {
      if (!board.value[r][c].isMine) {
        board.value[r][c].mineCount = countAdjacentMines(r, c)
      }
    }
  }
}

// 计算周围地雷数
function countAdjacentMines(row, col) {
  const { boardWidth, boardHeight } = currentDifficulty.value
  let count = 0

  for (let r = Math.max(0, row - 1); r <= Math.min(boardHeight - 1, row + 1); r++) {
    for (let c = Math.max(0, col - 1); c <= Math.min(boardWidth - 1, col + 1); c++) {
      if (board.value[r][c].isMine) count++
    }
  }
  return count
}

// 左键点击
function handleLeftClick(idx) {
  if (gameOver.value || gameWin.value) return

  const { boardWidth } = currentDifficulty.value
  const row = Math.floor(idx / boardWidth)
  const col = idx % boardWidth
  const cell = board.value[row][col]

  if (cell.isFlagged || cell.isRevealed) return

  // 第一次点击时生成地雷
  if (isFirstClick.value) {
    generateMines(row, col)
    isFirstClick.value = false
    startTimer()
    isTiming.value = true
  }

  // 点击到地雷
  if (cell.isMine) {
    cell.isExploded = true
    revealAllMines()
    gameOver.value = true
    stopTimer()
    autoSaveGame()
    return
  }

  // 揭开格子
  revealCell(row, col)

  // 检查是否胜利
  if (checkWin()) {
    gameWin.value = true
    stopTimer()
    autoSaveGame()
  }
}

// 右键点击
function handleRightClick(idx) {
  if (gameOver.value || gameWin.value) return

  const { boardWidth } = currentDifficulty.value
  const row = Math.floor(idx / boardWidth)
  const col = idx % boardWidth
  const cell = board.value[row][col]

  if (cell.isRevealed) return

  cell.isFlagged = !cell.isFlagged
}

// 双击快速展开
function handleDoubleClick(idx) {
  if (gameOver.value || gameWin.value) return

  const { boardWidth } = currentDifficulty.value
  const row = Math.floor(idx / boardWidth)
  const col = idx % boardWidth
  const cell = board.value[row][col]

  if (!cell.isRevealed || cell.mineCount === 0) return

  // 检查周围标记的地雷数是否正确
  const adjacentFlags = countAdjacentFlags(row, col)
  if (adjacentFlags !== cell.mineCount) return

  // 快速展开周围未标记的格子
  const exploded = quickReveal(row, col)
  if (exploded) {
    gameOver.value = true
    stopTimer()
    autoSaveGame()
  } else if (checkWin()) {
    gameWin.value = true
    stopTimer()
    autoSaveGame()
  }
}

// 计算周围标记数
function countAdjacentFlags(row, col) {
  const { boardWidth, boardHeight } = currentDifficulty.value
  let count = 0

  for (let r = Math.max(0, row - 1); r <= Math.min(boardHeight - 1, row + 1); r++) {
    for (let c = Math.max(0, col - 1); c <= Math.min(boardWidth - 1, col + 1); c++) {
      if (board.value[r][c].isFlagged) count++
    }
  }

  return count
}

// 快速展开
function quickReveal(row, col) {
  const { boardWidth, boardHeight } = currentDifficulty.value
  let exploded = false

  for (let r = Math.max(0, row - 1); r <= Math.min(boardHeight - 1, row + 1); r++) {
    for (let c = Math.max(0, col - 1); c <= Math.min(boardWidth - 1, col + 1); c++) {
      const cell = board.value[r][c]
      if (!cell.isRevealed && !cell.isFlagged) {
        if (cell.isMine) {
          cell.isExploded = true
          exploded = true
        } else {
          revealCell(r, c)
        }
      }
    }
  }

  // 如果踩到雷，显示所有地雷
  if (exploded) {
    revealAllMines()
  }

  return exploded
}

// 揭开格子
function revealCell(row, col) {
  const cell = board.value[row][col]
  if (cell.isRevealed || cell.isFlagged) return

  cell.isRevealed = true

  // 如果周围没有地雷，自动展开
  if (cell.mineCount === 0) {
    const { boardWidth, boardHeight } = currentDifficulty.value
    for (let r = Math.max(0, row - 1); r <= Math.min(boardHeight - 1, row + 1); r++) {
      for (let c = Math.max(0, col - 1); c <= Math.min(boardWidth - 1, col + 1); c++) {
        if (r !== row || c !== col) {
          revealCell(r, c)
        }
      }
    }
  }
}

// 显示所有地雷
function revealAllMines() {
  for (const row of board.value) {
    for (const cell of row) {
      if (cell.isMine && !cell.isExploded) {
        cell.isRevealed = true
      }
    }
  }
}

// 检查是否胜利
function checkWin() {
  const totalCells = currentDifficulty.value.boardWidth * currentDifficulty.value.boardHeight
  const revealedCells = board.value.flat().filter(cell => cell.isRevealed).length
  return revealedCells === totalCells - currentDifficulty.value.mineCount
}

// 获取格子样式类
function getCellClass(cell) {
  if (cell.isRevealed) {
    if (cell.isMine) {
      return cell.isExploded ? 'cell-exploded' : 'cell-mine'
    }
    return `cell-revealed cell-${cell.mineCount}`
  }
  return cell.isFlagged ? 'cell-flagged' : 'cell-hidden'
}

// 提取公共的数据组装函数
function getSaveData() {
  const id = user.value?.id
  let status = 0 // 0为进行中/中途退出
  if (gameWin.value) {
    status = 1 // 通关
  } else if (gameOver.value) {
    status = 2 // 失败
  }

  // 计算正确排雷数：标记的格子确实是地雷的数量
  const correctFlags = board.value.flat().filter(cell => cell.isFlagged && cell.isMine).length

  return {
    user_id: id,
    difficulty: currentDifficulty.value.level,
    board_width: currentDifficulty.value.boardWidth,
    board_height: currentDifficulty.value.boardHeight,
    mine_count: currentDifficulty.value.mineCount,
    correct_flags: correctFlags,
    duration: duration.value,
    status: status,
    game_data: JSON.stringify(board.value)
  }
}

// 自动保存
async function autoSaveGame() {
  const data = getSaveData()
  try {
    if (inProgressGame.value && inProgressGame.value.id) {
      data.id = inProgressGame.value.id
      await request.post('/minesweeper/update', data)
    } else {
      await request.post('/minesweeper/save', data)
    }
  } catch (e) {
    console.log("自动保存失败: " + e)
  }
}

// 保存并退出
async function saveAndExit() {
  stopTimer()
  const data = getSaveData()
  try {
    let response
    if (inProgressGame.value && inProgressGame.value.id) {
      data.id = inProgressGame.value.id
      response = await request.post('/minesweeper/update', data)
    } else {
      response = await request.post('/minesweeper/save', data)
    }

    if (response.data.code === 200) {
      ElMessage.success(response.data.message || '游戏已保存！')
      router.push('/user')
    } else {
      ElMessage.error(response.data.message || '保存失败，请重试')
    }
  } catch (e) {
    console.log("保存失败: " + e)
    ElMessage.error('保存失败，请重试')
  }
}

// 不保存并退出
function exitWithoutSave() {
  stopTimer()
  router.push('/user')
}

// 重新开始
async function restartGame() {
  if (inProgressGame.value) {
    await deleteInProgressGame()
  }
  initBoard()
}

// 弹窗提示用户是否继续
function showContinueDialog() {
  dialogTitle.value = '继续游戏'
  dialogMessage.value = '检测到你有一局未完成的扫雷游戏,是否继续?'
  showCustomDialog.value = true
  return new Promise((resolve, reject) => {
    dialogResolve = resolve
    dialogReject = reject
  }).then(() => {
    loadInProgressGame()
  }).catch(error_ => {
    if (error_ === 'cancel') {
      deleteInProgressGame()
    }
  })
}

function onDialogConfirm() {
  showCustomDialog.value = false
  setTimeout(() => {
    dialogResolve && dialogResolve()
  }, 0)
}

function onDialogCancel() {
  showCustomDialog.value = false
  setTimeout(() => {
    dialogReject && dialogReject('cancel')
  }, 0)
}

// 恢复游戏状态
function loadInProgressGame() {
  const data = inProgressGame.value
  const diff = difficultyOptions.find(opt => opt.level === data.difficulty)
  if (!diff) {
    ElMessage.error('存档数据异常，无法恢复')
    stage.value = 'select'
    return
  }

  isRestoringGame.value = true
  currentDifficulty.value = diff

  board.value = JSON.parse(data.gameData)
  duration.value = data.duration

  gameOver.value = data.status === 2
  gameWin.value = data.status === 1
  isTiming.value = data.status === 0

  stage.value = 'game'

  if (isTiming.value) startTimer()

  setTimeout(() => {
    isRestoringGame.value = false
  }, 0)
}

// 删除旧记录，进入新游戏
async function deleteInProgressGame() {
  try {
    await request.delete('/minesweeper/delete', { params: { id: inProgressGame.value.id } })
    inProgressGame.value = null
    stage.value = 'select'
  } catch (e) {
    ElMessage.error('删除旧存档失败，请重试')
    stage.value = 'select'
    console.log(e)
  }
}

// 监听stage切换到game时初始化棋盘
watch(stage, (val) => {
  if (val === 'game' && !isRestoringGame.value && currentDifficulty.value) {
    initBoard()
  }
})

// 生命周期
onMounted(async () => {
  userStore.setCurrentGame("扫雷")
  if (!user.value?.id) {
    stage.value = 'select'
    return
  }
  try {
    const res = await request.get('/minesweeper/getInProgress', { params: { user_id: user.value.id } })
    if (res.data && res.data.id) {
      inProgressGame.value = res.data
      await showContinueDialog()
    } else {
      stage.value = 'select'
    }
  } catch (e) {
    console.log(e)
    stage.value = 'select'
  }
})
onUnmounted(() => {
  stopTimer()
  userStore.clearCurrentGame()
})

// 棋盘一维渲染
const boardCells = computed(() => {
  if (!board.value || !board.value.length) return []
  return board.value.flat()
})
const boardStyle = computed(() => {
  if (!currentDifficulty.value) return {}
  const { boardWidth, boardHeight } = currentDifficulty.value
  return {
    gridTemplateColumns: `repeat(${boardWidth}, 1fr)`,
    gridTemplateRows: `repeat(${boardHeight}, 1fr)`
  }
})
</script>

<style scoped>
.minesweeper-root {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(135deg, #f0f8ff 0%, #e6f3ff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  overflow: hidden;
}

.difficulty-select {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
}

.difficulty-btns {
  margin-top: 2rem;
  display: flex;
  gap: 2rem;
  margin-bottom: 2rem;
}

.difficulty-btns button {
  font-size: 1.5rem;
  padding: 1rem 2.5rem;
  border-radius: 1rem;
  border: none;
  background: #4a90e2;
  color: #fff;
  cursor: pointer;
  transition: background 0.2s;
}

.difficulty-btns button:hover {
  background: #357abd;
}

.back-btn {
  font-size: 1.2rem;
  padding: 0.8rem 2rem;
  border-radius: 0.8rem;
  border: none;
  background: #8f7a66;
  color: #fff;
  cursor: pointer;
  transition: background 0.2s;
}

.back-btn:hover {
  background: #7f6a56;
}

.rules-display {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  max-width: 600px;
  padding: 2rem;
}

.rules-content {
  background: rgba(255, 255, 255, 0.9);
  border-radius: 1rem;
  padding: 2rem;
  margin: 2rem 0;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  width: 100%;
}

.rule-item {
  display: flex;
  align-items: flex-start;
  margin-bottom: 1rem;
  padding: 0.5rem 0;
  border-bottom: 1px solid #eee;
}

.rule-item:last-child {
  border-bottom: none;
}

.rule-label {
  font-weight: bold;
  color: #2c3e50;
  min-width: 120px;
  margin-right: 1rem;
}

.rule-value {
  color: #2c3e50;
  flex: 1;
}

.control-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  flex: 1;
}

.control-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  background: #ecf0f1;
  padding: 0.5rem 1rem;
  border-radius: 0.5rem;
}

.control-key {
  font-weight: bold;
  color: #e74c3c;
  min-width: 80px;
}

.control-desc {
  color: #2c3e50;
}

.action-btns {
  display: flex;
  gap: 1rem;
  margin-top: 1rem;
}

.start-btn {
  font-size: 1.3rem;
  padding: 1rem 2.5rem;
  border-radius: 1rem;
  border: none;
  background: #e74c3c;
  color: #fff;
  cursor: pointer;
  transition: background 0.2s;
}

.start-btn:hover {
  background: #c0392b;
}

.transition-anim {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #f0f8ff 0%, #e6f3ff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  z-index: 10;
}

.transition-anim .mine-icon {
  display: inline-block;
  font-size: 3rem;
  margin: 0 10px;
  transform-style: preserve-3d;
}

.transition-anim .start-text {
  display: block;
  margin-top: 2.5rem;
  font-size: 3rem;
  color: #e74c3c;
  font-weight: bold;
  letter-spacing: 0.2em;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  transform-style: preserve-3d;
}

/* ========== 爆炸动画样式 ========== */
.explosion-anim {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #0a0a0a 0%, #1a1a2e 50%, #16213e 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  z-index: 10;
  overflow: hidden;
}

/* 粒子系统 */
.particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 3;
}

.particle {
  position: absolute;
  top: 50%;
  left: 50%;
  width: var(--size);
  height: var(--size);
  border-radius: 50%;
  background: hsl(calc(30 + var(--hue)), 100%, 60%);
  opacity: 0;
  transform: translate(-50%, -50%);
}

.anim-active .particle {
  animation: particle-burst var(--duration) ease-out var(--delay) forwards;
}

@keyframes particle-burst {
  0% {
    opacity: 0;
    transform: translate(-50%, -50%) rotate(var(--angle)) translateX(0) scale(0);
  }

  20% {
    opacity: 1;
  }

  100% {
    opacity: 0;
    transform: translate(-50%, -50%) rotate(var(--angle)) translateX(var(--distance)) scale(1);
  }
}

/* 巨大地雷 */
.giant-mine {
  position: absolute;
  font-size: 12rem;
  z-index: 5;
  opacity: 0;
  transform: scale(0) rotate(-180deg);
  filter: drop-shadow(0 0 20px rgba(255, 69, 0, 0.5));
}

.anim-active .giant-mine {
  animation: mine-appear 0.6s cubic-bezier(0.34, 1.56, 0.64, 1) forwards,
    mine-blink 0.15s 0.6s ease-in-out 3,
    mine-explode 0.2s 1.05s ease-in forwards;
}

@keyframes mine-appear {
  to {
    opacity: 1;
    transform: scale(1.1) rotate(0deg);
    filter: drop-shadow(0 0 40px rgba(255, 69, 0, 0.9));
  }
}

@keyframes mine-blink {

  0%,
  100% {
    filter: drop-shadow(0 0 40px rgba(255, 69, 0, 0.9));
  }

  50% {
    filter: drop-shadow(0 0 80px rgba(255, 0, 0, 1)) brightness(2);
  }
}

@keyframes mine-explode {
  to {
    opacity: 0;
    transform: scale(0) rotate(180deg);
  }
}

/* 爆炸火光容器 */
.explosion-fire-burst {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 8;
}

/* 火焰核心 */
.fire-core {
  position: absolute;
  width: 200px;
  height: 200px;
  background: radial-gradient(circle,
      #fff 0%,
      #ffeb3b 10%,
      #ff9800 25%,
      #ff5722 40%,
      #f44336 60%,
      #d32f2f 80%,
      transparent 100%);
  border-radius: 50%;
  filter: blur(15px);
  opacity: 0;
  transform: scale(0);
}

.anim-active .fire-core {
  animation: fire-core-blast 0.5s 1.05s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}

@keyframes fire-core-blast {
  0% {
    opacity: 0;
    transform: scale(0);
    box-shadow: 0 0 0 transparent;
  }

  50% {
    opacity: 1;
    box-shadow:
      0 0 100px #ff5722,
      0 0 200px #ff9800,
      0 0 300px #ffeb3b;
  }

  100% {
    opacity: 0;
    transform: scale(4);
    box-shadow:
      0 0 150px #ff5722,
      0 0 300px #ff9800,
      0 0 450px #ffeb3b;
  }
}

/* 火焰环 */
.fire-ring {
  position: absolute;
  width: 250px;
  height: 250px;
  border-radius: 50%;
  opacity: 0;
  transform: scale(0);
}

.fire-ring-1 {
  border: 20px solid rgba(255, 235, 59, 0.9);
  box-shadow: 0 0 80px rgba(255, 235, 59, 0.8), inset 0 0 60px rgba(255, 152, 0, 0.5);
}

.fire-ring-2 {
  border: 15px solid rgba(255, 152, 0, 0.8);
  box-shadow: 0 0 100px rgba(255, 152, 0, 0.8), inset 0 0 50px rgba(255, 87, 34, 0.5);
}

.fire-ring-3 {
  border: 10px solid rgba(255, 87, 34, 0.7);
  box-shadow: 0 0 120px rgba(255, 87, 34, 0.8), inset 0 0 40px rgba(244, 67, 54, 0.5);
}

.anim-active .fire-ring-1 {
  animation: fire-ring-expand 0.5s 1.05s ease-out forwards;
}

.anim-active .fire-ring-2 {
  animation: fire-ring-expand 0.5s 1.1s ease-out forwards;
}

.anim-active .fire-ring-3 {
  animation: fire-ring-expand 0.5s 1.15s ease-out forwards;
}

@keyframes fire-ring-expand {
  0% {
    opacity: 0;
    transform: scale(0);
  }

  30% {
    opacity: 1;
  }

  100% {
    opacity: 0;
    transform: scale(6);
  }
}

/* 冲击波 */
.shockwave {
  position: absolute;
  width: 100px;
  height: 100px;
  border: 4px solid rgba(255, 255, 255, 0.8);
  border-radius: 50%;
  opacity: 0;
  transform: scale(0);
}

.anim-active .shockwave-1 {
  animation: shockwave-expand 1s 1.05s ease-out forwards;
}

.anim-active .shockwave-2 {
  animation: shockwave-expand 1s 1.15s ease-out forwards;
}

@keyframes shockwave-expand {
  0% {
    opacity: 0;
    transform: scale(0);
    border-width: 6px;
  }

  20% {
    opacity: 1;
  }

  100% {
    opacity: 0;
    transform: scale(20);
    border-width: 0px;
  }
}

/* 白光晕眩层 */
.white-flash-overlay {
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at center,
      #ffffff 0%,
      rgba(255, 255, 255, 0.98) 30%,
      rgba(255, 255, 255, 0.95) 60%,
      rgba(255, 255, 255, 0.9) 100%);
  z-index: 15;
  pointer-events: none;
  -webkit-backdrop-filter: blur(25px);
  backdrop-filter: blur(25px);
  opacity: 0;
}

.anim-active .white-flash-overlay {
  animation: white-flash-sequence 2.5s ease-in-out forwards;
}

@keyframes white-flash-sequence {
  0% {
    opacity: 0;
  }

  40% {
    opacity: 0;
  }

  50% {
    opacity: 1;
  }

  80% {
    opacity: 0.95;
  }

  100% {
    opacity: 0;
  }
}

/* 响应式设计 - 爆炸动画 */
@media (max-width: 768px) {
  .giant-mine {
    font-size: 8rem;
  }

  .fire-core {
    width: 150px;
    height: 150px;
  }

  .fire-ring {
    width: 200px;
    height: 200px;
  }
}

@media (max-width: 480px) {
  .giant-mine {
    font-size: 6rem;
  }

  .fire-core {
    width: 100px;
    height: 100px;
  }

  .fire-ring {
    width: 150px;
    height: 150px;
  }
}


.game-main {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.game-board {
  display: grid;
  gap: 2px;
  background: #bdc3c7;
  padding: 1rem;
  border-radius: 0.5rem;
  margin-top: 1rem;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
  max-width: 90vw;
  max-height: 70vh;
}

.cell {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.9rem;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.1s ease;
  -webkit-user-select: none;
  user-select: none;
}

.cell-hidden {
  background: #ecf0f1;
  border: 2px outset #bdc3c7;
}

.cell-hidden:hover {
  background: #d5dbdb;
}

.cell-flagged {
  background: #ecf0f1;
  border: 2px outset #bdc3c7;
}

.cell-revealed {
  background: #ecf0f1;
  border: 1px solid #bdc3c7;
}

.cell-0 {
  background: #ecf0f1;
}

.cell-1 {
  color: #3498db;
}

.cell-2 {
  color: #27ae60;
}

.cell-3 {
  color: #e74c3c;
}

.cell-4 {
  color: #8e44ad;
}

.cell-5 {
  color: #e67e22;
}

.cell-6 {
  color: #16a085;
}

.cell-7 {
  color: #2c3e50;
}

.cell-8 {
  color: #7f8c8d;
}

.cell-mine {
  background: #e74c3c;
  color: #fff;
}

.cell-exploded {
  background: #c0392b;
  color: #fff;
}

.game-info-table {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 48px;
  margin-bottom: 20px;
}

.info-cell {
  font-size: 1.2rem;
  font-weight: 500;
  color: #2c3e50;
}

.game-btn-row {
  display: flex;
  justify-content: center;
  gap: 40px;
  margin-bottom: 24px;
}

.save-btn {
  background: #8f7a66;
  color: #fff;
  border: none;
  border-radius: 0.5rem;
  padding: 0.5rem 1.5rem;
  cursor: pointer;
  font-size: 1rem;
}

.save-btn:hover {
  background: #7f6a56;
}

.game-result {
  position: absolute;
  top: 40%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: rgba(255, 255, 255, 0.95);
  border-radius: 1rem;
  padding: 2rem 3rem;
  box-shadow: 0 4px 32px rgba(0, 0, 0, 0.2);
  text-align: center;
  z-index: 20;
}

.result-text {
  font-size: 2rem;
  color: #e74c3c;
  margin-bottom: 1.5rem;
}

.result-time {
  font-size: 1.2rem;
  color: #2c3e50;
  margin-bottom: 1.5rem;
}

.enhanced-result {
  min-width: 340px;
  min-height: 220px;
  padding: 2.5rem 3.5rem;
  border-radius: 1.5rem;
  background: linear-gradient(135deg, #fffbe6 60%, #f7e9c4 100%);
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.result-title {
  font-size: 2.4rem;
  color: #e74c3c;
  font-weight: bold;
  margin-bottom: 1.2rem;
  letter-spacing: 0.1em;
}

.result-desc {
  font-size: 1.2rem;
  color: #2c3e50;
  margin-bottom: 2rem;
  text-align: center;
}

.result-actions {
  display: flex;
  gap: 2rem;
}

.result-btn {
  font-size: 1.1rem;
  padding: 0.9rem 2.2rem;
  border-radius: 0.8rem;
  border: none;
  cursor: pointer;
  font-weight: bold;
  transition: background 0.2s, color 0.2s;
}

.retry-btn {
  background: #8f7a66;
  color: #fff;
}

.retry-btn:hover {
  background: #7f6a56;
}

.exit-btn {
  background: #ecf0f1;
  color: #2c3e50;
}

.exit-btn:hover {
  background: #e74c3c;
  color: #fff;
}

.custom-dialog-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.custom-dialog {
  background: #fff;
  border-radius: 10px;
  min-width: 300px;
  padding: 24px 32px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.18);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.custom-dialog-title {
  font-size: 1.3rem;
  font-weight: bold;
  margin-bottom: 12px;
}

.custom-dialog-content {
  margin-bottom: 20px;
  font-size: 1rem;
}

.custom-dialog-actions {
  display: flex;
  gap: 16px;
}

.custom-dialog-actions button {
  padding: 6px 18px;
  border-radius: 6px;
  border: none;
  background: #4a90e2;
  color: #fff;
  font-size: 1rem;
  cursor: pointer;
  transition: background 0.2s;
}

.custom-dialog-actions button:hover {
  background: #357abd;
}

.game-title {
  margin-top: 10px;
  margin-bottom: 24px;
  text-align: center;
  font-size: 2rem;
  font-weight: bold;
  color: #2c3e50;
}

/* 响应式设计 - 游戏界面 */
@media (max-width: 768px) {
  .cell {
    width: 25px;
    height: 25px;
    font-size: 0.8rem;
  }

  .game-board {
    padding: 0.5rem;
    gap: 1px;
  }

  .difficulty-btns {
    flex-direction: column;
    gap: 1rem;
  }

  .game-btn-row {
    flex-direction: column;
    gap: 1rem;
  }
}

@media (max-width: 480px) {
  .cell {
    width: 20px;
    height: 20px;
    font-size: 0.7rem;
  }
}
</style>
