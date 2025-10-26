<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../config/api'
import { useUserStore } from '../../config/user'
import { useRoomStore } from '../../config/room'

// 定义组件名称，支持 keep-alive 缓存
defineOptions({
    name: 'GameRooms'
})

const router = useRouter()
const userStore = useUserStore()
const roomStore = useRoomStore()

// 房间列表数据
const rooms = ref([])
const loading = ref(false)
const searchQuery = ref('')
const selectedGame = ref('all')
const selectedStatus = ref('all')

// 房间统计
const statistics = ref({
    totalRooms: 0,
    waitingRooms: 0,
    playingRooms: 0,
    onlinePlayers: 0
})

// 游戏类型配置
const gameTypes = [
    { value: 'all', label: '全部游戏', icon: '🎮' },
    { value: 'gomoku', label: '五子棋', icon: '⭕️', color: '#667eea' },
    { value: 'tank-battle', label: '坦克大战', icon: '🚗', color: '#f093fb' },
    { value: 'minesweeper', label: '扫雷', icon: '💣', color: '#4facfe' },
    { value: '2048', label: '2048', icon: '🎯', color: '#fa709a' }
]

// 房间状态配置
const statusTypes = [
    { value: 'all', label: '全部状态' },
    { value: 'waiting', label: '等待中' },
    { value: 'playing', label: '游戏中' }
]

// 获取游戏配置
const getGameConfig = (gameName) => {
    return gameTypes.find(g => g.value === gameName) || gameTypes[0]
}

// 获取房间状态文本
const getStatusText = (status) => {
    const statusMap = {
        0: '等待中',
        1: '游戏中',
        2: '已结束'
    }
    return statusMap[status] || '未知'
}

// 获取房间状态类型
const getStatusType = (status) => {
    const typeMap = {
        0: 'waiting',
        1: 'playing',
        2: 'ended'
    }
    return typeMap[status] || 'waiting'
}

// 过滤后的房间列表
const filteredRooms = computed(() => {
    let result = rooms.value

    // 按游戏类型筛选
    if (selectedGame.value !== 'all') {
        result = result.filter(room => room.gameName === selectedGame.value)
    }

    // 按状态筛选
    if (selectedStatus.value !== 'all') {
        if (selectedStatus.value === 'waiting') {
            result = result.filter(room => room.status === 0)
        } else if (selectedStatus.value === 'playing') {
            result = result.filter(room => room.status === 1)
        }
    }

    // 按搜索关键词筛选
    if (searchQuery.value.trim()) {
        const query = searchQuery.value.toLowerCase()
        result = result.filter(room =>
            room.roomCode?.toLowerCase().includes(query) ||
            room.creatorName?.toLowerCase().includes(query)
        )
    }

    return result
})

// 计算统计数据
const updateStatistics = () => {
    statistics.value = {
        totalRooms: rooms.value.length,
        waitingRooms: rooms.value.filter(r => r.status === 0).length,
        playingRooms: rooms.value.filter(r => r.status === 1).length,
        onlinePlayers: rooms.value.reduce((sum, r) => sum + (r.currentPlayers || 0), 0)
    }
}

// 获取房间列表
const fetchRooms = async () => {
    loading.value = true
    try {
        const response = await request.get('/room/list')
        if (response.data.success) {
            rooms.value = response.data.rooms || []
            updateStatistics()
        } else {
            ElMessage.error(response.data.message || '获取房间列表失败')
        }
    } catch (error) {
        console.error('获取房间列表失败:', error)
        // 使用模拟数据
        rooms.value = generateMockRooms()
        updateStatistics()
    } finally {
        loading.value = false
    }
}

// 生成模拟数据
const generateMockRooms = () => {
    const mockGames = ['gomoku', 'tank-battle', 'minesweeper', '2048']
    const mockNames = ['小明', '小红', '小刚', '小李', '小王', '小张', '小赵', '小钱']
    const mockRooms = []

    for (let i = 0; i < 12; i++) {
        mockRooms.push({
            id: i + 1,
            roomCode: (1000 + Math.floor(Math.random() * 9000)).toString(),
            gameName: mockGames[Math.floor(Math.random() * mockGames.length)],
            creatorId: Math.floor(Math.random() * 1000),
            creatorName: mockNames[Math.floor(Math.random() * mockNames.length)],
            creatorAvatar: '/image/default-avatar.jpg',
            status: Math.floor(Math.random() * 3),
            currentPlayers: Math.floor(Math.random() * 2) + 1,
            maxPlayers: 2,
            isPrivate: Math.random() > 0.7 ? 1 : 0,
            createTime: new Date(Date.now() - Math.random() * 3600000).toISOString()
        })
    }

    return mockRooms
}

// 加入房间
const joinRoom = async (room) => {
    // 检查房间状态
    if (room.status === 1) {
        ElMessage.warning('该房间正在游戏中，无法加入')
        return
    }

    if (room.currentPlayers >= room.maxPlayers) {
        ElMessage.warning('房间已满')
        return
    }

    // 如果是私密房间，需要输入房间码
    if (room.isPrivate === 1) {
        try {
            const { value } = await ElMessageBox.prompt('请输入房间码', '加入私密房间', {
                confirmButtonText: '加入',
                cancelButtonText: '取消',
                inputPattern: /^\d{4}$/,
                inputErrorMessage: '请输入4位数字房间码'
            })

            if (value !== room.roomCode) {
                ElMessage.error('房间码错误')
                return
            }
        } catch {
            return // 用户取消
        }
    }

    // 调用加入房间接口
    try {
        const response = await request.post('/room/join', {
            roomCode: room.roomCode,
            userId: userStore.userInfo?.id
        })

        if (response.data.success) {
            roomStore.setCurrentRoom(response.data.room)
            ElMessage.success('成功加入房间')
            router.push(`/game/${room.gameName}`)
        } else {
            ElMessage.error(response.data.message || '加入房间失败')
        }
    } catch (error) {
        console.error('加入房间失败:', error)
        ElMessage.error('加入房间失败，请稍后重试')
    }
}

// 创建房间
const createRoom = () => {
    router.push('/dashboard/game-center')
}

// 刷新房间列表
const refreshRooms = () => {
    fetchRooms()
    ElMessage.success('已刷新房间列表')
}

// 定时刷新
let refreshTimer = null

onMounted(() => {
    fetchRooms()
    // 每10秒自动刷新
    refreshTimer = setInterval(() => {
        fetchRooms()
    }, 10000)
})

onUnmounted(() => {
    if (refreshTimer) {
        clearInterval(refreshTimer)
    }
})

// 格式化时间
const formatTime = (time) => {
    if (!time) return ''
    const date = new Date(time)
    const now = new Date()
    const diff = Math.floor((now - date) / 1000) // 秒

    if (diff < 60) return '刚刚'
    if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
    if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`
    return `${Math.floor(diff / 86400)}天前`
}
</script>

<template>
    <div class="game-rooms">
        <!-- 顶部统计卡片 -->
        <div class="statistics-bar">
            <div class="stat-card">
                <div class="stat-icon">🏠</div>
                <div class="stat-content">
                    <div class="stat-value">{{ statistics.totalRooms }}</div>
                    <div class="stat-label">在线房间</div>
                </div>
            </div>
            <div class="stat-card">
                <div class="stat-icon">⏳</div>
                <div class="stat-content">
                    <div class="stat-value">{{ statistics.waitingRooms }}</div>
                    <div class="stat-label">等待中</div>
                </div>
            </div>
            <div class="stat-card">
                <div class="stat-icon">🎮</div>
                <div class="stat-content">
                    <div class="stat-value">{{ statistics.playingRooms }}</div>
                    <div class="stat-label">游戏中</div>
                </div>
            </div>
            <div class="stat-card">
                <div class="stat-icon">👥</div>
                <div class="stat-content">
                    <div class="stat-value">{{ statistics.onlinePlayers }}</div>
                    <div class="stat-label">在线玩家</div>
                </div>
            </div>
        </div>

        <!-- 操作栏 -->
        <div class="action-bar">
            <div class="left-actions">
                <!-- 搜索框 -->
                <div class="search-box">
                    <span class="search-icon">🔍</span>
                    <input v-model="searchQuery" type="text" placeholder="搜索房间码或玩家名..." class="search-input" />
                </div>

                <!-- 游戏类型筛选 -->
                <div class="filter-group">
                    <button v-for="game in gameTypes" :key="game.value"
                        :class="['filter-btn', { active: selectedGame === game.value }]"
                        @click="selectedGame = game.value">
                        <span class="btn-icon">{{ game.icon }}</span>
                        <span>{{ game.label }}</span>
                    </button>
                </div>

                <!-- 状态筛选 -->
                <div class="filter-group">
                    <button v-for="status in statusTypes" :key="status.value"
                        :class="['filter-btn', { active: selectedStatus === status.value }]"
                        @click="selectedStatus = status.value">
                        {{ status.label }}
                    </button>
                </div>
            </div>

            <div class="right-actions">
                <button class="action-btn refresh-btn" @click="refreshRooms" :disabled="loading">
                    <span class="btn-icon">🔄</span>
                    刷新
                </button>
                <button class="action-btn create-btn" @click="createRoom">
                    <span class="btn-icon">➕</span>
                    创建房间
                </button>
            </div>
        </div>

        <!-- 房间列表 -->
        <div class="rooms-container">
            <div v-if="loading" class="loading-state">
                <div class="loading-spinner"></div>
                <p>加载中...</p>
            </div>

            <div v-else-if="filteredRooms.length === 0" class="empty-state">
                <div class="empty-icon">🏠</div>
                <h3>暂无房间</h3>
                <p>当前没有符合条件的游戏房间</p>
                <button class="create-room-btn" @click="createRoom">
                    创建新房间
                </button>
            </div>

            <div v-else class="rooms-grid">
                <div v-for="room in filteredRooms" :key="room.id" class="room-card" :class="getStatusType(room.status)">
                    <!-- 房间头部 -->
                    <div class="room-header" :style="{ background: getGameConfig(room.gameName).color || '#667eea' }">
                        <div class="game-info">
                            <span class="game-icon">{{ getGameConfig(room.gameName).icon }}</span>
                            <span class="game-name">{{ getGameConfig(room.gameName).label }}</span>
                        </div>
                        <div class="room-badges">
                            <span v-if="room.isPrivate === 1" class="badge private-badge" title="私密房间">
                                🔒
                            </span>
                            <span class="badge status-badge" :class="getStatusType(room.status)">
                                {{ getStatusText(room.status) }}
                            </span>
                        </div>
                    </div>

                    <!-- 房间内容 -->
                    <div class="room-body">
                        <div class="room-code-section">
                            <span class="label">房间码:</span>
                            <span class="room-code">{{ room.roomCode }}</span>
                        </div>

                        <div class="creator-info">
                            <img :src="room.creatorAvatar || '/image/default-avatar.jpg'" :alt="room.creatorName"
                                class="creator-avatar" />
                            <div class="creator-details">
                                <div class="creator-name">{{ room.creatorName }}</div>
                                <div class="creator-label">房主</div>
                            </div>
                        </div>

                        <div class="room-meta">
                            <div class="meta-item">
                                <span class="meta-icon">👥</span>
                                <span class="meta-text">{{ room.currentPlayers || 1 }}/{{ room.maxPlayers }}</span>
                            </div>
                            <div class="meta-item">
                                <span class="meta-icon">🕐</span>
                                <span class="meta-text">{{ formatTime(room.createTime) }}</span>
                            </div>
                        </div>
                    </div>

                    <!-- 房间底部操作 -->
                    <div class="room-footer">
                        <button class="join-btn" :class="getStatusType(room.status)"
                            :disabled="room.status === 1 || room.currentPlayers >= room.maxPlayers"
                            @click="joinRoom(room)">
                            <span v-if="room.status === 0 && room.currentPlayers < room.maxPlayers">
                                <span class="btn-icon">🚀</span> 加入房间
                            </span>
                            <span v-else-if="room.status === 1">
                                <span class="btn-icon">👁️</span> 观战
                            </span>
                            <span v-else-if="room.currentPlayers >= room.maxPlayers">
                                <span class="btn-icon">🔒</span> 房间已满
                            </span>
                            <span v-else>
                                已结束
                            </span>
                        </button>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.game-rooms {
    padding: 24px;
    max-width: 1400px;
    margin: 0 auto;
    height: 100%;
    overflow-y: auto;
}

/* 统计栏 */
.statistics-bar {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
    margin-bottom: 24px;
}

.stat-card {
    background: white;
    border-radius: 16px;
    padding: 20px;
    display: flex;
    align-items: center;
    gap: 16px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
    transition: all 0.3s ease;
}

.stat-card:hover {
    transform: translateY(-4px);
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
}

.stat-icon {
    font-size: 2.5rem;
    width: 60px;
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
    border-radius: 12px;
}

.stat-content {
    flex: 1;
}

.stat-value {
    font-size: 2rem;
    font-weight: 700;
    color: #232946;
    line-height: 1;
    margin-bottom: 4px;
}

.stat-label {
    font-size: 0.875rem;
    color: #6b7280;
}

/* 操作栏 */
.action-bar {
    background: white;
    border-radius: 16px;
    padding: 20px;
    margin-bottom: 24px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
}

.left-actions {
    display: flex;
    gap: 16px;
    flex-wrap: wrap;
    flex: 1;
}

.search-box {
    display: flex;
    align-items: center;
    background: #f8f9fa;
    border: 2px solid #e9ecef;
    border-radius: 12px;
    padding: 10px 16px;
    gap: 8px;
    min-width: 250px;
    transition: all 0.3s ease;
}

.search-box:focus-within {
    border-color: #667eea;
    box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.search-icon {
    font-size: 1.25rem;
}

.search-input {
    flex: 1;
    border: none;
    background: transparent;
    outline: none;
    font-size: 0.95rem;
    color: #232946;
}

.search-input::placeholder {
    color: #9ca3af;
}

.filter-group {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
}

.filter-btn {
    padding: 10px 16px;
    border: 2px solid #e9ecef;
    background: #f8f9fa;
    border-radius: 10px;
    font-size: 0.875rem;
    font-weight: 600;
    color: #6b7280;
    cursor: pointer;
    transition: all 0.3s ease;
    display: flex;
    align-items: center;
    gap: 6px;
}

.filter-btn:hover {
    border-color: #667eea;
    color: #667eea;
    background: #f5f6ff;
}

.filter-btn.active {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border-color: #667eea;
    color: white;
}

.filter-btn .btn-icon {
    font-size: 1.1rem;
}

.right-actions {
    display: flex;
    gap: 12px;
}

.action-btn {
    padding: 10px 20px;
    border: none;
    border-radius: 10px;
    font-size: 0.95rem;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.3s ease;
    display: flex;
    align-items: center;
    gap: 8px;
}

.refresh-btn {
    background: #f8f9fa;
    border: 2px solid #e9ecef;
    color: #6b7280;
}

.refresh-btn:hover:not(:disabled) {
    background: #e9ecef;
    transform: translateY(-2px);
}

.refresh-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
}

.create-btn {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;
    box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.create-btn:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 20px rgba(102, 126, 234, 0.5);
}

/* 房间容器 */
.rooms-container {
    min-height: 400px;
}

.loading-state,
.empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 80px 20px;
    text-align: center;
}

.loading-spinner {
    width: 50px;
    height: 50px;
    border: 5px solid #f3f3f3;
    border-top: 5px solid #667eea;
    border-radius: 50%;
    animation: spin 1s linear infinite;
    margin-bottom: 20px;
}

@keyframes spin {
    0% {
        transform: rotate(0deg);
    }

    100% {
        transform: rotate(360deg);
    }
}

.empty-icon {
    font-size: 5rem;
    margin-bottom: 20px;
    opacity: 0.5;
}

.empty-state h3 {
    font-size: 1.5rem;
    color: #232946;
    margin: 0 0 8px 0;
}

.empty-state p {
    color: #6b7280;
    margin: 0 0 24px 0;
}

.create-room-btn {
    padding: 12px 32px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;
    border: none;
    border-radius: 12px;
    font-size: 1rem;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.3s ease;
}

.create-room-btn:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

/* 房间网格 */
.rooms-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: 20px;
}

/* 房间卡片 */
.room-card {
    background: white;
    border-radius: 16px;
    overflow: hidden;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
    transition: all 0.3s ease;
    display: flex;
    flex-direction: column;
}

.room-card:hover {
    transform: translateY(-6px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.room-header {
    padding: 16px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;
}

.game-info {
    display: flex;
    align-items: center;
    gap: 8px;
}

.game-icon {
    font-size: 1.5rem;
}

.game-name {
    font-size: 1rem;
    font-weight: 600;
}

.room-badges {
    display: flex;
    gap: 6px;
}

.badge {
    padding: 4px 10px;
    border-radius: 12px;
    font-size: 0.75rem;
    font-weight: 600;
    background: rgba(255, 255, 255, 0.25);
    backdrop-filter: blur(10px);
}

.private-badge {
    font-size: 0.9rem;
}

.status-badge.waiting {
    background: rgba(16, 185, 129, 0.9);
}

.status-badge.playing {
    background: rgba(245, 158, 11, 0.9);
}

.status-badge.ended {
    background: rgba(107, 114, 128, 0.9);
}

.room-body {
    padding: 20px;
    flex: 1;
}

.room-code-section {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 16px;
    padding: 12px;
    background: #f8f9fa;
    border-radius: 10px;
}

.room-code-section .label {
    font-size: 0.875rem;
    color: #6b7280;
}

.room-code {
    font-size: 1.25rem;
    font-weight: 700;
    color: #667eea;
    letter-spacing: 2px;
}

.creator-info {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
    padding-bottom: 16px;
    border-bottom: 1px solid #e9ecef;
}

.creator-avatar {
    width: 48px;
    height: 48px;
    border-radius: 50%;
    object-fit: cover;
    border: 2px solid #e9ecef;
}

.creator-details {
    flex: 1;
}

.creator-name {
    font-size: 1rem;
    font-weight: 600;
    color: #232946;
    margin-bottom: 2px;
}

.creator-label {
    font-size: 0.75rem;
    color: #6b7280;
}

.room-meta {
    display: flex;
    gap: 16px;
}

.meta-item {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 0.875rem;
    color: #6b7280;
}

.meta-icon {
    font-size: 1.1rem;
}

.room-footer {
    padding: 0 20px 20px 20px;
}

.join-btn {
    width: 100%;
    padding: 12px;
    border: none;
    border-radius: 10px;
    font-size: 1rem;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.3s ease;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
}

.join-btn.waiting {
    background: linear-gradient(135deg, #10b981 0%, #059669 100%);
    color: white;
    box-shadow: 0 4px 12px rgba(16, 185, 129, 0.4);
}

.join-btn.waiting:hover:not(:disabled) {
    transform: scale(1.02);
    box-shadow: 0 6px 16px rgba(16, 185, 129, 0.5);
}

.join-btn.playing {
    background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
    color: white;
    box-shadow: 0 4px 12px rgba(245, 158, 11, 0.4);
}

.join-btn.playing:hover:not(:disabled) {
    transform: scale(1.02);
    box-shadow: 0 6px 16px rgba(245, 158, 11, 0.5);
}

.join-btn:disabled {
    background: #e9ecef;
    color: #9ca3af;
    cursor: not-allowed;
    box-shadow: none;
}

.join-btn .btn-icon {
    font-size: 1.1rem;
}

/* 响应式设计 */
@media (max-width: 1200px) {
    .statistics-bar {
        grid-template-columns: repeat(2, 1fr);
    }
}

@media (max-width: 768px) {
    .game-rooms {
        padding: 16px;
    }

    .statistics-bar {
        grid-template-columns: 1fr;
    }

    .action-bar {
        flex-direction: column;
        align-items: stretch;
    }

    .left-actions {
        flex-direction: column;
    }

    .search-box {
        min-width: auto;
    }

    .right-actions {
        width: 100%;
    }

    .action-btn {
        flex: 1;
    }

    .rooms-grid {
        grid-template-columns: 1fr;
    }
}
</style>