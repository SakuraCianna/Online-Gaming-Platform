<template>
  <div class="user-layout" ref="userLayout">
    <div class="side" ref="sideBar">
      <UserSide :selected="selected" @select="selected = $event" />
    </div>
    <div class="main" ref="mainContent">
      <div class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { gsap } from 'gsap'
import UserSide from './Dashboard/UserSide.vue'

const route = useRoute()
const selected = ref('games')
const notificationCount = ref(3)


// 动画相关的ref
const userLayout = ref(null)
const sideBar = ref(null)
const mainContent = ref(null)

// 监听路由变化,更新选中的菜单项
watch(() => route.path, (newPath) => {
  const pathSegments = newPath.split('/')
  if (pathSegments.length > 2) {
    selected.value = pathSegments[2]
  }
}, { immediate: true })

// 根据当前路由获取页面标题
function getPageTitle() {
  const routeMap = {
    'games': '游戏中心',
    'friends': '好友管理',
    'rooms': '游戏房间',
    'records': '游戏记录',
    'settings': '个人设置'
  }
  return routeMap[selected.value] || '游戏中心'
}

// 初始化GSAP动画
onMounted(() => {
  initAnimation()
})

function initAnimation() {
  // 设置初始状态
  gsap.set(sideBar.value, { x: -280, opacity: 0 })
  gsap.set(mainContent.value, { x: 100, opacity: 0 })
  
  // 创建时间轴
  const tl = gsap.timeline()
  
  // 侧边栏滑入动画
  tl.to(sideBar.value, {
    x: 0,
    opacity: 1,
    duration: 0.8,
    ease: "power2.out"
  })
  
  // 主内容区域滑入动画
  tl.to(mainContent.value, {
    x: 0,
    opacity: 1,
    duration: 0.8,
    ease: "power2.out"
  }, "-=0.4") // 稍微重叠，创造流畅效果
  
  // 添加一些弹性效果
  tl.to([sideBar.value, mainContent.value], {
    scale: 1.02,
    duration: 0.1,
    ease: "power2.out"
  })
  .to([sideBar.value, mainContent.value], {
    scale: 1,
    duration: 0.1,
    ease: "power2.out"
  })
}

// 页面切换时的动画
function animatePageTransition() {
  const mainContent = document.querySelector('.main-content')
  if (mainContent) {
    gsap.fromTo(mainContent, 
      { opacity: 0, y: 20 },
      { opacity: 1, y: 0, duration: 0.5, ease: "power2.out" }
    )
  }
}

// 监听路由变化，添加页面切换动画
watch(() => route.path, () => {
  setTimeout(animatePageTransition, 100)
})
</script>

<style scoped>
.user-layout {
  display: flex;
  width: 100vw;
  height: 100vh;
  background: #f8f9fa;
  /* 确保占满整个视口 */
  position: fixed;
  top: 0;
  left: 0;
}

/* 加载遮罩样式 */
.loading-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.loading-content {
  text-align: center;
  color: white;
}

.loading-spinner {
  width: 50px;
  height: 50px;
  border: 3px solid rgba(255, 255, 255, 0.3);
  border-top: 3px solid white;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 20px;
}

.loading-text {
  font-size: 18px;
  font-weight: 500;
  margin: 0;
  opacity: 0.9;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.side {
  flex-shrink: 0;
  /* 确保侧边栏高度占满 */
  height: 100vh;
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  /* 确保主内容区域占满剩余空间 */
  height: 100vh;
}

.main-header {
  background: white;
  padding: 20px 30px;
  border-bottom: 1px solid #e9ecef;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  flex-shrink: 0;
}

.page-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #2c3e50;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 20px;
}

.notification-bell {
  position: relative;
  cursor: pointer;
  padding: 10px;
  border-radius: 50%;
  transition: all 0.3s ease;
}

.notification-bell:hover {
  background: #f8f9fa;
  transform: scale(1.1);
}

.bell-icon {
  font-size: 24px;
  color: #6c757d;
}

.notification-count {
  position: absolute;
  top: 5px;
  right: 5px;
  background: #ff6b6b;
  color: white;
  font-size: 12px;
  font-weight: bold;
  padding: 2px 6px;
  border-radius: 10px;
  min-width: 18px;
  text-align: center;
}

.main-content {
  flex: 1;
  padding: 30px;
  overflow-y: auto;
  /* 调整高度，因为没有header了 */
  height: 100vh;
}

/* 页面切换动画 */
.fade-enter-active,
.fade-leave-active {
  transition: all 0.3s ease;
}

.fade-enter-from {
  opacity: 0;
  transform: translateX(20px);
}

.fade-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .user-layout {
    flex-direction: column;
  }
  
  .side {
    height: auto;
  }
  
  .main-header {
    padding: 15px 20px;
  }
  
  .page-title {
    font-size: 24px;
  }
  
  .main-content {
    padding: 20px;
    height: calc(100vh - 120px); /* 移动端调整高度 */
  }
  
  /* 移动端动画调整 */
  .loading-spinner {
    width: 40px;
    height: 40px;
  }
  
  .loading-text {
    font-size: 16px;
  }
}
</style>