<template>
  <div class="user-side">
    <!-- 展示头像以及等级 -->
    <div class="user-info">
      <div class="avatar">
        <img :src="userAvatar" alt="用户头像" />
        <div class="level-badge">Lv.{{ userLevel }}</div>
      </div>
      <div class="user-details">
        <h3 class="username">{{ username }}</h3>
      </div>
    </div>

    <!-- 展示侧边栏按钮 -->
    <nav class="side-bar">
      <ul>
        <li v-for="item in menu" :key="item.key" :class="{ active: isActive(item.key) }" @click="goRoute(item.key)">
          <span class="icon">{{ item.icon }}</span>
          <span class="label">{{ item.label }}</span>
        </li>
      </ul>
    </nav>

    <!-- 退出登录按钮 -->
    <div class="logout-section">
      <button class="logout-btn" @click="logout">
        <span>退出登录</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { useRouter, useRoute } from 'vue-router'
import { computed } from 'vue'
import { useUserStore } from '../../config/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 直接从 Pinia store 获取用户信息
const username = computed(() => userStore.user?.username || '玩家')
const userLevel = computed(() => userStore.user?.level || 1)
const userAvatar = computed(() => userStore.user?.avatar || '/image/default-avatar.jpg')

const menu = [
  { key: 'games', label: '游戏中心', icon: '🎮' },
  { key: 'friends', label: '好友', icon: '👥' },
  { key: 'rooms', label: '游戏房间', icon: '🏠' },
  { key: 'records', label: '游戏记录', icon: '📊' },
  { key: 'leaderboard', label: '排行榜', icon: '🏆' },
  { key: 'settings', label: '设置', icon: '⚙️' }
]

function goRoute(key) {
  router.push(`/user/${key}`)
}

function isActive(key) {
  return route.path === `/user/${key}`
}

async function logout() {
  try {
    userStore.stopPresence()
    userStore.markOffline().catch(() => { })
  } finally {
    userStore.clear()
    await router.replace('/')
  }
}

</script>

<style scoped>
.user-side {
  width: 280px;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  flex-direction: column;
  box-shadow: 2px 0 10px rgba(0, 0, 0, 0.1);
  /* 确保侧边栏占满整个高度 */
  position: relative;
}

.user-info {
  padding: 30px 20px;
  text-align: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  flex-shrink: 0;
}

.avatar {
  position: relative;
  margin-bottom: 15px;
}

.avatar img {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  border: 3px solid rgba(255, 255, 255, 0.3);
  object-fit: cover;
}

.level-badge {
  position: absolute;
  bottom: -5px;
  right: 50%;
  transform: translateX(50%);
  background: #ff6b6b;
  color: white;
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: bold;
}

.username {
  margin: 0 0 5px 0;
  font-size: 18px;
  font-weight: 600;
}

.user-status {
  margin: 0;
  font-size: 14px;
  opacity: 0.8;
}

.side-bar {
  flex: 1;
  padding: 20px 0;
  /* 确保导航区域占满剩余空间 */
  overflow-y: auto;
}

.side-bar ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.side-bar li {
  padding: 15px 25px;
  margin: 5px 15px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 15px;
}

.side-bar li:hover {
  background: rgba(255, 255, 255, 0.1);
  transform: translateX(5px);
}

.side-bar li.active {
  background: rgba(255, 255, 255, 0.2);
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
}

.side-bar .icon {
  font-size: 20px;
  width: 24px;
  text-align: center;
}

.side-bar .label {
  font-size: 16px;
  font-weight: 500;
}

.logout-section {
  padding: 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  flex-shrink: 0;
}

.logout-btn {
  width: 100%;
  padding: 12px 20px;
  background: rgba(255, 107, 107, 0.8);
  border: none;
  border-radius: 10px;
  color: white;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  /* 确保按钮可以点击 */
  position: relative;
  z-index: 10;
}

.logout-btn:hover {
  background: rgba(255, 107, 107, 1);
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(255, 107, 107, 0.4);
}

.logout-btn:active {
  transform: translateY(0);
  box-shadow: 0 2px 8px rgba(255, 107, 107, 0.4);
}

.logout-icon {
  font-size: 18px;
}

/* 响应式设计 - 移动端底部导航栏 */
@media (max-width: 768px) {
  .user-side {
    width: 100%;
    height: 70px;
    flex-direction: row;
    background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
    padding: 0;
    box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.1);
  }

  .user-info {
    display: none;
  }

  .side-bar {
    flex: 1;
    padding: 0;
    overflow-x: auto;
    overflow-y: hidden;
  }

  .side-bar ul {
    display: flex;
    justify-content: space-around;
    align-items: center;
    height: 70px;
    padding: 0 8px;
  }

  .side-bar li {
    flex-direction: column;
    padding: 8px 12px;
    margin: 0;
    gap: 4px;
    border-radius: 8px;
    min-width: 50px;
  }

  .side-bar li:hover {
    transform: none;
  }

  .side-bar .icon {
    font-size: 20px;
  }

  .side-bar .label {
    font-size: 10px;
    white-space: nowrap;
  }

  .logout-section {
    display: none;
  }
}
</style>