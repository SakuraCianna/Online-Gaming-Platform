<template>
    <div class="characters-section">
        <div class="characters-container" :style="containerStyle">
            <!-- 紫色矩形小人 -->
            <div class="character purple-rect" :class="{ shaking: isShaking }" :style="getCharacterStyle('purple')">
                <div class="eyes">
                    <div class="eye" :style="getEyeStyle('purple')">
                        <div class="eye-inner" :style="getEyeInnerStyle('purple')">
                            <div class="pupil" :style="getPupilStyle('purple')"></div>
                        </div>
                    </div>
                    <div class="eye" :style="getEyeStyle('purple')">
                        <div class="eye-inner" :style="getEyeInnerStyle('purple')">
                            <div class="pupil" :style="getPupilStyle('purple')"></div>
                        </div>
                    </div>
                </div>
                <div class="mouth" :style="getMouthStyle('purple')"></div>
                <div class="blush left" :style="getBlushStyle()"></div>
                <div class="blush right" :style="getBlushStyle()"></div>
            </div>

            <!-- 黑色矩形小人 -->
            <div class="character black-rect" :class="{ shaking: isShaking }" :style="getCharacterStyle('black')">
                <div class="eyes white-eyes">
                    <div class="eye" :style="getEyeStyle('black')">
                        <div class="eye-inner" :style="getEyeInnerStyle('black')">
                            <div class="pupil dark" :style="getPupilStyle('black')"></div>
                        </div>
                    </div>
                    <div class="eye" :style="getEyeStyle('black')">
                        <div class="eye-inner" :style="getEyeInnerStyle('black')">
                            <div class="pupil dark" :style="getPupilStyle('black')"></div>
                        </div>
                    </div>
                </div>
                <div class="mouth line" :style="getMouthStyle('black')"></div>
            </div>

            <!-- 橙色半圆小人 -->
            <div class="character orange-semi" :class="{ shaking: isShaking }" :style="getCharacterStyle('orange')">
                <div class="eyes">
                    <div class="eye small" :style="getEyeStyle('orange')">
                        <div class="eye-inner" :style="getEyeInnerStyle('orange')"></div>
                    </div>
                    <div class="eye small" :style="getEyeStyle('orange')">
                        <div class="eye-inner" :style="getEyeInnerStyle('orange')"></div>
                    </div>
                </div>
                <div class="mouth smile" :style="getMouthStyle('orange')"></div>
            </div>

            <!-- 黄色圆柱小人 -->
            <div class="character yellow-semi" :class="{ shaking: isShaking }" :style="getCharacterStyle('yellow')">
                <div class="eyes">
                    <div class="eye tiny" :style="getEyeStyle('yellow')">
                        <div class="eye-inner" :style="getEyeInnerStyle('yellow')"></div>
                    </div>
                    <div class="eye tiny" :style="getEyeStyle('yellow')">
                        <div class="eye-inner" :style="getEyeInnerStyle('yellow')"></div>
                    </div>
                </div>
                <div class="mouth o-shape" :style="getMouthStyle('yellow')"></div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'

const props = defineProps({
    isFocused: Boolean,
    showPassword: Boolean,
    // 新增：当前输入的字段类型，用于触发不同表情
    fieldType: {
        type: String,
        default: '' // 'username', 'name', 'email', 'phone', 'avatar', 'password'
    }
})

// 内部状态
const isSad = ref(false)
const isShaking = ref(false)
const headShakePhase = ref(0)
const tearDrop = ref(0) // 眼泪动画
const jumpPhase = ref(0) // 跳跃动画相位
const tick = ref(0) // 强制刷新用
const failurePhase = ref('') // 失败动画阶段: 'shock' | 'confused' | 'deny' | ''

// 持续刷新动画
let tickId = null
const startTick = () => {
    const update = () => {
        tick.value = Date.now()
        tickId = requestAnimationFrame(update)
    }
    update()
}
const stopTick = () => {
    if (tickId) cancelAnimationFrame(tickId)
}

// 表情映射：不同字段触发不同表情
const emotionMap = {
    username: 'curious',    // 好奇探头
    name: 'happy',          // 开心微笑
    email: 'thinking',      // 思考（@符号）
    phone: 'surprised',     // 惊讶（数字多）
    avatar: 'shy',          // 害羞脸红
    password: 'nervous'     // 紧张捂眼
}

// 当前表情
const currentEmotion = computed(() => {
    // 失败动画优先级最高
    if (failurePhase.value === 'shock') return 'surprised'
    if (failurePhase.value === 'confused') return 'thinking'
    if (failurePhase.value === 'deny') return 'nervous'

    if (isSad.value) return 'crying'
    if (props.showPassword) return 'shy'
    if (props.isFocused && props.fieldType) {
        return emotionMap[props.fieldType] || 'curious'
    }
    return 'idle'
})

// 暴露给父组件的方法 - 增强版失败动画
const playFailureAnimation = () => {
    // 阶段1: 惊讶 (0-600ms) - 所有角色跳起来
    failurePhase.value = 'shock'
    jumpPhase.value = 2

    setTimeout(() => {
        jumpPhase.value = 0
    }, 300)

    // 阶段2: 困惑 (600-1500ms) - 互相看看，歪头
    setTimeout(() => {
        failurePhase.value = 'confused'
        let confuseCount = 0
        const confuseInterval = setInterval(() => {
            headShakePhase.value = Math.sin(confuseCount * 0.15) * 0.8
            confuseCount++
            if (confuseCount > 25) {
                clearInterval(confuseInterval)
            }
        }, 35)
    }, 600)

    // 阶段3: 摇头否定 (1500-2500ms) - 坚定地摇头说不
    setTimeout(() => {
        failurePhase.value = 'deny'
        let denyCount = 0
        const denyInterval = setInterval(() => {
            headShakePhase.value = Math.sin(denyCount * 0.5) * 1.5
            denyCount++
            if (denyCount > 20) {
                clearInterval(denyInterval)
                headShakePhase.value = 0
            }
        }, 40)
    }, 1500)

    // 阶段4: 渐变恢复 (2500ms后)
    setTimeout(() => {
        let recovery = 1
        const recoveryInterval = setInterval(() => {
            recovery -= 0.1
            headShakePhase.value *= 0.8
            if (recovery <= 0) {
                clearInterval(recoveryInterval)
                failurePhase.value = ''
                headShakePhase.value = 0
            }
        }, 50)
    }, 2500)
}

// 成功动画（可选）
const playSuccessAnimation = () => {
    let jumpCount = 0
    const jumpInterval = setInterval(() => {
        jumpPhase.value = Math.sin(jumpCount * 0.3) * 1.5
        jumpCount++
        if (jumpCount > 20) {
            clearInterval(jumpInterval)
            jumpPhase.value = 0
        }
    }, 50)
}

// 重置状态 - 页面切换时调用
const resetState = () => {
    isSad.value = false
    isShaking.value = false
    headShakePhase.value = 0
    tearDrop.value = 0
    jumpPhase.value = 0
    failurePhase.value = ''
    // 强制刷新一次，确保根据当前鼠标位置更新表情
    tick.value = Date.now()
}

defineExpose({ playFailureAnimation, playSuccessAnimation, resetState })

// 鼠标位置逻辑
const mousePos = ref({ x: 0, y: 0 })
const smoothMouse = ref({ x: 0, y: 0 })
const mouseDistance = ref(500)
const proximity = computed(() => Math.max(0, Math.min(1, 1 - mouseDistance.value / 600)))

let animationId = null
const lerp = (a, b, t) => a + (b - a) * t

const animate = () => {
    smoothMouse.value.x = lerp(smoothMouse.value.x, mousePos.value.x, 0.15)
    smoothMouse.value.y = lerp(smoothMouse.value.y, mousePos.value.y, 0.15)
    animationId = requestAnimationFrame(animate)
}

const handleMouseMove = (e) => {
    mousePos.value = { x: e.clientX, y: e.clientY }
    const centerX = window.innerWidth * 0.25
    const centerY = window.innerHeight * 0.5
    mouseDistance.value = Math.sqrt(Math.pow(e.clientX - centerX, 2) + Math.pow(e.clientY - centerY, 2))
}

onMounted(() => {
    animate()
    startTick()
    window.addEventListener('mousemove', handleMouseMove)
})

onUnmounted(() => {
    if (animationId) cancelAnimationFrame(animationId)
    stopTick()
    window.removeEventListener('mousemove', handleMouseMove)
})

// 容器样式
const containerStyle = computed(() => {
    let translateX = 0, rotate = 0, scale = 1.2

    if (props.showPassword) {
        translateX = -25; rotate = -6
    } else if (props.isFocused) {
        translateX = 20 + proximity.value * 15
        rotate = 2 + proximity.value * 3
    } else {
        const dx = (smoothMouse.value.x - window.innerWidth * 0.25) / window.innerWidth
        translateX = dx * 20
        rotate = dx * 5
    }

    return {
        transform: `scale(${scale}) translateX(${translateX}px) rotate(${rotate}deg)`,
        transition: 'transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1)'
    }
})

// 角色身体样式 - 根据不同表情状态
const getCharacterStyle = (char) => {
    const t = tick.value // 引用tick确保响应式更新
    const styles = { transition: 'transform 0.18s cubic-bezier(0.25, 0.46, 0.45, 0.94)' }
    const p = proximity.value
    const shake = headShakePhase.value
    const jump = jumpPhase.value
    const emotion = currentEmotion.value

    // 根据表情类型设置不同的身体姿态
    const emotionStyles = {
        crying: {
            purple: `translateX(-20px) translateY(${15 + shake * 3}px) rotate(${-22 + shake * 12}deg) scaleY(0.92)`,
            black: `translateY(${15 + shake * 2}px) rotate(${8 + shake * 10}deg) scaleX(0.95)`,
            orange: `translateY(${18 + shake * 2}px) scaleY(0.75) rotate(${-8 + shake * 8}deg)`,
            yellow: `translateX(8px) translateY(${12 + shake * 2}px) rotate(${-18 + shake * 11}deg) scale(0.9)`
        },
        shy: {
            purple: `translateX(-20px) translateY(15px) rotate(-18deg) scaleY(0.88)`,
            black: `translateY(18px) rotate(22deg) scaleX(0.9)`,
            orange: `translateY(25px) scaleY(0.7) rotate(-5deg)`,
            yellow: `rotate(${25 + Math.sin(Date.now() / 120) * 12}deg) translateY(10px) scale(0.85)`
        },
        curious: {
            purple: `rotate(${8 + p * 12}deg) translateX(${p * 20}px) translateY(${-p * 12 - 8}px) scale(${1 + p * 0.08})`,
            black: `translateY(${-15 - p * 18}px) translateX(${p * 15}px) rotate(${p * 8}deg) scale(${1 + p * 0.05})`,
            orange: `translateY(${-p * 22 - 10}px) scaleY(${1.1 + p * 0.15}) rotate(${p * 5}deg)`,
            yellow: `rotate(${p * 18 + 5}deg) translateY(${-p * 15 - 5}px) scale(${1.05 + p * 0.1})`
        },
        happy: {
            purple: `rotate(${4 + Math.sin(Date.now() / 300) * 5}deg) translateY(${-8 + Math.sin(Date.now() / 250) * 6}px) scale(${1.02 + Math.sin(Date.now() / 400) * 0.03})`,
            black: `translateY(${-10 + Math.sin(Date.now() / 280) * 8}px) rotate(${Math.sin(Date.now() / 320) * 4}deg) scale(1.03)`,
            orange: `translateY(${-12 + Math.sin(Date.now() / 220) * 10}px) scaleY(${1.08 + Math.sin(Date.now() / 300) * 0.05})`,
            yellow: `rotate(${Math.sin(Date.now() / 200) * 8}deg) translateY(${-8 + Math.sin(Date.now() / 180) * 6}px) scale(1.05)`
        },
        thinking: {
            purple: `rotate(${-12 + Math.sin(Date.now() / 600) * 3}deg) translateX(-15px) translateY(8px)`,
            black: `translateY(-10px) rotate(${12 + Math.sin(Date.now() / 500) * 2}deg) translateX(5px)`,
            orange: `translateY(12px) scaleY(0.88) rotate(-8deg)`,
            yellow: `rotate(${-15 + Math.sin(Date.now() / 350) * 8}deg) translateY(5px) translateX(-8px)`
        },
        surprised: {
            purple: `translateY(${-25 + Math.sin(Date.now() / 80) * 5}px) rotate(${8 + Math.sin(Date.now() / 100) * 3}deg) scale(1.1)`,
            black: `translateY(${-30 + Math.sin(Date.now() / 90) * 6}px) scale(1.12) rotate(${Math.sin(Date.now() / 120) * 4}deg)`,
            orange: `translateY(${-20 + Math.sin(Date.now() / 70) * 8}px) scaleY(1.15) scale(1.08)`,
            yellow: `translateY(${-28 + Math.sin(Date.now() / 75) * 6}px) rotate(${Math.sin(Date.now() / 100) * 15}deg) scale(1.15)`
        },
        nervous: {
            purple: `translateX(${Math.sin(Date.now() / 50) * 6}px) rotate(${-8 + Math.sin(Date.now() / 60) * 6}deg) translateY(${Math.sin(Date.now() / 80) * 3}px)`,
            black: `translateY(${8 + Math.sin(Date.now() / 70) * 4}px) rotate(${Math.sin(Date.now() / 55) * 8}deg)`,
            orange: `translateY(${12 + Math.sin(Date.now() / 65) * 5}px) scaleY(0.85) translateX(${Math.sin(Date.now() / 45) * 5}px)`,
            yellow: `rotate(${Math.sin(Date.now() / 40) * 12}deg) scale(${0.92 + Math.sin(Date.now() / 100) * 0.05})`
        }
    }

    if (emotionStyles[emotion]) {
        styles.transform = emotionStyles[emotion][char]
    } else {
        // 默认idle状态
        const dx = (smoothMouse.value.x - window.innerWidth * 0.25) / 500
        const dy = (smoothMouse.value.y - window.innerHeight * 0.5) / 500
        styles.transform = `translateX(${dx * 3}px) translateY(${dy * 2}px) rotate(${dx * 2}deg)`
    }
    return styles
}

// 眼睛位置样式
const getEyeStyle = (char) => {
    if (props.showPassword) {
        return { transform: 'translateX(-5px) translateY(-2px)', transition: 'transform 0.3s ease-out' }
    }

    const centerX = window.innerWidth * 0.25
    const centerY = window.innerHeight * 0.5
    const dx = smoothMouse.value.x - centerX
    const dy = smoothMouse.value.y - centerY
    const dist = Math.sqrt(dx * dx + dy * dy) || 1

    const maxMove = 4 + proximity.value * 3
    const moveX = (dx / dist) * maxMove * Math.min(1, dist / 200)
    const moveY = (dy / dist) * maxMove * Math.min(1, dist / 200)

    return {
        transform: `translate(${moveX}px, ${moveY}px)`,
        transition: 'transform 0.08s ease-out'
    }
}

// 眼睛内部样式 - 根据表情状态变化
const getEyeInnerStyle = (char) => {
    const p = proximity.value
    const emotion = currentEmotion.value
    let scaleX = 1, scaleY = 1, borderRadius = '50%', translateY = 0, rotate = 0

    const emotionEyeStyles = {
        crying: {
            purple: { scaleX: 1.5, scaleY: 0.1, rotate: -25, borderRadius: '50% 50% 0 0' },
            black: { scaleY: 0.15, translateY: -5, borderRadius: '50% 50% 0 0' },
            orange: { scaleX: 1.6, scaleY: 0.08, rotate: 20, borderRadius: '50% 50% 0 0' },
            yellow: { scaleX: 0.5, scaleY: 0.5 }
        },
        shy: {
            purple: { scaleY: 0.08, scaleX: 1.3, borderRadius: '50% 50% 0 0' },
            black: { scaleX: 1.2, scaleY: 0.4, borderRadius: '50% 50% 0 0' },
            orange: { scaleX: 1.5, scaleY: 0.25, borderRadius: '50% 50% 0 0' },
            yellow: { scaleX: 1.6, scaleY: 1.6 }
        },
        curious: {
            purple: { scaleX: 1.35 + p * 0.3, scaleY: 1.35 + p * 0.3 },
            black: { scaleX: 1.3 + p * 0.25, scaleY: 1.3 + p * 0.25 },
            orange: { scaleX: 1.25 + p * 0.3, scaleY: 1.25 + p * 0.3 },
            yellow: { scaleX: 1.4 + p * 0.35, scaleY: 1.4 + p * 0.35 }
        },
        happy: {
            purple: { scaleX: 1.15, scaleY: 0.5, borderRadius: '50% 50% 0 0' },
            black: { scaleX: 1.2, scaleY: 0.6, borderRadius: '50% 50% 0 0' },
            orange: { scaleX: 1.1, scaleY: 0.55, borderRadius: '50% 50% 0 0' },
            yellow: { scaleX: 1.15, scaleY: 0.65, borderRadius: '50% 50% 0 0' }
        },
        thinking: {
            purple: { scaleX: 0.85, scaleY: 1.2, translateY: -4, rotate: -8 },
            black: { scaleX: 1.1, scaleY: 0.7, rotate: 10 },
            orange: { scaleX: 0.8, scaleY: 1.25, translateY: 2 },
            yellow: { scaleX: 1.1, scaleY: 0.9, rotate: -8 }
        },
        surprised: {
            purple: { scaleX: 1.5, scaleY: 1.5 },
            black: { scaleX: 1.6, scaleY: 1.6 },
            orange: { scaleX: 1.4, scaleY: 1.4 },
            yellow: { scaleX: 1.7, scaleY: 1.7 }
        },
        nervous: {
            purple: { scaleX: 0.9, scaleY: 0.7, rotate: -5 },
            black: { scaleX: 1.0, scaleY: 0.6 },
            orange: { scaleX: 0.85, scaleY: 0.65, rotate: 8 },
            yellow: { scaleX: 0.8, scaleY: 0.8 }
        }
    }

    if (emotionEyeStyles[emotion]) {
        const s = emotionEyeStyles[emotion][char]
        scaleX = s?.scaleX || 1
        scaleY = s?.scaleY || 1
        translateY = s?.translateY || 0
        rotate = s?.rotate || 0
        borderRadius = s?.borderRadius || '50%'
    } else {
        scaleX = 1 + p * 0.15
        scaleY = 1 + p * 0.15
    }

    return {
        transform: `scaleX(${scaleX}) scaleY(${scaleY}) translateY(${translateY}px) rotate(${rotate}deg) translateZ(0)`,
        borderRadius,
        transition: 'transform 0.12s ease-out, border-radius 0.12s ease'
    }
}

// 瞳孔样式
const getPupilStyle = (char) => {
    const p = proximity.value
    const scale = 1 + p * 0.2
    return {
        transform: `scale(${scale}) translateZ(0)`,
        transition: 'transform 0.08s ease-out'
    }
}

// 嘴巴样式 - 根据表情状态变化
const getMouthStyle = (char) => {
    const p = proximity.value
    const emotion = currentEmotion.value
    let width, height, borderRadius, extra = {}

    const baseSize = { purple: [16, 8], black: [24, 4], orange: [24, 12], yellow: [10, 10] }
    const [baseW, baseH] = baseSize[char] || [16, 8]

    const emotionMouthStyles = {
        crying: {
            purple: { w: 32, h: 18, r: '16px 16px 0 0' },
            black: { w: 24, h: 12, r: '6px 6px 0 0' },
            orange: { w: 36, h: 16, r: '18px 18px 0 0' },
            yellow: { w: 26, h: 8, r: '0', isWavy: true, rotate: -10 }
        },
        shy: {
            purple: { w: 22, h: 4, r: '2px', isWavy: true },
            black: { w: 24, h: 10, r: '10px 10px 0 0' },
            orange: { w: 22, h: 22, r: '50%' },
            yellow: { w: 20, h: 24, r: '50%' }
        },
        curious: {
            purple: { w: baseW + p * 10, h: baseH + p * 12, r: `0 0 ${12 + p * 15}px ${12 + p * 15}px` },
            black: { w: baseW + p * 8, h: baseH + p * 6, r: `0 0 ${6 + p * 8}px ${6 + p * 8}px` },
            orange: { w: baseW + p * 14, h: baseH + p * 16, r: `0 0 ${18 + p * 20}px ${18 + p * 20}px` },
            yellow: { w: baseW + p * 12, h: baseH + p * 12, r: '50%' }
        },
        happy: {
            purple: { w: 24, h: 14, r: '0 0 16px 16px' },
            black: { w: 26, h: 10, r: '0 0 14px 14px' },
            orange: { w: 36, h: 16, r: '0 0 28px 28px' },
            yellow: { w: 16, h: 16, r: '50%' }
        },
        thinking: {
            purple: { w: 14, h: 14, r: '50%', offsetX: 12 },
            black: { w: 20, h: 4, r: '2px', rotate: 8 },
            orange: { w: 16, h: 16, r: '50%', offsetX: -10 },
            yellow: { w: 10, h: 10, r: '50%' }
        },
        surprised: {
            purple: { w: 26, h: 32, r: '50%' },
            black: { w: 24, h: 30, r: '50%' },
            orange: { w: 30, h: 36, r: '50%' },
            yellow: { w: 22, h: 28, r: '50%' }
        },
        nervous: {
            purple: { w: 24, h: 5, r: '2px', isWavy: true },
            black: { w: 22, h: 4, r: '2px', rotate: -5 },
            orange: { w: 20, h: 18, r: '50%' },
            yellow: { w: 16, h: 16, r: '50%' }
        }
    }

    if (emotionMouthStyles[emotion]) {
        const m = emotionMouthStyles[emotion][char]
        width = m.w; height = m.h; borderRadius = m.r

        if (m.isWavy) {
            return {
                width: `${width}px`,
                height: `${height}px`,
                borderRadius: '0',
                background: 'transparent',
                borderBottom: char === 'yellow' ? '3px solid #78350F' : '3px solid currentColor',
                borderRadius: '0 0 8px 8px / 0 0 4px 4px',
                transform: `rotate(${m.rotate || -5}deg) translateX(${m.offsetX || 0}px)`,
                transition: 'all 0.3s cubic-bezier(0.34, 1.2, 0.64, 1)'
            }
        }

        if (m.offsetX || m.rotate) {
            extra.transform = `translateX(${m.offsetX || 0}px) rotate(${m.rotate || 0}deg)`
        }
    } else {
        // idle状态
        width = baseW + p * 4
        height = baseH + p * 4

        if (char === 'purple') {
            height = baseH + p * 6
            borderRadius = `0 0 ${8 + p * 8}px ${8 + p * 8}px`
        } else if (char === 'orange') {
            height = baseH + p * 8
            borderRadius = `0 0 ${12 + p * 12}px ${12 + p * 12}px`
        } else if (char === 'yellow') {
            width = baseW + p * 6
            height = baseH + p * 6
        } else {
            borderRadius = char === 'black' ? '2px' : '50%'
        }
    }

    return {
        width: `${width}px`,
        height: `${height}px`,
        borderRadius: borderRadius || (char === 'orange' ? '0 0 24px 24px' : '50%'),
        transition: 'all 0.15s cubic-bezier(0.25, 0.46, 0.45, 0.94)',
        transform: 'translateZ(0)',
        ...extra
    }
}

// 腮红样式 - 根据表情状态变化
const getBlushStyle = () => {
    const emotion = currentEmotion.value
    let opacity = 0, color = '255, 120, 120'

    const blushMap = {
        crying: { opacity: 0.85, color: '255, 80, 80' },
        shy: { opacity: 0.95, color: '255, 110, 130' },
        happy: { opacity: 0.7, color: '255, 140, 160' },
        nervous: { opacity: 0.65, color: '255, 120, 120' },
        surprised: { opacity: 0.5, color: '255, 150, 180' },
        curious: { opacity: 0.4, color: '255, 160, 170' },
        thinking: { opacity: 0.3, color: '255, 170, 180' }
    }

    if (blushMap[emotion]) {
        opacity = blushMap[emotion].opacity
        color = blushMap[emotion].color
    } else {
        opacity = proximity.value * 0.3
    }

    return {
        background: `rgba(${color}, ${opacity})`,
        transition: 'background 0.2s ease'
    }
}
</script>

<style scoped>
/* 左侧角色区域容器 */
.characters-section {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(180deg, #e8e8e8 0%, #f5f5f5 100%);
    padding: 40px;
    position: relative;
    overflow: hidden;
}

/* 噪点纹理 */
.characters-section::before {
    content: '';
    position: absolute;
    inset: 0;
    background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)'/%3E%3C/svg%3E");
    opacity: 0.03;
    pointer-events: none;
    z-index: 1;
}

/* 柔和光晕 */
.characters-section::after {
    content: '';
    position: absolute;
    width: 500px;
    height: 500px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(139, 92, 246, 0.12) 0%, rgba(249, 115, 22, 0.08) 40%, transparent 70%);
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    pointer-events: none;
    z-index: 0;
    animation: glowPulse 8s ease-in-out infinite;
}

@keyframes glowPulse {

    0%,
    100% {
        transform: translate(-50%, -50%) scale(1);
        opacity: 0.8;
    }

    50% {
        transform: translate(-50%, -50%) scale(1.15);
        opacity: 1;
    }
}

.characters-container {
    position: relative;
    width: 380px;
    height: 320px;
    display: flex;
    justify-content: center;
    align-items: flex-end;
    transform: scale(1.2);
    will-change: transform;
    transform-style: preserve-3d;
    backface-visibility: hidden;
    z-index: 2;
}

/* 角色基础样式 */
.character {
    position: absolute;
    bottom: 0;
    transition: transform 0.25s cubic-bezier(0.34, 1.2, 0.64, 1);
    transform-origin: bottom center;
    filter: drop-shadow(0 10px 10px rgba(0, 0, 0, 0.1));
    will-change: transform, filter;
    transform-style: preserve-3d;
    backface-visibility: hidden;
}

.character.shaking {
    animation: shake 0.5s ease-in-out;
}

/* 动画定义 */
@keyframes shake {

    0%,
    100% {
        transform: translateX(0) rotate(0);
    }

    20% {
        transform: translateX(-10px) rotate(-5deg);
    }

    40% {
        transform: translateX(10px) rotate(5deg);
    }

    60% {
        transform: translateX(-10px) rotate(-5deg);
    }

    80% {
        transform: translateX(10px) rotate(5deg);
    }
}

@keyframes blink {

    0%,
    48%,
    52%,
    100% {
        transform: scaleY(1);
    }

    50% {
        transform: scaleY(0.1);
    }
}

@keyframes sway {

    0%,
    100% {
        transform: rotate(-2deg);
    }

    50% {
        transform: rotate(2deg);
    }
}

@keyframes bounce {

    0%,
    100% {
        transform: scaleY(1) scaleX(1);
    }

    50% {
        transform: scaleY(0.96) scaleX(1.04);
    }
}

@keyframes float {

    0%,
    100% {
        transform: translateY(0);
    }

    50% {
        transform: translateY(-10px);
    }
}

@keyframes smile-move {

    0%,
    100% {
        border-radius: 0 0 20px 20px;
        transform: scaleX(1);
    }

    50% {
        border-radius: 0 0 25px 25px;
        transform: scaleX(1.2);
    }
}

/* 眼睛通用 */
.eyes {
    display: flex;
    justify-content: center;
    z-index: 10;
    position: relative;
}

.eye {
    position: relative;
    transition: transform 0.08s ease-out;
    will-change: transform;
    transform: translateZ(0);
}

.eye-inner {
    width: 100%;
    height: 100%;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    animation: blink 4s infinite;
    will-change: transform, border-radius;
    transition: transform 0.15s cubic-bezier(0.34, 1.2, 0.64, 1), border-radius 0.15s ease;
    transform: translateZ(0);
}

.pupil {
    will-change: transform;
    transition: transform 0.1s ease-out;
    transform: translateZ(0);
}

/* 嘴巴通用 */
.mouth {
    will-change: transform, width, height, border-radius;
    transition: all 0.2s cubic-bezier(0.34, 1.2, 0.64, 1);
    transform: translateZ(0);
}

/* 腮红通用 */
.blush {
    position: absolute;
    width: 18px;
    height: 10px;
    border-radius: 50%;
    will-change: background;
    transition: background 0.3s ease;
    transform: translateZ(0);
}

.blush.left {
    left: 15px;
    top: 100px;
}

.blush.right {
    right: 15px;
    top: 100px;
}

/* 紫色矩形 */
.purple-rect {
    width: 120px;
    height: 240px;
    background: #8B5CF6;
    border-radius: 24px;
    left: 40px;
    z-index: 2;
    animation: sway 6s ease-in-out infinite;
}

.purple-rect .eyes {
    gap: 20px;
    margin-top: 70px;
}

.purple-rect .eye {
    width: 20px;
    height: 20px;
}

.purple-rect .eye-inner {
    background: #fff;
}

.purple-rect .pupil {
    width: 10px;
    height: 10px;
    background: #1e1b4b;
    border-radius: 50%;
}

.purple-rect .mouth {
    width: 16px;
    height: 8px;
    background: #1e1b4b;
    border-radius: 0 0 16px 16px;
    margin: 25px auto 0;
}

/* 黑色矩形 */
.black-rect {
    width: 100px;
    height: 160px;
    background: #1F2937;
    border-radius: 20px;
    left: 140px;
    z-index: 3;
    animation: float 5s ease-in-out infinite;
    animation-delay: 1s;
}

.black-rect .white-eyes {
    gap: 16px;
    margin-top: 45px;
}

.black-rect .eye {
    width: 24px;
    height: 24px;
}

.black-rect .eye-inner {
    background: #fff;
}

.black-rect .pupil.dark {
    width: 12px;
    height: 12px;
    background: #111;
    border-radius: 50%;
}

.black-rect .mouth.line {
    width: 24px;
    height: 4px;
    background: #4B5563;
    border-radius: 2px;
    margin: 20px auto 0;
}

/* 橙色半圆 */
.orange-semi {
    width: 160px;
    height: 90px;
    background: #F97316;
    border-radius: 80px 80px 0 0;
    left: -10px;
    z-index: 4;
    animation: bounce 4s ease-in-out infinite;
}

.orange-semi .eyes {
    gap: 40px;
    margin-top: 35px;
}

.orange-semi .eye.small {
    width: 14px;
    height: 14px;
}

.orange-semi .eye-inner {
    background: #7C2D12;
}

.orange-semi .pupil {
    display: none;
}

.orange-semi .mouth.smile {
    width: 24px;
    height: 12px;
    border: 3px solid #7C2D12;
    border-top: none;
    border-radius: 0 0 24px 24px;
    margin: 8px auto 0;
    background: transparent;
    animation: smile-move 3s ease-in-out infinite;
}

/* 黄色圆柱 */
.yellow-semi {
    width: 110px;
    height: 130px;
    background: #FBBF24;
    border-radius: 55px 55px 0 0;
    left: 220px;
    z-index: 4;
    animation: bounce 4.5s ease-in-out infinite reverse;
}

.yellow-semi .eyes {
    gap: 28px;
    margin-top: 40px;
}

.yellow-semi .eye.tiny {
    width: 12px;
    height: 12px;
}

.yellow-semi .eye-inner {
    background: #78350F;
}

.yellow-semi .pupil {
    display: none;
}

.yellow-semi .mouth.o-shape {
    width: 10px;
    height: 10px;
    background: #78350F;
    border-radius: 50%;
    margin: 15px auto 0;
    transition: all 0.3s ease;
}

/* 腮红基础样式 */
.blush {
    position: absolute;
    width: 18px;
    height: 10px;
    border-radius: 50%;
    top: 100px;
}

.blush.left {
    left: 15px;
}

.blush.right {
    right: 15px;
}

/* 性能优化 */
.characters-container,
.character,
.eye,
.eye-inner,
.pupil,
.mouth,
.blush {
    will-change: transform;
    backface-visibility: hidden;
}

/* 响应式 - 只保留必要的，主要由父级控制布局 */
@media (max-width: 900px) {
    .characters-section {
        min-height: 300px;
    }

    .characters-container {
        transform: scale(0.7);
    }
}
</style>
