<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Icon } from '@iconify/vue'
import request from '../../config/api'
import { useUserStore } from '../../config/user'

// 游戏配置（遵循开闭原则）
// 新增游戏时，只需在此添加配置即可
const GAME_CONFIGS = {
    '2048': {
        name: '2048',
        icon: '🎯',
        color: '#f59e0b',
        apiEndpoint: '/api/game2048/records',
        statusMap: {
            0: { text: '进行中', color: '#3b82f6', icon: 'mdi:play-circle' },
            1: { text: '通关', color: '#10b981', icon: 'mdi:trophy' },
            2: { text: '失败', color: '#ef4444', icon: 'mdi:close-circle' }
        },
        fields: [
            { key: 'score', label: '得分', icon: 'mdi:star', format: (v) => v || 0 },
            { key: 'maxTile', label: '最大方块', icon: 'mdi:cube-outline', format: (v) => v || 2 },
            { key: 'movesCount', label: '移动次数', icon: 'mdi:gesture-swipe', format: (v) => v || 0 },
            { key: 'duration', label: '用时', icon: 'mdi:clock-outline', format: (v) => formatDuration(v) }
        ],
        sortOptions: [
            { label: '按时间', value: 'createTime' },
            { label: '按得分', value: 'score' },
            { label: '按方块', value: 'maxTile' }
        ]
    },
    'minesweeper': {
        name: '扫雷',
        icon: '💣',
        color: '#8b5cf6',
        apiEndpoint: '/api/minesweeper/records',
        statusMap: {
            0: { text: '进行中', color: '#3b82f6', icon: 'mdi:play-circle' },
            1: { text: '胜利', color: '#10b981', icon: 'mdi:trophy' },
            2: { text: '失败', color: '#ef4444', icon: 'mdi:bomb' }
        },
        fields: [
            {
                key: 'difficulty', label: '难度', icon: 'mdi:signal', format: (v) => {
                    const map = { beginner: '初级', intermediate: '中级', expert: '高级' }
                    return map[v] || v || '未知'
                }
            },
            { key: 'boardSize', label: '棋盘', icon: 'mdi:grid', format: (v, record) => `${record?.boardWidth || 0}×${record?.boardHeight || 0}` },
            { key: 'correctFlags', label: '正确标记', icon: 'mdi:flag', format: (v) => `${v || 0}` },
            { key: 'duration', label: '用时', icon: 'mdi:clock-outline', format: (v) => formatDuration(v) }
        ],
        sortOptions: [
            { label: '按时间', value: 'createTime' },
            { label: '按用时', value: 'duration' }
        ]
    },
    'gomoku': {
        name: '五子棋',
        icon: '⚫',
        color: '#06b6d4',
        apiEndpoint: '/api/gomoku/records',
        statusMap: {
            win: { text: '胜利', color: '#10b981', icon: 'mdi:trophy' },
            lose: { text: '失败', color: '#ef4444', icon: 'mdi:emoticon-sad-outline' },
            draw: { text: '平局', color: '#6b7280', icon: 'mdi:equal' }
        },
        fields: [
            { key: 'opponent', label: '对手', icon: 'mdi:account', format: (v, record) => record?.opponentName || 'AI玩家' },
            { key: 'moveCount', label: '步数', icon: 'mdi:counter', format: (v) => v || 0 },
            { key: 'duration', label: '用时', icon: 'mdi:clock-outline', format: (v) => formatDuration(v) }
        ],
        sortOptions: [
            { label: '按时间', value: 'createTime' },
            { label: '按步数', value: 'moveCount' }
        ]
    },
    'tank': {
        name: '坦克大战',
        icon: '🎮',
        color: '#ef4444',
        apiEndpoint: '/api/tank/records',
        statusMap: {
            0: { text: '进行中', color: '#3b82f6', icon: 'mdi:play-circle' },
            1: { text: '已结束', color: '#6b7280', icon: 'mdi:checkbox-marked-circle' }
        },
        fields: [
            { key: 'roomId', label: '房间ID', icon: 'mdi:door', format: (v) => `#${v || 'N/A'}` },
            { key: 'duration', label: '游戏时长', icon: 'mdi:clock-outline', format: (v) => formatDuration(v) }
        ],
        sortOptions: [
            { label: '按时间', value: 'startTime' },
            { label: '按时长', value: 'duration' }
        ]
    }
}

// ==================== 状态管理 ====================
const userStore = useUserStore()
const user = computed(() => userStore.user)

const activeTab = ref('all')
const sortBy = ref('createTime')
const sortOrder = ref('desc')
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const totalRecords = ref(0)

// 游戏记录数据（从后端加载）
const mockRecords = ref([])
// 统计数据
const statistics = ref({
    total: 0,
    totalDuration: 0,
    byGame: {}
})

// ==================== 计算属性 ====================
// 筛选后的记录（后端已处理，直接返回）
const filteredRecords = computed(() => {
    return mockRecords.value || []
})

// 当前激活游戏的排序选项
const currentSortOptions = computed(() => {
    if (activeTab.value === 'all') {
        return [{ label: '按时间', value: 'createTime' }]
    }
    return GAME_CONFIGS[activeTab.value]?.sortOptions || []
})

// ==================== 工具函数 ====================
// 格式化时长
function formatDuration(seconds) {
    if (!seconds) return '0秒'
    const hours = Math.floor(seconds / 3600)
    const minutes = Math.floor((seconds % 3600) / 60)
    const secs = seconds % 60

    if (hours > 0) {
        return `${hours}小时${minutes}分${secs}秒`
    } else if (minutes > 0) {
        return `${minutes}分${secs}秒`
    } else {
        return `${secs}秒`
    }
}

// 获取游戏配置
function getGameConfig(gameType) {
    return GAME_CONFIGS[gameType] || {}
}

// 获取状态信息
function getStatusInfo(record) {
    if (!record) return { text: '未知', color: '#9ca3af', icon: 'mdi:help-circle' }
    const config = getGameConfig(record.gameType)
    return config.statusMap?.[record.status] || { text: '未知', color: '#9ca3af', icon: 'mdi:help-circle' }
}

// 切换排序
function toggleSort(option) {
    if (sortBy.value === option) {
        sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
    } else {
        sortBy.value = option
        sortOrder.value = 'desc'
    }
}

// ==================== 生命周期 ====================
onMounted(() => {
    if (user.value?.id) {
        loadStatistics()
        // 初始化时加载"全部"Tab的数据
        if (activeTab.value === 'all') {
            loadAllRecords()
        }
    }
})

// 监听activeTab变化，切换游戏类型时加载对应记录
watch(activeTab, (newTab) => {
    if (newTab === 'all' && user.value?.id) {
        currentPage.value = 1
        loadAllRecords()
    } else if (newTab !== 'all' && user.value?.id) {
        currentPage.value = 1
        loadRecordsByGame(newTab)
    } else {
        mockRecords.value = []
        totalRecords.value = 0
    }
})

// 监听分页变化
watch(currentPage, (newPage) => {
    if (activeTab.value !== 'all' && user.value?.id) {
        loadRecordsByGame(activeTab.value, newPage)
    }
})

// 加载统计数据
async function loadStatistics() {
    try {
        const response = await request.get('/record/statistics', {
            params: { userId: user.value.id }
        })

        if (response.data.success && response.data.data) {
            statistics.value = response.data.data
        }
    } catch (error) {
        console.error('加载统计数据失败:', error)
        ElMessage.error('加载统计数据失败')
    }
}

// 获取API路径
function getApiPath(gameType) {
    return gameType === '2048' ? '/record/game2048' : `/record/${gameType}`
}

// 处理五子棋记录
function processGomokuRecord(record) {
    // 转换状态
    if (record.isDraw === 1) {
        record.status = 'draw'
    } else if (record.winnerId === user.value.id) {
        record.status = 'win'
    } else if (record.loserId === user.value.id) {
        record.status = 'lose'
    } else {
        record.status = 'unknown'
    }

    // 设置对手信息
    const opponentId = record.player1Id === user.value.id ? record.player2Id : record.player1Id
    record.opponentName = `玩家${opponentId}`
}

// 处理记录数据
function processRecords(records, gameType) {
    for (const record of records) {
        record.gameType = gameType
        if (gameType === 'gomoku') {
            processGomokuRecord(record)
        }
    }
    return records
}

// 加载所有游戏的记录（"全部"Tab - 后端统一查询）
async function loadAllRecords() {
    loading.value = true
    try {
        const response = await request.get('/record/all', {
            params: { userId: user.value.id, page: 1, size: 40 }
        })

        if (response.data.success && response.data.records) {
            // 处理五子棋记录的特殊字段
            const records = response.data.records.map(record => {
                if (record.gameType === 'gomoku') {
                    processGomokuRecord(record)
                }
                return record
            })

            mockRecords.value = records
            totalRecords.value = response.data.total || 0
        } else {
            mockRecords.value = []
            totalRecords.value = 0
        }
    } catch (error) {
        console.error('加载全部记录失败:', error)
        ElMessage.error('加载全部记录失败')
        mockRecords.value = []
        totalRecords.value = 0
    } finally {
        loading.value = false
    }
}

// 加载指定游戏的记录
async function loadRecordsByGame(gameType, page = 1) {
    loading.value = true
    try {
        const response = await request.get(getApiPath(gameType), {
            params: { userId: user.value.id, page, size: pageSize.value }
        })

        const hasRecords = response.data.success && response.data.records
        mockRecords.value = hasRecords ? processRecords(response.data.records, gameType) : []
        totalRecords.value = hasRecords ? (response.data.total || 0) : 0
    } catch (error) {
        console.error('加载游戏记录失败:', error)
        ElMessage.error('加载游戏记录失败')
        mockRecords.value = []
        totalRecords.value = 0
    } finally {
        loading.value = false
    }
}
</script>

<template>
    <div class="game-records">
        <!-- 页面标题 -->
        <div class="records-header">
            <Icon icon="mdi:history" class="title-icon" />
            <h2 class="title">游戏记录</h2>
            <span class="separator">|</span>
            <p class="subtitle">回顾你的游戏历程</p>
        </div>

        <!-- 统计卡片 -->
        <div class="statistics-cards">
            <div class="stat-card total">
                <Icon icon="mdi:gamepad-variant" class="stat-icon" />
                <div class="stat-content">
                    <div class="stat-value">{{ statistics.total }}</div>
                    <div class="stat-label">总游戏数</div>
                </div>
            </div>

            <div class="stat-card time">
                <Icon icon="mdi:clock-outline" class="stat-icon" />
                <div class="stat-content">
                    <div class="stat-value">{{ formatDuration(statistics.totalDuration) }}</div>
                    <div class="stat-label">总游戏时长</div>
                </div>
            </div>

            <div class="stat-card games" v-for="(data, gameType) in statistics.byGame" :key="gameType">
                <div class="stat-icon">{{ GAME_CONFIGS[gameType]?.icon }}</div>
                <div class="stat-content">
                    <div class="stat-value">{{ data.count }}</div>
                    <div class="stat-label">{{ GAME_CONFIGS[gameType]?.name }}</div>
                </div>
                <div class="stat-extra">胜率: {{ data.count > 0 ? Math.round(data.wins / data.count * 100) : 0 }}%</div>
            </div>
        </div>

        <!-- 筛选和排序 -->
        <div class="controls">
            <div class="tabs">
                <button class="tab-btn" :class="{ active: activeTab === 'all' }" @click="activeTab = 'all'">
                    <Icon icon="mdi:view-grid" />
                    全部
                </button>
                <button v-for="(config, key) in GAME_CONFIGS" :key="key" class="tab-btn"
                    :class="{ active: activeTab === key }" @click="activeTab = key"
                    :style="{ '--tab-color': config.color }">
                    <span class="tab-icon">{{ config.icon }}</span>
                    {{ config.name }}
                </button>
            </div>

            <div class="sort-controls">
                <Icon icon="mdi:sort" class="sort-icon" />
                <select v-model="sortBy" class="sort-select" aria-label="选择排序方式" title="选择排序方式">
                    <option v-for="option in currentSortOptions" :key="option.value" :value="option.value">
                        {{ option.label }}
                    </option>
                </select>
                <button class="sort-order-btn" @click="toggleSort(sortBy)"
                    :title="sortOrder === 'desc' ? '降序排列' : '升序排列'"
                    :aria-label="sortOrder === 'desc' ? '切换为升序' : '切换为降序'">
                    <Icon :icon="sortOrder === 'desc' ? 'mdi:arrow-down' : 'mdi:arrow-up'" />
                </button>
            </div>
        </div>

        <!-- 记录列表 -->
        <div class="records-list" v-loading="loading">
            <!-- 空数据提示 -->
            <div v-if="filteredRecords.length === 0 && !loading" class="empty-state">
                <Icon icon="mdi:file-document-outline" class="empty-icon" />
                <p>{{ activeTab === 'all' ? '暂无任何游戏记录' : '暂无游戏记录' }}</p>
            </div>

            <div v-for="record in filteredRecords"
                :key="`${record?.gameType || 'unknown'}-${record?.id || Math.random()}`" class="record-card"
                :style="{ '--game-color': getGameConfig(record?.gameType)?.color || '#667eea' }">
                <!-- 游戏类型标识 -->
                <div class="record-header">
                    <div class="game-badge">
                        <span class="game-icon">{{ getGameConfig(record?.gameType)?.icon || '🎮' }}</span>
                        <span class="game-name">{{ getGameConfig(record?.gameType)?.name || '未知游戏' }}</span>
                    </div>

                    <div class="status-badge" :style="{
                        backgroundColor: getStatusInfo(record)?.color + '20',
                        color: getStatusInfo(record)?.color,
                        borderColor: getStatusInfo(record)?.color
                    }">
                        <Icon :icon="getStatusInfo(record)?.icon" class="status-icon" />
                        {{ getStatusInfo(record)?.text }}
                    </div>
                </div>

                <!-- 游戏数据 -->
                <div class="record-body">
                    <div v-for="field in getGameConfig(record?.gameType)?.fields || []" :key="field.key"
                        class="data-item">
                        <Icon :icon="field.icon" class="data-icon" />
                        <span class="data-label">{{ field.label }}:</span>
                        <span class="data-value">{{ field.format(record?.[field.key], record) }}</span>
                    </div>
                </div>

                <!-- 时间信息 -->
                <div class="record-footer">
                    <Icon icon="mdi:calendar-clock" class="time-icon" />
                    <span class="time-text">{{ record?.createTime || '未知时间' }}</span>
                </div>
            </div>
        </div>

        <!-- 分页组件（"全部"Tab不显示分页，因为显示的是混合记录）-->
        <div v-if="activeTab !== 'all' && totalRecords > 0" class="pagination-wrapper">
            <el-pagination v-model:current-page="currentPage" :page-size="pageSize" :total="totalRecords"
                layout="total, prev, pager, next, jumper" :hide-on-single-page="false" background />
        </div>

        <!-- "全部"Tab提示 -->
        <div v-if="activeTab === 'all' && totalRecords > 0" class="all-tab-notice">
            <Icon icon="mdi:information" />
            <span>显示所有游戏的最近记录（共{{ totalRecords }}条），如需查看更多请选择具体游戏类型</span>
        </div>
    </div>
</template>

<style scoped>
.game-records {
    padding: 20px;
    max-width: 1400px;
    margin: 0 auto;
}

/* ==================== 页面标题 ==================== */
.records-header {
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

/* ==================== 统计卡片 ==================== */
.statistics-cards {
    display: flex;
    gap: 16px;
    margin-bottom: 30px;
    overflow-x: auto;
    padding-bottom: 8px;
    scroll-behavior: smooth;
}

.statistics-cards::-webkit-scrollbar {
    height: 6px;
}

.statistics-cards::-webkit-scrollbar-track {
    background: #f3f4f6;
    border-radius: 3px;
}

.statistics-cards::-webkit-scrollbar-thumb {
    background: #d1d5db;
    border-radius: 3px;
}

.statistics-cards::-webkit-scrollbar-thumb:hover {
    background: #9ca3af;
}

.stat-card {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 20px;
    background: white;
    border-radius: 12px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
    transition: all 0.3s ease;
    position: relative;
    overflow: hidden;
    min-width: 220px;
    flex-shrink: 0;
}

.stat-card::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    width: 4px;
    height: 100%;
    background: linear-gradient(to bottom, var(--stat-color, #667eea), transparent);
}

.stat-card.total {
    --stat-color: #667eea;
}

.stat-card.time {
    --stat-color: #f59e0b;
}

.stat-card.games {
    --stat-color: #10b981;
}

.stat-card:hover {
    transform: translateY(-4px);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

.stat-icon {
    font-size: 36px;
    color: var(--stat-color, #667eea);
}

.stat-content {
    flex: 1;
}

.stat-value {
    font-size: 24px;
    font-weight: 700;
    color: #1f2937;
    line-height: 1;
    margin-bottom: 4px;
}

.stat-label {
    font-size: 12px;
    color: #6b7280;
}

.stat-extra {
    font-size: 12px;
    color: #10b981;
    font-weight: 600;
    position: absolute;
    bottom: 8px;
    right: 12px;
}

/* ==================== 控制栏 ==================== */
.controls {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 20px;
    margin-bottom: 24px;
    flex-wrap: wrap;
}

.tabs {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
}

.tab-btn {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 10px 16px;
    background: white;
    border: 2px solid #e5e7eb;
    border-radius: 8px;
    cursor: pointer;
    font-size: 14px;
    font-weight: 500;
    color: #6b7280;
    transition: all 0.3s ease;
}

.tab-btn:hover {
    border-color: var(--tab-color, #667eea);
    color: var(--tab-color, #667eea);
    transform: translateY(-2px);
}

.tab-btn.active {
    background: var(--tab-color, #667eea);
    border-color: var(--tab-color, #667eea);
    color: white;
    box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.tab-icon {
    font-size: 18px;
}

.sort-controls {
    display: flex;
    align-items: center;
    gap: 8px;
    background: white;
    padding: 8px 12px;
    border-radius: 8px;
    border: 2px solid #e5e7eb;
}

.sort-icon {
    font-size: 18px;
    color: #6b7280;
}

.sort-select {
    border: none;
    background: none;
    font-size: 14px;
    color: #1f2937;
    cursor: pointer;
    outline: none;
    font-weight: 500;
}

.sort-order-btn {
    background: #f3f4f6;
    border: none;
    width: 28px;
    height: 28px;
    border-radius: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    color: #6b7280;
    transition: all 0.3s ease;
}

.sort-order-btn:hover {
    background: #e5e7eb;
    color: #1f2937;
}

/* ==================== 记录列表 ==================== */
.records-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
    gap: 20px;
}

/* 空数据状态 */
.empty-state {
    grid-column: 1 / -1;
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

/* ==================== 记录卡片 ==================== */
.record-card {
    background: white;
    border-radius: 12px;
    padding: 20px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
    transition: all 0.3s ease;
    border-left: 4px solid var(--game-color, #667eea);
    position: relative;
    overflow: hidden;
    animation: fadeInUp 0.4s ease-out;
}

.record-card::after {
    content: '';
    position: absolute;
    top: 0;
    right: 0;
    width: 100px;
    height: 100px;
    background: radial-gradient(circle, var(--game-color, #667eea) 0%, transparent 70%);
    opacity: 0.1;
    pointer-events: none;
}

.record-card:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.record-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    padding-bottom: 12px;
    border-bottom: 1px solid #f3f4f6;
}

.game-badge {
    display: flex;
    align-items: center;
    gap: 8px;
}

.game-icon {
    font-size: 24px;
}

.game-name {
    font-size: 16px;
    font-weight: 600;
    color: #1f2937;
}

.status-badge {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 6px 12px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 600;
    border: 1px solid;
}

.status-icon {
    font-size: 14px;
}

.record-body {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
    margin-bottom: 16px;
}

.data-item {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    padding: 8px;
    background: #f9fafb;
    border-radius: 8px;
    transition: all 0.2s ease;
}

.data-item:hover {
    background: #f3f4f6;
}

.data-icon {
    font-size: 16px;
    color: var(--game-color, #667eea);
    flex-shrink: 0;
}

.data-label {
    color: #6b7280;
    font-weight: 500;
}

.data-value {
    color: #1f2937;
    font-weight: 600;
    margin-left: auto;
}

.record-footer {
    display: flex;
    align-items: center;
    gap: 6px;
    padding-top: 12px;
    border-top: 1px solid #f3f4f6;
    color: #9ca3af;
    font-size: 12px;
}

.time-icon {
    font-size: 14px;
}

/* ==================== 分页组件 ==================== */
.pagination-wrapper {
    display: flex;
    justify-content: center;
    margin-top: 32px;
    padding: 20px 0;
}

/* "全部"Tab提示 */
.all-tab-notice {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    margin-top: 24px;
    padding: 12px 20px;
    background: #f0f9ff;
    border: 1px solid #bae6fd;
    border-radius: 8px;
    color: #0369a1;
    font-size: 13px;
}

/* ==================== 响应式 ==================== */
@media (max-width: 768px) {
    .records-header {
        flex-wrap: wrap;
        justify-content: flex-start;
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

    .statistics-cards {
        gap: 12px;
    }

    .stat-card {
        min-width: 180px;
    }

    .records-list {
        grid-template-columns: 1fr;
    }

    .controls {
        flex-direction: column;
        align-items: stretch;
    }

    .tabs {
        overflow-x: auto;
        -webkit-overflow-scrolling: touch;
    }

    .record-body {
        grid-template-columns: 1fr;
    }
}

/* ==================== 动画 ==================== */
@keyframes fadeInUp {
    from {
        opacity: 0;
        transform: translateY(20px);
    }

    to {
        opacity: 1;
        transform: translateY(0);
    }
}
</style>
