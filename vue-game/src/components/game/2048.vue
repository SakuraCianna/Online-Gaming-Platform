<template>
  <div class="game-2048-root">
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
          <span class="rule-label">网格大小：</span>
          <span class="rule-value">{{ selectedDifficulty.size }}x{{ selectedDifficulty.size }}</span>
        </div>
        <div class="rule-item">
          <span class="rule-label">初始数字：</span>
          <span class="rule-value">{{ selectedDifficulty.initialCount }}个随机数字</span>
        </div>
        <div class="rule-item">
          <span class="rule-label">初始数字范围：</span>
          <span class="rule-value">{{ selectedDifficulty.initialNumbers.join('、') }}</span>
        </div>
        <div class="rule-item">
          <span class="rule-label">新方块概率：</span>
          <div class="prob-list">
            <div v-for="(prob, num) in selectedDifficulty.newTileProb" :key="num" class="prob-item">
              <span class="prob-num">{{ num }}</span>
              <span class="prob-percent">{{ (prob * 100).toFixed(0) }}%</span>
            </div>
          </div>
        </div>
        <div class="rule-item">
          <span class="rule-label">胜利条件：</span>
          <span class="rule-value">合成 {{ selectedDifficulty.winCondition }}</span>
        </div>
        <div class="rule-item">
          <span class="rule-label">失败条件：</span>
          <span class="rule-value">网格填满且无法合并时结束</span>
        </div>
      </div>
      <div class="action-btns">
        <button class="start-btn" @click="startGame">开始游戏</button>
        <button class="back-btn" @click="backToSelect">返回选择</button>
      </div>
    </div>

    <!-- 过渡动画界面 -->
    <div v-if="stage === 'transition'" ref="transitionBox" class="transition-anim">
      <!-- 背景粒子 -->
      <div class="particles">
        <div v-for="i in 20" :key="'particle-' + i" class="particle"></div>
      </div>
      <!-- 数字方块网格 -->
      <div class="tiles-container">
        <div v-for="n in 8" :key="'tile-' + n" class="tile" :class="'tile-' + n">
          {{ 2 ** n }}
        </div>
      </div>
      <!-- 主标题 -->
      <div class="title-container">
        <span class="start-text">2048!</span>
        <div class="subtitle">准备开始</div>
      </div>
    </div>

    <!-- 游戏主界面 -->
    <div v-if="stage === 'game'" class="game-main">
      <!-- 标题上移 -->
      <h2 class="game-title">2048 - {{ currentDifficulty.label }}</h2>
      <!-- 信息表格 -->
      <div class="game-info-table">
        <div class="info-cell">分数：{{ score }}</div>
        <div class="info-cell">步数：{{ moveCount }}</div>
        <div class="info-cell">用时：{{ formattedDuration }}</div>
      </div>
      <!-- 按钮行 -->
      <div class="game-btn-row">
        <button class="save-btn" @click="saveAndExit">保存并退出</button>
        <button class="save-btn" @click="restartGame">重新开始</button>
        <button class="save-btn" @click="exitWithoutSave">不保存并退出</button>
      </div>
      <div class="game-board" :style="boardStyle">
        <div v-for="(cell, idx) in boardCells" :key="idx" class="cell" :class="'cell-' + cell">{{ cell || '' }}</div>
      </div>
      <div v-if="gameWin" class="game-result">
        <div class="result-text">🎉 恭喜通关！</div>
        <button class="save-btn" @click="restartGame">再来一局</button>
      </div>
      <!-- 游戏失败弹窗 -->
      <div v-if="gameOver && !gameWin" class="game-result enhanced-result">
        <div class="result-title">游戏结束</div>
        <div class="result-desc">很遗憾，未能通关。要不要再试一次？</div>
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
import { gsap } from 'gsap'
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
    label: '简单',
    level: 'easy',
    size: 4,
    initialCount: 2,
    initialNumbers: [2],
    newTileProb: { 2: 0.9, 4: 0.1 },
    winCondition: 2048
  },
  {
    label: '普通',
    level: 'normal',
    size: 4,
    initialCount: 2,
    initialNumbers: [2, 4],
    newTileProb: { 2: 0.75, 4: 0.25 },
    winCondition: 2048
  },
  {
    label: '困难',
    level: 'hard',
    size: 5,
    initialCount: 4,
    initialNumbers: [2, 4, 8],
    newTileProb: { 2: 0.3, 4: 0.6, 8: 0.1 },
    winCondition: 4096
  }
]

const stage = ref('select') // select | rules | transition | game
const selectedDifficulty = ref(null)
const currentDifficulty = ref(null)
const transitionBox = ref(null)

// 游戏状态
const board = ref([]) // 二维数组
const score = ref(0)
const moveCount = ref(0)
const duration = ref(0)
const timer = ref(null)
const isTiming = ref(false)
const gameOver = ref(false)
const gameWin = ref(false)
const maxTile = ref(2)

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
  stage.value = 'transition'
  // 动画稍后执行
  setTimeout(runTransitionAnim, 50)
}

// 过渡动画
function runTransitionAnim() {
  const box = transitionBox.value
  if (!box) return

  const tiles = box.querySelectorAll('.tile')
  const particles = box.querySelectorAll('.particle')
  const startText = box.querySelector('.start-text')
  const subtitle = box.querySelector('.subtitle')
  const tilesContainer = box.querySelector('.tiles-container')

  // 创建时间线
  const tl = gsap.timeline()

  // 1. 背景粒子动画 - 从中心爆发
  gsap.set(particles, {
    x: 0,
    y: 0,
    scale: 0,
    opacity: 0
  })

  for (let i = 0; i < particles.length; i++) {
    const particle = particles[i]
    const angle = (i / particles.length) * Math.PI * 2
    const distance = 300 + Math.random() * 200
    const x = Math.cos(angle) * distance
    const y = Math.sin(angle) * distance

    tl.to(particle, {
      x: x,
      y: y,
      scale: Math.random() * 0.5 + 0.5,
      opacity: 0.6,
      duration: 1.2,
      ease: 'power2.out'
    }, 0)

    // 粒子逐渐消失
    tl.to(particle, {
      opacity: 0,
      scale: 0,
      duration: 0.5,
      ease: 'power2.in'
    }, 1.5)
  }

  // 2. 数字方块动画 - 螺旋飞入并旋转
  gsap.set(tiles, {
    scale: 0,
    rotation: 0,
    opacity: 0
  })

  gsap.set(tilesContainer, {
    rotationY: 0
  })

  for (let i = 0; i < tiles.length; i++) {
    const tile = tiles[i]
    const delay = i * 0.08
    const angle = (i / tiles.length) * Math.PI * 2
    const radius = 500
    const startX = Math.cos(angle) * radius
    const startY = Math.sin(angle) * radius

    gsap.set(tile, {
      x: startX,
      y: startY
    })

    tl.to(tile, {
      x: 0,
      y: 0,
      scale: 1,
      rotation: 360,
      opacity: 1,
      duration: 0.8,
      ease: 'back.out(1.5)'
    }, 0.3 + delay)
  }

  // 3. 整个方块容器3D旋转效果
  tl.to(tilesContainer, {
    rotationY: 360,
    duration: 1.2,
    ease: 'power2.inOut'
  }, 0.5)

  // 4. 方块淡出并缩小
  tl.to(tiles, {
    scale: 0.3,
    opacity: 0,
    rotation: 720,
    duration: 0.6,
    ease: 'power2.in',
    stagger: 0.03
  }, 1.6)

  // 5. 主标题从下方弹出并放大
  gsap.set(startText, {
    y: 100,
    scale: 0,
    rotationX: -90,
    opacity: 0
  })

  tl.to(startText, {
    y: 0,
    scale: 1.2,
    rotationX: 0,
    opacity: 1,
    duration: 0.7,
    ease: 'elastic.out(1, 0.6)'
  }, 2)

  // 6. 标题轻微呼吸效果
  tl.to(startText, {
    scale: 1,
    duration: 0.4,
    ease: 'power2.inOut'
  }, 2.5)

  // 7. 副标题淡入
  gsap.set(subtitle, {
    y: 20,
    opacity: 0
  })

  tl.to(subtitle, {
    y: 0,
    opacity: 0.8,
    duration: 0.5,
    ease: 'power2.out'
  }, 2.3)

  // 8. 最后整体淡出，进入游戏
  tl.to(box, {
    opacity: 0,
    scale: 1.1,
    duration: 0.4,
    ease: 'power2.in',
    onComplete: () => {
      stage.value = 'game'
      initBoard()
    }
  }, 3.2)
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
  const size = currentDifficulty.value.size
  board.value = Array.from({ length: size }, () => new Array(size).fill(0))
  score.value = 0
  moveCount.value = 0
  duration.value = 0
  isTiming.value = false
  gameOver.value = false
  gameWin.value = false
  maxTile.value = 2
  clearInterval(timer.value)
  // 随机生成初始数字
  for (let i = 0; i < currentDifficulty.value.initialCount; i++) {
    addRandomTile(true)
  }
}

// 随机生成新方块
function addRandomTile(isInit = false) {
  const empty = []
  const size = currentDifficulty.value.size
  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      if (board.value[r][c] === 0) empty.push([r, c])
    }
  }
  if (empty.length === 0) return
  const [row, col] = empty[Math.floor(Math.random() * empty.length)]
  let num
  if (isInit) {
    // 初始数字
    const pool = currentDifficulty.value.initialNumbers
    num = pool[Math.floor(Math.random() * pool.length)]
  } else {
    // 按概率生成
    const prob = currentDifficulty.value.newTileProb
    const rand = Math.random()
    let acc = 0
    for (const [n, p] of Object.entries(prob)) {
      acc += p
      if (rand <= acc) {
        num = Number(n)
        break
      }
    }
  }
  board.value[row][col] = num
}

// 检查是否胜利
function checkWin() {
  const size = currentDifficulty.value.size
  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      if (board.value[r][c] >= currentDifficulty.value.winCondition) {
        return true
      }
    }
  }
  return false
}

// 检查是否还能移动
function canMove() {
  const size = currentDifficulty.value.size
  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      if (board.value[r][c] === 0) return true
      if (c < size - 1 && board.value[r][c] === board.value[r][c + 1]) return true
      if (r < size - 1 && board.value[r][c] === board.value[r + 1][c]) return true
    }
  }
  return false
}

// 合并一行数字
function mergeRow(row, size) {
  let mergedScore = 0
  let arr = row.filter(Boolean)
  let i = 0
  while (i < arr.length - 1) {
    if (arr[i] && arr[i] === arr[i + 1]) {
      arr[i] *= 2
      mergedScore += arr[i]
      maxTile.value = Math.max(maxTile.value, arr[i])
      arr[i + 1] = 0
      i += 2 // 跳过下一个
    } else {
      i++
    }
  }
  arr = arr.filter(Boolean)
  while (arr.length < size) arr.push(0)
  return { row: arr, score: mergedScore }
}

// 处理行移动
function processRow(board, rowIndex, direction, size) {
  const old = board[rowIndex].slice()
  let newRow

  if (direction === 'left') {
    const result = mergeRow(board[rowIndex], size)
    newRow = result.row
    return { newRow, score: result.score, moved: old.toString() !== newRow.toString() }
  } else {
    const result = mergeRow(board[rowIndex].slice().reverse(), size)
    newRow = result.row.reverse()
    return { newRow, score: result.score, moved: old.toString() !== newRow.toString() }
  }
}

// 处理列移动
function processColumn(board, colIndex, direction, size) {
  let col = []
  for (let r = 0; r < size; r++) col.push(board[r][colIndex])

  const old = col.slice()
  let newCol
  let result

  if (direction === 'up') {
    result = mergeRow(col, size)
    newCol = result.row
  } else {
    result = mergeRow(col.reverse(), size)
    newCol = result.row.reverse()
  }

  for (let r = 0; r < size; r++) board[r][colIndex] = newCol[r]
  return { score: result.score, moved: old.toString() !== newCol.toString() }
}

function move(direction) {
  if (gameOver.value || gameWin.value) return

  const size = currentDifficulty.value.size
  let moved = false
  let mergedScore = 0
  let newBoard = board.value.map(row => row.slice())

  if (isRowMove(direction)) {
    const rowResult = processAllRows(newBoard, direction, size)
    newBoard = rowResult.newBoard
    mergedScore += rowResult.mergedScore
    moved = rowResult.moved
  } else {
    const colResult = processAllColumns(newBoard, direction, size)
    newBoard = colResult.newBoard
    mergedScore += colResult.mergedScore
    moved = colResult.moved
  }

  if (moved) {
    updateGameStateAfterMove(newBoard, mergedScore)
  }
}

function isRowMove(direction) {
  return direction === 'left' || direction === 'right'
}

function processAllRows(board, direction, size) {
  let moved = false
  let mergedScore = 0
  let newBoard = board.map(row => row.slice())
  for (let r = 0; r < size; r++) {
    const result = processRow(newBoard, r, direction, size)
    newBoard[r] = result.newRow
    mergedScore += result.score
    if (result.moved) moved = true
  }
  return { newBoard, mergedScore, moved }
}

function processAllColumns(board, direction, size) {
  let moved = false
  let mergedScore = 0
  let newBoard = board.map(row => row.slice())
  for (let c = 0; c < size; c++) {
    const result = processColumn(newBoard, c, direction, size)
    mergedScore += result.score
    if (result.moved) moved = true
  }
  return { newBoard, mergedScore, moved }
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
  return {
    user_id: id,
    difficulty: currentDifficulty.value.level,
    max_tile: maxTile.value,
    moves_count: moveCount.value,
    duration: duration.value,
    status: status,
    game_data: JSON.stringify({ board: board.value }),
    score: score.value
  }
}

// 自动保存
async function autoSaveGame() {
  const data = getSaveData()
  try {
    // 如果有现有游戏记录，则更新；否则创建新记录
    if (inProgressGame.value && inProgressGame.value.id) {
      // 添加id到数据中用于更新
      data.id = inProgressGame.value.id
      await request.post('/2048/update', data)
    } else {
      await request.post('/2048/save', data)
    }
  } catch (e) {
    console.log("自动保存失败" + e)
  }
}

// 保存并退出
async function saveAndExit() {
  stopTimer()
  const data = getSaveData()
  try {
    let response
    // 如果有现有游戏记录，则更新；否则创建新记录
    if (inProgressGame.value && inProgressGame.value.id) {
      // 添加id到数据中用于更新
      data.id = inProgressGame.value.id
      response = await request.post('/2048/update', data)
    } else {
      response = await request.post('/2048/save', data)
    }

    if (response.data.code === 200) {
      ElMessage.success(response.data.message || '游戏已保存！')
      router.push('/user')
    } else {
      ElMessage.error(response.data.message || '保存失败，请重试')
    }
  } catch (e) {
    console.log(e)
    ElMessage.error('保存失败，请重试')
  }
}

function updateGameStateAfterMove(newBoard, mergedScore) {
  board.value = newBoard
  score.value += mergedScore
  moveCount.value++
  if (!isTiming.value) {
    startTimer()
    isTiming.value = true
  }
  addRandomTile()
  if (checkWin()) {
    stopTimer()
    gameWin.value = true
    autoSaveGame() // 通关自动保存
  } else if (!canMove()) {
    stopTimer()
    gameOver.value = true
    autoSaveGame() // 失败自动保存
  }
}

// 键盘监听
function handleKeydown(e) {
  if (stage.value !== 'game') return
  if (gameOver.value || gameWin.value) return
  if (['ArrowLeft', 'a', 'A'].includes(e.key)) {
    move('left')
  } else if (['ArrowRight', 'd', 'D'].includes(e.key)) {
    move('right')
  } else if (['ArrowUp', 'w', 'W'].includes(e.key)) {
    move('up')
  } else if (['ArrowDown', 's', 'S'].includes(e.key)) {
    move('down')
  }
}

// 不保存并退出
function exitWithoutSave() {
  stopTimer()
  router.push('/user')
}

// 重新开始
async function restartGame() {
  // 如果有未完成的游戏记录，先删除
  if (inProgressGame.value) {
    await deleteInProgressGame()
  }
  // 重新初始化棋盘
  initBoard()
}

// 弹窗提示用户是否继续
function showContinueDialog() {
  dialogTitle.value = '继续游戏'
  dialogMessage.value = '检测到你有一局未完成的2048游戏,是否继续?'
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
  // 还原难度
  const diff = difficultyOptions.find(opt => opt.level === data.difficulty)
  if (!diff) {
    ElMessage.error('存档数据异常，无法恢复')
    stage.value = 'select'
    return
  }

  // 检查 gameData 是否为空
  if (!data.gameData) {
    ElMessage.error('存档数据为空，无法恢复')
    stage.value = 'select'
    return
  }

  // 先设置恢复标志，防止watch触发
  isRestoringGame.value = true

  currentDifficulty.value = diff

  const parsedData = JSON.parse(data.gameData)
  board.value = parsedData.board
  score.value = data.score
  moveCount.value = data.movesCount
  duration.value = data.duration
  maxTile.value = data.maxTile

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
    await request.delete('/2048/delete', { params: { id: inProgressGame.value.id } })
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
  userStore.setCurrentGame('2048')
  document.addEventListener('keydown', handleKeydown)
  if (!user.value?.id) {
    stage.value = 'select'
    return
  }
  try {
    const res = await request.get('/2048/getInProgress', { params: { user_id: user.value.id } })
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
  document.removeEventListener('keydown', handleKeydown)
})

// 棋盘一维渲染
const boardCells = computed(() => {
  if (!board.value || !board.value.length) return []
  return board.value.flat()
})
const boardStyle = computed(() => {
  if (!currentDifficulty.value) return {}
  const size = currentDifficulty.value.size
  return {
    gridTemplateColumns: `repeat(${size}, 1fr)`,
    gridTemplateRows: `repeat(${size}, 1fr)`
  }
})
</script>

<style scoped>
.game-2048-root {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(135deg, #faf8ef 0%, #e0cda7 100%);
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
  background: #985d30;
  color: #fff;
  cursor: pointer;
  transition: background 0.2s;
}

.difficulty-btns button:hover {
  background: #a87840;
}

.back-btn {
  font-size: 1.2rem;
  padding: 0.8rem 2rem;
  border-radius: 0.8rem;
  border: none;
  background: #6f5a46;
  color: #fff;
  cursor: pointer;
  transition: background 0.2s;
}

.back-btn:hover {
  background: #5f4a36;
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
  color: #5a5248;
  min-width: 120px;
  margin-right: 1rem;
}

.rule-value {
  color: #5a5248;
  flex: 1;
}

.prob-list {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.prob-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: #985d30;
  color: white;
  padding: 0.3rem 0.8rem;
  border-radius: 0.5rem;
  font-size: 0.9rem;
}

.prob-num {
  font-weight: bold;
}

.prob-percent {
  opacity: 0.9;
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
  background: #b84c2f;
  color: #fff;
  cursor: pointer;
  transition: background 0.2s;
}

.start-btn:hover {
  background: #983c1f;
}

.transition-anim {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #fdfbf7 0%, #f7e9c4 50%, #e0cda7 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  z-index: 10;
  overflow: hidden;
  perspective: 1000px;
}

.particles {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.particle {
  position: absolute;
  width: 12px;
  height: 12px;
  background: linear-gradient(135deg, #f2b179, #f67c5f);
  border-radius: 50%;
  box-shadow: 0 0 10px rgba(246, 124, 95, 0.5);
}

.tiles-container {
  position: absolute;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 15px;
  max-width: 400px;
  transform-style: preserve-3d;
}

.transition-anim .tile {
  width: 80px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2rem;
  font-weight: bold;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(187, 167, 122, 0.4);
  transform-style: preserve-3d;
  will-change: transform;
}

/* 不同数字的颜色 */
.transition-anim .tile-1 {
  background: #eee4da;
  color: #3a3228;
}

.transition-anim .tile-2 {
  background: #ede0c8;
  color: #3a3228;
}

.transition-anim .tile-3 {
  background: #985d30;
  color: #fff;
}

.transition-anim .tile-4 {
  background: #984023;
  color: #fff;
}

.transition-anim .tile-5 {
  background: #983c1f;
  color: #fff;
}

.transition-anim .tile-6 {
  background: #982000;
  color: #fff;
}

.transition-anim .tile-7 {
  background: #685722;
  color: #fff;
}

.transition-anim .tile-8 {
  background: #685000;
  color: #fff;
}

.title-container {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  transform-style: preserve-3d;
  z-index: 5;
}

.transition-anim .start-text {
  display: block;
  font-size: 5rem;
  color: #782c0f;
  font-weight: bold;
  letter-spacing: 0.2em;
  text-shadow: 0 4px 20px rgba(120, 44, 15, 0.3),
    0 0 40px rgba(120, 44, 15, 0.2);
  transform-style: preserve-3d;
}

.subtitle {
  margin-top: 1rem;
  font-size: 1.5rem;
  color: #5a5248;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
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
  gap: 10px;
  background: #bbada0;
  padding: 2vw;
  border-radius: 1.5vw;
  margin-top: 2vw;
  box-shadow: 0 4px 32px #bba77a44;
  width: 60vw;
  max-width: 500px;
  aspect-ratio: 1/1;
}

.cell {
  background: #cdc1b4;
  border-radius: 0.5vw;
  width: 100%;
  aspect-ratio: 1/1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: clamp(1.2rem, 3vw, 2.5rem);
  font-weight: bold;
  font-family: 'Arial Rounded MT Bold', 'Helvetica Rounded', Arial, sans-serif;
  color: #3a3228;
  transition: all 0.15s ease;
}

.game-info-bar {
  display: flex;
  gap: 2rem;
  align-items: center;
  margin-bottom: 1rem;
}

.save-btn {
  background: #6f5a46;
  color: #fff;
  border: none;
  border-radius: 0.5rem;
  padding: 0.5rem 1.5rem;
  cursor: pointer;
  font-size: 1rem;
  margin-left: 2rem;
}

.save-btn:hover {
  background: #5f4a36;
}

.game-result {
  position: absolute;
  top: 40%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: rgba(255, 255, 255, 0.95);
  border-radius: 1rem;
  padding: 2rem 3rem;
  box-shadow: 0 4px 32px #bba77a44;
  text-align: center;
  z-index: 20;
}

.result-text {
  font-size: 2rem;
  color: #b84c2f;
  margin-bottom: 1.5rem;
}

.cell-2 {
  background: #eee4da;
  color: #3a3228;
  font-size: clamp(1.2rem, 3vw, 2.5rem);
}

.cell-4 {
  background: #ede0c8;
  color: #3a3228;
  font-size: clamp(1.2rem, 3vw, 2.5rem);
}

.cell-8 {
  background: #985d30;
  color: #fff;
  font-size: clamp(1.2rem, 3vw, 2.5rem);
}

.cell-16 {
  background: #984023;
  color: #fff;
  font-size: clamp(1rem, 2.8vw, 2.2rem);
}

.cell-32 {
  background: #983c1f;
  color: #fff;
  font-size: clamp(1rem, 2.8vw, 2.2rem);
}

.cell-64 {
  background: #982000;
  color: #fff;
  font-size: clamp(1rem, 2.8vw, 2.2rem);
}

.cell-128 {
  background: #685722;
  color: #fff;
  font-size: clamp(0.9rem, 2.4vw, 1.8rem);
}

.cell-256 {
  background: #887421;
  color: #fff;
  font-size: clamp(0.9rem, 2.4vw, 1.8rem);
}

.cell-512 {
  background: #887010;
  color: #fff;
  font-size: clamp(0.9rem, 2.4vw, 1.8rem);
}

.cell-1024 {
  background: #886d00;
  color: #fff;
  font-size: clamp(0.8rem, 2vw, 1.5rem);
}

.cell-2048 {
  background: #886a00;
  color: #fff;
  font-size: clamp(0.8rem, 2vw, 1.5rem);
}

.cell-4096 {
  background: #2c2a22;
  color: #fff;
  font-size: clamp(0.8rem, 2vw, 1.5rem);
}

.game-title {
  margin-top: 10px;
  margin-bottom: 24px;
  text-align: center;
  font-size: 2rem;
  font-weight: bold;
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
}

.game-btn-row {
  display: flex;
  justify-content: center;
  gap: 40px;
  margin-bottom: 24px;
}

/* 更大气的弹窗样式 */
.enhanced-result {
  min-width: 340px;
  min-height: 220px;
  padding: 2.5rem 3.5rem;
  border-radius: 1.5rem;
  background: linear-gradient(135deg, #fffbe6 60%, #f7e9c4 100%);
  box-shadow: 0 8px 40px #bba77a55;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.result-title {
  font-size: 2.4rem;
  color: #b84c2f;
  font-weight: bold;
  margin-bottom: 1.2rem;
  letter-spacing: 0.1em;
}

.result-desc {
  font-size: 1.2rem;
  color: #3a3228;
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
  background: #6f5a46;
  color: #fff;
}

.retry-btn:hover {
  background: #5f4a36;
}

.exit-btn {
  background: #e0cda7;
  color: #3a3228;
}

.exit-btn:hover {
  background: #b84c2f;
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
  background: #985d30;
  color: #fff;
  font-size: 1rem;
  cursor: pointer;
  transition: background 0.2s;
}

.custom-dialog-actions button:hover {
  background: #784d20;
}
</style>