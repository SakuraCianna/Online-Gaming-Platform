<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../config/api'
import { useRoomStore } from '../../config/room'
import { wsService } from '../../config/websocket'

defineOptions({ name: 'GameRooms' })

const router = useRouter()
const roomStore = useRoomStore()

const rooms = ref([])
const loading = ref(false)
const searchQuery = ref('')
const selectedGame = ref('all')
const selectedStatus = ref('all')
const joining = ref(false) // 防止重复点击

const gameTypes = [
    { value: 'all', label: '全部游戏', icon: '🎮' },
    { value: 'gomoku', label: '五子棋', icon: '⭕' },
    { value: 'tank_battle', label: '坦克大战', icon: '🚗' },
    { value: 'minesweeper', label: '扫雷', icon: '💣' },
    { value: '2048', label: '2048', icon: '🎯' }
]

const statusTypes = [
    { value: 'all', label: '全部状态' },
    { value: 'waiting', label: '等待中' },
    { value: 'playing', label: '游戏中' }
]

const getGameConfig = (gameName) => gameTypes.find(g => g.value === gameName) || gameTypes[0]
const getStatusText = (status) => ({ 0: '等待中', 1: '游戏中', 2: '已结束' }[status] || '未知')
const getStatusType = (status) => ({ 0: 'waiting', 1: 'playing', 2: 'ended' }[status] || 'waiting')

const filteredRooms = computed(() => {
    let result = rooms.value
    // 过滤掉私密房间
    result = result.filter(room => room.isPrivate !== 1)
    if (selectedGame.value !== 'all') {
        result = result.filter(room => room.gameName === selectedGame.value)
    }
    if (selectedStatus.value !== 'all') {
        result = result.filter(room => 
            selectedStatus.value === 'waiting' ? room.status === 0 : room.status === 1
        )
    }
    if (searchQuery.value.trim()) {
        const query = searchQuery.value.toLowerCase()
        result = result.filter(room =>
            room.roomCode?.toLowerCase().includes(query) ||
            room.creatorName?.toLowerCase().includes(query)
        )
    }
    return result
})

const fetchRooms = async () => {
    loading.value = true
    try {
        const response = await request.get('/room/list')
        if (response.data.success) {
            rooms.value = response.data.rooms || []
        } else {
            ElMessage.error(response.data.message || '获取房间列表失败')
            rooms.value = []
        }
    } catch (error) {
        ElMessage.error('获取房间列表失败')
        rooms.value = []
    } finally {
        loading.value = false
    }
}

const joinRoom = async (room) => {
    if (joining.value) return
    if (room.status === 1) return ElMessage.warning('该房间正在游戏中')
    if (room.currentPlayers >= room.maxPlayers) return ElMessage.warning('房间已满')

    if (room.isPrivate === 1) {
        try {
            const { value } = await ElMessageBox.prompt('请输入房间码', '加入私密房间', {
                confirmButtonText: '加入',
                cancelButtonText: '取消',
                inputPattern: /^\d{4}$/,
                inputErrorMessage: '请输入4位数字房间码'
            })
            if (value !== room.roomCode) return ElMessage.error('房间码错误')
        } catch { return }
    }

    joining.value = true
    try {
        const response = await request.post('/room/join', {
            roomCode: room.roomCode
        })
        if (response.data.success) {
            roomStore.setCurrentRoom(response.data.room)
            ElMessage.success('成功加入房间')
            router.push(`/game/${room.gameName}`)
        } else {
            ElMessage.error(response.data.message || '加入房间失败')
        }
    } catch {
        ElMessage.error('加入房间失败')
    } finally {
        joining.value = false
    }
}

const createRoom = () => router.push('/user/games')
const refreshRooms = () => { fetchRooms(); ElMessage.success('已刷新') }

const formatTime = (time) => {
    if (!time) return ''
    const diff = Math.floor((Date.now() - new Date(time)) / 1000)
    if (diff < 60) return '刚刚'
    if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
    return `${Math.floor(diff / 3600)}小时前`
}

// 订阅房间列表更新
const subscribeRoomListUpdate = () => {
    wsService.subscribe('/topic/roomList', (message) => {
        if (message.type === 'roomListUpdated') {
            // 收到更新通知，刷新房间列表
            fetchRooms()
        }
    })
}

let refreshTimer = null
onMounted(() => {
    fetchRooms()
    // 订阅WebSocket房间列表更新
    subscribeRoomListUpdate()
    // 备用定时刷新（30秒一次，作为兜底）
    refreshTimer = setInterval(fetchRooms, 30000)
})

onUnmounted(() => {
    if (refreshTimer) clearInterval(refreshTimer)
    // 取消订阅
    wsService.unsubscribe('/topic/roomList')
})
</script>

<template>
    <div class="game-rooms">
        <!-- 页面标题 -->
        <div class="page-header">
            <h2>游戏房间</h2>
        </div>

        <!-- 筛选栏 - 一行显示 -->
        <div class="filter-bar">
            <div class="search-box">
                <span class="search-icon">🔍</span>
                <input v-model="searchQuery" type="text" placeholder="搜索房间码或玩家..." />
            </div>

            <select v-model="selectedGame" class="filter-select">
                <option v-for="game in gameTypes" :key="game.value" :value="game.value">
                    {{ game.icon }} {{ game.label }}
                </option>
            </select>

            <select v-model="selectedStatus" class="filter-select">
                <option v-for="status in statusTypes" :key="status.value" :value="status.value">
                    {{ status.label }}
                </option>
            </select>

            <button class="btn-refresh" @click="refreshRooms" :disabled="loading">🔄 刷新</button>
            <div class="create-section">
                <button class="btn-create" @click="createRoom">➕ 创建房间</button>
                <span class="create-tip">私密房间不显示在列表里</span>
            </div>
        </div>

        <!-- 房间列表 - 行式布局 -->
        <div class="rooms-list" v-loading="loading">
            <div v-if="filteredRooms.length === 0 && !loading" class="empty-state">
                <span>🏠</span>
                <p>暂无房间</p>
            </div>

            <div v-for="room in filteredRooms" :key="room.id" class="room-row" :class="getStatusType(room.status)">
                <!-- 游戏类型 -->
                <div class="room-game">
                    <span class="game-icon">{{ getGameConfig(room.gameName).icon }}</span>
                    <span class="game-name">{{ getGameConfig(room.gameName).label }}</span>
                </div>

                <!-- 房间码 -->
                <div class="room-code">
                    <span class="label">房间码</span>
                    <span class="code">{{ room.roomCode }}</span>
                </div>

                <!-- 房主信息 -->
                <div class="room-creator">
                    <img :src="room.creatorAvatar || '/image/default-avatar.jpg'" class="avatar" />
                    <span class="name">{{ room.creatorName }}</span>
                </div>

                <!-- 人数 -->
                <div class="room-players">
                    <span>👥 {{ room.currentPlayers || 1 }}/{{ room.maxPlayers }}</span>
                </div>

                <!-- 时间 -->
                <div class="room-time">
                    <span>🕐 {{ formatTime(room.createTime) }}</span>
                </div>

                <!-- 状态 -->
                <div class="room-status" :class="getStatusType(room.status)">
                    {{ getStatusText(room.status) }}
                </div>

                <!-- 操作按钮 -->
                <div class="room-action">
                    <button 
                        class="btn-join" 
                        :class="getStatusType(room.status)"
                        :disabled="room.status !== 0 || room.currentPlayers >= room.maxPlayers"
                        @click="joinRoom(room)">
                        {{ room.status === 0 && room.currentPlayers < room.maxPlayers ? '加入' : 
                           room.currentPlayers >= room.maxPlayers ? '已满' : '游戏中' }}
                    </button>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.game-rooms {
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

.search-box {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 16px;
    background: #f5f5f5;
    border-radius: 10px;
    flex: 1;
    min-width: 200px;
    max-width: 300px;
}

.search-box input {
    border: none;
    background: transparent;
    outline: none;
    font-size: 15px;
    width: 100%;
}

.search-icon {
    font-size: 16px;
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

.btn-refresh, .btn-create {
    padding: 10px 20px;
    border: none;
    border-radius: 10px;
    font-size: 15px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.2s;
}

.btn-refresh {
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

.btn-create {
    background: #f5f5f5;
    color: #666;
}

.btn-create:hover {
    background: #e8e8e8;
}

.create-section {
    display: flex;
    align-items: center;
    gap: 12px;
}

.create-tip {
    font-size: 12px;
    color: #bbb;
}

/* 房间列表 */
.rooms-list {
    flex: 1;
    overflow: hidden;
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

/* 房间行 */
.room-row {
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

.room-row:hover {
    box-shadow: 0 4px 16px rgba(0,0,0,0.1);
}

.room-row.waiting {
    border-left-color: #10b981;
}

.room-row.playing {
    border-left-color: #f59e0b;
}

.room-row.ended {
    border-left-color: #9ca3af;
}

/* 游戏类型 */
.room-game {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 120px;
}

.game-icon {
    font-size: 28px;
}

.game-name {
    font-size: 16px;
    font-weight: 600;
    color: #333;
}

/* 房间码 */
.room-code {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 140px;
}

.room-code .label {
    font-size: 14px;
    color: #999;
}

.room-code .code {
    font-size: 18px;
    font-weight: 700;
    color: #667eea;
    letter-spacing: 2px;
}

.private-icon {
    font-size: 14px;
}

/* 房主 */
.room-creator {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 120px;
}

.room-creator .avatar {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    object-fit: cover;
    border: 2px solid #e5e7eb;
}

.room-creator .name {
    font-size: 15px;
    font-weight: 500;
    color: #333;
}

/* 人数 */
.room-players {
    font-size: 15px;
    color: #666;
    min-width: 80px;
}

/* 时间 */
.room-time {
    font-size: 14px;
    color: #999;
    min-width: 100px;
}

/* 状态 */
.room-status {
    padding: 6px 14px;
    border-radius: 16px;
    font-size: 14px;
    font-weight: 600;
    min-width: 70px;
    text-align: center;
}

.room-status.waiting {
    background: #d1fae5;
    color: #059669;
}

.room-status.playing {
    background: #fef3c7;
    color: #d97706;
}

.room-status.ended {
    background: #f3f4f6;
    color: #6b7280;
}

/* 操作按钮 */
.room-action {
    margin-left: auto;
}

.btn-join {
    padding: 10px 24px;
    border: none;
    border-radius: 8px;
    font-size: 15px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.2s;
}

.btn-join.waiting {
    background: #10b981;
    color: #fff;
}

.btn-join.waiting:hover:not(:disabled) {
    background: #059669;
}

.btn-join.playing, .btn-join:disabled {
    background: #e5e7eb;
    color: #9ca3af;
    cursor: not-allowed;
}

/* 响应式 */
@media (max-width: 900px) {
    .room-time {
        display: none;
    }
}

@media (max-width: 768px) {
    .filter-bar {
        gap: 8px;
    }

    .search-box {
        max-width: none;
        flex: 1 1 100%;
    }

    .filter-select {
        flex: 1;
        min-width: 100px;
    }

    .room-row {
        flex-wrap: wrap;
        gap: 10px;
    }

    .room-game {
        min-width: 80px;
    }

    .room-code {
        min-width: auto;
    }

    .room-creator {
        min-width: auto;
    }

    .room-players, .room-time {
        display: none;
    }

    .room-action {
        margin-left: 0;
        width: 100%;
    }

    .btn-join {
        width: 100%;
        padding: 10px;
    }
}

@media (max-width: 480px) {
    .game-rooms {
        padding: 12px;
        padding-bottom: 80px;
    }

    .page-header h2 {
        font-size: 18px;
    }

    .filter-bar {
        padding: 10px 12px;
    }

    .btn-refresh, .btn-create {
        padding: 8px 12px;
        font-size: 13px;
    }

    .room-row {
        padding: 10px 12px;
    }

    .game-icon {
        font-size: 18px;
    }

    .game-name {
        font-size: 13px;
    }

    .room-code .code {
        font-size: 14px;
    }

    .room-creator .avatar {
        width: 24px;
        height: 24px;
    }

    .room-creator .name {
        font-size: 13px;
    }
}
</style>
