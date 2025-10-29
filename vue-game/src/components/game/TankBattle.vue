<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, inject } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { gsap } from 'gsap'
import request from '../../config/api'
import { useUserStore } from '../../config/user'
import { storeToRefs } from 'pinia'
import { Icon } from '@iconify/vue'
import { ElMessage } from 'element-plus'
import { useRoomStore } from '../../config/room'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { user, friends } = storeToRefs(userStore)

const roomStore = useRoomStore()
const roomInfo = computed(() => roomStore.currentRoom)

const wsService = inject('wsService')

// UI 状态
const boardRef = ref(null)
const inviting = ref({})
const inviteCooldown = ref({})
const cooldownTimer = ref(null)

// 游戏模式选择（默认值）
const selectedMode = ref('1v1v1v1')
const selectedMap = ref('classic')

const gameModes = [
    { value: '1v1v1v1', label: '混战模式', players: 4, description: '四人混战，各自为战' },
    { value: '2v2', label: '团队模式', players: 4, description: '两队对抗，配合作战' }
]

const mapOptions = [
    { value: 'classic', label: '经典地图', description: '标准对称地图' },
    { value: 'desert', label: '沙漠地图', description: '即将上线' },
    { value: 'forest', label: '森林地图', description: '即将上线' }
]

// 玩家座位（最多4人）
const seats = ref([
    { id: 1, player: null, team: 0 },
    { id: 2, player: null, team: 0 },
    { id: 3, player: null, team: 1 },
    { id: 4, player: null, team: 1 }
])

// 当前选择的模式配置
const currentModeConfig = computed(() => {
    return gameModes.find(m => m.value === selectedMode.value)
})

// 是否可以开始游戏
const canStartGame = computed(() => {
    const occupiedSeats = seats.value.filter(s => s.player !== null).length
    const required = currentModeConfig.value?.players || 4

    if (selectedMode.value === 'practice') {
        return occupiedSeats >= 1
    }
    return occupiedSeats >= required
})

// AI对手
function addAI(seatIndex) {
    if (seats.value[seatIndex].player) return

    seats.value[seatIndex].player = {
        id: `AI_${Date.now()}`,
        username: `AI ${seatIndex + 1}`,
        isAI: true,
        avatar: '/image/default-avatar.jpg'
    }

    ElMessage.success(`已添加 AI ${seatIndex + 1}`)
}

function removePlayer(seatIndex) {
    if (!seats.value[seatIndex].player) return

    // 不能移除自己
    if (seats.value[seatIndex].player.id === user.value?.id) {
        ElMessage.warning('不能移除自己')
        return
    }

    seats.value[seatIndex].player = null
    ElMessage.success('已移除玩家')
}

// 加载好友
async function loadFriends() {
    if (!user.value?.id) return
    try {
        const res = await request.get('/friend/getAllFriend', {
            params: { id: user.value.id, page: 1, pageSize: 9999 }
        })
        userStore.setFriends(res?.data?.data || [], res?.data?.total || 0)
    } catch (e) {
        userStore.setFriends([], 0)
        console.error('加载好友列表失败:', e)
    }
}

// 排序好友
const sortedFriends = computed(() => {
    const list = friends.value || []
    const score = (f) => {
        if (f?.online && (!f?.currentGame || f?.currentGame === '')) return 0
        if (f?.online) return 1
        return 2
    }
    return [...list].sort((a, b) => {
        const sa = score(a), sb = score(b)
        if (sa !== sb) return sa - sb
        const an = a.name || a.username || ''
        const bn = b.name || b.username || ''
        return an.localeCompare(bn, 'zh-CN')
    })
})

function statusText(f) {
    if (f?.online) {
        return f?.currentGame ? `在玩 ${f.currentGame}` : '在线'
    }
    return '离线'
}

// 检查邀请冷却
const cooldownTrigger = ref(0)

function isInviteCooldown(friendId) {
    // 访问 cooldownTrigger 以触发响应式更新
    // eslint-disable-next-line @typescript-eslint/no-unused-expressions
    cooldownTrigger.value
    const lastInviteTime = inviteCooldown.value[friendId]
    if (!lastInviteTime) return false
    const now = Date.now()
    const cooldownTime = 5000
    return (now - lastInviteTime) < cooldownTime
}

function getRemainingCooldown(friendId) {
    // 访问 cooldownTrigger 以触发响应式更新
    // eslint-disable-next-line @typescript-eslint/no-unused-expressions
    cooldownTrigger.value
    const lastInviteTime = inviteCooldown.value[friendId]
    if (!lastInviteTime) return 0
    const now = Date.now()
    const cooldownTime = 5000
    const remaining = cooldownTime - (now - lastInviteTime)
    return Math.max(0, Math.ceil(remaining / 1000))
}

function startCooldownTimer() {
    if (cooldownTimer.value) return
    cooldownTimer.value = setInterval(() => {
        cooldownTrigger.value++
        const now = Date.now()
        for (const friendId of Object.keys(inviteCooldown.value)) {
            if (now - inviteCooldown.value[friendId] >= 5000) {
                delete inviteCooldown.value[friendId]
            }
        }
        if (Object.keys(inviteCooldown.value).length === 0) {
            stopCooldownTimer()
        }
    }, 100)
}

function stopCooldownTimer() {
    if (cooldownTimer.value) {
        clearInterval(cooldownTimer.value)
        cooldownTimer.value = null
    }
}

async function inviteFriend(friend) {
    if (!friend?.id || !user.value?.id) return

    // 检查冷却时间
    if (isInviteCooldown(friend.id)) {
        const remaining = getRemainingCooldown(friend.id)
        ElMessage.warning(`请等待 ${remaining} 秒后再邀请该好友`)
        return
    }

    // 检查好友是否在线且空闲
    if (!friend.online) {
        ElMessage.warning('该好友当前不在线')
        return
    }

    if (friend.currentGame && friend.currentGame !== '') {
        ElMessage.warning('该好友正在游戏中，无法邀请')
        return
    }

    inviting.value[friend.id] = true
    try {
        const resp = await request.post('/room/invite', {
            userId: user.value.id,
            friendId: friend.id,
            roomCode: roomInfo.value?.roomCode,
            gameName: 'tank-battle'
        })

        if (resp?.data?.success || resp?.data?.code === 200) {
            ElMessage.success('邀请已发送，等待好友响应')
            // 设置冷却时间
            inviteCooldown.value[friend.id] = Date.now()
            // 启动冷却定时器
            startCooldownTimer()
        } else {
            ElMessage.error(resp?.data?.message || '邀请发送失败')
        }
    } catch (e) {
        ElMessage.error('邀请失败，请稍后重试')
        console.error('invite failed', e)
    } finally {
        inviting.value[friend.id] = false
    }
}

// 开始游戏
async function startGame() {
    if (!canStartGame.value) {
        ElMessage.warning('玩家人数不足')
        return
    }

    // 跳转到游戏页面，传递游戏配置
    router.push({
        path: '/game/tank-battle/play',
        query: {
            roomCode: roomInfo.value?.roomCode,
            mode: selectedMode.value,
            map: selectedMap.value
        }
    })
}

// ========== 调试功能（后续删除） ==========
function startDebugGame() {
    ElMessage.success('启动调试模式')
    router.push({
        path: '/game/tank-battle/play',
        query: {
            mode: '1v1v1v1',
            debug: 'true'  // 调试标记
        }
    })
}
// ========== 调试功能结束 ==========

// 返回游戏中心
function goBack() {
    router.push('/user/games')
}

// 动画
function runEnterAnimation() {
    gsap.from('.tank-battle-lobby', { opacity: 0, scale: 0.98, duration: 0.6, ease: 'power2.out' })
    gsap.from('.players-area', { scale: 0.95, opacity: 0, duration: 0.8, ease: 'power2.out', delay: 0.1 })
    gsap.from('.player-slot', { scale: 0.9, opacity: 0, duration: 0.6, ease: 'back.out(1.6)', stagger: 0.1, delay: 0.2 })
    gsap.from('.friend-card', { opacity: 0, x: -20, duration: 0.4, ease: 'power2.out', stagger: 0.05, delay: 0.3 })
}

onMounted(async () => {
    await nextTick()

    // 将当前用户放入第一个座位
    if (user.value) {
        seats.value[0].player = {
            id: user.value.id,
            username: user.value.username,
            avatar: user.value.avatar || '/image/default-avatar.jpg',
            isAI: false
        }
    }

    await loadFriends()
    runEnterAnimation()
})

onBeforeUnmount(() => {
    stopCooldownTimer()
})
</script>

<template>
    <div class="tank-battle-lobby">
        <!-- 装饰背景 -->
        <div class="bg-decoration">
            <div class="circle circle-1"></div>
            <div class="circle circle-2"></div>
            <div class="circle circle-3"></div>
        </div>

        <!-- 顶部导航栏 -->
        <header class="lobby-header">
            <button class="btn-back" @click="goBack">
                <Icon icon="mdi:arrow-left" width="18" />
                返回大厅
            </button>

            <div class="room-badge">
                <Icon icon="mdi:shield-account" width="20" />
                <span>房间 {{ roomInfo?.roomCode || '----' }}</span>
            </div>

            <button class="btn-rules">
                <Icon icon="mdi:help-circle-outline" width="18" />
                规则
                <div class="rules-dropdown">
                    <div class="rules-title">⚔️ 游戏规则</div>
                    <div class="rules-grid">
                        <div class="rule-item">
                            <Icon icon="mdi:keyboard" width="16" /> WASD 移动
                        </div>
                        <div class="rule-item">
                            <Icon icon="mdi:target" width="16" /> K键 射击
                        </div>
                        <div class="rule-item">
                            <Icon icon="mdi:heart" width="16" /> 血量 300
                        </div>
                        <div class="rule-item">
                            <Icon icon="mdi:fire" width="16" /> 最多10弹
                        </div>
                    </div>
                </div>
            </button>
        </header>

        <!-- 主要内容区 -->
        <div class="lobby-content">
            <!-- 左侧：配置区 -->
            <aside class="config-panel">
                <div class="panel-card">
                    <div class="card-title">
                        <Icon icon="mdi:cog-outline" width="18" />
                        游戏设置
                    </div>

                    <!-- 模式选择 -->
                    <div class="mode-selector">
                        <button v-for="mode in gameModes" :key="mode.value"
                            :class="['mode-chip', { active: selectedMode === mode.value }]"
                            @click="selectedMode = mode.value">
                            <span class="mode-name">{{ mode.label }}</span>
                            <span class="mode-info">{{ mode.description }}</span>
                        </button>
                    </div>

                    <!-- 地图选择 -->
                    <div class="map-selector">
                        <div class="selector-label">选择地图</div>
                        <div class="map-grid">
                            <button v-for="map in mapOptions" :key="map.value"
                                :class="['map-card', { active: selectedMap === map.value, disabled: map.value !== 'classic' }]"
                                @click="selectedMap = map.value" :disabled="map.value !== 'classic'">
                                <Icon icon="mdi:map-marker-radius" width="24" />
                                <span>{{ map.label }}</span>
                                <span v-if="map.value !== 'classic'" class="soon-tag">Soon</span>
                            </button>
                        </div>
                    </div>
                </div>

                <!-- 好友邀请区 -->
                <div class="panel-card friends-panel">
                    <div class="card-title">
                        <Icon icon="mdi:account-multiple-outline" width="18" />
                        邀请好友
                    </div>
                    <div class="friends-list">
                        <div v-for="friend in sortedFriends.slice(0, 4)" :key="friend.id"
                            :class="['friend-card', { offline: !friend.online }]">
                            <img :src="friend.avatar || '/image/default-avatar.jpg'" class="friend-img" />
                            <div class="friend-details">
                                <span class="friend-username">{{ friend.username }}</span>
                                <span class="friend-state">{{ statusText(friend) }}</span>
                            </div>
                            <button class="btn-invite"
                                :disabled="!friend.online || friend.currentGame || inviting[friend.id] || isInviteCooldown(friend.id)"
                                @click="inviteFriend(friend)">
                                <template v-if="isInviteCooldown(friend.id)">
                                    {{ getRemainingCooldown(friend.id) }}
                                </template>
                                <template v-else-if="inviting[friend.id]">
                                    <Icon icon="mdi:loading" class="spin" width="14" />
                                </template>
                                <template v-else>
                                    <Icon icon="mdi:plus" width="14" />
                                </template>
                            </button>
                        </div>
                        <div v-if="sortedFriends.length === 0" class="no-data">
                            <Icon icon="mdi:account-off-outline" width="32" />
                            <span>暂无在线好友</span>
                        </div>
                    </div>
                </div>
            </aside>

            <!-- 中间：玩家席位 -->
            <main class="players-area">
                <div class="area-header">
                    <h2>准备区域</h2>
                    <span class="player-count">{{seats.filter(s => s.player).length}}/{{ currentModeConfig?.players ||
                        4 }}</span>
                </div>

                <div :class="['seats-layout', { 'mode-2v2': selectedMode === '2v2' }]">
                    <div v-for="(seat, index) in seats" :key="seat.id"
                        v-show="selectedMode !== 'practice' || index === 0" :class="['player-slot', {
                            filled: seat.player,
                            'team-a': selectedMode === '2v2' && seat.team === 0,
                            'team-b': selectedMode === '2v2' && seat.team === 1
                        }]">

                        <template v-if="seat.player">
                            <div class="slot-content">
                                <img :src="seat.player.avatar" class="player-img" />
                                <div class="player-info">
                                    <span class="player-username">{{ seat.player.username }}</span>
                                    <span class="player-badge" v-if="seat.player.isAI">🤖 AI</span>
                                    <span class="player-badge me" v-else-if="seat.player.id === user?.id">👤 你</span>
                                </div>
                                <button v-if="seat.player.id !== user?.id" class="btn-remove"
                                    @click="removePlayer(index)">
                                    <Icon icon="mdi:close" width="14" />
                                </button>
                            </div>
                        </template>

                        <template v-else>
                            <div class="slot-empty">
                                <Icon icon="mdi:account-plus-outline" width="32" />
                                <span>等待玩家</span>
                                <button class="btn-add-ai" @click="addAI(index)">
                                    添加 AI
                                </button>
                            </div>
                        </template>
                    </div>
                </div>

                <!-- 开始按钮 -->
                <button class="btn-start-game" :disabled="!canStartGame" @click="startGame">
                    <Icon icon="mdi:rocket-launch" width="20" />
                    <span>{{canStartGame ? '开始战斗' : `等待玩家 (${seats.filter(s =>
                        s.player).length}/${currentModeConfig?.players})`
                        }}</span>
                </button>

                <!-- ========== 调试按钮（后续删除） ========== -->
                <button class="btn-debug-game" @click="startDebugGame">
                    <Icon icon="mdi:bug" width="20" />
                    <span>🔧 调试模式（单人测试）</span>
                </button>
                <!-- ========== 调试按钮结束 ========== -->
            </main>

            <!-- 右侧：战斗预览/统计 -->
            <aside class="stats-panel">
                <div class="panel-card battle-preview">
                    <div class="card-title">
                        <Icon icon="mdi:trophy-outline" width="18" />
                        战斗信息
                    </div>
                    <div class="preview-content">
                        <div class="stat-row">
                            <span class="stat-label">游戏模式</span>
                            <span class="stat-value">{{ currentModeConfig?.label }}</span>
                        </div>
                        <div class="stat-row">
                            <span class="stat-label">战斗地图</span>
                            <span class="stat-value">{{mapOptions.find(m => m.value === selectedMap)?.label}}</span>
                        </div>
                        <div class="stat-row">
                            <span class="stat-label">玩家数量</span>
                            <span class="stat-value">{{seats.filter(s => s.player).length}}/{{
                                currentModeConfig?.players
                                }}</span>
                        </div>
                    </div>
                </div>

                <!-- 提示卡片 -->
                <div class="panel-card tips-card">
                    <div class="card-title">
                        <Icon icon="mdi:lightbulb-outline" width="18" />
                        游戏提示
                    </div>
                    <div class="tips-content">
                        <div class="tip-item">
                            <Icon icon="mdi:gamepad-variant" width="16" />
                            <span>使用 WASD 控制坦克移动</span>
                        </div>
                        <div class="tip-item">
                            <Icon icon="mdi:target" width="16" />
                            <span>按 K 键发射炮弹攻击</span>
                        </div>
                        <div class="tip-item">
                            <Icon icon="mdi:shield-half-full" width="16" />
                            <span>炮弹可反弹，利用地形</span>
                        </div>
                        <div class="tip-item">
                            <Icon icon="mdi:trophy-variant" width="16" />
                            <span>击杀敌人获得 100 分</span>
                        </div>
                    </div>
                </div>
            </aside>
        </div>
    </div>
</template>

<style scoped>
/* ===== 全局容器 ===== */
.tank-battle-lobby {
    height: 100vh;
    width: 100vw;
    overflow: hidden;
    background: linear-gradient(135deg, #0a0e27 0%, #1a1f3a 50%, #0f1419 100%);
    color: #fff;
    display: flex;
    flex-direction: column;
    position: relative;
}

/* ===== 装饰背景 ===== */
.bg-decoration {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    pointer-events: none;
    z-index: 0;
}

.circle {
    position: absolute;
    border-radius: 50%;
    filter: blur(80px);
    opacity: 0.3;
    animation: float 20s infinite;
}

.circle-1 {
    width: 400px;
    height: 400px;
    background: radial-gradient(circle, rgba(88, 101, 242, 0.4), transparent);
    top: -200px;
    left: -100px;
    animation-delay: 0s;
}

.circle-2 {
    width: 300px;
    height: 300px;
    background: radial-gradient(circle, rgba(214, 48, 167, 0.3), transparent);
    bottom: -150px;
    right: -100px;
    animation-delay: 7s;
}

.circle-3 {
    width: 350px;
    height: 350px;
    background: radial-gradient(circle, rgba(59, 130, 246, 0.3), transparent);
    top: 50%;
    right: 10%;
    animation-delay: 14s;
}

@keyframes float {

    0%,
    100% {
        transform: translateY(0) scale(1);
    }

    50% {
        transform: translateY(-30px) scale(1.1);
    }
}

/* ===== 顶部导航栏 ===== */
.lobby-header {
    position: relative;
    z-index: 10;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px 24px;
    background: rgba(255, 255, 255, 0.05);
    backdrop-filter: blur(10px);
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.btn-back {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px;
    background: rgba(255, 255, 255, 0.08);
    border: 1px solid rgba(255, 255, 255, 0.15);
    border-radius: 8px;
    color: #fff;
    font-size: 14px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.3s;
}

.btn-back:hover {
    background: rgba(255, 255, 255, 0.12);
    transform: translateX(-4px);
    border-color: rgba(255, 255, 255, 0.25);
}

.room-badge {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 20px;
    background: linear-gradient(135deg, rgba(88, 101, 242, 0.2), rgba(88, 101, 242, 0.1));
    border: 1px solid rgba(88, 101, 242, 0.3);
    border-radius: 20px;
    font-size: 14px;
    font-weight: 700;
    letter-spacing: 0.5px;
}

.btn-rules {
    position: relative;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px;
    background: rgba(255, 255, 255, 0.08);
    border: 1px solid rgba(255, 255, 255, 0.15);
    border-radius: 8px;
    color: #fff;
    font-size: 14px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.3s;
}

.btn-rules:hover {
    background: rgba(255, 255, 255, 0.12);
}

.btn-rules:hover .rules-dropdown {
    opacity: 1;
    visibility: visible;
    transform: translateY(0);
}

.rules-dropdown {
    position: absolute;
    top: calc(100% + 8px);
    right: 0;
    width: 280px;
    padding: 16px;
    background: rgba(10, 14, 39, 0.95);
    backdrop-filter: blur(20px);
    border: 1px solid rgba(255, 255, 255, 0.15);
    border-radius: 12px;
    opacity: 0;
    visibility: hidden;
    transform: translateY(-10px);
    transition: all 0.3s;
    box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
    z-index: 100;
}

.rules-dropdown:hover {
    opacity: 1;
    visibility: visible;
    transform: translateY(0);
}

.rules-title {
    font-size: 15px;
    font-weight: 700;
    margin-bottom: 12px;
    text-align: center;
    color: #ffd700;
}

.rules-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
}

.rule-item {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px;
    background: rgba(255, 255, 255, 0.05);
    border-radius: 6px;
    font-size: 12px;
}

/* ===== 主内容区 ===== */
.lobby-content {
    position: relative;
    z-index: 1;
    flex: 1;
    display: grid;
    grid-template-columns: 280px 1fr 300px;
    gap: 20px;
    padding: 20px 24px 24px;
    overflow: hidden;
}

/* ===== 左侧配置面板 ===== */
.config-panel {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.panel-card {
    background: rgba(255, 255, 255, 0.06);
    backdrop-filter: blur(10px);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 16px;
    padding: 16px;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
}

.card-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    font-weight: 700;
    margin-bottom: 14px;
    color: #a5b4fc;
}

/* 模式选择 */
.mode-selector {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.mode-chip {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 12px;
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 10px;
    cursor: pointer;
    transition: all 0.3s;
    text-align: left;
}

.mode-chip:hover {
    background: rgba(255, 255, 255, 0.08);
    border-color: rgba(255, 255, 255, 0.2);
    transform: translateX(4px);
}

.mode-chip.active {
    background: linear-gradient(135deg, rgba(88, 101, 242, 0.3), rgba(88, 101, 242, 0.2));
    border-color: rgba(88, 101, 242, 0.5);
    box-shadow: 0 0 20px rgba(88, 101, 242, 0.3);
}

.mode-name {
    font-size: 13px;
    font-weight: 700;
    color: #fff;
}

.mode-info {
    font-size: 11px;
    color: rgba(255, 255, 255, 0.6);
}

/* 地图选择 */
.map-selector {
    margin-top: 12px;
}

.selector-label {
    font-size: 12px;
    color: rgba(255, 255, 255, 0.6);
    margin-bottom: 10px;
    text-transform: uppercase;
    letter-spacing: 0.5px;
}

.map-grid {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.map-card {
    position: relative;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 12px;
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 8px;
    color: #fff;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.3s;
}

.map-card:hover:not(.disabled) {
    background: rgba(255, 255, 255, 0.08);
    border-color: rgba(255, 255, 255, 0.2);
}

.map-card.active {
    background: linear-gradient(135deg, rgba(34, 197, 94, 0.3), rgba(34, 197, 94, 0.2));
    border-color: rgba(34, 197, 94, 0.5);
}

.map-card.disabled {
    opacity: 0.4;
    cursor: not-allowed;
}

.soon-tag {
    margin-left: auto;
    padding: 2px 8px;
    background: rgba(239, 68, 68, 0.2);
    border: 1px solid rgba(239, 68, 68, 0.3);
    border-radius: 10px;
    font-size: 10px;
    color: #fca5a5;
}

/* 好友面板 */
.friends-panel {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-height: 0;
}

.friends-list {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.friend-card {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px;
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-radius: 10px;
    transition: all 0.3s;
}

.friend-card:hover {
    background: rgba(255, 255, 255, 0.08);
    border-color: rgba(255, 255, 255, 0.15);
}

.friend-card.offline {
    opacity: 0.5;
}

.friend-img {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    object-fit: cover;
    border: 2px solid rgba(255, 255, 255, 0.2);
}

.friend-details {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
}

.friend-username {
    font-size: 13px;
    font-weight: 600;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.friend-state {
    font-size: 11px;
    color: rgba(255, 255, 255, 0.5);
}

.btn-invite {
    width: 28px;
    height: 28px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, rgba(88, 101, 242, 0.3), rgba(88, 101, 242, 0.2));
    border: 1px solid rgba(88, 101, 242, 0.4);
    border-radius: 6px;
    color: #fff;
    cursor: pointer;
    transition: all 0.3s;
}

.btn-invite:hover:not(:disabled) {
    background: linear-gradient(135deg, rgba(88, 101, 242, 0.5), rgba(88, 101, 242, 0.3));
    box-shadow: 0 0 15px rgba(88, 101, 242, 0.4);
}

.btn-invite:disabled {
    opacity: 0.4;
    cursor: not-allowed;
}

.spin {
    animation: spin 1s linear infinite;
}

@keyframes spin {
    to {
        transform: rotate(360deg);
    }
}

.no-data {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 30px 10px;
    color: rgba(255, 255, 255, 0.3);
    font-size: 12px;
}

/* ===== 中间玩家区域 ===== */
.players-area {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.area-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 8px;
}

.area-header h2 {
    margin: 0;
    font-size: 24px;
    font-weight: 800;
    background: linear-gradient(135deg, #fff, #a5b4fc);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
}

.player-count {
    padding: 6px 14px;
    background: rgba(88, 101, 242, 0.2);
    border: 1px solid rgba(88, 101, 242, 0.3);
    border-radius: 20px;
    font-size: 13px;
    font-weight: 700;
}

/* 座位布局 */
.seats-layout {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 14px;
    flex: 1;
}

.player-slot {
    background: rgba(255, 255, 255, 0.04);
    backdrop-filter: blur(10px);
    border: 2px solid rgba(255, 255, 255, 0.1);
    border-radius: 16px;
    padding: 16px;
    transition: all 0.3s;
    position: relative;
    overflow: hidden;
}

.player-slot::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: linear-gradient(135deg, transparent, rgba(255, 255, 255, 0.05));
    opacity: 0;
    transition: opacity 0.3s;
}

.player-slot:hover::before {
    opacity: 1;
}

.player-slot.filled {
    border-color: rgba(34, 197, 94, 0.4);
    background: rgba(34, 197, 94, 0.05);
}

.player-slot.team-a {
    border-color: rgba(239, 68, 68, 0.4);
    background: rgba(239, 68, 68, 0.05);
}

.player-slot.team-b {
    border-color: rgba(59, 130, 246, 0.4);
    background: rgba(59, 130, 246, 0.05);
}

/* 已填充的座位 */
.slot-content {
    position: relative;
    display: flex;
    align-items: center;
    gap: 12px;
}

.player-img {
    width: 50px;
    height: 50px;
    border-radius: 12px;
    object-fit: cover;
    border: 2px solid rgba(255, 255, 255, 0.3);
}

.player-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.player-username {
    font-size: 15px;
    font-weight: 700;
}

.player-badge {
    display: inline-block;
    padding: 4px 10px;
    background: rgba(255, 255, 255, 0.1);
    border-radius: 10px;
    font-size: 11px;
    font-weight: 600;
    align-self: flex-start;
}

.player-badge.me {
    background: linear-gradient(135deg, rgba(34, 197, 94, 0.3), rgba(34, 197, 94, 0.2));
    border: 1px solid rgba(34, 197, 94, 0.4);
}

.btn-remove {
    position: absolute;
    top: 8px;
    right: 8px;
    width: 24px;
    height: 24px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(239, 68, 68, 0.3);
    border: 1px solid rgba(239, 68, 68, 0.4);
    border-radius: 6px;
    color: #fff;
    cursor: pointer;
    transition: all 0.3s;
}

.btn-remove:hover {
    background: rgba(239, 68, 68, 0.5);
    transform: rotate(90deg);
}

/* 空座位 */
.slot-empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 10px;
    padding: 20px;
    color: rgba(255, 255, 255, 0.3);
}

.btn-add-ai {
    padding: 6px 16px;
    background: rgba(88, 101, 242, 0.2);
    border: 1px solid rgba(88, 101, 242, 0.3);
    border-radius: 8px;
    color: #fff;
    font-size: 12px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.3s;
}

.btn-add-ai:hover {
    background: rgba(88, 101, 242, 0.3);
    box-shadow: 0 0 15px rgba(88, 101, 242, 0.3);
}

/* 开始按钮 */
.btn-start-game {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    padding: 16px;
    background: linear-gradient(135deg, #22c55e, #16a34a);
    border: none;
    border-radius: 12px;
    color: #fff;
    font-size: 16px;
    font-weight: 700;
    cursor: pointer;
    transition: all 0.3s;
    box-shadow: 0 4px 20px rgba(34, 197, 94, 0.4);
}

.btn-start-game:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 6px 30px rgba(34, 197, 94, 0.6);
}

.btn-start-game:disabled {
    background: rgba(255, 255, 255, 0.1);
    cursor: not-allowed;
    box-shadow: none;
    opacity: 0.6;
}

/* ========== 调试按钮样式（后续删除） ========== */
.btn-debug-game {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    padding: 12px;
    background: linear-gradient(135deg, #f97316, #ea580c);
    border: 2px dashed #fff;
    border-radius: 12px;
    color: #fff;
    font-size: 14px;
    font-weight: 700;
    cursor: pointer;
    transition: all 0.3s;
    box-shadow: 0 4px 20px rgba(249, 115, 22, 0.4);
    margin-top: 10px;
}

.btn-debug-game:hover {
    transform: translateY(-2px) scale(1.02);
    box-shadow: 0 6px 30px rgba(249, 115, 22, 0.6);
}

.btn-debug-game:active {
    transform: translateY(0) scale(0.98);
}

/* ========== 调试按钮样式结束 ========== */

/* ===== 右侧统计面板 ===== */
.stats-panel {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.battle-preview,
.tips-card {
    flex: 1;
}

.preview-content {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.stat-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 12px;
    background: rgba(255, 255, 255, 0.05);
    border-radius: 8px;
}

.stat-label {
    font-size: 12px;
    color: rgba(255, 255, 255, 0.6);
}

.stat-value {
    font-size: 13px;
    font-weight: 700;
    color: #fff;
}

.tips-content {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.tip-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px;
    background: rgba(255, 255, 255, 0.05);
    border-radius: 8px;
    font-size: 12px;
    line-height: 1.4;
}

/* ===== 响应式 ===== */
@media (max-width: 1400px) {
    .lobby-content {
        grid-template-columns: 260px 1fr 280px;
        gap: 16px;
    }
}

@media (max-width: 1200px) {
    .lobby-content {
        grid-template-columns: 1fr;
        grid-template-rows: auto 1fr;
    }

    .config-panel {
        flex-direction: row;
    }

    .stats-panel {
        display: none;
    }
}

@media (max-width: 768px) {
    .lobby-header {
        padding: 12px 16px;
    }

    .lobby-content {
        padding: 16px;
        gap: 12px;
    }

    .seats-layout {
        grid-template-columns: 1fr;
    }
}
</style>
