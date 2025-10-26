<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../config/user'
import { ElMessage } from 'element-plus'

// 定义组件名称，支持 keep-alive 缓存
defineOptions({
  name: 'GameCenter'
})

const router = useRouter()
const userStore = useUserStore()

// 游戏数据定义
const games = ref([
  {
    id: '2048',
    name: '2048',
    icon: '🎯',
    description: '数字合成游戏，挑战极限',
    category: 'single',
    difficulty: '简单',
    difficultyColor: '#10b981',
    gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    online: null,
    features: ['单人游戏', '益智', '策略']
  },
  {
    id: 'minesweeper',
    name: '扫雷',
    icon: '💣',
    description: '经典扫雷游戏，考验逻辑思维',
    category: 'single',
    difficulty: '中等',
    difficultyColor: '#f59e0b',
    gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    online: null,
    features: ['单人游戏', '益智', '经典']
  },
  {
    id: 'gomoku',
    name: '五子棋',
    icon: '⭕️',
    description: '经典对战五子棋，支持好友邀请和匹配',
    category: 'multi',
    difficulty: '简单',
    difficultyColor: '#10b981',
    gradient: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    online: 0,
    features: ['多人对战', '实时', '策略']
  },
  {
    id: 'tank-battle',
    name: '坦克大战',
    icon: '🚗',
    description: '经典坦克对战游戏',
    category: 'multi',
    difficulty: '困难',
    difficultyColor: '#ef4444',
    gradient: 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
    online: 0,
    features: ['多人对战', '动作', '经典']
  }
])

// 最近玩过的游戏
const recentGames = ref([])

// 用户统计数据
const userStats = ref({
  totalGames: 0,
  totalWins: 0,
  winRate: 0,
  onlineFriends: 0
})

// 计算属性：单人游戏
const singlePlayerGames = computed(() =>
  games.value.filter(game => game.category === 'single')
)

// 计算属性：多人游戏
const multiPlayerGames = computed(() =>
  games.value.filter(game => game.category === 'multi')
)

// 获取用户信息
const currentUser = computed(() => userStore.userInfo || {})
const userName = computed(() => currentUser.value.username || '玩家')

// 开始游戏
const startGame = (gameType) => {
  const game = games.value.find(g => g.id === gameType)
  if (game?.category === 'multi') {
    router.push(`/room/create/${gameType}`)
  } else {
    router.push(`/game/${gameType}`)
  }
}

// 快速匹配
const quickMatch = () => {
  ElMessage.info('正在为您匹配对手...')
  // 这里可以调用后端匹配接口
  setTimeout(() => {
    router.push('/room/create/gomoku')
  }, 1000)
}

// 创建房间
const createRoom = () => {
  router.push('/room/create/gomoku')
}

// 获取在线人数（模拟数据，可以后期接入真实API）
const fetchOnlineCount = () => {
  for (const game of games.value) {
    if (game.category === 'multi') {
      game.online = Math.floor(Math.random() * 50) + 10
    }
  }
}

// 获取最近游戏记录
const fetchRecentGames = () => {
  // 模拟最近玩过的游戏数据
  const allGameIds = games.value.map(g => g.id)
  const shuffled = allGameIds.sort(() => 0.5 - Math.random())
  recentGames.value = shuffled.slice(0, 2)
}

// 获取用户统计
const fetchUserStats = () => {
  // 这里可以调用真实的API
  userStats.value = {
    totalGames: 156,
    totalWins: 89,
    winRate: 57,
    onlineFriends: 5
  }
}

// 组件挂载时获取数据
onMounted(() => {
  fetchOnlineCount()
  fetchRecentGames()
  fetchUserStats()
})
</script>

<template>
  <div class="tab-content games-center">
    <!-- 欢迎横幅 -->
    <div class="welcome-banner">
      <div class="welcome-content">
        <div class="welcome-text">
          <h1>欢迎回来，{{ userName }}！</h1>
          <p>准备好开始新的游戏挑战了吗？</p>
        </div>
        <div class="quick-stats">
          <div class="stat-item">
            <div class="stat-icon">🎮</div>
            <div class="stat-info">
              <span class="stat-value">{{ userStats.totalGames }}</span>
              <span class="stat-label">总场次</span>
            </div>
          </div>
          <div class="stat-item">
            <div class="stat-icon">🏆</div>
            <div class="stat-info">
              <span class="stat-value">{{ userStats.totalWins }}</span>
              <span class="stat-label">胜场</span>
            </div>
          </div>
          <div class="stat-item">
            <div class="stat-icon">📊</div>
            <div class="stat-info">
              <span class="stat-value">{{ userStats.winRate }}%</span>
              <span class="stat-label">胜率</span>
            </div>
          </div>
          <div class="stat-item">
            <div class="stat-icon">👥</div>
            <div class="stat-info">
              <span class="stat-value">{{ userStats.onlineFriends }}</span>
              <span class="stat-label">在线好友</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 快速操作区 -->
    <div class="quick-actions">
      <button class="action-btn primary" @click="quickMatch">
        <span class="btn-icon">⚡</span>
        <span>快速匹配</span>
      </button>
      <button class="action-btn secondary" @click="createRoom">
        <span class="btn-icon">➕</span>
        <span>创建房间</span>
      </button>
      <button class="action-btn tertiary" @click="router.push('/dashboard/friends')">
        <span class="btn-icon">👥</span>
        <span>邀请好友</span>
      </button>
    </div>

    <!-- 最近玩过的游戏 -->
    <div v-if="recentGames.length > 0" class="recent-section">
      <h2 class="section-title">
        <span class="title-icon">🕐</span>
        继续游戏
      </h2>
      <div class="recent-games">
        <div v-for="gameId in recentGames" :key="gameId" class="recent-game-card" @click="startGame(gameId)">
          <div class="recent-game-icon">
            {{games.find(g => g.id === gameId)?.icon}}
          </div>
          <div class="recent-game-info">
            <h4>{{games.find(g => g.id === gameId)?.name}}</h4>
            <p>继续上次的游戏</p>
          </div>
          <div class="play-icon">▶️</div>
        </div>
      </div>
    </div>

    <!-- 单人游戏区域 -->
    <div class="game-section">
      <h2 class="section-title">
        <span class="title-icon">🎯</span>
        单人游戏
      </h2>
      <div class="games-grid">
        <div v-for="game in singlePlayerGames" :key="game.id" class="game-card" @click="startGame(game.id)">
          <div class="card-header" :style="{ background: game.gradient }">
            <div class="game-icon">{{ game.icon }}</div>
            <span class="difficulty-badge" :style="{ background: game.difficultyColor }">
              {{ game.difficulty }}
            </span>
          </div>
          <div class="card-body">
            <h3>{{ game.name }}</h3>
            <p>{{ game.description }}</p>
            <div class="game-features">
              <span v-for="feature in game.features" :key="feature" class="feature-tag">
                {{ feature }}
              </span>
            </div>
          </div>
          <div class="card-footer">
            <button class="play-btn">开始游戏</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 多人游戏区域 -->
    <div class="game-section">
      <h2 class="section-title">
        <span class="title-icon">👥</span>
        多人游戏
      </h2>
      <div class="games-grid">
        <div v-for="game in multiPlayerGames" :key="game.id" class="game-card" @click="startGame(game.id)">
          <div class="card-header" :style="{ background: game.gradient }">
            <div class="game-icon">{{ game.icon }}</div>
            <div class="online-badge">
              <span class="online-dot"></span>
              {{ game.online }} 在线
            </div>
          </div>
          <div class="card-body">
            <h3>{{ game.name }}</h3>
            <p>{{ game.description }}</p>
            <div class="game-features">
              <span v-for="feature in game.features" :key="feature" class="feature-tag">
                {{ feature }}
              </span>
            </div>
          </div>
          <div class="card-footer">
            <button class="play-btn">创建房间</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.games-center {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
  overflow-y: auto;
  height: 100%;
}

/* 欢迎横幅 */
.welcome-banner {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 20px;
  padding: 32px;
  margin-bottom: 32px;
  color: white;
  box-shadow: 0 10px 30px rgba(102, 126, 234, 0.3);
}

.welcome-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 32px;
}

.welcome-text h1 {
  font-size: 2rem;
  margin: 0 0 8px 0;
  font-weight: 700;
}

.welcome-text p {
  margin: 0;
  opacity: 0.9;
  font-size: 1.1rem;
}

.quick-stats {
  display: flex;
  gap: 24px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 12px;
  background: rgba(255, 255, 255, 0.15);
  padding: 16px 20px;
  border-radius: 12px;
  backdrop-filter: blur(10px);
  min-width: 120px;
}

.stat-icon {
  font-size: 2rem;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 700;
  line-height: 1;
}

.stat-label {
  font-size: 0.75rem;
  opacity: 0.9;
  margin-top: 4px;
}

/* 快速操作区 */
.quick-actions {
  display: flex;
  gap: 16px;
  margin-bottom: 32px;
}

.action-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px 24px;
  border: none;
  border-radius: 12px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.action-btn .btn-icon {
  font-size: 1.25rem;
}

.action-btn.primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.action-btn.primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.5);
}

.action-btn.secondary {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
  box-shadow: 0 4px 15px rgba(245, 87, 108, 0.4);
}

.action-btn.secondary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(245, 87, 108, 0.5);
}

.action-btn.tertiary {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  color: white;
  box-shadow: 0 4px 15px rgba(79, 172, 254, 0.4);
}

.action-btn.tertiary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(79, 172, 254, 0.5);
}

/* 最近游戏 */
.recent-section {
  margin-bottom: 40px;
}

.section-title {
  font-size: 1.5rem;
  font-weight: 700;
  margin-bottom: 20px;
  color: #232946;
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-icon {
  font-size: 1.75rem;
}

.recent-games {
  display: flex;
  gap: 16px;
}

.recent-game-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 16px;
  background: white;
  border-radius: 16px;
  padding: 20px;
  cursor: pointer;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
}

.recent-game-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.12);
}

.recent-game-icon {
  font-size: 3rem;
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  border-radius: 12px;
}

.recent-game-info {
  flex: 1;
}

.recent-game-info h4 {
  margin: 0 0 4px 0;
  font-size: 1.1rem;
  color: #232946;
}

.recent-game-info p {
  margin: 0;
  font-size: 0.875rem;
  color: #6b7280;
}

.play-icon {
  font-size: 1.5rem;
  opacity: 0.6;
}

/* 游戏区域 */
.game-section {
  margin-bottom: 40px;
}

.games-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
}

.game-card {
  background: white;
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
}

.game-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
}

.card-header {
  height: 140px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  padding: 20px;
}

.game-icon {
  font-size: 4rem;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.difficulty-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 0.75rem;
  font-weight: 600;
  color: white;
  backdrop-filter: blur(10px);
}

.online-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 0.75rem;
  font-weight: 600;
  color: white;
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  gap: 6px;
}

.online-dot {
  width: 8px;
  height: 8px;
  background: #10b981;
  border-radius: 50%;
  animation: pulse 2s infinite;
}

@keyframes pulse {

  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.5;
  }
}

.card-body {
  padding: 20px;
}

.card-body h3 {
  margin: 0 0 8px 0;
  font-size: 1.25rem;
  color: #232946;
  font-weight: 700;
}

.card-body p {
  margin: 0 0 16px 0;
  font-size: 0.875rem;
  color: #6b7280;
  line-height: 1.5;
}

.game-features {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.feature-tag {
  padding: 4px 10px;
  background: #f3f4f6;
  border-radius: 12px;
  font-size: 0.75rem;
  color: #6b7280;
  font-weight: 500;
}

.card-footer {
  padding: 0 20px 20px 20px;
}

.play-btn {
  width: 100%;
  padding: 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.play-btn:hover {
  transform: scale(1.02);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .quick-stats {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .games-center {
    padding: 16px;
  }

  .welcome-content {
    flex-direction: column;
  }

  .quick-stats {
    width: 100%;
    flex-wrap: wrap;
  }

  .stat-item {
    min-width: calc(50% - 12px);
  }

  .quick-actions {
    flex-direction: column;
  }

  .recent-games {
    flex-direction: column;
  }

  .games-grid {
    grid-template-columns: 1fr;
  }
}
</style>