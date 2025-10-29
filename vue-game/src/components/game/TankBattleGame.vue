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
import { onMounted, onBeforeUnmount, ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

// Konva 舞台引用
const stageRef = ref(null)

// 游戏状态
const gameStarted = ref(false)
const currentMode = ref(route.query.mode || '1v1v1v1')

// 地图数据
const currentMapData = ref(null)

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

let animationFrameId = null
let lastTime = 0
const TARGET_FPS = 60
const FRAME_TIME = 1000 / TARGET_FPS  // 约16.67ms
const MAX_DELTA_TIME = 100  // 最大帧间隔100ms，防止穿墙
let lastShootKeyState = false  // 用于K键单次触发

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

// 创建爆炸特效
function createExplosion(x, y) {
    const explosion = {
        id: explosionIdCounter.value++,
        x,
        y,
        radius: 5,
        opacity: 1,
        createTime: Date.now()
    }
    explosions.value.push(explosion)
}

// 创建履带痕迹
function createTrackMark(tank) {
    const now = Date.now()
    if (now - tank.lastTrackTime < 100) return  // 每100ms留一次痕迹

    tank.lastTrackTime = now

    const track = {
        id: trackIdCounter.value++,
        points: [tank.x, tank.y, tank.x, tank.y],  // 起点和终点相同（点）
        color: getColorHex(tank.color),
        opacity: 0.3,
        createTime: now
    }

    trackMarks.value.push(track)

    // 限制履带数量，防止内存泄漏
    if (trackMarks.value.length > 200) {
        trackMarks.value.shift()
    }
}

// 坦克射击
function tankShoot(tank) {
    const now = Date.now()
    if (now - tank.lastShootTime < GAME_CONFIG.tank.shootCooldown) return
    if (tank.bullets.length >= GAME_CONFIG.tank.maxBullets) return
    if (tank.isDead) return

    tank.lastShootTime = now

    // 计算子弹发射位置
    const angle = tank.rotation * Math.PI / 180
    const spawnDistance = GAME_CONFIG.tank.bulletSpawnDistance
    const bulletX = tank.x + Math.cos(angle) * spawnDistance
    const bulletY = tank.y + Math.sin(angle) * spawnDistance

    // 检查发射路径是否被墙阻挡（检查从坦克到子弹生成点的整条路径）
    let isBlocked = false

    // 沿着发射路径检测多个点（每隔4像素检测一次）
    const checkPoints = Math.ceil(spawnDistance / 4)
    for (let i = 0; i <= checkPoints; i++) {
        const t = i / checkPoints  // 0 到 1
        const checkX = tank.x + Math.cos(angle) * spawnDistance * t
        const checkY = tank.y + Math.sin(angle) * spawnDistance * t

        for (const wall of walls.value) {
            if (rectCircleCollision(wall, { x: checkX, y: checkY, radius: GAME_CONFIG.bullet.radius })) {
                isBlocked = true
                break
            }
        }

        if (isBlocked) break
    }

    if (isBlocked) return

    // 创建子弹
    const bullet = {
        id: bulletIdCounter.value++,
        x: bulletX,
        y: bulletY,
        vx: Math.cos(angle) * GAME_CONFIG.bullet.speed,
        vy: Math.sin(angle) * GAME_CONFIG.bullet.speed,
        ownerId: tank.playerId,
        bounceCount: 0,
        createTime: now,
        active: true,
        opacity: 1
    }

    tank.bullets.push(bullet)
    updateTankStats(tank)
}

// 坦克受伤
function tankTakeDamage(tank, damage, attackerId) {
    if (tank.isDead) return

    tank.health = Math.max(0, tank.health - damage)

    // 受伤闪烁特效
    tank.flashOpacity = 0.3

    if (tank.health <= 0) {
        tankDie(tank, attackerId)
    }

    updateTankStats(tank)
}

// 坦克死亡
function tankDie(tank, killerId) {
    tank.isDead = true
    tank.deaths++

    // 给击杀者加分
    if (killerId !== tank.playerId && killerId !== null) {
        const killer = tanks.value.find(t => t.playerId === killerId)
        if (killer) {
            killer.kills++
            killer.score += 100
            updateTankStats(killer)
        }
    }

    // 清理所有子弹
    tank.bullets = []

    updateTankStats(tank)

    // 3秒后重生
    let respawnCounter = 3
    players.value[tank.playerId].respawnTime = respawnCounter

    const respawnInterval = setInterval(() => {
        respawnCounter--
        players.value[tank.playerId].respawnTime = respawnCounter

        if (respawnCounter <= 0) {
            clearInterval(respawnInterval)
            tankRespawn(tank)
        }
    }, 1000)
}

// 坦克重生
function tankRespawn(tank) {
    const spawnPoints = [
        { x: 100, y: 100 },
        { x: 1100, y: 100 },
        { x: 100, y: 700 },
        { x: 1100, y: 700 },
        { x: 600, y: 400 }
    ]

    // 打乱
    for (let i = spawnPoints.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [spawnPoints[i], spawnPoints[j]] = [spawnPoints[j], spawnPoints[i]]
    }

    // 找安全点
    let spawn = spawnPoints[0]
    const otherTanks = tanks.value.filter(t => t !== tank && !t.isDead)

    for (const point of spawnPoints) {
        let isSafe = true
        for (const otherTank of otherTanks) {
            const distance = Math.hypot(point.x - otherTank.x, point.y - otherTank.y)
            if (distance < 80) {
                isSafe = false
                break
            }
        }
        if (isSafe) {
            spawn = point
            break
        }
    }

    tank.x = spawn.x
    tank.y = spawn.y
    tank.health = tank.maxHealth
    tank.isDead = false

    updateTankStats(tank)
}

// 更新坦克统计
function updateTankStats(tank) {
    players.value[tank.playerId].health = tank.health
    players.value[tank.playerId].bulletsCount = tank.bullets.length
    players.value[tank.playerId].kills = tank.kills
    players.value[tank.playerId].deaths = tank.deaths
    players.value[tank.playerId].score = tank.score
}

// AI 逻辑
function findNearestEnemy(tank) {
    let nearestEnemy = null
    let minDistance = Infinity

    for (const other of tanks.value) {
        if (other === tank || other.isDead) continue

        const distance = Math.hypot(tank.x - other.x, tank.y - other.y)

        if (distance < minDistance) {
            minDistance = distance
            nearestEnemy = other
        }
    }

    return { enemy: nearestEnemy, distance: minDistance }
}

function updateAI(tank, deltaTime) {
    const now = Date.now()
    const { enemy: nearestEnemy, distance: minDistance } = findNearestEnemy(tank)

    if (nearestEnemy) {
        const angleToEnemy = Math.atan2(nearestEnemy.y - tank.y, nearestEnemy.x - tank.x)
        tank.rotation = angleToEnemy * 180 / Math.PI

        // AI 移动策略
        if (minDistance < 150) {
            // 后退
            const dodgeAngle = angleToEnemy + Math.PI + (Math.random() - 0.5) * Math.PI / 2
            tank.vx = Math.cos(dodgeAngle) * GAME_CONFIG.tank.speed * 0.8
            tank.vy = Math.sin(dodgeAngle) * GAME_CONFIG.tank.speed * 0.8
        } else if (minDistance > 400) {
            // 接近
            tank.vx = Math.cos(angleToEnemy) * GAME_CONFIG.tank.speed * 0.6
            tank.vy = Math.sin(angleToEnemy) * GAME_CONFIG.tank.speed * 0.6
        } else {
            // 横向移动
            const strafeAngle = angleToEnemy + Math.PI / 2
            tank.vx = Math.cos(strafeAngle) * GAME_CONFIG.tank.speed * 0.5
            tank.vy = Math.sin(strafeAngle) * GAME_CONFIG.tank.speed * 0.5
        }

        // AI 射击
        if (now - tank.aiShootTimer > 1200 + Math.random() * 1500) {
            if (Math.random() > 0.4) {
                tankShoot(tank)
                tank.aiShootTimer = now
            }
        }
    } else {
        // 随机移动
        if (now - tank.aiChangeDirectionTimer > 2000) {
            tank.aiDirection = {
                x: (Math.random() - 0.5) * 2,
                y: (Math.random() - 0.5) * 2
            }
            tank.aiChangeDirectionTimer = now
        }

        tank.vx = tank.aiDirection.x * GAME_CONFIG.tank.speed * 0.3
        tank.vy = tank.aiDirection.y * GAME_CONFIG.tank.speed * 0.3

        if (tank.aiDirection.x !== 0 || tank.aiDirection.y !== 0) {
            tank.rotation = Math.atan2(tank.aiDirection.y, tank.aiDirection.x) * 180 / Math.PI
        }
    }
}

// 更新玩家坦克
function updatePlayer(tank, deltaTime) {
    const dt = deltaTime / 1000  // 转换为秒

    // W/S 控制速度（前进/后退）
    let speed = 0
    if (keys.value.w) speed = GAME_CONFIG.tank.speed      // 前进
    if (keys.value.s) speed = -GAME_CONFIG.tank.speed     // 后退

    // A/D 控制旋转角度（基于时间，保证不同帧率下旋转速度一致）
    const rotationDelta = GAME_CONFIG.tank.rotationSpeed * dt  // 度/秒 × 秒 = 度
    if (keys.value.a) tank.rotation -= rotationDelta      // 左转
    if (keys.value.d) tank.rotation += rotationDelta      // 右转

    // 根据当前角度和速度计算速度向量
    if (speed === 0) {
        // 没有前进/后退时停止移动
        tank.vx = 0
        tank.vy = 0
    } else {
        const angleInRadians = tank.rotation * Math.PI / 180
        tank.vx = Math.cos(angleInRadians) * speed
        tank.vy = Math.sin(angleInRadians) * speed
    }

    // 射击（修复K键长按问题 - 只在按下时触发一次）
    if (keys.value.k && !lastShootKeyState) {
        tankShoot(tank)
    }
    lastShootKeyState = keys.value.k
}

// 检查墙壁碰撞
function checkWallCollision(x, y) {
    const tankRect = { x, y, width: GAME_CONFIG.tank.collisionSize, height: GAME_CONFIG.tank.collisionSize }
    for (const wall of walls.value) {
        if (rectRectCollision(tankRect, wall)) return true
    }
    return false
}

// 检查坦克间碰撞
function checkTankCollision(tank, x, y) {
    const rect1 = { x, y, width: GAME_CONFIG.tank.collisionSize, height: GAME_CONFIG.tank.collisionSize }
    for (const other of tanks.value) {
        if (other === tank || other.isDead) continue
        const rect2 = { x: other.x, y: other.y, width: GAME_CONFIG.tank.collisionSize, height: GAME_CONFIG.tank.collisionSize }
        if (rectRectCollision(rect1, rect2)) return true
    }
    return false
}

// 更新坦克位置
function updateTankPosition(tank, deltaTime) {
    if (tank.isDead) return

    const dt = deltaTime / 1000
    let newX = tank.x + tank.vx * dt
    let newY = tank.y + tank.vy * dt

    // 世界边界检测
    const halfSize = GAME_CONFIG.tank.collisionSize / 2
    newX = Math.max(halfSize, Math.min(GAME_CONFIG.map.width - halfSize, newX))
    newY = Math.max(halfSize, Math.min(GAME_CONFIG.map.height - halfSize, newY))

    // 碰撞检测
    const collided = checkWallCollision(newX, newY) || checkTankCollision(tank, newX, newY)

    if (!collided) {
        tank.x = newX
        tank.y = newY

        // 移动时创建履带痕迹
        if (tank.vx !== 0 || tank.vy !== 0) {
            createTrackMark(tank)
        }
    }
}

// 处理子弹世界边界反弹（改进版：额外1像素确保完全分离）
function handleBulletWorldBounds(bullet) {
    let bounced = false
    const radius = GAME_CONFIG.bullet.radius

    if (bullet.x - radius <= 0) {
        bullet.x = radius + 1  // 额外1像素
        bullet.vx = Math.abs(bullet.vx)
        bounced = true
    } else if (bullet.x + radius >= GAME_CONFIG.map.width) {
        bullet.x = GAME_CONFIG.map.width - radius - 1  // 额外1像素
        bullet.vx = -Math.abs(bullet.vx)
        bounced = true
    }

    if (bullet.y - radius <= 0) {
        bullet.y = radius + 1  // 额外1像素
        bullet.vy = Math.abs(bullet.vy)
        bounced = true
    } else if (bullet.y + radius >= GAME_CONFIG.map.height) {
        bullet.y = GAME_CONFIG.map.height - radius - 1  // 额外1像素
        bullet.vy = -Math.abs(bullet.vy)
        bounced = true
    }

    return bounced
}

// 应用墙壁反弹效果
function applyWallBounce(bullet, wall, dx, dy) {
    const halfWidth = wall.width / 2
    const halfHeight = wall.height / 2
    const radius = GAME_CONFIG.bullet.radius

    // 计算穿透深度
    const penetrationX = (halfWidth + radius) - Math.abs(dx)
    const penetrationY = (halfHeight + radius) - Math.abs(dy)

    // 根据穿透深度最小的方向反弹
    if (penetrationX < penetrationY) {
        // 水平碰撞
        bullet.vx = -bullet.vx
        bullet.x = wall.x + (dx > 0 ? 1 : -1) * (halfWidth + radius + 1)
    } else {
        // 垂直碰撞
        bullet.vy = -bullet.vy
        bullet.y = wall.y + (dy > 0 ? 1 : -1) * (halfHeight + radius + 1)
    }
}

// 处理子弹墙壁反弹（改进版：精确反弹，防止穿墙和陷入）
function handleBulletWallBounce(bullet) {
    for (const wall of walls.value) {
        if (rectCircleCollision(wall, { x: bullet.x, y: bullet.y, radius: GAME_CONFIG.bullet.radius })) {
            const dx = bullet.x - wall.x
            const dy = bullet.y - wall.y
            applyWallBounce(bullet, wall, dx, dy)
            return true
        }
    }
    return false
}

// 检查子弹坦克碰撞
function checkBulletTankCollision(bullet) {
    for (const targetTank of tanks.value) {
        if (targetTank.isDead) continue

        const tankCircle = {
            x: targetTank.x,
            y: targetTank.y,
            radius: GAME_CONFIG.tank.collisionSize / 2
        }

        if (circleCircleCollision(
            { x: bullet.x, y: bullet.y, radius: GAME_CONFIG.bullet.radius },
            tankCircle
        )) {
            tankTakeDamage(targetTank, GAME_CONFIG.bullet.damage, bullet.ownerId)
            createExplosion(bullet.x, bullet.y)  // 创建爆炸特效
            return true
        }
    }
    return false
}

// 更新单个子弹
function updateSingleBullet(bullet, dt, now) {
    if (!bullet.active) return false

    // 检查存活时间
    if (now - bullet.createTime > GAME_CONFIG.bullet.lifeTime) {
        bullet.active = false
        return false
    }

    // 更新位置
    bullet.x += bullet.vx * dt
    bullet.y += bullet.vy * dt

    // 边界和墙壁反弹
    let bounced = handleBulletWorldBounds(bullet)
    if (handleBulletWallBounce(bullet)) {
        bounced = true
    }

    if (bounced) {
        bullet.bounceCount++
        if (bullet.bounceCount >= GAME_CONFIG.bullet.maxBounces) {
            bullet.active = false
            return false
        }
    }

    // 坦克碰撞
    if (checkBulletTankCollision(bullet)) {
        bullet.active = false
        return false
    }

    return true
}

// 更新子弹
function updateBullets(deltaTime) {
    const dt = deltaTime / 1000
    const now = Date.now()

    for (const tank of tanks.value) {
        tank.bullets = tank.bullets.filter(bullet => updateSingleBullet(bullet, dt, now))
        updateTankStats(tank)
    }
}

// 更新特效
function updateEffects(deltaTime) {
    const now = Date.now()
    const dt = deltaTime / 1000

    // 更新受伤闪烁（快速恢复）
    for (const tank of tanks.value) {
        if (tank.flashOpacity < 1) {
            tank.flashOpacity += dt * 5  // 0.2秒恢复
            if (tank.flashOpacity > 1) tank.flashOpacity = 1
        }
    }

    // 更新爆炸特效（扩散并消失）
    explosions.value = explosions.value.filter(exp => {
        const age = now - exp.createTime
        if (age > 300) return false  // 0.3秒后消失

        exp.radius += dt * 50  // 扩散
        exp.opacity = 1 - (age / 300)  // 渐隐
        return true
    })

    // 更新履带痕迹（渐隐）
    trackMarks.value = trackMarks.value.filter(track => {
        const age = now - track.createTime
        if (age > 3000) return false  // 3秒后完全消失

        track.opacity = 0.3 * (1 - age / 3000)  // 渐隐
        return true
    })
}

// 游戏主循环（60FPS锁定）
function gameLoop(currentTime) {
    if (!lastTime) lastTime = currentTime
    let deltaTime = currentTime - lastTime

    // 帧率限制：如果距离上一帧时间小于目标帧时间，跳过这一帧
    if (deltaTime < FRAME_TIME) {
        animationFrameId = requestAnimationFrame(gameLoop)
        return
    }

    // 限制最大deltaTime，防止穿墙
    if (deltaTime > MAX_DELTA_TIME) {
        deltaTime = MAX_DELTA_TIME
    }

    lastTime = currentTime

    // 更新坦克
    for (const tank of tanks.value) {
        if (tank.isDead) continue

        if (tank.isPlayer) {
            updatePlayer(tank, deltaTime)
        } else {
            updateAI(tank, deltaTime)
        }

        updateTankPosition(tank, deltaTime)
    }

    // 更新子弹
    updateBullets(deltaTime)

    // 更新特效
    updateEffects(deltaTime)

    animationFrameId = requestAnimationFrame(gameLoop)
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

// 初始化游戏
const initGame = async () => {
    await loadMap()
    initWalls()
    initTanks()

    gameStarted.value = true

    // 启动游戏循环
    animationFrameId = requestAnimationFrame(gameLoop)
}

onMounted(async () => {
    await initGame()

    // 添加键盘监听
    globalThis.addEventListener('keydown', handleKeyDown)
    globalThis.addEventListener('keyup', handleKeyUp)
})

onBeforeUnmount(() => {
    // 清理
    if (animationFrameId) {
        cancelAnimationFrame(animationFrameId)
    }

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
