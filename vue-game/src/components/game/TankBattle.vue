<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, inject, watch } from 'vue'
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
const addingAI = ref({}) // 添加AI的loading状态
const gameStartSubscription = ref(null) // 游戏开始订阅

// 游戏模式选择（默认值）
const selectedMode = ref('1v1v1v1')
const switchingMode = ref(false) // 模式切换中，用于锁定页面
const switchingTeam = ref(false) // 队伍切换中，用于锁定

// 是否为房主
const isRoomOwner = computed(() => {
    return roomInfo.value?.creatorId === user.value?.id
})

// 监听模式切换，通知后端重新分配队伍/座位
watch(selectedMode, async (newMode, oldMode) => {
    if (!roomInfo.value?.roomCode || !user.value?.id) return
    if (switchingMode.value) return // 防止重复触发
    if (!oldMode) return // 首次加载时跳过

    switchingMode.value = true

    try {
        const response = await request.post('/room/switchMode', {
            roomCode: roomInfo.value.roomCode,
            mode: newMode,
            oldMode: oldMode
        })

        if (response.data.success || response.data.code === 200) {
            // 后端会通过WebSocket广播模式切换消息，前端等待消息更新UI
            ElMessage.success('正在切换模式，请稍候...')
            // 设置超时保护，防止WebSocket消息丢失导致永久锁定
            setTimeout(() => {
                if (switchingMode.value) {
                    switchingMode.value = false
                    ElMessage.warning('模式切换超时，请刷新页面')
                }
            }, 10000)
        } else {
            ElMessage.error(response.data.message || '切换模式失败')
            // 恢复旧模式
            selectedMode.value = oldMode
            switchingMode.value = false
        }
    } catch (e) {
        console.error('切换游戏模式失败:', e)
        ElMessage.error('切换模式失败，请重试')
        selectedMode.value = oldMode
        switchingMode.value = false
    }
})

const MAP_STATIC_PREFIX = '/resource/maps/'
const mapFiles = ref(['tankmap-1.json'])
const selectedMapPath = ref('')
const currentMapDisplay = computed(() => {
    if (!selectedMapPath.value) {
        return '随机地图'
    }
    const fileName = selectedMapPath.value.split('/').pop() || ''
    return fileName ? fileName.replace('.json', '') : '随机地图'
})

function pickRandomMap() {
    const files = mapFiles.value
    if (!files || files.length === 0) {
        return `${MAP_STATIC_PREFIX}tankmap-1.json`
    }
    const randomIndex = Math.floor(Math.random() * files.length)
    return `${MAP_STATIC_PREFIX}${files[randomIndex]}`
}

function assignRandomMap() {
    selectedMapPath.value = pickRandomMap()
}

async function discoverMapFiles() {
    const candidateIndices = Array.from({ length: 20 }, (_, i) => i + 1)

    // 使用 Promise.all 并行检测所有地图文件，提高加载速度
    const checkPromises = candidateIndices.map(async (index) => {
        const candidate = `tankmap-${index}.json`
        const candidatePath = `${MAP_STATIC_PREFIX}${candidate}`
        try {
            const response = await fetch(candidatePath, { method: 'HEAD' })
            return response.ok ? candidate : null
        } catch {
            return null
        }
    })

    const results = await Promise.all(checkPromises)
    const discovered = results.filter(name => name !== null)

    if (discovered.length > 0) {
        mapFiles.value = discovered
    }
}

const gameModes = [
    { value: '1v1v1v1', label: '混战模式', players: 4, description: '四人混战，各自为战' },
    { value: '2v2', label: '团队模式', players: 4, description: '两队对抗，配合作战' }
]

// 玩家座位（最多4人）
// 左侧两个（索引0,2）是队伍A（蓝色），右侧两个（索引1,3）是队伍B（红色）
const seats = ref([
    { id: 1, player: null, team: 1, ready: false },  // 左上 - 队伍A（蓝）
    { id: 2, player: null, team: 2, ready: false },  // 右上 - 队伍B（红）
    { id: 3, player: null, team: 1, ready: false },  // 左下 - 队伍A（蓝）
    { id: 4, player: null, team: 2, ready: false }   // 右下 - 队伍B（红）
])

// 计算AI编号（使用全局计数确保编号连续）
const getAINumber = (index) => {
    const allAIPlayers = seats.value
        .map((seat, idx) => ({ seat, idx }))
        .filter(item => item.seat.player?.isAI)

    const currentAIIndex = allAIPlayers.findIndex(item => item.idx === index)
    return currentAIIndex !== -1 ? currentAIIndex + 1 : 0
}

// 当前选择的模式配置
const currentModeConfig = computed(() => {
    return gameModes.find(m => m.value === selectedMode.value)
})

// 计算当前占用的座位数
const occupiedSeatsCount = computed(() => {
    return seats.value.filter(s => s.player !== null).length
})

// 是否可以开始游戏
const canStartGame = computed(() => {
    const occupiedSeats = occupiedSeatsCount.value
    const required = currentModeConfig.value?.players || 4

    if (selectedMode.value === 'practice') {
        return occupiedSeats >= 1
    }
    // 必须正好等于 required（4人）才能开始
    return occupiedSeats === required
})

// AI对手
async function addAI(seatIndex) {

    if (seats.value[seatIndex].player) {
        return
    }

    if (!roomInfo.value?.roomCode) {
        ElMessage.error('房间信息不存在')
        return
    }

    // 设置loading状态
    addingAI.value[seatIndex] = true

    try {
        // 获取目标座位的队伍和位置信息
        const targetSeat = seats.value[seatIndex]

        // 调用后端接口，将AI玩家信息存入Redis
        const response = await request.post('/room/addAI', {
            roomCode: roomInfo.value.roomCode,
            userId: user.value?.id,
            aiDifficulty: 'medium', // 默认中等难度
            targetTeamId: targetSeat.team, // 传递目标队伍
            targetSeatIndex: seatIndex // 传递座位索引
        })

        console.log('添加AI响应:', response.data)

        if (response.data.success || response.data.code === 200) {
            // 这里不需要本地立即更新，避免与WebSocket消息冲突
        } else {
            console.error('添加AI失败，返回信息:', response.data)
            ElMessage.error(response.data.message || '添加AI失败')
        }
    } catch (error) {
        console.error('添加AI异常:', error)
        ElMessage.error('添加AI失败，请稍后重试')
    } finally {
        addingAI.value[seatIndex] = false
    }
}

async function removePlayer(seatIndex) {
    const player = seats.value[seatIndex].player
    if (!player) return

    // 不能移除自己
    if (player.id === user.value?.id) {
        ElMessage.warning('不能移除自己')
        return
    }

    // 调用后端接口踢出玩家（包括AI）
    try {
        await request.post('/room/kick', {
            roomCode: roomInfo.value?.roomCode,
            kickerName: user.value?.username,
            kickedUserId: player.id || 0,
            kickedUserName: player.username || 'AI Player'
        })

        // 本地移除（WebSocket消息会同步更新，但这里提前更新UI）
        seats.value[seatIndex].player = null
        seats.value[seatIndex].ready = false
        ElMessage.success('已将玩家移出房间')
    } catch (e) {
        console.error('踢出玩家失败:', e)
        ElMessage.error('踢出玩家失败，请重试')
    }
}

// 切换队伍（只能切换到空位）
async function switchToTeam(targetTeamId) {
    if (!user.value?.id || !roomInfo.value?.roomCode) return
    if (switchingTeam.value) {
        ElMessage.warning('正在切换队伍，请稍候')
        return
    }

    // 检查是否为2v2模式
    if (selectedMode.value !== '2v2') {
        ElMessage.warning('只有团队模式才能切换队伍')
        return
    }

    // 查找当前玩家的座位
    const currentSeatIndex = seats.value.findIndex(seat =>
        seat.player?.id === user.value.id
    )

    if (currentSeatIndex === -1) {
        ElMessage.error('未找到您的座位')
        return
    }

    const currentTeamId = seats.value[currentSeatIndex].team

    // 检查是否尝试切换到同一队伍
    if (currentTeamId === targetTeamId) {
        ElMessage.warning('您已经在这个队伍中了')
        return
    }

    // 查找目标队伍的空座位
    const targetEmptySeat = seats.value.find(seat =>
        seat.team === targetTeamId && !seat.player
    )

    if (!targetEmptySeat) {
        ElMessage.warning('目标队伍没有空位')
        return
    }

    switchingTeam.value = true

    try {
        const response = await request.post('/room/switchTeam', {
            roomCode: roomInfo.value.roomCode,
            targetTeamId: targetTeamId
        })

        if (response.data.success || response.data.code === 200) {
            // WebSocket会广播teamSwitched消息更新UI
        } else {
            ElMessage.error(response.data.message || '切换队伍失败')
            switchingTeam.value = false
        }
    } catch (error) {
        console.error('切换队伍失败:', error)
        ElMessage.error('切换队伍失败，请重试')
        switchingTeam.value = false
    }
}

// 加载好友
async function loadFriends() {
    if (!user.value?.id) return
    try {
        const res = await request.get('/friend/getAllFriend', {
            params: { id: user.value.id, page: 1, pageSize: 100 } // 限制为100个，足够显示
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
    cooldownTrigger.value
    const lastInviteTime = inviteCooldown.value[friendId]
    if (!lastInviteTime) return false
    const now = Date.now()
    const cooldownTime = 5000
    return (now - lastInviteTime) < cooldownTime
}

function getRemainingCooldown(friendId) {
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
    }, 1000) // 改为1秒更新一次，减少性能消耗
}

function stopCooldownTimer() {
    if (cooldownTimer.value) {
        clearInterval(cooldownTimer.value)
        cooldownTimer.value = null
    }
}

// 此函数已删除，统一使用switchToTeam函数

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
            friendId: friend.id,
            roomCode: roomInfo.value?.roomCode,
            gameName: 'tank_battle'
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

    try {
        // 调用后端接口启动游戏
        let modeValue = 0;
        if (selectedMode.value === '2v2') modeValue = 2;

        assignRandomMap()

        const response = await request.post(`/tankbattle/${roomInfo.value?.roomCode}/start`, {
            mode: modeValue,
            map: selectedMapPath.value,
        })

        if (response.data.success || response.data.code === 200) {
            ElMessage.success('游戏启动中...')
            // 订阅游戏开始事件，收到后跳转
            if (wsService) {
                gameStartSubscription.value = wsService.subscribe(`/topic/tankbattle/${roomInfo.value?.roomCode}/gameStart`, (msg) => {
                    try {
                        JSON.parse(msg.body)
                        // 跳转到游戏页面
                        router.push({
                            path: '/game/tank_battle/play',
                            query: {
                                roomCode: roomInfo.value?.roomCode,
                                mode: selectedMode.value,
                                map: selectedMapPath.value
                            }
                        })
                    } catch (e) {
                        console.error('处理游戏开始消息失败:', e)
                    }
                })
            }
        } else {
            ElMessage.error(response.data.message || '启动游戏失败')
        }
    } catch (error) {
        console.error('启动游戏失败:', error)
        ElMessage.error('启动游戏失败，请重试')
    }
}

// 返回游戏中心
async function goBack() {
    // 退出房间前调用后端接口
    if (roomInfo.value?.roomCode) {
        try {
            await request.post('/room/leave', {
                roomCode: roomInfo.value.roomCode,
                username: user.value?.username
            })
        } catch (error) {
            console.error('退出房间失败:', error)
        }
    }
    router.push('/user/games')
}

// 加载房间玩家列表
async function loadRoomPlayers() {
    if (!roomInfo.value?.roomCode) return

    try {
        const res = await request.get('/room/players', {
            params: { roomCode: roomInfo.value.roomCode }
        })

        if (res.data.success && res.data.players) {
            const players = res.data.players
            // 清空所有座位
            for (const seat of seats.value) {
                seat.player = null
                seat.ready = false
            }

            if (selectedMode.value === '2v2') {
                // 2v2模式：按队伍分配座位
                // 左侧座位（0,2）给队伍A（teamId=1），右侧座位（1,3）给队伍B（teamId=2）
                const team1Players = players.filter(p => p.teamId === 1)
                const team2Players = players.filter(p => p.teamId === 2)

                // 队伍1分配到左侧
                const team1Seats = [0, 2]
                team1Players.forEach((playerData, idx) => {
                    if (idx < team1Seats.length) {
                        const seatIndex = team1Seats[idx]
                        const isAI = playerData.isAi === 1
                        seats.value[seatIndex].player = {
                            id: playerData.userId,
                            username: playerData.username,
                            avatar: playerData.avatar || '/image/default-avatar.jpg',
                            isAI: isAI
                        }
                        seats.value[seatIndex].team = 1
                        seats.value[seatIndex].ready = isAI || playerData.isReady === 1
                    }
                })

                // 队伍2分配到右侧
                const team2Seats = [1, 3]
                team2Players.forEach((playerData, idx) => {
                    if (idx < team2Seats.length) {
                        const seatIndex = team2Seats[idx]
                        const isAI = playerData.isAi === 1
                        seats.value[seatIndex].player = {
                            id: playerData.userId,
                            username: playerData.username,
                            avatar: playerData.avatar || '/image/default-avatar.jpg',
                            isAI: isAI
                        }
                        seats.value[seatIndex].team = 2
                        seats.value[seatIndex].ready = isAI || playerData.isReady === 1
                    }
                })
            } else {
                // 混战模式：按顺序分配座位
                for (let index = 0; index < players.length && index < seats.value.length; index++) {
                    const playerData = players[index]
                    const isAI = playerData.isAi === 1
                    seats.value[index].player = {
                        id: playerData.userId,
                        username: playerData.username,
                        avatar: playerData.avatar || '/image/default-avatar.jpg',
                        isAI: isAI
                    }
                    seats.value[index].team = 0 // 混战模式无队伍
                    seats.value[index].ready = isAI || playerData.isReady === 1
                }
            }
        }
    } catch (e) {
        console.error('加载房间玩家失败:', e)
    }
}

// WebSocket 消息处理函数
async function handleRoomMessage(data) {
    if (data.type === 'memberJoined') {
        // 有新成员加入（包括AI）
        const newPlayer = {
            id: data.userId,
            username: data.username,
            avatar: data.avatar || '/image/default-avatar.jpg',
            isAI: data.isAI || false
        }

        // 优先使用后端传递的 position 信息确定座位
        let emptySeatIndex = -1

        if (data.position !== undefined && data.position !== null && typeof data.position === 'number') {
            // position 从 1 开始，需要减 1 转换为索引
            const targetIndex = data.position - 1
            // 检查目标位置是否有效且为空
            if (targetIndex >= 0 && targetIndex < seats.value.length && !seats.value[targetIndex].player) {
                emptySeatIndex = targetIndex
            }
        }

        // 如果 position 无效或已被占用，退回到查找空座位
        if (emptySeatIndex === -1) {
            if (selectedMode.value === '2v2' && data.teamId !== undefined && data.teamId !== null) {
                // 优先找与队伍匹配的空座位
                emptySeatIndex = seats.value.findIndex(seat => !seat.player && seat.team === data.teamId)
            }
            // 如果没找到匹配的或不是2v2模式，找任意空座位
            if (emptySeatIndex === -1) {
                emptySeatIndex = seats.value.findIndex(seat => !seat.player)
            }
        }

        if (emptySeatIndex !== -1) {
            seats.value[emptySeatIndex].player = newPlayer
            // 设置队伍信息（2v2模式）
            if (data.teamId !== undefined && data.teamId !== null) {
                seats.value[emptySeatIndex].team = data.teamId
            }
            // AI自动就位
            seats.value[emptySeatIndex].ready = newPlayer.isAI
            ElMessage.success(`${data.username} 已加入房间`)
        }
    } else if (data.type === 'memberLeft') {
        // 有成员离开
        const leftPlayerId = data.userId
        const seatIndex = seats.value.findIndex(seat => seat.player?.id === leftPlayerId)
        if (seatIndex !== -1) {
            seats.value[seatIndex].player = null
            seats.value[seatIndex].ready = false
            ElMessage.info(`${data.username || '某玩家'} 已退出房间`)
        }
    } else if (data.type === 'kicked') {
        // 玩家被踢出（支持字符串和数字类型的ID比较）
        const kickedId = String(data.kickedUserId)
        const currentUserId = String(user.value?.id || '')

        if (kickedId === currentUserId) {
            ElMessage.warning(`你已被房主 ${data.kickerName || ''} 移出房间`)
            roomStore.clearCurrentRoom()
            setTimeout(() => router.push('/user/games'), 1500)
        } else {
            const seatIndex = seats.value.findIndex(seat => String(seat.player?.id || '') === kickedId)
            if (seatIndex !== -1) {
                seats.value[seatIndex].player = null
                seats.value[seatIndex].ready = false
                ElMessage.info(`${data.kickedUserName || '玩家'} 已被移出房间`)
            }
        }
    } else if (data.type === 'roomDestroyed') {
        ElMessage.warning('房主已离开，房间已解散')
        roomStore.clearCurrentRoom()
        router.push('/user/games')
    } else if (data.type === 'teamSwitched') {
        // 玩家切换队伍完成

        // 重新加载玩家列表以获取最新的座位安排
        await loadRoomPlayers()

        // 解锁队伍切换
        switchingTeam.value = false

        // 显示提示消息
        if (data.userId === user.value?.id) {
            ElMessage.success('切换队伍成功')
        } else if (data.message) {
            ElMessage.info(data.message)
        }
    } else if (data.type === 'modeSwitched') {
        // 游戏模式切换完成

        // 更新模式（不触发watch）
        if (data.mode) {
            selectedMode.value = data.mode
        }

        // 直接使用后端返回的完整玩家列表数据，无需重新请求
        if (data.players && Array.isArray(data.players)) {
            // 清空所有座位
            for (const seat of seats.value) {
                seat.player = null
                seat.ready = false
            }

            // 根据模式分配座位
            if (selectedMode.value === '2v2') {
                // 2v2模式：按队伍和position分配座位
                // 左侧座位（0,2）给队伍1（teamId=1），右侧座位（1,3）给队伍2（teamId=2）
                const team1Players = data.players.filter(p => p.teamId === 1).sort((a, b) => a.position - b.position)
                const team2Players = data.players.filter(p => p.teamId === 2).sort((a, b) => a.position - b.position)

                // 队伍1分配到左侧座位
                const team1Seats = [0, 2]
                team1Players.forEach((playerData, idx) => {
                    if (idx < team1Seats.length) {
                        const seatIndex = team1Seats[idx]
                        const isAI = playerData.isAi === 1
                        seats.value[seatIndex].player = {
                            id: playerData.userId,
                            username: playerData.username,
                            avatar: playerData.avatar || '/image/default-avatar.jpg',
                            isAI: isAI
                        }
                        seats.value[seatIndex].team = 1
                        seats.value[seatIndex].ready = isAI || playerData.isReady === 1
                    }
                })

                // 队伍2分配到右侧座位
                const team2Seats = [1, 3]
                team2Players.forEach((playerData, idx) => {
                    if (idx < team2Seats.length) {
                        const seatIndex = team2Seats[idx]
                        const isAI = playerData.isAi === 1
                        seats.value[seatIndex].player = {
                            id: playerData.userId,
                            username: playerData.username,
                            avatar: playerData.avatar || '/image/default-avatar.jpg',
                            isAI: isAI
                        }
                        seats.value[seatIndex].team = 2
                        seats.value[seatIndex].ready = isAI || playerData.isReady === 1
                    }
                })
            } else {
                // 混战模式：按position顺序分配座位
                const sortedPlayers = data.players.sort((a, b) => a.position - b.position)
                sortedPlayers.forEach((playerData, index) => {
                    if (index < seats.value.length) {
                        const isAI = playerData.isAi === 1
                        seats.value[index].player = {
                            id: playerData.userId,
                            username: playerData.username,
                            avatar: playerData.avatar || '/image/default-avatar.jpg',
                            isAI: isAI
                        }
                        seats.value[index].team = 0 // 混战模式无队伍
                        seats.value[index].ready = isAI || playerData.isReady === 1
                    }
                })
            }
        } else {
            await loadRoomPlayers()
        }

        // 解锁页面
        switchingMode.value = false

        ElMessage.success(data.message || '模式切换完成')
    }
}

// 简化的入场动画
function runEnterAnimation() {
    const lobby = document.querySelector('.tank-battle-lobby')
    if (lobby) {
        lobby.style.opacity = '1'
    }
}

// 页面关闭前的清理（使用 sendBeacon 确保请求发送）
function handleBeforeUnload() {
    if (roomInfo.value?.roomCode && user.value?.id) {
        const data = JSON.stringify({
            roomCode: roomInfo.value.roomCode,
            userId: user.value.id,
            username: user.value.username
        })
        // sendBeacon 在页面卸载时也能发送请求
        navigator.sendBeacon('/api/room/leave', new Blob([data], { type: 'application/json' }))
    }
}

onMounted(async () => {
    // 1. 立即显示页面主体，避免黑屏等待
    await nextTick()
    runEnterAnimation()

    // 检查房间信息
    if (!roomInfo.value || !roomInfo.value.roomCode) {
        ElMessage.warning('房间信息不存在，请重新创建房间')
        router.push('/user/games')
        return
    }

    // 2. 并行加载数据
    const loadPromises = [
        loadFriends(),
        loadRoomPlayers()
    ]

    // 地图发现可以不阻塞后续逻辑
    discoverMapFiles().catch(error => {
        console.error('加载地图列表失败:', error)
    })

    await Promise.all(loadPromises)

    // 注册 WebSocket 订阅
    if (wsService && roomInfo.value?.roomCode) {
        wsService.subscribe(`/topic/room/${roomInfo.value.roomCode}`, (msg) => {
            try {
                const data = JSON.parse(msg.body)
                handleRoomMessage(data)
            } catch (e) {
                console.error('处理房间消息失败:', e, msg)
            }
        })
    }

    // 监听页面关闭事件
    window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(async () => {
    stopCooldownTimer()

    // 移除页面关闭监听
    window.removeEventListener('beforeunload', handleBeforeUnload)

    // 退出房间（清理Redis数据）
    if (roomInfo.value?.roomCode && user.value?.id) {
        try {
            await request.post('/room/leave', {
                roomCode: roomInfo.value.roomCode,
                username: user.value.username
            })
        } catch (error) {
            console.error('组件卸载时退出房间失败:', error)
        }
    }

    // 取消 WebSocket 订阅
    if (wsService && roomInfo.value?.roomCode) {
        wsService.unsubscribe(`/topic/room/${roomInfo.value.roomCode}`)

        // 取消游戏开始事件订阅
        if (gameStartSubscription.value) {
            wsService.unsubscribe(`/topic/tankbattle/${roomInfo.value.roomCode}/gameStart`)
            gameStartSubscription.value = null
        }
    }
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
            <aside class="config-panel" aria-label="游戏配置面板">
                <div class="panel-card">
                    <div class="card-title">
                        <Icon icon="mdi:cog-outline" width="18" />
                        游戏设置
                    </div>

                    <!-- 模式选择（只有房主可操作） -->
                    <div class="mode-selector">
                        <button v-for="mode in gameModes" :key="mode.value"
                            :class="['mode-chip', { active: selectedMode === mode.value }]"
                            :disabled="!isRoomOwner || switchingMode" @click="selectedMode = mode.value"
                            :title="isRoomOwner ? mode.description : '只有房主可以切换模式'">
                            <span class="mode-name">{{ mode.label }}</span>
                            <span class="mode-info">{{ mode.description }}</span>
                        </button>
                    </div>

                    <!-- 随机地图展示 -->
                    <div class="map-info">
                        <div class="selector-label">当前地图</div>
                        <div class="map-display">
                            <Icon icon="mdi:map-marker-radius" width="20" />
                            <span>{{ currentMapDisplay }}</span>
                        </div>
                        <span class="map-tip">地图由系统随机分配</span>
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
                            <img :src="friend.avatar || '/image/default-avatar.jpg'"
                                :alt="(friend.name || friend.username) + '的头像'" class="friend-img" />
                            <div class="friend-details">
                                <span class="friend-username">{{ friend.name || friend.username }}</span>
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
                <!-- 模式切换加载遮罩 -->
                <div v-if="switchingMode" class="switching-overlay">
                    <div class="switching-content">
                        <Icon icon="mdi:loading" class="spin" width="48" />
                        <div class="switching-text">正在切换模式...</div>
                        <div class="switching-hint">请稍候，正在重新分配队伍</div>
                    </div>
                </div>

                <div class="area-header">
                    <h2>准备区域</h2>
                    <span class="player-count">{{ occupiedSeatsCount }}/{{ currentModeConfig?.players ||
                        4 }}</span>
                </div>

                <div v-if="selectedMode === '2v2'" class="team-mode-hint">
                    <div class="team-hint team-a-hint">
                        <Icon icon="mdi:shield" width="16" />
                        <span>队伍 A（蓝）</span>
                    </div>
                    <div class="team-hint team-b-hint">
                        <Icon icon="mdi:shield" width="16" />
                        <span>队伍 B（红）</span>
                    </div>
                </div>

                <div :class="['seats-layout', { 'mode-2v2': selectedMode === '2v2' }]">
                    <div v-for="(seat, index) in seats" :key="seat.id"
                        v-show="selectedMode !== 'practice' || index === 0" :class="['player-slot', {
                            filled: seat.player,
                            'team-a': selectedMode === '2v2' && seat.team === 1,
                            'team-b': selectedMode === '2v2' && seat.team === 2
                        }]">

                        <template v-if="seat.player">
                            <div class="slot-content">
                                <div class="player-avatar-wrapper">
                                    <img :src="seat.player.avatar" :alt="seat.player.username + '的头像'"
                                        class="player-img" />
                                    <span class="player-badge" :class="{
                                        'badge-ai': seat.player.isAI,
                                        'badge-me': seat.player.id === user?.id
                                    }">
                                        {{ seat.player.isAI ? `AI #${getAINumber(index)}` : '你' }}
                                    </span>
                                </div>
                                <div class="player-info">
                                    <div class="player-username">{{ seat.player.username }}</div>
                                    <div class="player-status" :class="{ ready: seat.ready }">
                                        <span class="status-dot"></span>
                                        <span class="status-text">{{ seat.ready ? '已就位' : '准备中' }}</span>
                                    </div>
                                </div>

                                <!-- 切换队伍按钮（只有本人且在2v2模式下显示） -->
                                <button
                                    v-if="seat.player.id === user?.id && selectedMode === '2v2' && !seat.player.isAI"
                                    class="btn-switch-team" :disabled="switchingTeam || switchingMode"
                                    @click="switchToTeam(seat.team === 1 ? 2 : 1)"
                                    :title="`切换到${seat.team === 1 ? '队伍B' : '队伍A'}`">
                                    <Icon icon="mdi:swap-horizontal" width="16" />
                                </button>

                                <!-- 踢出按钮（房主踢其他人） -->
                                <button v-if="isRoomOwner && seat.player.id !== user?.id" class="btn-remove"
                                    :disabled="switchingMode || switchingTeam" @click="removePlayer(index)">
                                    <Icon icon="mdi:close" width="16" />
                                </button>
                            </div>
                        </template>

                        <template v-else>
                            <div class="slot-empty">
                                <Icon icon="mdi:account-plus-outline" width="32" />
                                <span>等待玩家</span>
                                <!-- 只有房主才能添加AI -->
                                <button v-if="isRoomOwner" class="btn-add-ai"
                                    :disabled="switchingMode || switchingTeam || addingAI[index]" @click="addAI(index)">
                                    <Icon v-if="addingAI[index]" icon="mdi:loading" class="spin" width="16" />
                                    <Icon v-else icon="mdi:robot" width="16" />
                                    <span>{{ addingAI[index] ? '添加中...' : '添加 AI' }}</span>
                                </button>
                            </div>
                        </template>
                    </div>
                </div>

                <!-- 开始按钮（只有房主可见） -->
                <button v-if="isRoomOwner" class="btn-start-game"
                    :disabled="switchingMode || switchingTeam || !canStartGame" @click="startGame">
                    <Icon icon="mdi:rocket-launch" width="20" />
                    <span>{{ canStartGame ? '开始战斗' : `等待玩家 (${occupiedSeatsCount}/${currentModeConfig?.players})`
                        }}</span>
                </button>
            </main>

            <!-- 右侧：战斗预览/统计 -->
            <aside class="stats-panel" aria-label="战斗信息面板">
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
                            <span class="stat-value">{{ currentMapDisplay }}</span>
                        </div>
                        <div class="stat-row">
                            <span class="stat-label">玩家数量</span>
                            <span class="stat-value">{{ occupiedSeatsCount }}/{{ currentModeConfig?.players }}</span>
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
    background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
    color: #fff;
    display: flex;
    flex-direction: column;
    position: relative;
    opacity: 0;
    transition: opacity 0.5s ease-in;
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
    padding: 20px 32px;
    background: rgba(30, 41, 59, 0.9);
    backdrop-filter: blur(20px);
    border-bottom: 2px solid rgba(148, 163, 184, 0.2);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.btn-back {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 18px;
    background: linear-gradient(135deg, rgba(71, 85, 105, 0.9), rgba(51, 65, 85, 0.9));
    border: 2px solid rgba(148, 163, 184, 0.4);
    border-radius: 10px;
    color: #f1f5f9;
    font-size: 15px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.3s ease;
    backdrop-filter: blur(10px);
}

.btn-back:hover {
    background: linear-gradient(135deg, rgba(100, 116, 139, 0.9), rgba(71, 85, 105, 0.9));
    transform: translateX(-4px);
    border-color: rgba(148, 163, 184, 0.6);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
}

.room-badge {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 24px;
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.3), rgba(37, 99, 235, 0.3));
    border: 2px solid rgba(59, 130, 246, 0.5);
    border-radius: 24px;
    font-size: 16px;
    font-weight: 700;
    letter-spacing: 0.5px;
    color: #f1f5f9;
    box-shadow: 0 4px 12px rgba(59, 130, 246, 0.2);
}

.btn-rules {
    position: relative;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 18px;
    background: linear-gradient(135deg, rgba(71, 85, 105, 0.9), rgba(51, 65, 85, 0.9));
    border: 2px solid rgba(148, 163, 184, 0.4);
    border-radius: 10px;
    color: #f1f5f9;
    font-size: 15px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.3s ease;
    backdrop-filter: blur(10px);
}

.btn-rules:hover {
    background: linear-gradient(135deg, rgba(100, 116, 139, 0.9), rgba(71, 85, 105, 0.9));
    border-color: rgba(148, 163, 184, 0.6);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
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
    background: rgba(30, 41, 59, 0.95);
    backdrop-filter: blur(10px);
    border: 2px solid rgba(148, 163, 184, 0.3);
    border-radius: 16px;
    padding: 20px;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
}

.card-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 16px;
    font-weight: 700;
    margin-bottom: 16px;
    color: #f1f5f9;
    padding-bottom: 12px;
    border-bottom: 2px solid rgba(148, 163, 184, 0.2);
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
    background: linear-gradient(135deg, rgba(88, 101, 242, 0.5), rgba(88, 101, 242, 0.4));
    border-color: rgba(88, 101, 242, 0.7);
    box-shadow: 0 0 20px rgba(88, 101, 242, 0.5);
}

.mode-chip:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(255, 255, 255, 0.1);
    transform: none !important;
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

/* 地图信息 */
.map-info {
    margin-top: 12px;
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.selector-label {
    font-size: 12px;
    color: rgba(255, 255, 255, 0.6);
    text-transform: uppercase;
    letter-spacing: 0.5px;
}

.map-display {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    background: rgba(60, 60, 80, 0.7);
    border: 1px solid rgba(255, 255, 255, 0.2);
    border-radius: 8px;
    font-size: 13px;
    font-weight: 600;
}

.map-tip {
    font-size: 11px;
    color: rgba(255, 255, 255, 0.5);
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
    gap: 12px;
    padding: 14px;
    background: linear-gradient(135deg, rgba(71, 85, 105, 0.8), rgba(51, 65, 85, 0.8));
    backdrop-filter: blur(10px);
    border: 2px solid rgba(148, 163, 184, 0.4);
    border-radius: 12px;
    transition: all 0.3s ease;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
    cursor: pointer;
}

.friend-card:hover {
    background: linear-gradient(135deg, rgba(100, 116, 139, 0.9), rgba(71, 85, 105, 0.9));
    border-color: rgba(148, 163, 184, 0.6);
    transform: translateX(4px);
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.4);
}

.friend-card.offline {
    background: linear-gradient(135deg, rgba(51, 65, 85, 0.6), rgba(30, 41, 59, 0.6));
    border-color: rgba(148, 163, 184, 0.25);
}

.friend-card.offline:hover {
    background: linear-gradient(135deg, rgba(71, 85, 105, 0.7), rgba(51, 65, 85, 0.7));
}

.friend-img {
    width: 42px;
    height: 42px;
    border-radius: 50%;
    object-fit: cover;
    border: 3px solid rgba(148, 163, 184, 0.5);
    background: rgba(15, 23, 42, 0.5);
}

.friend-details {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4px;
    min-width: 0;
}

.friend-username {
    font-size: 15px;
    font-weight: 700;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    color: #f1f5f9;
    letter-spacing: 0.3px;
}

.friend-state {
    font-size: 12px;
    color: #cbd5e1;
    font-weight: 500;
}

.btn-invite {
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, #3b82f6, #2563eb);
    border: 2px solid rgba(59, 130, 246, 0.8);
    border-radius: 8px;
    color: #fff;
    cursor: pointer;
    transition: all 0.3s ease;
    box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}

.btn-invite:hover:not(:disabled) {
    background: linear-gradient(135deg, #60a5fa, #3b82f6);
    transform: scale(1.1);
    box-shadow: 0 4px 12px rgba(59, 130, 246, 0.5);
}

.btn-invite:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    background: linear-gradient(135deg, #64748b, #475569);
    border-color: rgba(100, 116, 139, 0.5);
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
    gap: 10px;
    padding: 40px 10px;
    color: #94a3b8;
    font-size: 14px;
    font-weight: 500;
    background: rgba(51, 65, 85, 0.3);
    border-radius: 12px;
    border: 2px dashed rgba(148, 163, 184, 0.3);
}

/* ===== 中间玩家区域 ===== */
.players-area {
    display: flex;
    flex-direction: column;
    gap: 16px;
    position: relative;
}

/* 模式切换遮罩 */
.switching-overlay {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.85);
    backdrop-filter: blur(8px);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1000;
    border-radius: 16px;
}

.switching-content {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 16px;
    color: #fff;
    animation: fadeIn 0.3s ease;
}

.switching-text {
    font-size: 20px;
    font-weight: 700;
    color: #60a5fa;
}

.switching-hint {
    font-size: 14px;
    color: rgba(255, 255, 255, 0.7);
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
    background-clip: text;
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

/* 队伍模式提示 */
.team-mode-hint {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
    margin-bottom: 12px;
}

.team-hint {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 10px 16px;
    border-radius: 10px;
    font-size: 14px;
    font-weight: 700;
    letter-spacing: 0.5px;
}

.team-a-hint {
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.3), rgba(37, 99, 235, 0.2));
    border: 2px solid rgba(59, 130, 246, 0.6);
    color: #93c5fd;
}

.team-b-hint {
    background: linear-gradient(135deg, rgba(239, 68, 68, 0.3), rgba(220, 38, 38, 0.2));
    border: 2px solid rgba(239, 68, 68, 0.6);
    color: #fca5a5;
}

/* 座位布局 */
.seats-layout {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
    flex: 1;
}

.seats-layout.mode-2v2 {
    gap: 16px 40px;
    position: relative;
}

.seats-layout.mode-2v2::before {
    content: 'VS';
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    font-size: 36px;
    font-weight: 900;
    color: rgba(255, 255, 255, 0.2);
    z-index: 2;
    letter-spacing: 4px;
    text-shadow: 0 0 20px rgba(255, 255, 255, 0.4);
    background: radial-gradient(circle, rgba(15, 23, 42, 0.9) 0%, transparent 70%);
    padding: 12px 20px;
    border-radius: 50%;
    pointer-events: none;
}

.seats-layout.mode-2v2::after {
    content: '';
    position: absolute;
    top: 0;
    left: 50%;
    bottom: 0;
    width: 3px;
    background: linear-gradient(180deg,
            rgba(59, 130, 246, 0.6) 0%,
            rgba(255, 255, 255, 0.4) 50%,
            rgba(239, 68, 68, 0.6) 100%);
    transform: translateX(-50%);
    z-index: 0;
    box-shadow: 0 0 20px rgba(255, 255, 255, 0.3);
    pointer-events: none;
}

.player-slot {
    background: linear-gradient(135deg, rgba(51, 65, 85, 0.8), rgba(30, 41, 59, 0.9));
    backdrop-filter: blur(20px);
    border: 3px solid rgba(148, 163, 184, 0.4);
    border-radius: 20px;
    padding: 20px;
    transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
    position: relative;
    overflow: hidden;
    z-index: 1;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.1);
    min-height: 160px;
    height: 160px;
    display: flex;
    align-items: center;
}

.player-slot:hover {
    transform: translateY(-4px);
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255, 255, 255, 0.15);
    border-color: rgba(148, 163, 184, 0.6);
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
    pointer-events: none;
}

.player-slot:hover::before {
    opacity: 1;
}

.player-slot.team-a {
    border-color: rgba(59, 130, 246, 0.9) !important;
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.3), rgba(37, 99, 235, 0.25)) !important;
    box-shadow: 0 8px 32px rgba(59, 130, 246, 0.5), inset 0 0 40px rgba(59, 130, 246, 0.15), inset 0 1px 0 rgba(255, 255, 255, 0.1) !important;
}

.player-slot.team-a:hover {
    box-shadow: 0 12px 40px rgba(59, 130, 246, 0.6), inset 0 0 50px rgba(59, 130, 246, 0.2), inset 0 1px 0 rgba(255, 255, 255, 0.15) !important;
}

.player-slot.team-a::after {
    content: '队伍 A';
    position: absolute;
    top: 10px;
    left: 10px;
    font-size: 11px;
    font-weight: 800;
    color: rgba(59, 130, 246, 1);
    background: linear-gradient(135deg, rgba(255, 255, 255, 0.95), rgba(255, 255, 255, 0.9));
    padding: 5px 10px;
    border-radius: 12px;
    letter-spacing: 0.5px;
    pointer-events: none;
    box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
    z-index: 2;
}

.player-slot.team-b {
    border-color: rgba(239, 68, 68, 0.9) !important;
    background: linear-gradient(135deg, rgba(239, 68, 68, 0.3), rgba(220, 38, 38, 0.25)) !important;
    box-shadow: 0 8px 32px rgba(239, 68, 68, 0.5), inset 0 0 40px rgba(239, 68, 68, 0.15), inset 0 1px 0 rgba(255, 255, 255, 0.1) !important;
}

.player-slot.team-b:hover {
    box-shadow: 0 12px 40px rgba(239, 68, 68, 0.6), inset 0 0 50px rgba(239, 68, 68, 0.2), inset 0 1px 0 rgba(255, 255, 255, 0.15) !important;
}

.player-slot.team-b::after {
    content: '队伍 B';
    position: absolute;
    top: 10px;
    left: 10px;
    font-size: 11px;
    font-weight: 800;
    color: rgba(239, 68, 68, 1);
    background: linear-gradient(135deg, rgba(255, 255, 255, 0.95), rgba(255, 255, 255, 0.9));
    padding: 5px 10px;
    border-radius: 12px;
    letter-spacing: 0.5px;
    pointer-events: none;
    box-shadow: 0 2px 8px rgba(239, 68, 68, 0.3);
    z-index: 2;
}

.player-slot.filled:not(.team-a):not(.team-b) {
    border-color: rgba(34, 197, 94, 0.8);
    background: rgba(34, 197, 94, 0.2);
    box-shadow: 0 4px 16px rgba(34, 197, 94, 0.3);
}

/* 已填充的座位 */
.slot-content {
    position: relative;
    display: flex;
    align-items: center;
    gap: 20px;
    z-index: 2;
    width: 100%;
}

.player-avatar-wrapper {
    position: relative;
    flex-shrink: 0;
}

.player-img {
    width: 90px;
    height: 90px;
    border-radius: 18px;
    object-fit: cover;
    border: 3px solid rgba(255, 255, 255, 0.3);
    background: linear-gradient(135deg, rgba(255, 255, 255, 0.15), rgba(255, 255, 255, 0.05));
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.5), inset 0 2px 4px rgba(255, 255, 255, 0.15);
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    display: block;
}

.slot-content:hover .player-img {
    transform: scale(1.05);
    border-color: rgba(255, 255, 255, 0.5);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.6);
}

.player-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 10px;
    min-width: 0;
}

.player-username {
    font-size: 22px;
    font-weight: 900;
    color: #ffffff;
    letter-spacing: 0.5px;
    text-shadow: 0 3px 8px rgba(0, 0, 0, 0.5);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    line-height: 1.2;
}

.player-status {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    font-weight: 600;
    color: rgba(255, 255, 255, 0.6);
    transition: all 0.3s ease;
}

.status-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: rgba(251, 191, 36, 0.8);
    box-shadow: 0 0 8px rgba(251, 191, 36, 0.6);
    animation: pulse 2s ease-in-out infinite;
}

.player-status.ready .status-dot {
    background: rgba(34, 197, 94, 0.9);
    box-shadow: 0 0 10px rgba(34, 197, 94, 0.7);
    animation: none;
}

.player-status.ready {
    color: rgba(34, 197, 94, 1);
}

@keyframes pulse {

    0%,
    100% {
        opacity: 1;
        transform: scale(1);
    }

    50% {
        opacity: 0.6;
        transform: scale(0.9);
    }
}

.player-badge {
    position: absolute;
    bottom: -8px;
    left: 50%;
    transform: translateX(-50%);
    padding: 4px 12px;
    background: linear-gradient(135deg, rgba(88, 101, 242, 0.95), rgba(88, 101, 242, 0.85));
    border: 2px solid rgba(255, 255, 255, 0.3);
    border-radius: 12px;
    font-size: 11px;
    font-weight: 800;
    color: #ffffff;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
    letter-spacing: 0.5px;
    white-space: nowrap;
    backdrop-filter: blur(10px);
}

.player-badge.badge-me {
    background: linear-gradient(135deg, rgba(34, 197, 94, 0.95), rgba(34, 197, 94, 0.85));
}

.player-badge.badge-ai {
    background: linear-gradient(135deg, rgba(139, 92, 246, 0.95), rgba(124, 58, 237, 0.85));
}

.btn-switch-team {
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.6), rgba(37, 99, 235, 0.5));
    border: 2px solid rgba(59, 130, 246, 0.8);
    border-radius: 50%;
    color: #fff;
    cursor: pointer;
    transition: all 0.3s ease;
    box-shadow: 0 2px 8px rgba(59, 130, 246, 0.4);
    z-index: 3;
    flex-shrink: 0;
    margin-left: auto;
    margin-right: 8px;
}

.btn-switch-team:hover:not(:disabled) {
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.8), rgba(37, 99, 235, 0.7));
    transform: scale(1.15) rotate(180deg);
    box-shadow: 0 4px 12px rgba(59, 130, 246, 0.6);
}

.btn-switch-team:disabled {
    opacity: 0.4;
    cursor: not-allowed;
    background: rgba(255, 255, 255, 0.1);
    border-color: rgba(255, 255, 255, 0.2);
}

.btn-remove {
    width: 28px;
    height: 28px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, rgba(239, 68, 68, 0.5), rgba(220, 38, 38, 0.4));
    border: 2px solid rgba(239, 68, 68, 0.6);
    border-radius: 50%;
    color: #fff;
    cursor: pointer;
    transition: all 0.3s ease;
    box-shadow: 0 2px 8px rgba(239, 68, 68, 0.3);
    z-index: 3;
    flex-shrink: 0;
    margin-left: auto;
}

.btn-remove:hover:not(:disabled) {
    background: linear-gradient(135deg, rgba(239, 68, 68, 0.8), rgba(220, 38, 38, 0.7));
    transform: scale(1.2) rotate(90deg);
    box-shadow: 0 4px 12px rgba(239, 68, 68, 0.6);
}

.btn-remove:disabled {
    opacity: 0.4;
    cursor: not-allowed;
    background: rgba(255, 255, 255, 0.1);
}

/* 空座位 */
.slot-empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 12px;
    color: rgba(255, 255, 255, 0.6);
    position: relative;
    z-index: 2;
    width: 100%;
    height: 100%;
}

.slot-empty>.iconify {
    opacity: 0.4;
    transition: all 0.3s ease;
}

.slot-empty:hover>.iconify {
    opacity: 0.7;
    transform: scale(1.1);
}

.slot-empty>span {
    font-size: 14px;
    font-weight: 600;
    color: rgba(255, 255, 255, 0.5);
    letter-spacing: 0.5px;
}

.btn-add-ai {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 10px 24px;
    background: linear-gradient(135deg, #8b5cf6, #7c3aed);
    border: 2px solid rgba(139, 92, 246, 0.8);
    border-radius: 10px;
    color: #fff;
    font-size: 14px;
    font-weight: 700;
    cursor: pointer;
    transition: all 0.3s ease;
    box-shadow: 0 4px 12px rgba(139, 92, 246, 0.3);
    letter-spacing: 0.3px;
    position: relative;
    z-index: 3;
}

.btn-add-ai:hover:not(:disabled) {
    background: linear-gradient(135deg, #a78bfa, #8b5cf6);
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(139, 92, 246, 0.5);
}

.btn-add-ai:active:not(:disabled) {
    transform: translateY(0);
}

.btn-add-ai:disabled {
    opacity: 0.6;
    cursor: not-allowed;
    background: linear-gradient(135deg, #64748b, #475569);
    border-color: rgba(100, 116, 139, 0.6);
}

/* 开始按钮 */
.btn-start-game {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    padding: 16px;
    background: linear-gradient(135deg, #15803d, #14532d);
    border: none;
    border-radius: 12px;
    color: #fff;
    font-size: 16px;
    font-weight: 700;
    cursor: pointer;
    transition: all 0.3s;
    box-shadow: 0 4px 20px rgba(34, 197, 94, 0.5);
}

.btn-start-game:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 6px 30px rgba(34, 197, 94, 0.8);
}

.btn-start-game:disabled {
    background: rgba(255, 255, 255, 0.1);
    cursor: not-allowed;
    box-shadow: none;
    opacity: 0.6;
}

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

/* ===== 图标样式 ===== */
.iconify,
svg.iconify {
    display: inline-block;
    vertical-align: middle;
    color: inherit;
    fill: currentColor;
}

button .iconify,
button svg.iconify {
    flex-shrink: 0;
    pointer-events: none;
}

.btn-back .iconify,
.btn-rules .iconify,
.btn-invite .iconify,
.btn-add-ai .iconify,
.btn-start-game .iconify,
.btn-remove .iconify {
    color: inherit;
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
