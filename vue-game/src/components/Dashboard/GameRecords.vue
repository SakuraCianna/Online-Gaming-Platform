<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Icon } from '@iconify/vue'
import * as XLSX from 'xlsx'
import request from '../../config/api'
import { useUserStore } from '../../config/user'
import { formatTime, formatDuration } from '../../utils/time'

defineOptions({ name: 'GameRecords' })

// 游戏配置
const GAME_CONFIGS = {
    '2048': {
        name: '2048', icon: '🎯',
        statusMap: { 0: { text: '进行中', type: 'playing' }, 1: { text: '通关', type: 'win' }, 2: { text: '失败', type: 'lose' } },
        fields: [
            { key: 'score', label: '得分', format: v => v || 0 },
            { key: 'maxTile', label: '最大方块', format: v => v || 2 },
            { key: 'duration', label: '用时', format: v => formatDuration(v) }
        ]
    },
    'minesweeper': {
        name: '扫雷', icon: '💣',
        statusMap: { 0: { text: '进行中', type: 'playing' }, 1: { text: '胜利', type: 'win' }, 2: { text: '失败', type: 'lose' } },
        fields: [
            { key: 'difficulty', label: '难度', format: v => ({ beginner: '初级', intermediate: '中级', expert: '高级' }[v] || '未知') },
            { key: 'correctFlags', label: '标记', format: v => v || 0 },
            { key: 'duration', label: '用时', format: v => formatDuration(v) }
        ]
    },
    'gomoku': {
        name: '五子棋', icon: '⚫',
        statusMap: { win: { text: '胜利', type: 'win' }, lose: { text: '失败', type: 'lose' }, draw: { text: '平局', type: 'draw' } },
        fields: [
            { key: 'opponent', label: '对手', format: (v, r) => r?.opponentName || '玩家0' },
            { key: 'moveCount', label: '步数', format: v => v || 0 },
            { key: 'duration', label: '用时', format: v => formatDuration(v) }
        ]
    },
    'tank': {
        name: '坦克大战', icon: '🚗',
        statusMap: { 0: { text: '进行中', type: 'playing' }, 1: { text: '已结束', type: 'ended' } },
        fields: [
            { key: 'roomId', label: '房间', format: v => `#${v || 'N/A'}` },
            { key: 'duration', label: '时长', format: v => formatDuration(v) }
        ]
    }
}

const DEFAULT_CONFIG = {
    name: '未知', icon: '❓',
    statusMap: { default: { text: '未知', type: 'unknown' } },
    fields: []
}

const userStore = useUserStore()
const user = computed(() => userStore.user)

const selectedGame = ref('all')
const loading = ref(false)
const allRecords = ref([])
const dateRange = ref([])

// 游戏类型选项
const gameOptions = computed(() => [
    { value: 'all', label: '全部游戏', icon: '🎮' },
    ...Object.entries(GAME_CONFIGS).map(([k, v]) => ({ value: k, label: v.name, icon: v.icon }))
])

// 筛选后的记录
const filteredRecords = computed(() => {
    let records = allRecords.value
    if (selectedGame.value !== 'all') {
        records = records.filter(r => r.gameType === selectedGame.value)
    }
    if (dateRange.value?.length === 2) {
        const [start, end] = dateRange.value
        records = records.filter(r => {
            const d = new Date(r.createTime)
            return d >= start && d <= end
        })
    }
    // 按时间降序
    return [...records].sort((a, b) => new Date(b.createTime) - new Date(a.createTime))
})

const getGameConfig = type => GAME_CONFIGS[type] || DEFAULT_CONFIG
const getStatusInfo = record => {
    if (!record) return { text: '未知', type: 'unknown' }
    const config = getGameConfig(record.gameType)
    return config.statusMap?.[record.status] || { text: '未知', type: 'unknown' }
}

function exportToExcel() {
    if (!filteredRecords.value.length) return ElMessage.warning('暂无数据可导出')
    try {
        const data = filteredRecords.value.map(r => {
            const cfg = getGameConfig(r.gameType)
            const row = { '游戏': cfg.name, '状态': getStatusInfo(r).text, '时间': formatTime(r.createTime) }
            cfg.fields.forEach(f => { row[f.label] = f.format(r[f.key], r) })
            return row
        })
        const ws = XLSX.utils.json_to_sheet(data)
        ws['!cols'] = Object.keys(data[0]).map(() => ({ wch: 12 }))
        const wb = XLSX.utils.book_new()
        XLSX.utils.book_append_sheet(wb, ws, '游戏记录')
        XLSX.writeFile(wb, `游戏记录_${new Date().toISOString().slice(0, 10)}.xlsx`)
        ElMessage.success('导出成功')
    } catch { ElMessage.error('导出失败') }
}

// 处理五子棋记录
function processGomokuRecord(r, userId) {
    if (r.isDraw === 1) r.status = 'draw'
    else if (r.winnerId === userId) r.status = 'win'
    else if (r.loserId === userId) r.status = 'lose'
    else r.status = 'unknown'
    r.opponentName = `玩家${r.player1Id === userId ? r.player2Id : r.player1Id}`
}

// 加载记录
async function loadRecords() {
    if (!user.value?.id) return
    loading.value = true
    try {
        const res = await request.get('/record/all', { params: { userId: user.value.id, page: 1, size: 500 } })
        if (res.data.success && res.data.records) {
            const userId = user.value.id
            allRecords.value = res.data.records.map(r => {
                if (r.gameType === 'gomoku') processGomokuRecord(r, userId)
                return r
            })
        } else {
            allRecords.value = []
        }
    } catch {
        ElMessage.error('加载记录失败')
        allRecords.value = []
    } finally {
        loading.value = false
    }
}

onMounted(() => {
    if (user.value?.id) loadRecords()
})
</script>

<template>
    <div class="game-records">
        <div class="page-header">
            <h2>游戏记录</h2>
        </div>

        <!-- 筛选栏 -->
        <div class="filter-bar">
            <select v-model="selectedGame" class="filter-select game-select">
                <option v-for="opt in gameOptions" :key="opt.value" :value="opt.value">
                    {{ opt.icon }} {{ opt.label }}
                </option>
            </select>

            <div class="right-controls">
                <el-date-picker v-model="dateRange" type="daterange" range-separator="-"
                    start-placeholder="开始日期" end-placeholder="结束日期" size="default"
                    style="width: 240px" :clearable="false" />
                <button class="btn-action" @click="dateRange = []" v-if="dateRange?.length">
                    <Icon icon="mdi:refresh" /> 重置
                </button>
                <button class="btn-action" @click="exportToExcel">
                    <Icon icon="mdi:file-excel" /> 导出
                </button>
                <button class="btn-action" @click="loadRecords" :disabled="loading">
                    🔄 刷新
                </button>
            </div>
        </div>

        <!-- 记录列表 -->
        <div class="records-list" v-loading="loading">
            <div v-if="!filteredRecords.length && !loading" class="empty-state">
                <span>📋</span>
                <p>暂无游戏记录</p>
            </div>

            <div v-for="record in filteredRecords" :key="`${record.gameType}-${record.id}`" 
                class="record-row" :class="getStatusInfo(record).type">
                <div class="record-game">
                    <span class="game-icon">{{ getGameConfig(record.gameType).icon }}</span>
                    <span class="game-name">{{ getGameConfig(record.gameType).name }}</span>
                </div>

                <div class="record-status" :class="getStatusInfo(record).type">
                    {{ getStatusInfo(record).text }}
                </div>

                <div class="record-fields">
                    <div v-for="field in getGameConfig(record.gameType).fields" :key="field.key" class="field-item">
                        <span class="field-label">{{ field.label }}:</span>
                        <span class="field-value">{{ field.format(record[field.key], record) }}</span>
                    </div>
                </div>

                <div class="record-time">🕐 {{ formatTime(record.createTime) }}</div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.game-records {
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

.filter-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 14px 20px;
    background: #fff;
    border-radius: 12px;
    box-shadow: 0 2px 8px rgba(0,0,0,0.06);
    margin-bottom: 20px;
}

.filter-select {
    padding: 10px 16px;
    border: 1px solid #e0e0e0;
    border-radius: 10px;
    font-size: 15px;
    background: #fff;
    cursor: pointer;
}

.game-select {
    min-width: 160px;
}

.filter-select:focus {
    outline: none;
    border-color: #667eea;
}

.right-controls {
    display: flex;
    align-items: center;
    gap: 10px;
}

.btn-action {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 10px 16px;
    background: #f5f5f5;
    border: none;
    border-radius: 10px;
    font-size: 14px;
    color: #666;
    cursor: pointer;
    transition: all 0.2s;
}

.btn-action:hover:not(:disabled) {
    background: #e8e8e8;
}

.btn-action:disabled {
    opacity: 0.6;
    cursor: not-allowed;
}

.records-list {
    flex: 1;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 12px;
    scrollbar-width: none; /* Firefox */
    -ms-overflow-style: none; /* IE/Edge */
}

.records-list::-webkit-scrollbar {
    display: none; /* Chrome/Safari */
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

.record-row {
    display: flex;
    align-items: center;
    gap: 24px;
    padding: 16px 24px;
    background: #fff;
    border-radius: 12px;
    box-shadow: 0 2px 8px rgba(0,0,0,0.06);
    transition: all 0.2s;
    border-left: 4px solid transparent;
}

.record-row:hover {
    box-shadow: 0 4px 16px rgba(0,0,0,0.1);
}

.record-row.win { border-left-color: #10b981; }
.record-row.lose { border-left-color: #ef4444; }
.record-row.draw { border-left-color: #6b7280; }
.record-row.playing { border-left-color: #3b82f6; }
.record-row.ended { border-left-color: #9ca3af; }

.record-game {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 100px;
}

.game-icon { font-size: 26px; }

.game-name {
    font-size: 15px;
    font-weight: 600;
    color: #333;
}

.record-status {
    padding: 5px 12px;
    border-radius: 16px;
    font-size: 13px;
    font-weight: 600;
    min-width: 60px;
    text-align: center;
}

.record-status.win { background: #d1fae5; color: #059669; }
.record-status.lose { background: #fee2e2; color: #dc2626; }
.record-status.draw { background: #f3f4f6; color: #6b7280; }
.record-status.playing { background: #dbeafe; color: #2563eb; }
.record-status.ended { background: #f3f4f6; color: #6b7280; }

.record-fields {
    display: flex;
    align-items: center;
    gap: 20px;
    flex: 1;
}

.field-item {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;
}

.field-label { color: #999; }
.field-value { color: #333; font-weight: 600; }

.record-time {
    font-size: 13px;
    color: #999;
    min-width: 140px;
}

@media (max-width: 768px) {
    .filter-bar { flex-wrap: wrap; gap: 10px; padding: 12px 16px; }
    .game-select { flex: 1; min-width: 120px; }
    .right-controls { width: 100%; justify-content: flex-end; }
    .record-row { flex-wrap: wrap; gap: 12px; padding: 14px 16px; }
    .record-fields { width: 100%; flex-wrap: wrap; gap: 12px; }
    .record-time { display: none; }
}

@media (max-width: 480px) {
    .game-records { padding: 12px; padding-bottom: 80px; }
    .page-header h2 { font-size: 18px; }
    .btn-action { padding: 8px 12px; font-size: 12px; }
    .record-row { padding: 12px; }
    .game-icon { font-size: 20px; }
    .game-name { font-size: 13px; }
    .record-status { padding: 4px 10px; font-size: 12px; }
    .field-item { font-size: 12px; }
    .empty-state span { font-size: 48px; }
}
</style>
