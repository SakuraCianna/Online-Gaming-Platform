<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Icon } from '@iconify/vue'
import request from '../../config/api'
import { useUserStore } from '../../config/user'

// 定义组件名称，支持 keep-alive 缓存
defineOptions({
    name: 'Leaderboard'
})

// 游戏配置
const GAME_TYPES = {
    '2048': {
        name: '2048',
        icon: '🎯',
        color: '#f59e0b',
        rankTypes: [
            { label: '最高分', value: 'highScore', field: 'bestScore' },
            { label: '最大方块', value: 'maxTile', field: 'bestTile' }
        ]
    },
    'minesweeper': {
        name: '扫雷',
        icon: '💣',
        color: '#8b5cf6',
        rankTypes: [
            { label: '最快通关', value: 'fastestTime', field: 'bestTime' },
            { label: '胜率', value: 'winRate', field: 'winRate' }
        ]
    },
    'gomoku': {
        name: '五子棋',
        icon: '⚫',
        color: '#06b6d4',
        rankTypes: [
            { label: '胜场数', value: 'wins', field: 'wins' },
            { label: '胜率', value: 'winRate', field: 'winRate' }
        ]
    },
    'tank': {
        name: '坦克大战',
        icon: '🚗',
        color: '#ef4444',
        rankTypes: [
            { label: '总击杀', value: 'kills', field: 'totalKills' },
            { label: '胜场数', value: 'wins', field: 'wins' }
        ]
    }
}

// 状态管理
const userStore = useUserStore()
const user = computed(() => userStore.user)

const activeGame = ref('2048')
const activeRankType = ref('highScore')
const loading = ref(false)
const leaderboardData = ref([])
const myRank = ref(null)

// 当前游戏配置
const currentGameConfig = computed(() => GAME_TYPES[activeGame.value])

// 当前排行类型
const currentRankTypes = computed(() => currentGameConfig.value?.rankTypes || [])

// 获取显示值
function getDisplayValue(item, field) {
    const value = item[field]

    if (field === 'winRate') {
        return `${(value * 100).toFixed(1)}%`
    } else if (field === 'bestTime') {
        return formatTime(value)
    } else {
        return value || 0
    }
}

// 格式化时间（秒转为分秒）
function formatTime(seconds) {
    if (!seconds) return '0秒'
    const minutes = Math.floor(seconds / 60)
    const secs = seconds % 60
    return minutes > 0 ? `${minutes}分${secs}秒` : `${secs}秒`
}

// 获取排名奖章
function getRankMedal(rank) {
    const medals = {
        1: { icon: 'mdi:trophy', color: '#fbbf24', label: '冠军' },
        2: { icon: 'mdi:medal', color: '#c0c0c0', label: '亚军' },
        3: { icon: 'mdi:medal', color: '#cd7f32', label: '季军' }
    }
    return medals[rank] || null
}

// 监听游戏类型切换
watch(activeGame, (newGame) => {
    // 切换游戏时重置为该游戏的第一个排行类型
    activeRankType.value = GAME_TYPES[newGame]?.rankTypes[0]?.value || 'highScore'
    loadLeaderboard()
})

// 监听排行类型切换
watch(activeRankType, () => {
    loadLeaderboard()
})

// 加载排行榜数据
async function loadLeaderboard() {
    loading.value = true
    try {
        const response = await request.get('/leaderboard', {
            params: {
                gameType: activeGame.value,
                rankType: activeRankType.value,
                limit: 50
            }
        })

        if (response.data.success) {
            leaderboardData.value = response.data.data || []
            myRank.value = response.data.myRank || null
        } else {
            ElMessage.error(response.data.message || '加载排行榜失败')
            leaderboardData.value = []
            myRank.value = null
        }
    } catch (error) {
        console.error('加载排行榜失败:', error)
        // 使用模拟数据作为fallback
        generateMockData()
    } finally {
        loading.value = false
    }
}

// 生成模拟数据（用于开发测试）
function generateMockData() {
    const mockData = []
    const currentField = currentRankTypes.value.find(t => t.value === activeRankType.value)?.field || 'bestScore'

    for (let i = 0; i < 20; i++) {
        let value
        if (currentField === 'winRate') {
            value = Math.random() * 0.5 + 0.5 // 50%-100%
        } else if (currentField === 'bestTime') {
            value = Math.floor(Math.random() * 300) + 30 // 30-330秒
        } else if (currentField === 'bestScore') {
            value = Math.floor(Math.random() * 50000) + 5000
        } else if (currentField === 'bestTile') {
            const tiles = [128, 256, 512, 1024, 2048, 4096]
            value = tiles[Math.floor(Math.random() * tiles.length)]
        } else {
            value = Math.floor(Math.random() * 100) + 10
        }

        mockData.push({
            rank: i + 1,
            userId: 1000 + i,
            username: `玩家${1000 + i}`,
            avatar: `/image/default-avatar.jpg`,
            [currentField]: value,
            totalGames: Math.floor(Math.random() * 500) + 50,
            isCurrentUser: i === 5 && user.value // 假设当前用户排第6
        })
    }

    leaderboardData.value = mockData
    myRank.value = user.value ? { rank: 6, [currentField]: mockData[5][currentField] } : null
}

// 组件挂载时加载数据
onMounted(() => {
    loadLeaderboard()
})
</script>

<template>
    <div class="leaderboard">
        <!-- 页面标题 -->
        <div class="leaderboard-header">
            <Icon icon="mdi:podium" class="title-icon" />
            <h2 class="title">排行榜</h2>
            <span class="separator">|</span>
            <p class="subtitle">与所有玩家一较高下</p>
        </div>

        <!-- 我的排名卡片 -->
        <div v-if="myRank && user" class="my-rank-card">
            <div class="my-rank-badge">
                <Icon icon="mdi:account-star" class="badge-icon" />
                <span>我的排名</span>
            </div>
            <div class="my-rank-content">
                <div class="rank-number">
                    <span class="rank-label">第</span>
                    <span class="rank-value">{{ myRank.rank }}</span>
                    <span class="rank-label">名</span>
                </div>
                <div class="rank-stats">
                    <Icon :icon="getRankMedal(myRank.rank)?.icon || 'mdi:medal-outline'"
                        :style="{ color: getRankMedal(myRank.rank)?.color || '#9ca3af' }" class="medal-icon" />
                    <span class="stats-text">
                        {{currentRankTypes.find(t => t.value === activeRankType)?.label}}:
                        {{getDisplayValue(myRank, currentRankTypes.find(t => t.value === activeRankType)?.field)}}
                    </span>
                </div>
            </div>
        </div>

        <!-- 游戏类型选择 -->
        <div class="game-selector">
            <button v-for="(config, key) in GAME_TYPES" :key="key" class="game-btn"
                :class="{ active: activeGame === key }" :style="{ '--game-color': config.color }"
                @click="activeGame = key">
                <span class="game-icon">{{ config.icon }}</span>
                <span class="game-name">{{ config.name }}</span>
            </button>
        </div>

        <!-- 排行类型选择 -->
        <div class="rank-type-selector">
            <button v-for="rankType in currentRankTypes" :key="rankType.value" class="rank-type-btn"
                :class="{ active: activeRankType === rankType.value }" @click="activeRankType = rankType.value">
                {{ rankType.label }}
            </button>
        </div>

        <!-- 排行榜列表 -->
        <div class="leaderboard-list" v-loading="loading">
            <!-- 空数据提示 -->
            <div v-if="leaderboardData.length === 0 && !loading" class="empty-state">
                <Icon icon="mdi:trophy-outline" class="empty-icon" />
                <p>暂无排行榜数据</p>
            </div>

            <!-- 前三名特殊展示 -->
            <div v-if="leaderboardData.length > 0" class="top-three">
                <div v-for="item in leaderboardData.slice(0, 3)" :key="item.userId" class="top-card"
                    :class="`rank-${item.rank}`" :style="{ '--rank-color': getRankMedal(item.rank)?.color }">
                    <div class="medal-container">
                        <Icon :icon="getRankMedal(item.rank)?.icon" class="medal" />
                    </div>
                    <div class="avatar-container">
                        <img :src="item.avatar" :alt="item.username" class="avatar" />
                        <div class="rank-badge">{{ item.rank }}</div>
                    </div>
                    <h3 class="username">{{ item.username }}</h3>
                    <div class="score">
                        {{getDisplayValue(item, currentRankTypes.find(t => t.value === activeRankType)?.field)}}
                    </div>
                    <div class="games-count">{{ item.totalGames }}场游戏</div>
                </div>
            </div>

            <!-- 其他排名 -->
            <div v-if="leaderboardData.length > 3" class="rank-list">
                <div v-for="item in leaderboardData.slice(3)" :key="item.userId" class="rank-item"
                    :class="{ 'is-current-user': item.isCurrentUser }">
                    <div class="rank-number">{{ item.rank }}</div>
                    <img :src="item.avatar" :alt="item.username" class="avatar" />
                    <div class="user-info">
                        <div class="username">{{ item.username }}</div>
                        <div class="games-count">{{ item.totalGames }}场</div>
                    </div>
                    <div class="score">
                        {{getDisplayValue(item, currentRankTypes.find(t => t.value === activeRankType)?.field)}}
                    </div>
                    <Icon v-if="item.isCurrentUser" icon="mdi:account-check" class="current-user-icon" />
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.leaderboard {
    padding: 20px;
    max-width: 1200px;
    margin: 0 auto;
    height: 100%;
    overflow: hidden;
}

/* 页面标题 */
.leaderboard-header {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    margin-bottom: 24px;
    padding: 16px 24px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border-radius: 12px;
    color: white;
    box-shadow: 0 4px 20px rgba(102, 126, 234, 0.3);
}

.title-icon {
    font-size: 24px;
    flex-shrink: 0;
}

.title {
    font-size: 20px;
    font-weight: 700;
    margin: 0;
}

.separator {
    font-size: 16px;
    opacity: 0.5;
    margin: 0 4px;
}

.subtitle {
    margin: 0;
    font-size: 14px;
    opacity: 0.9;
    font-weight: 400;
}

/* 我的排名卡片 */
.my-rank-card {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border-radius: 16px;
    padding: 20px;
    margin-bottom: 24px;
    color: white;
    box-shadow: 0 4px 20px rgba(102, 126, 234, 0.3);
}

.my-rank-badge {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    font-weight: 600;
    margin-bottom: 12px;
    opacity: 0.9;
}

.badge-icon {
    font-size: 18px;
}

.my-rank-content {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.rank-number {
    display: flex;
    align-items: baseline;
    gap: 4px;
}

.rank-label {
    font-size: 16px;
}

.rank-value {
    font-size: 48px;
    font-weight: 700;
    line-height: 1;
}

.rank-stats {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 16px;
}

.medal-icon {
    font-size: 32px;
}

/* 游戏选择器 */
.game-selector {
    display: flex;
    gap: 12px;
    margin-bottom: 20px;
    flex-wrap: wrap;
}

.game-btn {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 20px;
    background: white;
    border: 2px solid #e5e7eb;
    border-radius: 12px;
    cursor: pointer;
    transition: all 0.3s ease;
    font-size: 16px;
    font-weight: 500;
    color: #6b7280;
}

.game-btn:hover {
    border-color: var(--game-color, #667eea);
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.game-btn.active {
    background: var(--game-color, #667eea);
    border-color: var(--game-color, #667eea);
    color: white;
    box-shadow: 0 4px 16px rgba(102, 126, 234, 0.3);
}

.game-icon {
    font-size: 24px;
}

/* 排行类型选择器 */
.rank-type-selector {
    display: flex;
    gap: 8px;
    margin-bottom: 24px;
    padding: 4px;
    background: #f3f4f6;
    border-radius: 10px;
    width: fit-content;
}

.rank-type-btn {
    padding: 10px 20px;
    background: transparent;
    border: none;
    border-radius: 8px;
    cursor: pointer;
    font-size: 14px;
    font-weight: 500;
    color: #6b7280;
    transition: all 0.3s ease;
}

.rank-type-btn:hover {
    background: white;
    color: #667eea;
}

.rank-type-btn.active {
    background: white;
    color: #667eea;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

/* 空数据 */
.empty-state {
    text-align: center;
    padding: 60px 20px;
    color: #9ca3af;
}

.empty-icon {
    font-size: 64px;
    margin-bottom: 16px;
    opacity: 0.5;
}

.empty-state p {
    font-size: 16px;
    margin: 0;
}

/* 前三名特殊展示 */
.top-three {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 20px;
    margin-bottom: 24px;
}

.top-card {
    background: white;
    border-radius: 16px;
    padding: 24px;
    text-align: center;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
    position: relative;
    overflow: hidden;
    transition: all 0.3s ease;
    border-top: 4px solid var(--rank-color);
}

.top-card::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 100px;
    background: linear-gradient(to bottom, var(--rank-color), transparent);
    opacity: 0.1;
    pointer-events: none;
}

.top-card:hover {
    transform: translateY(-8px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.top-card.rank-1 {
    grid-column: 1 / -1;
    grid-row: 1;
}

.medal-container {
    margin-bottom: 12px;
}

.medal {
    font-size: 48px;
    color: var(--rank-color);
    filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.2));
}

.avatar-container {
    position: relative;
    display: inline-block;
    margin-bottom: 12px;
}

.top-card .avatar {
    width: 80px;
    height: 80px;
    border-radius: 50%;
    border: 4px solid var(--rank-color);
    object-fit: cover;
}

.rank-badge {
    position: absolute;
    bottom: -4px;
    right: -4px;
    background: var(--rank-color);
    color: white;
    width: 28px;
    height: 28px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    font-weight: 700;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

.top-card .username {
    font-size: 18px;
    font-weight: 600;
    color: #1f2937;
    margin: 0 0 8px 0;
}

.top-card .score {
    font-size: 24px;
    font-weight: 700;
    color: var(--rank-color);
    margin-bottom: 4px;
}

.top-card .games-count {
    font-size: 12px;
    color: #9ca3af;
}

/* 排名列表 */
.rank-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.rank-item {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 16px;
    background: white;
    border-radius: 12px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
    transition: all 0.3s ease;
}

.rank-item:hover {
    transform: translateX(4px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.rank-item.is-current-user {
    background: linear-gradient(90deg, #667eea15 0%, transparent 100%);
    border: 2px solid #667eea;
    box-shadow: 0 4px 16px rgba(102, 126, 234, 0.2);
}

.rank-item .rank-number {
    font-size: 18px;
    font-weight: 700;
    color: #6b7280;
    min-width: 40px;
    text-align: center;
}

.rank-item .avatar {
    width: 48px;
    height: 48px;
    border-radius: 50%;
    object-fit: cover;
    border: 2px solid #e5e7eb;
}

.user-info {
    flex: 1;
}

.rank-item .username {
    font-size: 16px;
    font-weight: 600;
    color: #1f2937;
    margin-bottom: 2px;
}

.rank-item .games-count {
    font-size: 12px;
    color: #9ca3af;
}

.rank-item .score {
    font-size: 20px;
    font-weight: 700;
    color: #667eea;
}

.current-user-icon {
    font-size: 24px;
    color: #667eea;
}

/* 响应式 */
@media (max-width: 768px) {
    .leaderboard-header {
        flex-wrap: wrap;
        padding: 12px 16px;
    }

    .separator {
        display: none;
    }

    .subtitle {
        width: 100%;
        text-align: center;
        margin-top: 4px;
        font-size: 12px;
    }

    .my-rank-content {
        flex-direction: column;
        gap: 16px;
        text-align: center;
    }

    .top-three {
        grid-template-columns: 1fr;
    }

    .top-card.rank-1 {
        grid-column: 1;
    }

    .game-selector {
        gap: 8px;
    }

    .game-btn {
        padding: 10px 16px;
        font-size: 14px;
    }

    .game-icon {
        font-size: 20px;
    }

    .rank-item {
        gap: 12px;
        padding: 12px;
    }

    .rank-item .rank-number {
        min-width: 30px;
        font-size: 16px;
    }

    .rank-item .avatar {
        width: 40px;
        height: 40px;
    }

    .rank-item .score {
        font-size: 18px;
    }
}

/* 移动端小屏幕适配 */
@media (max-width: 480px) {
    .leaderboard {
        padding: 12px;
        padding-bottom: 80px;
    }

    .leaderboard-header {
        padding: 12px;
        gap: 8px;
        margin-bottom: 16px;
    }

    .title-icon {
        font-size: 20px;
    }

    .title {
        font-size: 16px;
    }

    .my-rank-card {
        padding: 16px;
        margin-bottom: 16px;
    }

    .my-rank-badge {
        font-size: 12px;
        margin-bottom: 10px;
    }

    .badge-icon {
        font-size: 16px;
    }

    .rank-value {
        font-size: 36px;
    }

    .rank-label {
        font-size: 14px;
    }

    .rank-stats {
        font-size: 14px;
    }

    .medal-icon {
        font-size: 24px;
    }

    .game-selector {
        gap: 6px;
        margin-bottom: 14px;
        overflow-x: auto;
        flex-wrap: nowrap;
        padding-bottom: 4px;
    }

    .game-btn {
        padding: 8px 12px;
        font-size: 12px;
        flex-shrink: 0;
    }

    .game-icon {
        font-size: 16px;
    }

    .game-name {
        white-space: nowrap;
    }

    .rank-type-selector {
        gap: 4px;
        padding: 3px;
        margin-bottom: 16px;
    }

    .rank-type-btn {
        padding: 8px 14px;
        font-size: 12px;
    }

    .top-three {
        gap: 12px;
        margin-bottom: 16px;
    }

    .top-card {
        padding: 16px;
    }

    .medal {
        font-size: 36px;
    }

    .top-card .avatar {
        width: 60px;
        height: 60px;
    }

    .rank-badge {
        width: 22px;
        height: 22px;
        font-size: 12px;
    }

    .top-card .username {
        font-size: 15px;
    }

    .top-card .score {
        font-size: 20px;
    }

    .top-card .games-count {
        font-size: 11px;
    }

    .rank-list {
        gap: 8px;
    }

    .rank-item {
        padding: 10px;
        gap: 10px;
    }

    .rank-item .rank-number {
        min-width: 26px;
        font-size: 14px;
    }

    .rank-item .avatar {
        width: 36px;
        height: 36px;
    }

    .rank-item .username {
        font-size: 14px;
    }

    .rank-item .games-count {
        font-size: 11px;
    }

    .rank-item .score {
        font-size: 16px;
    }

    .current-user-icon {
        font-size: 20px;
    }

    .empty-icon {
        font-size: 48px;
    }

    .empty-state {
        padding: 40px 16px;
    }

    .empty-state p {
        font-size: 14px;
    }
}
</style>
