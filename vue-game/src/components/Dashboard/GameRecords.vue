<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Icon } from '@iconify/vue'
import { useDebounceFn } from '@vueuse/core'
import * as XLSX from 'xlsx'
import request from '../../config/api'
import { useUserStore } from '../../config/user'
import { formatTime, formatDuration } from '../../utils/time'
import StatCard from './StatCard.vue'

defineOptions({
    name: 'GameRecords'
})

// 此处为新增游戏配置的地方
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

// 默认游戏配置（错误边界处理）
const DEFAULT_GAME_CONFIG = {
    name: '未知游戏',
    icon: '❓',
    color: '#9ca3af',
    statusMap: {
        default: { text: '未知', color: '#9ca3af', icon: 'mdi:help-circle' }
    },
    fields: [],
    sortOptions: [{ label: '按时间', value: 'createTime' }]
}

const userStore = useUserStore()
const user = computed(() => userStore.user)

const activeTab = ref('all')
const sortBy = ref('createTime')
const sortOrder = ref('desc')
const loading = ref(false)

// 游戏记录数据（从后端加载）
const allRecords = ref([])

// 高级筛选状态
const filterDrawer = ref(false)
const dateRange = ref([])
const statusFilter = ref('all') // all | 0 | 1 | 2 | win | lose | draw
const difficultyFilter = ref('all') // all | beginner | intermediate | expert

// 统计数据
const statistics = ref({
    total: 0,
    totalDuration: 0,
    byGame: {}
})

// 筛选后的记录
const filteredRecords = computed(() => {
    let records = allRecords.value || []

    // 按游戏类型筛选
    if (activeTab.value !== 'all') {
        records = records.filter(r => r.gameType === activeTab.value)
    }

    // 按日期范围筛选
    if (dateRange.value && dateRange.value.length === 2) {
        const [start, end] = dateRange.value
        records = records.filter(r => {
            const recordDate = new Date(r.createTime)
            return recordDate >= start && recordDate <= end
        })
    }

    // 按状态筛选
    if (statusFilter.value !== 'all') {
        records = records.filter(r => String(r.status) === String(statusFilter.value))
    }

    // 按难度筛选（仅扫雷）
    if (difficultyFilter.value !== 'all' && activeTab.value === 'minesweeper') {
        records = records.filter(r => r.difficulty === difficultyFilter.value)
    }

    // 排序
    records = sortRecords([...records])

    return records
})

// 当前激活游戏的排序选项
const currentSortOptions = computed(() => {
    if (activeTab.value === 'all') {
        return [{ label: '按时间', value: 'createTime' }]
    }
    const config = getGameConfig(activeTab.value)
    return config.sortOptions || []
})

// 当前游戏的状态选项（用于筛选）
const currentStatusOptions = computed(() => {
    if (activeTab.value === 'all') return []
    const config = getGameConfig(activeTab.value)
    return Object.entries(config.statusMap).map(([key, value]) => ({
        value: key,
        label: value.text
    }))
})

// 获取游戏配置（错误边界处理）
function getGameConfig(gameType) {
    return GAME_CONFIGS[gameType] || DEFAULT_GAME_CONFIG
}

// 获取状态信息（错误边界处理）
function getStatusInfo(record) {
    if (!record) return DEFAULT_GAME_CONFIG.statusMap.default
    const config = getGameConfig(record.gameType)
    return config.statusMap?.[record.status] || DEFAULT_GAME_CONFIG.statusMap.default
}

// 排序记录
function sortRecords(records) {
    return records.sort((a, b) => {
        const aVal = a[sortBy.value]
        const bVal = b[sortBy.value]

        // 处理null/undefined
        if (aVal === null || aVal === undefined) return 1
        if (bVal === null || bVal === undefined) return -1

        const comparison = aVal > bVal ? 1 : -1
        return sortOrder.value === 'desc' ? -comparison : comparison
    })
}

// 切换排序（带防抖）
const toggleSort = useDebounceFn((option) => {
    if (sortBy.value === option) {
        sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
    } else {
        sortBy.value = option
        sortOrder.value = 'desc'
    }
}, 300)

// 重置筛选条件
function resetFilters() {
    dateRange.value = []
    statusFilter.value = 'all'
    difficultyFilter.value = 'all'
}

// 应用筛选（带防抖）
const applyFilters = useDebounceFn(() => {
    filterDrawer.value = false
    ElMessage.success('筛选已应用')
}, 300)

function exportToExcel() {
    if (filteredRecords.value.length === 0) {
        ElMessage.warning('暂无数据可导出')
        return
    }

    try {
        // 准备导出数据
        const exportData = filteredRecords.value.map(record => {
            const config = getGameConfig(record.gameType)
            const statusInfo = getStatusInfo(record)

            const row = {
                '游戏类型': config.name,
                '状态': statusInfo.text,
                '创建时间': formatTime(record.createTime)
            }

            // 添加游戏特定字段
            for (const field of config.fields) {
                row[field.label] = field.format(record[field.key], record)
            }

            return row
        })

        // 创建工作表
        const ws = XLSX.utils.json_to_sheet(exportData)

        // 设置列宽
        const colWidths = Object.keys(exportData[0]).map(() => ({ wch: 15 }))
        ws['!cols'] = colWidths

        // 创建工作簿
        const wb = XLSX.utils.book_new()
        XLSX.utils.book_append_sheet(wb, ws, '游戏记录')

        // 导出文件
        const fileName = `游戏记录_${formatTime(new Date(), 'YYYYMMDD_HHmmss')}.xlsx`
        XLSX.writeFile(wb, fileName)

        ElMessage.success('导出成功')
    } catch (error) {
        console.error('导出失败:', error)
        ElMessage.error('导出失败，请重试')
    }
}

// 优化数据加载时机，避免阻塞首次渲染
onMounted(async () => {
    if (user.value?.id) {
        // 优先加载统计数据（数据量小）
        loadStatistics()

        // 使用 nextTick 和延迟加载记录列表，让页面先渲染
        await nextTick()
        setTimeout(() => {
            loadAllRecords()
        }, 50) // 给页面 50ms 的渲染时间
    }
})

// 监听activeTab变化（带防抖）
watch(activeTab, useDebounceFn((newTab) => {
    resetFilters()
    if (user.value?.id) {
        if (newTab === 'all') {
            loadAllRecords()
        } else {
            loadRecordsByGame(newTab)
        }
    }
}, 300))

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
            params: { userId: user.value.id, page: 1, size: 1000 }
        })

        if (response.data.success && response.data.records) {
            // 处理五子棋记录的特殊字段
            const records = response.data.records.map(record => {
                if (record.gameType === 'gomoku') {
                    processGomokuRecord(record)
                }
                return record
            })

            allRecords.value = records
        } else {
            allRecords.value = []
        }
    } catch (error) {
        console.error('加载全部记录失败:', error)
        ElMessage.error('加载全部记录失败')
        allRecords.value = []
    } finally {
        loading.value = false
    }
}

// 加载指定游戏的记录
async function loadRecordsByGame(gameType) {
    loading.value = true
    try {
        const response = await request.get(getApiPath(gameType), {
            params: { userId: user.value.id, page: 1, size: 1000 }
        })

        const hasRecords = response.data.success && response.data.records
        allRecords.value = hasRecords ? processRecords(response.data.records, gameType) : []
    } catch (error) {
        console.error('加载游戏记录失败:', error)
        ElMessage.error('加载游戏记录失败')
        allRecords.value = []
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

            <!-- 导出按钮 -->
            <button class="export-btn" @click="exportToExcel" title="导出为Excel">
                <Icon icon="mdi:file-excel" />
                <span>导出为Excel</span>
            </button>
        </div>

        <!-- 统计卡片 -->
        <div class="statistics-cards">
            <StatCard icon="mdi:gamepad-variant" :value="statistics.total" label="总游戏数" type="total" />

            <StatCard icon="mdi:clock-outline" :value="formatDuration(statistics.totalDuration)" label="总游戏时长"
                type="time" />

            <StatCard v-for="(data, gameType) in statistics.byGame" :key="gameType"
                :icon="GAME_CONFIGS[gameType]?.icon || '🎮'" :value="data.count"
                :label="GAME_CONFIGS[gameType]?.name || '未知游戏'" :color="GAME_CONFIGS[gameType]?.color"
                :extra="`胜率: ${data.count > 0 ? Math.round(data.wins / data.count * 100) : 0}%`" type="games" />
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

            <div class="right-controls">
                <!-- 高级筛选按钮 -->
                <button class="filter-btn" @click="filterDrawer = true" title="高级筛选">
                    <Icon icon="mdi:filter-variant" />
                    <span>筛选</span>
                </button>

                <!-- 排序控制 -->
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
        </div>

        <!-- 记录列表（虚拟滚动） -->
        <div class="records-list-wrapper" v-loading="loading">
            <!-- 空数据提示 -->
            <div v-if="filteredRecords.length === 0 && !loading" class="empty-state">
                <Icon icon="mdi:file-document-outline" class="empty-icon" />
                <p>{{ activeTab === 'all' ? '暂无任何游戏记录' : '暂无游戏记录' }}</p>
            </div>

            <!-- Grid 布局列表 -->
            <div v-else class="records-list">
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
                        <span class="time-text">{{ formatTime(record?.createTime) }}</span>
                    </div>
                </div>
            </div>
        </div>

        <!-- 高级筛选抽屉 -->
        <el-drawer v-model="filterDrawer" title="高级筛选" size="400px" direction="rtl">
            <div class="filter-content">
                <!-- 日期范围 -->
                <div class="filter-section">
                    <div class="filter-label">
                        <Icon icon="mdi:calendar-range" />
                        时间范围
                    </div>
                    <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期"
                        end-placeholder="结束日期" style="width: 100%" />
                </div>

                <!-- 游戏状态 -->
                <div class="filter-section" v-if="activeTab !== 'all' && currentStatusOptions.length > 0">
                    <div class="filter-label">
                        <Icon icon="mdi:state-machine" />
                        游戏状态
                    </div>
                    <el-select v-model="statusFilter" placeholder="选择状态" style="width: 100%">
                        <el-option label="全部状态" value="all" />
                        <el-option v-for="option in currentStatusOptions" :key="option.value" :label="option.label"
                            :value="option.value" />
                    </el-select>
                </div>

                <!-- 扫雷难度 -->
                <div class="filter-section" v-if="activeTab === 'minesweeper'">
                    <div class="filter-label">
                        <Icon icon="mdi:signal" />
                        游戏难度
                    </div>
                    <el-select v-model="difficultyFilter" placeholder="选择难度" style="width: 100%">
                        <el-option label="全部难度" value="all" />
                        <el-option label="初级" value="beginner" />
                        <el-option label="中级" value="intermediate" />
                        <el-option label="高级" value="expert" />
                    </el-select>
                </div>

                <!-- 操作按钮 -->
                <div class="filter-actions">
                    <el-button @click="resetFilters">重置</el-button>
                    <el-button type="primary" @click="applyFilters">应用筛选</el-button>
                </div>
            </div>
        </el-drawer>
    </div>
</template>

<style scoped>
.game-records {
    padding: 20px;
    max-width: 1400px;
    margin: 0 auto;
    height: 100%;
    overflow: hidden;
}

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
    position: relative;
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

.export-btn {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 16px;
    background: rgba(255, 255, 255, 0.2);
    border: 1px solid rgba(255, 255, 255, 0.3);
    border-radius: 8px;
    color: white;
    cursor: pointer;
    font-size: 14px;
    font-weight: 500;
    transition: all 0.3s ease;
}

.export-btn:hover {
    background: rgba(255, 255, 255, 0.3);
    transform: translateY(-2px);
}

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

.right-controls {
    display: flex;
    align-items: center;
    gap: 12px;
}

.filter-btn {
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

.filter-btn:hover {
    border-color: #667eea;
    color: #667eea;
    transform: translateY(-2px);
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

.records-list-wrapper {
    min-height: 400px;
    position: relative;
}

.records-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
    gap: 20px;
}

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

.record-card {
    background: white;
    border-radius: 12px;
    padding: 16px;
    margin-bottom: 12px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
    transition: all 0.3s ease;
    border-left: 4px solid var(--game-color, #667eea);
    position: relative;
    overflow: hidden;
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
    margin-bottom: 12px;
    padding-bottom: 10px;
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
    gap: 10px;
    margin-bottom: 12px;
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
    padding-top: 10px;
    border-top: 1px solid #f3f4f6;
    color: #9ca3af;
    font-size: 12px;
}

.time-icon {
    font-size: 14px;
}

.filter-content {
    padding: 20px;
}

.filter-section {
    margin-bottom: 24px;
}

.filter-label {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    font-weight: 600;
    color: #1f2937;
    margin-bottom: 12px;
}

.filter-actions {
    display: flex;
    gap: 12px;
    margin-top: 32px;
}

.filter-actions .el-button {
    flex: 1;
}

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

    .export-btn {
        position: absolute;
        top: 12px;
        right: 12px;
    }

    .statistics-cards {
        gap: 12px;
    }

    .controls {
        flex-direction: column;
        align-items: stretch;
    }

    .tabs {
        overflow-x: auto;
        -webkit-overflow-scrolling: touch;
    }

    .right-controls {
        width: 100%;
        justify-content: space-between;
    }

    .records-list {
        grid-template-columns: 1fr;
    }

    .record-body {
        grid-template-columns: 1fr;
    }
}

/* 移动端小屏幕适配 */
@media (max-width: 480px) {
    .game-records {
        padding: 12px;
        padding-bottom: 80px;
    }

    .records-header {
        padding: 12px;
        gap: 8px;
    }

    .title-icon {
        font-size: 20px;
    }

    .title {
        font-size: 16px;
    }

    .export-btn {
        padding: 6px 10px;
        font-size: 12px;
        top: 10px;
        right: 10px;
    }

    .export-btn span {
        display: none;
    }

    .statistics-cards {
        gap: 10px;
        margin-bottom: 20px;
    }

    .controls {
        gap: 12px;
        margin-bottom: 16px;
    }

    .tabs {
        gap: 6px;
        padding-bottom: 4px;
    }

    .tab-btn {
        padding: 8px 12px;
        font-size: 12px;
        white-space: nowrap;
    }

    .tab-icon {
        font-size: 14px;
    }

    .right-controls {
        gap: 8px;
    }

    .filter-btn {
        padding: 8px 12px;
        font-size: 12px;
    }

    .sort-controls {
        padding: 6px 10px;
    }

    .sort-select {
        font-size: 12px;
    }

    .sort-order-btn {
        width: 24px;
        height: 24px;
    }

    .records-list {
        gap: 12px;
    }

    .record-card {
        padding: 12px;
        border-radius: 10px;
    }

    .record-header {
        margin-bottom: 10px;
        padding-bottom: 8px;
    }

    .game-icon {
        font-size: 20px;
    }

    .game-name {
        font-size: 14px;
    }

    .status-badge {
        padding: 4px 8px;
        font-size: 11px;
    }

    .record-body {
        gap: 8px;
        margin-bottom: 10px;
    }

    .data-item {
        padding: 6px;
        font-size: 12px;
    }

    .data-icon {
        font-size: 14px;
    }

    .record-footer {
        padding-top: 8px;
        font-size: 11px;
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
