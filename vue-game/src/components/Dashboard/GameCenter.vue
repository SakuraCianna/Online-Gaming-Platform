<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

// 默认游戏统计数据
const gameStats = reactive({
  game2048: {
    totalGames: 0,
    bestScore: 0,
    bestTile: 2
  },
  minesweeper: {
    totalGames: 0,
    wins: 0,
    bestTime: 0
  },
  tankBattle: {
    totalGames: 0,
    wins: 0,
    totalKills: 0
  },
  gomoku: { // 新增五子棋统计
    totalGames: 0,
    wins: 0
  }
})

const getGameStats = (gameType) => {
  switch (gameType) {
    case '2048':
      return {
        totalGames: gameStats.game2048.totalGames,
        bestScore: gameStats.game2048.bestScore,
        bestTile: gameStats.game2048.bestTile
      }
    case 'minesweeper':
      return {
        totalGames: gameStats.minesweeper.totalGames,
        wins: gameStats.minesweeper.wins,
        bestTime: gameStats.minesweeper.bestTime
      }
    case 'tank-battle':
      return {
        totalGames: gameStats.tankBattle.totalGames,
        wins: gameStats.tankBattle.wins,
        totalKills: gameStats.tankBattle.totalKills
      }
    case 'gomoku':
      return {
        totalGames: gameStats.gomoku.totalGames,
        wins: gameStats.gomoku.wins
      }
    default:
      return {}
  }
}

const startGame = (gameType) => {
  // 如果是五子棋等多人游戏，跳转到房间创建页
  if (gameType === 'gomoku' || gameType === 'tank-battle') {
    router.push(`/room/create/${gameType}`)
  } else {
    router.push(`/game/${gameType}`)
  }
}
</script>

<template>
  <div class="tab-content games-center">
    <h2 class="section-title">游戏中心</h2>
    
    <!-- 游戏卡片网格 -->
    <div class="games-grid">
      <!-- 2048游戏卡片 -->
      <div class="game-card" @click="startGame('2048')">
        <div class="game-icon">🎯</div>
        <h3>2048</h3>
        <p>数字合成游戏，挑战极限</p>
        <div class="game-stats">
          <div class="stat-item">
            <span class="stat-label">最高分:</span>
            <span class="stat-value">{{ getGameStats('2048').bestScore || 0 }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">最高方块:</span>
            <span class="stat-value">{{ getGameStats('2048').bestTile || 2 }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">游戏次数:</span>
            <span class="stat-value">{{ getGameStats('2048').totalGames || 0 }}</span>
          </div>
        </div>
      </div>

      <!-- 扫雷游戏卡片 -->
      <div class="game-card" @click="startGame('minesweeper')">
        <div class="game-icon">💣</div>
        <h3>扫雷</h3>
        <p>经典扫雷游戏，考验逻辑思维</p>
        <div class="game-stats">
          <div class="stat-item">
            <span class="stat-label">胜利次数:</span>
            <span class="stat-value">{{ getGameStats('minesweeper').wins || 0 }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">最佳时间:</span>
            <span class="stat-value">{{ getGameStats('minesweeper').bestTime || 0 }}s</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">游戏次数:</span>
            <span class="stat-value">{{ getGameStats('minesweeper').totalGames || 0 }}</span>
          </div>
        </div>
      </div>

      <!-- 坦克大战游戏卡片 -->
      <div class="game-card" @click="startGame('tank-battle')">
        <div class="game-icon">🚗</div>
        <h3>坦克大战</h3>
        <p>经典坦克对战游戏</p>
        <div class="game-stats">
          <div class="stat-item">
            <span class="stat-label">胜利次数:</span>
            <span class="stat-value">{{ getGameStats('tank-battle').wins || 0 }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">总击杀:</span>
            <span class="stat-value">{{ getGameStats('tank-battle').totalKills || 0 }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">游戏次数:</span>
            <span class="stat-value">{{ getGameStats('tank-battle').totalGames || 0 }}</span>
          </div>
        </div>
      </div>

      <!-- 五子棋游戏卡片 -->
      <div class="game-card" @click="startGame('gomoku')">
        <div class="game-icon">⭕️❌</div>
        <h3>五子棋</h3>
        <p>经典对战五子棋，支持好友邀请和匹配</p>
        <div class="game-stats">
          <div class="stat-item">
            <span class="stat-label">对局次数:</span>
            <span class="stat-value">{{ getGameStats('gomoku').totalGames || 0 }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">胜利次数:</span>
            <span class="stat-value">{{ getGameStats('gomoku').wins || 0 }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.section-title { 
  font-size: 2rem; 
  font-weight: bold; 
  margin-bottom: 24px; 
  color: #232946; 
}

.games-grid { 
  display: flex; 
  gap: 32px; 
  margin-bottom: 32px; 
}

.game-card { 
  background: #fff; 
  border-radius: 16px; 
  box-shadow: 0 2px 12px rgba(0,0,0,0.08); 
  padding: 24px; 
  width: 220px; 
  text-align: center; 
  cursor: pointer; 
  transition: transform 0.2s, box-shadow 0.2s; 
}

.game-card:hover { 
  transform: translateY(-6px) scale(1.04); 
  box-shadow: 0 6px 24px rgba(76,205,196,0.15); 
}

.game-icon { 
  font-size: 2.5rem; 
  margin-bottom: 12px; 
}

.game-stats { 
  margin-top: 10px; 
  color: #4ecdc4; 
  font-size: 1rem; 
}

.stat-item { 
  display: flex; 
  justify-content: space-between; 
  margin-bottom: 4px; 
}
</style>