<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../config/api'
import { useUserStore } from '../../config/user'

defineOptions({ name: 'Leaderboard' })

const userStore = useUserStore()
const user = computed(() => userStore.user)

const loading = ref(false)
const leaderboardData = ref([])
const myRank = ref(null)
const activeGame = ref('2048')
const activeRankType = ref('highScore')

const gameTypes = [
    { value: '2048', label: '2048', icon: '🎯', color: '#f59e0b' },
    { value: 'minesweeper', label: '扫雷', icon: '💣', color: '#8b5cf6' },
    { value: 'gomoku', label: '五子棋', icon: '⚫', color: '#06b6d4' },
    { value: 'tank', label: '坦克大战', icon: '🚗', color: '#ef4444' }
]

const rankTypeMap = {
    '2048': [
        { label: '最高分', value: 'highScore', field: 'bestScore' },
        { label: '最大方块', value: 'maxTile', field: 'bestTile' }
    ],
    'minesweeper': [
        { label: '最快通关', value: 'fastestTime', field: 'bestTime' },
        { label: '胜率', value: 'winRate', field: 'winRate' }
    ],
    'gomoku': [
        { label: '胜场数', value: 'wins', field: 'wins' },
        { label: '胜率', value: 'winRate', field: 'winRate' }
    ],
    'tank': [
        { label: '总击杀', value: 'kills', field: 'totalKills' },
        { label: '胜场数', value: 'wins', field: 'wins' }
    ]
}

const currentRankTypes = computed(() => rankTypeMap[activeGame.value] || [])
const currentField = computed(() => currentRankTypes.value.find(t => t.value === activeRankType.value)?.field || 'bestScore')
const currentGameConfig = computed(() => gameTypes.find(g => g.value === activeGame.value))

const getDisplayValue = (item, field) => {
    const value = item[field]
    if (field === 'winRate') return `${(value * 100).toFixed(1)}%`
    if (field === 'bestTime') return formatTime(value)
    return value || 0
}

const formatTime = (seconds) => {
    if (!seconds) return '0秒'
    const minutes = Math.floor(seconds / 60)
    const secs = seconds % 60
    return minutes > 0 ? `${minutes}分${secs}秒` : `${secs}秒`
}

const getRankStyle = (rank) => {
    if (rank === 1) return { bg: '#fef3c7', color: '#d97706', border: '#fbbf24' }
    if (rank === 2) return { bg: '#f3f4f6', color: '#6b7280', border: '#c0c0c0' }
    if (rank === 3) return { bg: '#fed7aa', color: '#c2410c', border: '#cd7f32' }
    return { bg: '#fff', color: '#6b7280', border: 'transparent' }
}

const getRankIcon = (rank) => {
    if (rank === 1) return '🥇'
    if (rank === 2) return '🥈'
    if (rank === 3) return '🥉'
    return rank
}

watch(activeGame, (newGame) => {
    activeRankType.value = rankTypeMap[newGame]?.[0]?.value || 'highScore'
    loadLeaderboard()
})

watch(activeRankType, () => loadLeaderboard())

const loadLeaderboard = async () => {
    loading.value = true
    try {
        const response = await request.get('/leaderboard', {
            params: { gameType: activeGame.value, rankType: activeRankType.value, limit: 50 }
        })
        if (response.data.success) {
            leaderboardData.value = response.data.data || []
            myRank.value = response.data.myRank || null
        } else {
            ElMessage.error(response.data.message || '加载排行榜失败')
            leaderboardData.value = []
            myRank.value = null
        }
    } catch {
        ElMessage.error('加载排行榜失败')
        leaderboardData.value = []
        myRank.value = null
    } finally {
        loading.value = false
    }
}

const refreshData = () => { loadLeaderboard(); ElMessage.success('已刷新') }

onMounted(() => loadLeaderboard())
</script>

<template>
    <div class="leaderboard">
        <!-- 页面标题 -->
        <div class="page-header">
            <h2>🏆 排行榜</h2>
        </div>

        <!-- 筛选栏 -->
        <div class="filter-bar">
            <select v-model="activeGame" class="filter-select">
                <option v-for="game in gameTypes" :key="game.value" :value="game.value">
                    {{ game.icon }} {{ game.label }}
                </option>
            </select>

            <select v-model="activeRankType" class="filter-select">
                <option v-for="type in currentRankTypes" :key="type.value" :value="type.value">
                    {{ type.label }}
                </option>
            </select>

            <button class="btn-refresh" @click="refreshData" :disabled="loading">🔄 刷新</button>

            <!-- 我的排名 -->
            <div v-if="myRank && user" class="my-rank-info">
                <span class="my-rank-label">我的排名</span>
                <span class="my-rank-value">第 {{ myRank.rank }} 名</span>
            </div>
        </div>

        <!-- 排行榜列表 -->
        <div class="rank-list" v-loading="loading">
            <div v-if="leaderboardData.length === 0 && !loading" class="empty-state">
                <span>🏆</span>
                <p>此排行榜空空如也</p>
            </div>

            <div 
                v-for="item in leaderboardData" 
                :key="item.userId" 
                class="rank-row"
                :class="{ 'is-current-user': item.isCurrentUser, 'top-three': item.rank <= 3 }"
                :style="{ borderLeftColor: getRankStyle(item.rank).border }">
                
                <!-- 排名 -->
                <div class="rank-position" :style="{ background: getRankStyle(item.rank).bg, color: getRankStyle(item.rank).color }">
                    <span v-if="item.rank <= 3" class="rank-medal">{{ getRankIcon(item.rank) }}</span>
                    <span v-else class="rank-num">{{ item.rank }}</span>
                </div>

                <!-- 玩家信息 -->
                <div class="player-info">
                    <img :src="item.avatar || '/image/default-avatar.jpg'" class="avatar" />
                    <div class="player-detail">
                        <span class="player-name">{{ item.username }}</span>
                        <span class="player-games">{{ item.totalGames }}场游戏</span>
                    </div>
                </div>

                <!-- 游戏类型 -->
                <div class="game-type">
                    <span class="game-icon">{{ currentGameConfig.icon }}</span>
                    <span class="game-name">{{ currentGameConfig.label }}</span>
                </div>

                <!-- 成绩 -->
                <div class="score-info">
                    <span class="score-label">{{ currentRankTypes.find(t => t.value === activeRankType)?.label }}</span>
                    <span class="score-value" :style="{ color: currentGameConfig.color }">
                        {{ getDisplayValue(item, currentField) }}
                    </span>
                </div>

                <!-- 当前用户标识 -->
                <div v-if="item.isCurrentUser" class="current-user-badge">我</div>
            </div>
        </div>
    </div>
</template>


<style scoped>
.leaderboard {
    padding: 20px;
    height: 100%;
    display: flex;
    flex-direction: column;
    overflow: hidden;
}

.page-header {
    margin-bottom: 16px;
}

.page-header h2 {
    font-size: 20px;
    font-weight: 600;
    color: #333;
    margin: 0;
}

/* 筛选栏 */
.filter-bar {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 16px 20px;
    background: #fff;
    border-radius: 12px;
    box-shadow: 0 2px 8px rgba(0,0,0,0.06);
    margin-bottom: 20px;
    flex-wrap: wrap;
}

.filter-select {
    padding: 10px 16px;
    border: 1px solid #e0e0e0;
    border-radius: 10px;
    font-size: 15px;
    background: #fff;
    cursor: pointer;
    min-width: 140px;
}

.filter-select:focus {
    outline: none;
    border-color: #667eea;
}

.btn-refresh {
    padding: 10px 20px;
    border: none;
    border-radius: 10px;
    font-size: 15px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.2s;
    background: #f5f5f5;
    color: #666;
}

.btn-refresh:hover:not(:disabled) {
    background: #e8e8e8;
}

.btn-refresh:disabled {
    opacity: 0.6;
    cursor: not-allowed;
}

.my-rank-info {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 20px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border-radius: 10px;
    color: #fff;
}

.my-rank-label {
    font-size: 14px;
    opacity: 0.9;
}

.my-rank-value {
    font-size: 16px;
    font-weight: 700;
}

/* 排行榜列表 */
.rank-list {
    flex: 1;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.empty-state {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: #999;
}

.empty-state span {
    font-size: 64px;
    margin-bottom: 16px;
}

/* 排名行 */
.rank-row {
    display: flex;
    align-items: center;
    gap: 24px;
    padding: 18px 24px;
    background: #fff;
    border-radius: 12px;
    box-shadow: 0 2px 8px rgba(0,0,0,0.06);
    transition: all 0.2s;
    border-left: 4px solid transparent;
}

.rank-row:hover {
    box-shadow: 0 4px 16px rgba(0,0,0,0.1);
}

.rank-row.top-three {
    background: linear-gradient(90deg, rgba(251, 191, 36, 0.05) 0%, #fff 100%);
}

.rank-row.is-current-user {
    background: linear-gradient(90deg, rgba(102, 126, 234, 0.1) 0%, #fff 100%);
    border-left-color: #667eea;
}

/* 排名位置 */
.rank-position {
    min-width: 50px;
    height: 50px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 12px;
    font-weight: 700;
}

.rank-medal {
    font-size: 28px;
}

.rank-num {
    font-size: 18px;
}

/* 玩家信息 */
.player-info {
    display: flex;
    align-items: center;
    gap: 12px;
    min-width: 180px;
}

.player-info .avatar {
    width: 48px;
    height: 48px;
    border-radius: 50%;
    object-fit: cover;
    border: 2px solid #e5e7eb;
}

.player-detail {
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.player-name {
    font-size: 16px;
    font-weight: 600;
    color: #333;
}

.player-games {
    font-size: 13px;
    color: #999;
}

/* 游戏类型 */
.game-type {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 100px;
}

.game-icon {
    font-size: 24px;
}

.game-name {
    font-size: 15px;
    color: #666;
}

/* 成绩信息 */
.score-info {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;
    margin-left: auto;
    min-width: 120px;
}

.score-label {
    font-size: 13px;
    color: #999;
}

.score-value {
    font-size: 22px;
    font-weight: 700;
}

/* 当前用户标识 */
.current-user-badge {
    padding: 6px 14px;
    background: #667eea;
    color: #fff;
    border-radius: 16px;
    font-size: 13px;
    font-weight: 600;
}

/* 响应式 */
@media (max-width: 900px) {
    .game-type {
        display: none;
    }
}

@media (max-width: 768px) {
    .filter-bar {
        gap: 8px;
    }

    .filter-select {
        flex: 1;
        min-width: 100px;
    }

    .my-rank-info {
        margin-left: 0;
        width: 100%;
        justify-content: center;
    }

    .rank-row {
        flex-wrap: wrap;
        gap: 12px;
        padding: 14px 16px;
    }

    .player-info {
        min-width: auto;
        flex: 1;
    }

    .score-info {
        min-width: auto;
    }
}

@media (max-width: 480px) {
    .leaderboard {
        padding: 12px;
        padding-bottom: 80px;
    }

    .page-header h2 {
        font-size: 18px;
    }

    .filter-bar {
        padding: 10px 12px;
    }

    .btn-refresh {
        padding: 8px 12px;
        font-size: 13px;
    }

    .rank-row {
        padding: 12px;
        gap: 10px;
    }

    .rank-position {
        min-width: 40px;
        height: 40px;
    }

    .rank-medal {
        font-size: 22px;
    }

    .rank-num {
        font-size: 15px;
    }

    .player-info .avatar {
        width: 36px;
        height: 36px;
    }

    .player-name {
        font-size: 14px;
    }

    .player-games {
        font-size: 12px;
    }

    .score-value {
        font-size: 18px;
    }

    .score-label {
        font-size: 12px;
    }

    .current-user-badge {
        padding: 4px 10px;
        font-size: 12px;
    }
}
</style>
