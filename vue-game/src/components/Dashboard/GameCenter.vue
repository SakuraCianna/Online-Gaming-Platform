<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../config/user'

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
    description: '数字合成游戏',
    category: 'single',
    online: null
  },
  {
    id: 'minesweeper',
    name: '扫雷',
    icon: '💣',
    description: '经典扫雷游戏',
    category: 'single',
    online: null
  },
  {
    id: 'gomoku',
    name: '五子棋',
    icon: '⭕',
    description: '双人对战',
    category: 'multi',
    online: 0
  },
  {
    id: 'tank_battle',
    name: '坦克大战',
    icon: '🚗',
    description: '双人对战',
    category: 'multi',
    online: 0
  }
])

// 计算属性：单人游戏
const singlePlayerGames = computed(() =>
  games.value.filter(game => game.category === 'single')
)

// 计算属性：多人游戏
const multiPlayerGames = computed(() =>
  games.value.filter(game => game.category === 'multi')
)

// 开始游戏
const startGame = (gameType) => {
  const game = games.value.find(g => g.id === gameType)
  if (game?.category === 'multi') {
    router.push(`/room/create/${gameType}`)
  } else {
    router.push(`/game/${gameType}`)
  }
}

// 获取在线人数
const fetchOnlineCount = () => {
  for (const game of games.value) {
    if (game.category === 'multi') {
      game.online = Math.floor(Math.random() * 50) + 10
    }
  }
}

// 组件挂载时获取数据
onMounted(() => {
  fetchOnlineCount()
})
</script>

<template>
  <div class="games-center">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1>游戏中心</h1>
    </div>

    <!-- 单人游戏 -->
    <div class="game-section">
      <h2 class="section-title">单人游戏</h2>
      <div class="games-grid">
        <div v-for="game in singlePlayerGames" :key="game.id" class="game-card" @click="startGame(game.id)">
          <div class="card-icon">{{ game.icon }}</div>
          <h3 class="card-title">{{ game.name }}</h3>
          <p class="card-desc">{{ game.description }}</p>
          <button class="play-btn">开始游戏</button>
        </div>
      </div>
    </div>

    <!-- 多人游戏 -->
    <div class="game-section">
      <h2 class="section-title">多人游戏</h2>
      <div class="games-grid">
        <div v-for="game in multiPlayerGames" :key="game.id" class="game-card multi-player" @click="startGame(game.id)">
          <span class="online-badge">{{ game.online }} 人在线</span>
          <div class="card-icon">{{ game.icon }}</div>
          <h3 class="card-title">{{ game.name }}</h3>
          <p class="card-desc">{{ game.description }}</p>
          <button class="play-btn">创建房间</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.games-center {
  padding: 32px;
  max-width: 1200px;
  margin: 0 auto;
  height: 100%;
  overflow-y: auto;
}

/* 页面标题 */
.page-header {
  margin-bottom: 32px;
}

.page-header h1 {
  font-size: 1.75rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}

/* 游戏区域 */
.game-section {
  margin-bottom: 40px;
}

.section-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: #374151;
  margin: 0 0 20px 0;
  padding-bottom: 8px;
  border-bottom: 2px solid #e5e7eb;
}

/* 游戏卡片网格 */
.games-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px;
}

.game-card {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 24px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  position: relative;
}

.game-card:hover {
  border-color: #667eea;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.card-icon {
  font-size: 3.5rem;
  margin-bottom: 16px;
}

.online-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  font-size: 0.75rem;
  color: #6b7280;
  background: #f3f4f6;
  padding: 4px 10px;
  border-radius: 12px;
  white-space: nowrap;
}

.card-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 8px 0;
}

.card-desc {
  font-size: 0.875rem;
  color: #6b7280;
  margin: 0 0 20px 0;
  line-height: 1.5;
}

.play-btn {
  width: 100%;
  padding: 10px;
  background: #667eea;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}

.play-btn:hover {
  background: #5568d3;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .games-center {
    padding: 16px;
  }

  .games-grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: 16px;
  }

  .game-card {
    padding: 20px 16px;
  }

  .card-icon {
    font-size: 2.5rem;
  }

  .card-title {
    font-size: 1rem;
  }

  .card-desc {
    font-size: 0.8125rem;
  }

  .online-badge {
    font-size: 0.7rem;
    padding: 3px 8px;
  }
}

/* 移动端小屏幕适配 */
@media (max-width: 480px) {
  .games-center {
    padding: 16px 12px;
    padding-bottom: 80px; /* 为底部导航留空间 */
  }

  .page-header {
    margin-bottom: 20px;
  }

  .page-header h1 {
    font-size: 1.4rem;
  }

  .game-section {
    margin-bottom: 28px;
  }

  .section-title {
    font-size: 1rem;
    margin-bottom: 14px;
  }

  .games-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .game-card {
    padding: 16px 12px;
    border-radius: 10px;
  }

  .card-icon {
    font-size: 2rem;
    margin-bottom: 10px;
  }

  .card-title {
    font-size: 0.9rem;
    margin-bottom: 4px;
  }

  .card-desc {
    font-size: 0.75rem;
    margin-bottom: 14px;
    line-height: 1.4;
  }

  .play-btn {
    padding: 8px;
    font-size: 0.8rem;
    border-radius: 6px;
  }

  .online-badge {
    top: 8px;
    right: 8px;
    font-size: 0.65rem;
    padding: 2px 6px;
  }
}
</style>