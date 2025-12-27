<template>
  <div class="not-found">
    <!-- 背景动画元素 -->
    <div class="game-background">
      <div class="floating-particles"></div>
      <div class="game-grid-bg"></div>
      <div class="error-particles"></div>
      <!-- 全屏404背景文字 -->
      <div class="fullscreen-404-bg">
        <div class="bg-404-text" v-for="i in 30" :key="i" :style="getRandom404Style(i)">404</div>
      </div>

      <div class="geometric-shapes">
        <div class="shape shape-circle" v-for="i in 8" :key="`circle-${i}`" :style="getShapeStyle('circle', i)"></div>
        <div class="shape shape-triangle" v-for="i in 6" :key="`triangle-${i}`" :style="getShapeStyle('triangle', i)">
        </div>
        <div class="shape shape-square" v-for="i in 5" :key="`square-${i}`" :style="getShapeStyle('square', i)"></div>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="main-content">
      <div class="error-container">
        <div class="error-code" ref="errorCodeRef">404</div>
        <div class="error-message" ref="errorMessageRef">页面未找到</div>
        <div class="error-description" ref="errorDescRef">
          看起来你进入了一个不存在的关卡！
          <br>让我们回到主界面继续冒险吧！
        </div>

        <div class="action-buttons" ref="actionButtonsRef">
          <button @click="goHome" class="home-btn">
            <span class="btn-icon">🏠</span>
            <span class="btn-text">回到主页</span>
          </button>
          <button @click="goBack" class="back-btn">
            <span class="btn-icon">⬅️</span>
            <span class="btn-text">返回上页</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 游戏元素装饰 -->
    <div class="game-elements">
      <div class="floating-game-icon" ref="gameIcon1"></div>
      <div class="floating-game-icon" ref="gameIcon2"></div>
      <div class="floating-game-icon" ref="gameIcon3"></div>
      <div class="floating-game-icon" ref="gameIcon4"></div>
      <div class="floating-game-icon" ref="gameIcon5">⚡</div>
      <div class="floating-game-icon" ref="gameIcon6"></div>
      <div class="floating-game-icon" ref="gameIcon7"></div>
      <div class="floating-game-icon" ref="gameIcon8"></div>
      <div class="floating-game-icon" ref="gameIcon9"></div>
      <div class="floating-game-icon" ref="gameIcon10"></div>
      <div class="floating-game-icon" ref="gameIcon11"></div>
      <div class="floating-game-icon" ref="gameIcon12">🎭</div>
    </div>

    <!-- 错误图标装饰 -->
    <div class="error-icons">
      <div class="error-icon" v-for="i in 20" :key="`error-icon-${i}`" :style="getErrorIconStyle(i)">
        {{ getErrorIcon(i) }}
      </div>
    </div>

    <!-- 警告图标装饰 -->
    <div class="warning-icons">
      <div class="warning-icon" v-for="i in 15" :key="`warning-icon-${i}`" :style="getWarningIconStyle(i)">
        {{ getWarningIcon(i) }}
      </div>
    </div>

    <!-- 浮动404数字 -->
    <div class="floating-404-numbers">
      <div class="floating-404" v-for="i in 25" :key="`404-${i}`" :style="getFloating404Style(i)">404</div>
    </div>

    <!-- 错误提示气泡 -->
    <div class="error-bubbles">
      <div class="error-bubble" v-for="i in 12" :key="`bubble-${i}`" :style="getBubbleStyle(i)">
        <span class="bubble-text">{{ getBubbleText(i) }}</span>
      </div>
    </div>

    <!-- 动态光效 -->
    <div class="light-effects">
      <div class="light-beam" v-for="i in 6" :key="`light-${i}`" :style="getLightStyle(i)"></div>
    </div>

    <!-- 粒子系统 -->
    <div class="particle-system">
      <div class="particle" v-for="i in 50" :key="`particle-${i}`" :style="getParticleStyle(i)"></div>
    </div>

    <!-- 错误符号装饰 -->
    <div class="error-symbols">
      <div class="error-symbol" v-for="i in 18" :key="`symbol-${i}`" :style="getErrorSymbolStyle(i)">
        {{ getErrorSymbol(i) }}
      </div>
    </div>

    <!-- 故障效果元素 -->
    <div class="glitch-elements">
      <div class="glitch-element" v-for="i in 10" :key="`glitch-${i}`" :style="getGlitchStyle(i)">
        {{ getGlitchText(i) }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ref, onMounted } from 'vue'
import { gsap } from 'gsap'

const router = useRouter()

// 模板引用
const errorCodeRef = ref(null)
const errorMessageRef = ref(null)
const errorDescRef = ref(null)
const actionButtonsRef = ref(null)
const gameIcon1 = ref(null)
const gameIcon2 = ref(null)
const gameIcon3 = ref(null)
const gameIcon4 = ref(null)
const gameIcon5 = ref(null)
const gameIcon6 = ref(null)
const gameIcon7 = ref(null)
const gameIcon8 = ref(null)
const gameIcon9 = ref(null)
const gameIcon10 = ref(null)
const gameIcon11 = ref(null)
const gameIcon12 = ref(null)

// 生成随机404背景样式
const getRandom404Style = (index) => {
  const left = Math.random() * 100
  const top = Math.random() * 100
  const size = 20 + Math.random() * 40
  const opacity = 0.05 + Math.random() * 0.1
  const rotation = Math.random() * 360

  return {
    left: `${left}%`,
    top: `${top}%`,
    fontSize: `${size}px`,
    opacity: opacity,
    transform: `rotate(${rotation}deg)`
  }
}

// 生成浮动404样式
const getFloating404Style = (index) => {
  const left = Math.random() * 100
  const top = Math.random() * 100
  const size = 15 + Math.random() * 25
  const delay = Math.random() * 5

  return {
    left: `${left}%`,
    top: `${top}%`,
    fontSize: `${size}px`,
    animationDelay: `${delay}s`
  }
}

// 生成几何图形样式
const getShapeStyle = (type, index) => {
  const left = Math.random() * 100
  const top = Math.random() * 100
  const size = 20 + Math.random() * 60
  const opacity = 0.1 + Math.random() * 0.2
  const rotation = Math.random() * 360

  return {
    left: `${left}%`,
    top: `${top}%`,
    width: `${size}px`,
    height: `${size}px`,
    opacity: opacity,
    transform: `rotate(${rotation}deg)`,
    animationDelay: `${index * 0.2}s`
  }
}

// 生成错误图标样式
const getErrorIconStyle = (index) => {
  const left = Math.random() * 100
  const top = Math.random() * 100
  const size = 1.5 + Math.random() * 2
  const delay = Math.random() * 4

  return {
    left: `${left}%`,
    top: `${top}%`,
    fontSize: `${size}rem`,
    animationDelay: `${delay}s`
  }
}

// 生成警告图标样式
const getWarningIconStyle = (index) => {
  const left = Math.random() * 100
  const top = Math.random() * 100
  const size = 1.2 + Math.random() * 1.8
  const delay = Math.random() * 3

  return {
    left: `${left}%`,
    top: `${top}%`,
    fontSize: `${size}rem`,
    animationDelay: `${delay}s`
  }
}

// 生成错误符号样式
const getErrorSymbolStyle = (index) => {
  const left = Math.random() * 100
  const top = Math.random() * 100
  const size = 1 + Math.random() * 1.5
  const delay = Math.random() * 5

  return {
    left: `${left}%`,
    top: `${top}%`,
    fontSize: `${size}rem`,
    animationDelay: `${delay}s`
  }
}

// 生成故障效果样式
const getGlitchStyle = (index) => {
  const left = Math.random() * 100
  const top = Math.random() * 100
  const size = 0.8 + Math.random() * 1.2
  const delay = Math.random() * 6

  return {
    left: `${left}%`,
    top: `${top}%`,
    fontSize: `${size}rem`,
    animationDelay: `${delay}s`
  }
}

// 生成气泡样式
const getBubbleStyle = (index) => {
  const left = Math.random() * 100
  const top = Math.random() * 100
  const size = 80 + Math.random() * 120
  const delay = Math.random() * 3

  return {
    left: `${left}%`,
    top: `${top}%`,
    width: `${size}px`,
    height: `${size}px`,
    animationDelay: `${delay}s`
  }
}

// 生成光效样式
const getLightStyle = (index) => {
  const left = Math.random() * 100
  const top = Math.random() * 100
  const rotation = Math.random() * 360
  const delay = Math.random() * 2

  return {
    left: `${left}%`,
    top: `${top}%`,
    transform: `rotate(${rotation}deg)`,
    animationDelay: `${delay}s`
  }
}

// 生成粒子样式
const getParticleStyle = (index) => {
  const left = Math.random() * 100
  const top = Math.random() * 100
  const size = 2 + Math.random() * 4
  const delay = Math.random() * 5

  return {
    left: `${left}%`,
    top: `${top}%`,
    width: `${size}px`,
    height: `${size}px`,
    animationDelay: `${delay}s`
  }
}

// 获取错误图标
const getErrorIcon = (index) => {
  const icons = ['❌', '', '⛔', '', '', '', '⚡', '💣', '🎯', '🎪', '🎭', '🎨', '🎵', '🎬', '🎪', '🎭', '🎮', '🎲', '🎯', '🏆']
  return icons[index % icons.length]
}

// 获取警告图标
const getWarningIcon = (index) => {
  const icons = ['⚠️', '🚨', '🔴', '🟠', '🟡', '🔶', '🔷', '💠', '🔔', '📢', '🎺', '🔊', '📣', '🔔', '📢']
  return icons[index % icons.length]
}

// 获取错误符号
const getErrorSymbol = (index) => {
  const symbols = ['✖', '✗', '✘', '✕', '✖', '✗', '✘', '✕', '✖', '✗', '✘', '✕', '✖', '✗', '✘', '✕', '✖', '✗']
  return symbols[index % symbols.length]
}

// 获取故障文本
const getGlitchText = (index) => {
  const texts = ['ERROR', 'FAIL', 'BUG', 'CRASH', 'ERROR', 'FAIL', 'BUG', 'CRASH', 'ERROR', 'FAIL']
  return texts[index % texts.length]
}

// 获取气泡文本
const getBubbleText = (index) => {
  const texts = [
    '页面走丢了！', '找不到路了！', '迷路了！', '404！',
    '页面不存在！', '走错路了！', '找不到！', '404错误！',
    '页面丢失！', '路径错误！', '404！', '页面未找到！'
  ]
  return texts[index % texts.length]
}

const goHome = () => {
  // 按钮点击动画
  gsap.to('.home-btn', {
    duration: 0.2,
    scale: 0.95,
    yoyo: true,
    repeat: 1,
    onComplete: () => {
      router.push('/user')
    }
  })
}

const goBack = () => {
  // 按钮点击动画
  gsap.to('.back-btn', {
    duration: 0.2,
    scale: 0.95,
    yoyo: true,
    repeat: 1,
    onComplete: () => {
      router.go(-1)
    }
  })
}

// 页面入场动画
const initAnimations = () => {
  // 设置初始状态
  gsap.set([errorCodeRef.value, errorMessageRef.value, errorDescRef.value, actionButtonsRef.value], {
    opacity: 0,
    y: 50
  })

  gsap.set([gameIcon1.value, gameIcon2.value, gameIcon3.value, gameIcon4.value, gameIcon5.value, gameIcon6.value, gameIcon7.value, gameIcon8.value, gameIcon9.value, gameIcon10.value, gameIcon11.value, gameIcon12.value], {
    opacity: 0,
    scale: 0
  })

  // 创建时间轴
  const tl = gsap.timeline()

  // 错误代码动画
  tl.to(errorCodeRef.value, {
    duration: 1,
    opacity: 1,
    y: 0,
    ease: "back.out(1.7)"
  })

  // 错误信息动画
  tl.to(errorMessageRef.value, {
    duration: 0.8,
    opacity: 1,
    y: 0,
    ease: "back.out(1.7)"
  }, "-=0.5")

  // 错误描述动画
  tl.to(errorDescRef.value, {
    duration: 0.8,
    opacity: 1,
    y: 0,
    ease: "power2.out"
  }, "-=0.3")

  // 按钮动画
  tl.to(actionButtonsRef.value, {
    duration: 0.8,
    opacity: 1,
    y: 0,
    ease: "back.out(1.7)"
  }, "-=0.4")

  // 游戏图标动画
  tl.to([gameIcon1.value, gameIcon2.value, gameIcon3.value, gameIcon4.value, gameIcon5.value, gameIcon6.value, gameIcon7.value, gameIcon8.value, gameIcon9.value, gameIcon10.value, gameIcon11.value, gameIcon12.value], {
    duration: 0.6,
    opacity: 1,
    scale: 1,
    stagger: 0.1,
    ease: "back.out(1.7)"
  }, "-=0.6")

  // 添加浮动动画
  gsap.to('.floating-particles', {
    duration: 20,
    rotation: 360,
    repeat: -1,
    ease: "none"
  })

  // 错误粒子动画
  gsap.to('.error-particles', {
    duration: 3,
    y: -20,
    repeat: -1,
    yoyo: true,
    ease: "power1.inOut"
  })

  // 游戏图标浮动动画
  const gameIcons = [gameIcon1.value, gameIcon2.value, gameIcon3.value, gameIcon4.value, gameIcon5.value, gameIcon6.value, gameIcon7.value, gameIcon8.value, gameIcon9.value, gameIcon10.value, gameIcon11.value, gameIcon12.value]
  for (let index = 0; index < gameIcons.length; index++) {
    const icon = gameIcons[index]
    if (icon) {
      gsap.to(icon, {
        duration: 2 + index * 0.5,
        y: -15,
        repeat: -1,
        yoyo: true,
        ease: "power1.inOut",
        delay: index * 0.3
      })
    }
  }

  // 404数字闪烁动画
  gsap.to(errorCodeRef.value, {
    duration: 0.1,
    opacity: 0.7,
    repeat: -1,
    yoyo: true,
    ease: "power1.inOut"
  })

  // 背景404文字动画
  gsap.to('.bg-404-text', {
    duration: 3,
    opacity: 0.3,
    repeat: -1,
    yoyo: true,
    ease: "power1.inOut",
    stagger: 0.1
  })

  // 几何图形动画
  gsap.to('.shape', {
    duration: 4,
    rotation: 360,
    repeat: -1,
    ease: "none",
    stagger: 0.2
  })
}

onMounted(() => {
  initAnimations()
})
</script>

<style scoped>
.not-found {
  min-height: 100vh;
  width: 100vw;
  background: linear-gradient(135deg, #ff6b6b 0%, #ee5a24 50%, #ff6348 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

/* 背景动画 */
.game-background {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
}

.floating-particles {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-image:
    radial-gradient(circle at 20% 80%, rgba(255, 255, 255, 0.1) 2px, transparent 2px),
    radial-gradient(circle at 80% 20%, rgba(255, 255, 255, 0.1) 2px, transparent 2px),
    radial-gradient(circle at 40% 40%, rgba(255, 255, 255, 0.1) 2px, transparent 2px);
  background-size: 100px 100px, 150px 150px, 200px 200px;
}

.game-grid-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.05) 1px, transparent 1px);
  background-size: 50px 50px;
  animation: gridMove 20s linear infinite;
}

.error-particles {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-image:
    radial-gradient(circle at 10% 90%, rgba(255, 255, 255, 0.2) 1px, transparent 1px),
    radial-gradient(circle at 90% 10%, rgba(255, 255, 255, 0.2) 1px, transparent 1px),
    radial-gradient(circle at 30% 70%, rgba(255, 255, 255, 0.2) 1px, transparent 1px);
  background-size: 80px 80px, 120px 120px, 160px 160px;
}

/* 全屏404背景 */
.fullscreen-404-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
}

.bg-404-text {
  position: absolute;
  color: rgba(255, 255, 255, 0.1);
  font-weight: bold;
  font-family: 'Arial', sans-serif;
  -webkit-user-select: none;
  user-select: none;
}

/* 几何图形装饰 */
.geometric-shapes {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
}

.shape {
  position: absolute;
  animation: shapeFloat 6s ease-in-out infinite;
}

.shape-circle {
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  border: 2px solid rgba(255, 255, 255, 0.2);
}

.shape-triangle {
  width: 0;
  height: 0;
  border-left: 15px solid transparent;
  border-right: 15px solid transparent;
  border-bottom: 30px solid rgba(255, 255, 255, 0.1);
}

.shape-square {
  background: rgba(255, 255, 255, 0.1);
  border: 2px solid rgba(255, 255, 255, 0.2);
}

@keyframes shapeFloat {

  0%,
  100% {
    transform: translateY(0px) rotate(0deg);
  }

  50% {
    transform: translateY(-20px) rotate(180deg);
  }
}

/* 错误图标装饰 */
.error-icons {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.error-icon {
  position: absolute;
  animation: errorIconFloat 5s ease-in-out infinite;
  filter: drop-shadow(0 0 5px rgba(255, 255, 255, 0.3));
}

@keyframes errorIconFloat {

  0%,
  100% {
    transform: translateY(0px) rotate(0deg) scale(1);
    opacity: 0.4;
  }

  50% {
    transform: translateY(-25px) rotate(15deg) scale(1.2);
    opacity: 0.8;
  }
}

/* 警告图标装饰 */
.warning-icons {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.warning-icon {
  position: absolute;
  animation: warningIconFloat 4s ease-in-out infinite;
  filter: drop-shadow(0 0 8px rgba(255, 193, 7, 0.4));
}

@keyframes warningIconFloat {

  0%,
  100% {
    transform: translateY(0px) rotate(0deg) scale(1);
    opacity: 0.3;
  }

  50% {
    transform: translateY(-20px) rotate(-10deg) scale(1.1);
    opacity: 0.7;
  }
}

/* 错误符号装饰 */
.error-symbols {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.error-symbol {
  position: absolute;
  color: rgba(255, 255, 255, 0.6);
  font-weight: bold;
  animation: errorSymbolFloat 6s ease-in-out infinite;
}

@keyframes errorSymbolFloat {

  0%,
  100% {
    transform: translateY(0px) rotate(0deg);
    opacity: 0.4;
  }

  50% {
    transform: translateY(-30px) rotate(20deg);
    opacity: 0.8;
  }
}

/* 故障效果元素 */
.glitch-elements {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.glitch-element {
  position: absolute;
  color: rgba(255, 255, 255, 0.7);
  font-weight: bold;
  font-family: 'Courier New', monospace;
  animation: glitchEffect 3s ease-in-out infinite;
  text-shadow: 2px 0 red, -2px 0 blue;
}

@keyframes glitchEffect {

  0%,
  100% {
    transform: translateY(0px) skew(0deg);
    opacity: 0.5;
    text-shadow: 2px 0 red, -2px 0 blue;
  }

  25% {
    transform: translateY(-5px) skew(2deg);
    opacity: 0.8;
    text-shadow: -2px 0 red, 2px 0 blue;
  }

  50% {
    transform: translateY(5px) skew(-2deg);
    opacity: 0.6;
    text-shadow: 2px 0 red, -2px 0 blue;
  }

  75% {
    transform: translateY(-3px) skew(1deg);
    opacity: 0.9;
    text-shadow: -2px 0 red, 2px 0 blue;
  }
}

/* 浮动404数字 */
.floating-404-numbers {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.floating-404 {
  position: absolute;
  color: rgba(255, 255, 255, 0.3);
  font-weight: bold;
  font-family: 'Arial', sans-serif;
  animation: floating404 6s ease-in-out infinite;
  -webkit-user-select: none;
  user-select: none;
}

@keyframes floating404 {

  0%,
  100% {
    transform: translateY(0px) rotate(0deg) scale(1);
    opacity: 0.3;
  }

  50% {
    transform: translateY(-30px) rotate(10deg) scale(1.1);
    opacity: 0.6;
  }
}

/* 错误提示气泡 */
.error-bubbles {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.error-bubble {
  position: absolute;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: bubbleFloat 8s ease-in-out infinite;
  -webkit-backdrop-filter: blur(5px);
  backdrop-filter: blur(5px);
}

.bubble-text {
  font-size: 0.8rem;
  color: rgba(255, 255, 255, 0.8);
  text-align: center;
  padding: 10px;
}

@keyframes bubbleFloat {

  0%,
  100% {
    transform: translateY(0px) scale(1);
    opacity: 0.3;
  }

  50% {
    transform: translateY(-25px) scale(1.1);
    opacity: 0.7;
  }
}

/* 动态光效 */
.light-effects {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
}

.light-beam {
  position: absolute;
  width: 2px;
  height: 100px;
  background: linear-gradient(to bottom, transparent, rgba(255, 255, 255, 0.3), transparent);
  animation: lightSweep 4s ease-in-out infinite;
}

@keyframes lightSweep {

  0%,
  100% {
    opacity: 0;
    transform: translateY(-100px) rotate(0deg);
  }

  50% {
    opacity: 1;
    transform: translateY(100vh) rotate(360deg);
  }
}

/* 粒子系统 */
.particle-system {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
}

.particle {
  position: absolute;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 50%;
  animation: particleFloat 8s ease-in-out infinite;
}

@keyframes particleFloat {

  0%,
  100% {
    transform: translateY(0px) translateX(0px);
    opacity: 0.3;
  }

  50% {
    transform: translateY(-40px) translateX(20px);
    opacity: 0.8;
  }
}

@keyframes gridMove {
  0% {
    transform: translate(0, 0);
  }

  100% {
    transform: translate(50px, 50px);
  }
}

/* 主要内容区域 */
.main-content {
  position: relative;
  z-index: 2;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.error-container {
  text-align: center;
  padding: 40px;
  position: relative;
  z-index: 3;
  max-width: 600px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 20px;
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.error-code {
  font-size: 8rem;
  font-weight: bold;
  margin-bottom: 20px;
  text-shadow: 0 4px 8px rgba(0, 0, 0, 0.3);
  background: linear-gradient(45deg, #fff, #ffd700);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  position: relative;
}

.error-code::after {
  content: '';
  position: absolute;
  bottom: -10px;
  left: 50%;
  width: 200px;
  height: 4px;
  background: linear-gradient(45deg, #ffd700, #ff6348);
  border-radius: 2px;
  transform: translateX(-50%);
  transform-origin: center;
  animation: errorLine 2s ease-in-out infinite;
}

@keyframes errorLine {

  0%,
  100% {
    transform: translateX(-50%) scaleX(1);
    opacity: 1;
  }

  50% {
    transform: translateX(-50%) scaleX(1.5);
    opacity: 0.7;
  }
}

.error-message {
  font-size: 2.5rem;
  margin-bottom: 20px;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
  position: relative;
}

.error-message::before {
  content: '⚠️';
  position: absolute;
  left: -60px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 2rem;
  animation: warningBounce 2s ease-in-out infinite;
}

@keyframes warningBounce {

  0%,
  20%,
  50%,
  80%,
  100% {
    transform: translateY(-50%) scale(1);
  }

  40% {
    transform: translateY(-50%) scale(1.2);
  }

  60% {
    transform: translateY(-50%) scale(1.1);
  }
}

.error-description {
  font-size: 1.2rem;
  opacity: 0.9;
  margin-bottom: 40px;
  line-height: 1.6;
}

.action-buttons {
  display: flex;
  gap: 20px;
  justify-content: center;
  flex-wrap: wrap;
}

.home-btn,
.back-btn {
  background: rgba(255, 255, 255, 0.2);
  border: 2px solid rgba(255, 255, 255, 0.3);
  color: white;
  padding: 15px 30px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-weight: 600;
  font-size: 1.1rem;
  display: flex;
  align-items: center;
  gap: 10px;
  position: relative;
  overflow: hidden;
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
}

.home-btn::before,
.back-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transform: translateX(-100%);
  transition: transform 0.5s;
}

.home-btn:hover::before,
.back-btn:hover::before {
  transform: translateX(100%);
}

.home-btn:hover,
.back-btn:hover {
  background: rgba(255, 255, 255, 0.3);
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.3);
}

.btn-icon {
  font-size: 1.2rem;
}

.btn-text {
  font-weight: 600;
}

.game-elements {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.floating-game-icon {
  position: absolute;
  font-size: 2rem;
  opacity: 0.6;
  animation: float 4s ease-in-out infinite;
}

.floating-game-icon:nth-child(1) {
  top: 10%;
  left: 8%;
  animation-delay: 0s;
}

.floating-game-icon:nth-child(2) {
  top: 20%;
  right: 12%;
  animation-delay: 1s;
}

.floating-game-icon:nth-child(3) {
  bottom: 20%;
  left: 15%;
  animation-delay: 2s;
}

.floating-game-icon:nth-child(4) {
  bottom: 10%;
  right: 8%;
  animation-delay: 3s;
}

.floating-game-icon:nth-child(5) {
  top: 45%;
  left: 3%;
  animation-delay: 0.5s;
}

.floating-game-icon:nth-child(6) {
  top: 55%;
  right: 3%;
  animation-delay: 1.5s;
}

.floating-game-icon:nth-child(7) {
  bottom: 45%;
  left: 12%;
  animation-delay: 2.5s;
}

.floating-game-icon:nth-child(8) {
  bottom: 55%;
  right: 12%;
  animation-delay: 3.5s;
}

.floating-game-icon:nth-child(9) {
  top: 35%;
  left: 25%;
  animation-delay: 0.8s;
}

.floating-game-icon:nth-child(10) {
  top: 65%;
  right: 25%;
  animation-delay: 1.8s;
}

.floating-game-icon:nth-child(11) {
  bottom: 35%;
  left: 35%;
  animation-delay: 2.8s;
}

.floating-game-icon:nth-child(12) {
  bottom: 65%;
  right: 35%;
  animation-delay: 3.8s;
}

@keyframes float {

  0%,
  100% {
    transform: translateY(0px) rotate(0deg);
  }

  50% {
    transform: translateY(-20px) rotate(10deg);
  }
}

@media (max-width: 768px) {
  .error-code {
    font-size: 5rem;
  }

  .error-message {
    font-size: 1.8rem;
  }

  .error-message::before {
    display: none;
  }

  .action-buttons {
    flex-direction: column;
    align-items: center;
  }

  .home-btn,
  .back-btn {
    width: 200px;
    justify-content: center;
  }

  .error-container {
    margin: 20px;
    padding: 20px;
  }
}

/* 移动端小屏幕适配 */
@media (max-width: 480px) {
  .error-container {
    margin: 16px;
    padding: 24px 16px;
    border-radius: 16px;
  }

  .error-code {
    font-size: 4rem;
    margin-bottom: 16px;
  }

  .error-code::after {
    width: 120px;
    height: 3px;
  }

  .error-message {
    font-size: 1.4rem;
    margin-bottom: 16px;
  }

  .error-description {
    font-size: 0.95rem;
    margin-bottom: 28px;
    line-height: 1.5;
  }

  .action-buttons {
    gap: 12px;
  }

  .home-btn,
  .back-btn {
    width: 100%;
    padding: 14px 24px;
    font-size: 1rem;
  }

  .btn-icon {
    font-size: 1.1rem;
  }

  /* 减少移动端装饰元素数量，提升性能 */
  .floating-game-icon:nth-child(n+7) {
    display: none;
  }

  .error-icon:nth-child(n+10) {
    display: none;
  }

  .warning-icon:nth-child(n+8) {
    display: none;
  }

  .floating-404:nth-child(n+12) {
    display: none;
  }

  .error-bubble:nth-child(n+6) {
    display: none;
  }

  .particle:nth-child(n+25) {
    display: none;
  }

  .glitch-element:nth-child(n+5) {
    display: none;
  }

  .bg-404-text:nth-child(n+15) {
    display: none;
  }
}
</style>