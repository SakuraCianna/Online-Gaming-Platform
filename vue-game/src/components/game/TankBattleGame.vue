<template>
    <div class="tank-battle-page">
        <div class="tank-battle-container">
            <div class="game-wrapper">
                <v-stage ref="stageRef" :config="stageConfig" @mousedown="handleStageClick"
                    @touchstart="handleStageClick">
                    <!-- 静态层（背景+墙壁，不频繁重绘） -->
                    <v-layer ref="staticLayer">
                        <v-rect :config="backgroundConfig" />
                        <v-rect :config="borderConfig" />
                        <v-rect v-for="(wall, index) in walls" :key="'wall-' + index" :config="wall" />
                    </v-layer>

                    <!-- 履带痕迹层 -->
                    <v-layer ref="trackLayer">
                        <v-line v-for="track in trackMarks" :key="'track-' + track.id" :config="{
                            points: track.points,
                            stroke: track.color,
                            strokeWidth: 4,
                            opacity: track.opacity,
                            lineCap: 'round',
                            lineJoin: 'round'
                        }" />
                    </v-layer>

                    <!-- 动态层（坦克+子弹+特效） -->
                    <v-layer ref="dynamicLayer">
                        <!-- 坦克 -->
                        <v-group v-for="tank in tanks" :key="'tank-' + tank.playerId" :config="{
                            x: tank.x,
                            y: tank.y,
                            rotation: tank.rotation,
                            visible: !tank.isDead,
                            opacity: tank.flashOpacity || 1
                        }">
                            <!-- 坦克主体 -->
                            <v-rect :config="{
                                x: -GAME_CONFIG.tank.bodySize / 2,
                                y: -GAME_CONFIG.tank.bodySize / 2,
                                width: GAME_CONFIG.tank.bodySize,
                                height: GAME_CONFIG.tank.bodySize,
                                fill: getColorHex(tank.color),
                                stroke: '#000000',
                                strokeWidth: 2
                            }" />

                            <!-- 炮管 -->
                            <v-rect :config="{
                                x: 1,
                                y: -GAME_CONFIG.tank.barrelWidth / 2,
                                width: GAME_CONFIG.tank.barrelLength,
                                height: GAME_CONFIG.tank.barrelWidth,
                                fill: getColorHex(tank.color),
                                stroke: '#000000',
                                strokeWidth: 1
                            }" />

                        </v-group>

                        <!-- 子弹 -->
                        <v-circle v-for="bullet in allBullets" :key="'bullet-' + bullet.id" :config="{
                            x: bullet.x,
                            y: bullet.y,
                            radius: GAME_CONFIG.bullet.radius,
                            fill: '#000000',
                            stroke: '#333333',
                            strokeWidth: 1,
                            opacity: bullet.opacity
                        }" />

                        <!-- 爆炸特效 -->
                        <v-circle v-for="explosion in explosions" :key="'explosion-' + explosion.id" :config="{
                            x: explosion.x,
                            y: explosion.y,
                            radius: explosion.radius,
                            stroke: '#ff6600',
                            strokeWidth: 3,
                            opacity: explosion.opacity
                        }" />
                    </v-layer>
                </v-stage>

                <div class="game-overlay" v-if="!gameStarted">
                    <div class="start-menu">
                        <h1>坦克大战</h1>
                        <p>正在加载地图...</p>
                    </div>
                </div>
            </div>

            <div class="game-info">
                <div class="players-grid">
                    <div class="player-card" v-for="(player, index) in players" :key="index"
                        :class="{ 'dead': player.health <= 0 }">

                        <div class="player-header">
                            <div class="player-color-dot" :style="{ backgroundColor: getPlayerColor(index) }"></div>
                            <span class="player-name">{{ player.name }}</span>
                        </div>

                        <div class="health-container">
                            <div class="health-label">
                                <span>生命值</span>
                                <span class="health-value">{{ player.health }}/300</span>
                            </div>
                            <div class="health-bar">
                                <div class="health-fill" :style="{
                                    width: (player.health / 300 * 100) + '%',
                                    background: getHealthColor(player.health)
                                }"></div>
                            </div>
                        </div>

                        <div class="respawn-overlay" v-if="player.health <= 0 && player.respawnTime > 0">
                            <div class="respawn-text">{{ player.respawnTime }}s 后重生</div>
                        </div>
                    </div>
                </div>

                <div class="game-controls">
                    <div class="ping-display">
                        <span class="ping-label">延迟:</span>
                        <span class="ping-value"
                            :class="{ 'ping-good': ping < 50, 'ping-ok': ping >= 50 && ping < 100, 'ping-bad': ping >= 100 }">
                            {{ ping }}ms
                        </span>
                    </div>
                    <button @click="exitGame" class="control-btn exit-btn">退出游戏</button>
                </div>

                <div class="controls-guide">
                    <div class="guide-title">操作指南</div>
                    <div class="guide-content">
                        <div><strong>W/S</strong> - 前进/后退</div>
                        <div><strong>A/D</strong> - 左转/右转</div>
                        <div><strong>K</strong> - 发射炮弹</div>
                        <div class="guide-divider"></div>
                        <div class="guide-tips-title">游戏提示</div>
                        <div>• 炮弹最多反弹6次</div>
                        <div>• 炮弹会伤害所有坦克</div>
                        <div>• 小心自己的反弹炮弹</div>
                        <div>• 同时最多10发炮弹</div>
                        <div>• 死亡后3秒自动重生</div>
                        <div>• 利用墙壁反弹攻击</div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, computed, inject } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../../config/user'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { user } = storeToRefs(userStore)
const wsService = inject('wsService')

// Konva 舞台引用
const stageRef = ref(null)

// 游戏状态
const gameStarted = ref(false)
const currentMode = ref(route.query.mode || '1v1v1v1')
const roomCode = ref(route.query.roomCode || '')
const gameId = ref(route.query.gameId || '')

// 地图数据
const currentMapData = ref(null)

// 网络延迟
const ping = ref(0)
const lastPingTime = ref(0)

// 玩家数据
const players = ref([
    { name: '玩家1', type: '真人', health: 300, kills: 0, deaths: 0, bulletsCount: 0, score: 0, respawnTime: 0 },
    { name: 'AI 1', type: 'AI', health: 300, kills: 0, deaths: 0, bulletsCount: 0, score: 0, respawnTime: 0 },
    { name: 'AI 2', type: 'AI', health: 300, kills: 0, deaths: 0, bulletsCount: 0, score: 0, respawnTime: 0 },
    { name: 'AI 3', type: 'AI', health: 300, kills: 0, deaths: 0, bulletsCount: 0, score: 0, respawnTime: 0 }
])

// ==================== 游戏配置 ====================
const GAME_CONFIG = {
    tank: {
        speed: 210,           // 坦克速度（像素/秒）
        health: 300,          // 坦克血量
        shootCooldown: 400,   // 射击冷却时间(ms)
        maxBullets: 10,       // 最大炮弹数
        rotationSpeed: 200,   // 旋转速度（度/秒）
        bodySize: 32,         // 坦克主体尺寸（正方形）
        barrelLength: 18,     // 炮管长度
        barrelWidth: 6,       // 炮管宽度
        collisionSize: 36,    // 碰撞体尺寸
        bulletSpawnDistance: 36,
        respawnDelay: 3000    // 重生延迟(ms)
    },
    bullet: {
        speed: 200,           // 炮弹速度（像素/秒）
        damage: 100,          // 伤害值
        maxBounces: 6,        // 最大反弹次数
        lifeTime: 15000,      // 最大存活时间(ms)
        radius: 4             // 炮弹半径
    },
    map: {
        width: 1200,
        height: 800
    },
    colors: [
        0xff0000,  // 红色
        0x0000ff,  // 蓝色
        0x800080,  // 紫色
        0x000000   // 黑色
    ],
    debug: false
}

// Konva 舞台配置
const stageConfig = {
    width: GAME_CONFIG.map.width,
    height: GAME_CONFIG.map.height
}

// 背景配置
const backgroundConfig = {
    x: 0,
    y: 0,
    width: GAME_CONFIG.map.width,
    height: GAME_CONFIG.map.height,
    fill: '#ffffff'
}

const borderConfig = {
    x: 0,
    y: 0,
    width: GAME_CONFIG.map.width,
    height: GAME_CONFIG.map.height,
    stroke: '#000000',
    strokeWidth: 2
}

// 游戏数据
const tanks = ref([])
const walls = ref([])
const bulletIdCounter = ref(0)
const trackMarks = ref([])  // 履带痕迹
const explosions = ref([])  // 爆炸特效
const trackIdCounter = ref(0)
const explosionIdCounter = ref(0)

// 所有子弹（扁平化）
const allBullets = computed(() => {
    const bullets = []
    for (const tank of tanks.value) {
        for (const bullet of tank.bullets) {
            bullets.push(bullet)
        }
    }
    return bullets
})

// 键盘状态
const keys = ref({
    w: false,
    s: false,
    a: false,
    d: false,
    k: false
})

// 移除客户端游戏循环相关变量
// 改为接收服务端状态
let sendInputInterval = null  // 发送玩家输入的定时器

// 加载地图
async function loadMap() {
    try {
        const response = await fetch('/resource/maps/tankmap-1.json')
        if (!response.ok) {
            throw new Error('地图加载失败')
        }
        const data = await response.json()
        currentMapData.value = data
        return data
    } catch (error) {
        console.error('地图加载错误:', error)
        return {
            id: 'default',
            name: '默认地图',
            size: { width: 1200, height: 800 },
            walls: []
        }
    }
}

// 初始化墙壁
function initWalls() {
    const wallData = currentMapData.value?.walls || []
    walls.value = wallData.map(wall => ({
        x: wall.x,
        y: wall.y,
        width: wall.width,
        height: wall.height,
        offsetX: wall.width / 2,  // 让墙壁以中心点为原点
        offsetY: wall.height / 2,
        fill: '#000000'
    }))
}

// 颜色转换
function shuffleColors() {
    const colors = [...GAME_CONFIG.colors]
    for (let i = colors.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [colors[i], colors[j]] = [colors[j], colors[i]]
    }
    return colors
}

function getColorHex(color) {
    return '#' + color.toString(16).padStart(6, '0')
}

function getPlayerColor(index) {
    if (!tanks.value[index]) return '#000000'
    return getColorHex(tanks.value[index].color)
}

// 根据血量返回颜色
function getHealthColor(health) {
    const percentage = health / 300
    if (percentage > 0.6) return '#22c55e'  // 绿色（健康）
    if (percentage > 0.3) return '#eab308'  // 黄色（警告）
    return '#ef4444'  // 红色（危险）
}

// 初始化坦克
function initTanks() {
    const colors = shuffleColors()
    const spawnPoints = [
        { x: 150, y: 150 },
        { x: 1050, y: 150 },
        { x: 150, y: 650 },
        { x: 1050, y: 650 }
    ]

    tanks.value = spawnPoints.map((spawn, index) => ({
        playerId: index,
        x: spawn.x,
        y: spawn.y,
        vx: 0,
        vy: 0,
        rotation: 0,
        color: colors[index],
        health: GAME_CONFIG.tank.health,
        maxHealth: GAME_CONFIG.tank.health,
        bullets: [],
        lastShootTime: 0,
        kills: 0,
        deaths: 0,
        score: 0,
        isDead: false,
        isPlayer: index === 0,
        // 特效相关
        flashOpacity: 1,       // 受伤闪烁（0-1）
        lastTrackTime: 0,      // 上次留下履带时间
        // AI 相关
        aiTarget: null,
        aiChangeDirectionTimer: 0,
        aiDirection: { x: 0, y: 0 },
        aiShootTimer: 0
    }))

    for (let index = 0; index < players.value.length; index++) {
        const player = players.value[index]
        player.name = index === 0 ? '玩家1 (你)' : `AI ${index}`
        player.type = index === 0 ? '真人' : 'AI'
    }
}

// 碰撞检测辅助函数
function rectCircleCollision(rect, circle) {
    const closestX = Math.max(rect.x - rect.width / 2, Math.min(circle.x, rect.x + rect.width / 2))
    const closestY = Math.max(rect.y - rect.height / 2, Math.min(circle.y, rect.y + rect.height / 2))
    const distance = Math.hypot(circle.x - closestX, circle.y - closestY)
    return distance < circle.radius
}

function rectRectCollision(rect1, rect2) {
    return Math.abs(rect1.x - rect2.x) < (rect1.width + rect2.width) / 2 &&
        Math.abs(rect1.y - rect2.y) < (rect1.height + rect2.height) / 2
}

function circleCircleCollision(c1, c2) {
    const distance = Math.hypot(c1.x - c2.x, c1.y - c2.y)
    return distance < (c1.radius + c2.radius)
}

// 客户端不再处理射击、受伤、死亡、重生等逻辑
// 这些都由服务端计算并通过WebSocket广播

// 更新坦克统计
function updateTankStats(tank) {
    players.value[tank.playerId].health = tank.health
    players.value[tank.playerId].bulletsCount = tank.bullets.length
    players.value[tank.playerId].kills = tank.kills
    players.value[tank.playerId].deaths = tank.deaths
    players.value[tank.playerId].score = tank.score
}

// AI逻辑已移至服务端，客户端不再处理

// ========== 客户端只负责发送操作和接收状态，不再计算游戏逻辑 ==========

// 发送玩家操作到服务端
function sendPlayerAction() {
    if (!wsService || !roomCode.value) return

    wsService.send(`/app/tankbattle/${roomCode.value}/playerAction`, {
        userId: user.value?.id,
        keys: keys.value,
        timestamp: Date.now()
    })
}

// 更新延迟
function updatePing(timestamp) {
    if (!timestamp) return
    const now = Date.now()
    ping.value = now - lastPingTime.value
    lastPingTime.value = now
}

// 更新坦克状态
function updateTanksFromServer(serverTanks) {
    if (!serverTanks || !Array.isArray(serverTanks)) return

    for (let index = 0; index < serverTanks.length; index++) {
        const serverTank = serverTanks[index]
        if (!tanks.value[index]) continue

        tanks.value[index].x = serverTank.x
        tanks.value[index].y = serverTank.y
        tanks.value[index].rotation = serverTank.r
        tanks.value[index].health = serverTank.h
        tanks.value[index].isDead = serverTank.d === 1

        if (players.value[index]) {
            players.value[index].health = serverTank.h
        }
    }
}

// 更新子弹状态
function updateBulletsFromServer(serverBullets) {
    if (!serverBullets || !Array.isArray(serverBullets)) return

    const newBullets = serverBullets.map(b => ({
        id: b.id,
        x: b.x,
        y: b.y,
        vx: b.vx,
        vy: b.vy,
        opacity: 1,
        active: true
    }))

    if (tanks.value[0]) {
        tanks.value[0].bullets = newBullets
    }
}

// 处理服务端广播的游戏状态
function handleGameState(data) {
    try {
        updatePing(data.timestamp)
        updateTanksFromServer(data.tanks)
        updateBulletsFromServer(data.bullets)
    } catch (error) {
        console.error('处理游戏状态失败:', error)
    }
}

// 处理游戏事件（击杀、死亡、重生）
function handleGameEvent(data) {
    try {
        if (data.type === 'kill') {
            ElMessage.success(`玩家 ${data.killerId} 击杀了玩家 ${data.victimId}`)
        } else if (data.type === 'death') {
            ElMessage.warning(`玩家 ${data.playerId} 阵亡，${data.respawnTime}秒后重生`)
            // 更新重生倒计时
            if (players.value[data.playerId]) {
                players.value[data.playerId].respawnTime = data.respawnTime
            }
        } else if (data.type === 'respawn') {
            ElMessage.info(`玩家 ${data.playerId} 已重生`)
            // 更新玩家状态
            const tank = tanks.value.find(t => t.playerId === data.playerId)
            if (tank) {
                tank.x = data.x
                tank.y = data.y
                tank.health = data.health
                tank.isDead = false
            }
        }
    } catch (error) {
        console.error('处理游戏事件失败:', error)
    }
}

// 处理游戏结束
function handleGameEnd(data) {
    try {
        ElMessage.success('游戏结束！')

        // 停止发送输入
        if (sendInputInterval) {
            clearInterval(sendInputInterval)
            sendInputInterval = null
        }

        // 显示结算界面（暂时简单处理，后续可以做成弹窗）
        console.log('游戏结束数据:', data)

        // 3秒后返回房间
        setTimeout(() => {
            router.push('/game/tank-battle')
        }, 3000)
    } catch (error) {
        console.error('处理游戏结束失败:', error)
    }
}

// 键盘事件
function handleKeyDown(e) {
    const key = e.key.toLowerCase()
    if (key === 'w') keys.value.w = true
    if (key === 's') keys.value.s = true
    if (key === 'a') keys.value.a = true
    if (key === 'd') keys.value.d = true
    if (key === 'k') keys.value.k = true
}

function handleKeyUp(e) {
    const key = e.key.toLowerCase()
    if (key === 'w') keys.value.w = false
    if (key === 's') keys.value.s = false
    if (key === 'a') keys.value.a = false
    if (key === 'd') keys.value.d = false
    if (key === 'k') keys.value.k = false
}

function handleStageClick() {
    // 点击舞台时聚焦以接收键盘事件
}

// 退出游戏
const exitGame = () => {
    router.push('/game/tank-battle')
}

// 初始化游戏（客户端只负责初始化渲染，不运行游戏逻辑）
const initGame = async () => {
    await loadMap()
    initWalls()
    initTanks()

    gameStarted.value = true

    // 订阅WebSocket频道
    if (wsService && roomCode.value) {
        // 订阅游戏状态广播（60FPS）
        wsService.subscribe(`/topic/tankbattle/${roomCode.value}/gameState`, (msg) => {
            try {
                const data = JSON.parse(msg.body)
                handleGameState(data)
            } catch (e) {
                console.error('处理游戏状态消息失败:', e)
            }
        })

        // 订阅游戏事件（击杀、死亡、重生）
        wsService.subscribe(`/topic/tankbattle/${roomCode.value}/gameEvent`, (msg) => {
            try {
                const data = JSON.parse(msg.body)
                handleGameEvent(data)
            } catch (e) {
                console.error('处理游戏事件消息失败:', e)
            }
        })

        // 订阅游戏结束事件
        wsService.subscribe(`/topic/tankbattle/${roomCode.value}/gameEnd`, (msg) => {
            try {
                const data = JSON.parse(msg.body)
                handleGameEnd(data)
            } catch (e) {
                console.error('处理游戏结束消息失败:', e)
            }
        })

        // 启动玩家输入发送定时器（60FPS）
        sendInputInterval = setInterval(sendPlayerAction, 16.67)
    }
}

onMounted(async () => {
    await initGame()

    // 添加键盘监听
    globalThis.addEventListener('keydown', handleKeyDown)
    globalThis.addEventListener('keyup', handleKeyUp)
})

onBeforeUnmount(() => {
    // 清理定时器
    if (sendInputInterval) {
        clearInterval(sendInputInterval)
        sendInputInterval = null
    }

    // 取消WebSocket订阅
    if (wsService && roomCode.value) {
        wsService.unsubscribe(`/topic/tankbattle/${roomCode.value}/gameState`)
        wsService.unsubscribe(`/topic/tankbattle/${roomCode.value}/gameEvent`)
        wsService.unsubscribe(`/topic/tankbattle/${roomCode.value}/gameEnd`)
    }

    // 移除键盘监听
    globalThis.removeEventListener('keydown', handleKeyDown)
    globalThis.removeEventListener('keyup', handleKeyUp)
})
</script>

<style scoped>
.tank-battle-page {
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 100vh;
    background: #f5f5f5;
    padding: 20px;
}

.tank-battle-container {
    display: flex;
    gap: 20px;
    align-items: flex-start;
    font-family: Arial, sans-serif;
}

.game-wrapper {
    position: relative;
    display: flex;
    flex-shrink: 0;
}

.game-overlay {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(255, 255, 255, 0.95);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1000;
}

.start-menu {
    text-align: center;
    color: #000;
    padding: 40px;
}

.start-menu h1 {
    font-size: 32px;
    margin-bottom: 20px;
    font-weight: bold;
}

.start-menu p {
    font-size: 16px;
    color: #666;
}

.game-info {
    display: flex;
    flex-direction: column;
    gap: 15px;
    width: 300px;
    flex-shrink: 0;
}

.players-grid {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.player-card {
    position: relative;
    background: #fff;
    padding: 12px;
    border: 2px solid #000;
}

.player-card.dead {
    opacity: 0.5;
}

.player-header {
    margin-bottom: 10px;
    display: flex;
    align-items: center;
    gap: 8px;
}

.player-color-dot {
    width: 12px;
    height: 12px;
    border-radius: 50%;
    border: 2px solid #000;
}

.player-name {
    font-size: 14px;
    font-weight: bold;
    color: #000;
}

.health-container {
    margin-bottom: 0;
}

.health-label {
    display: flex;
    justify-content: space-between;
    margin-bottom: 5px;
    font-size: 12px;
    color: #666;
}

.health-value {
    font-weight: bold;
    font-family: monospace;
    color: #000;
}

.health-bar {
    width: 100%;
    height: 20px;
    border: 1px solid #000;
    background: #f0f0f0;
}

.health-fill {
    height: 100%;
    transition: width 0.3s, background 0.3s;
}

.respawn-overlay {
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.8);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 10;
}

.respawn-text {
    font-size: 14px;
    font-weight: bold;
    color: #fff;
}

.game-controls {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.ping-display {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    padding: 8px 12px;
    background: #fff;
    border: 2px solid #000;
    font-size: 13px;
    font-weight: bold;
}

.ping-label {
    color: #666;
}

.ping-value {
    font-family: monospace;
    font-size: 14px;
}

.ping-good {
    color: #22c55e;
}

.ping-ok {
    color: #eab308;
}

.ping-bad {
    color: #ef4444;
}

.control-btn {
    padding: 10px;
    font-size: 14px;
    background: #fff;
    color: #000;
    border: 2px solid #000;
    cursor: pointer;
    font-weight: bold;
    transition: all 0.2s;
}

.control-btn:hover {
    background: #000;
    color: #fff;
}

.exit-btn:hover {
    background: #dc3545;
    border-color: #dc3545;
    color: #fff;
}

.controls-guide {
    background: #fff;
    padding: 12px;
    border: 2px solid #000;
}

.guide-title {
    font-size: 14px;
    font-weight: bold;
    color: #000;
    margin-bottom: 8px;
}

.guide-content {
    font-size: 12px;
    color: #666;
    line-height: 1.8;
}

.guide-content div {
    margin: 3px 0;
}

.guide-content strong {
    color: #000;
    font-weight: bold;
}

.guide-divider {
    height: 1px;
    background: #ddd;
    margin: 10px 0 !important;
}

.guide-tips-title {
    font-weight: bold;
    color: #000;
    margin-top: 5px !important;
    margin-bottom: 5px !important;
}
</style>
