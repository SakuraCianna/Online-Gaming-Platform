import { createRouter, createWebHistory } from 'vue-router'
import LoginRegister from '../components/LoginRegister.vue'
import { useUserStore } from './user'
import request from './api'

const routes = [
  {
    path: '/',
    name: 'LoginRegister',
    component: LoginRegister
  },
  {
    path: '/user',
    name: 'UserView',
    component: () => import('../components/UserView.vue'),
    meta: { requiresAuth: true, title: '玩家中心' },
    redirect: '/user/games',
    children: [
      {
        path: 'games',
        name: 'GameCenter',
        component: () => import('../components/Dashboard/GameCenter.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'friends',
        name: 'Friends',
        component: () => import('../components/Dashboard/Friends.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'records',
        name: 'GameRecords',
        component: () => import('../components/Dashboard/GameRecords.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'leaderboard',
        name: 'Leaderboard',
        component: () => import('../components/Dashboard/Leaderboard.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'rooms',
        name: 'GameRooms',
        component: () => import('../components/Dashboard/GameRooms.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'settings',
        name: 'UserSettings',
        component: () => import('../components/Dashboard/UserSettings.vue'),
        meta: { requiresAuth: true }
      }
    ]
  },
  {
    path: '/game/minesweeper',
    name: 'Minesweeper',
    component: () => import('../components/game/Minesweeper.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/game/tank-battle',
    name: 'TankBattle',
    component: () => import('../components/game/TankBattle.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/game/tank-battle/play',
    name: 'TankBattleGame',
    component: () => import('../components/game/TankBattleGame.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/game/2048',
    name: 'Game2048',
    component: () => import('../components/game/2048.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/game/gomoku',
    name: 'Gomoku',
    component: () => import('../components/game/Gomoku.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/game/gomoku/play/:roomCode?',
    name: 'GomokuGame',
    component: () => import('../components/game/GomokuGame.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/room/create/:game',
    name: 'RoomCreate',
    component: () => import('../components/Dashboard/RoomCreate.vue'),
    meta: { requiresAuth: true }
  },
  // 404 页面
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../components/NotFound.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  const token = userStore.token

  console.log('[Router] 路由守卫检查:', {
    to: to.path,
    from: from.path,
    requiresAuth: to.meta.requiresAuth,
    hasToken: !!token
  })

  if (to.meta.requiresAuth) {
    if (!token) {
      console.log('[Router] 无Token，跳转登录页')
      next('/')
      return
    }
    
    try {
      // 验证 token 是否有效
      console.log('[Router] 验证Token有效性')
      await request.get('/user/verifyToken', {
        headers: {
          Authorization: `Bearer ${token}`
        }
      })
      console.log('[Router] Token验证通过，允许访问')
      next()
    } catch (error) {
      console.error('[Router] Token验证失败:', error)
      
      // Token无效（过期或错误），清除本地数据
      localStorage.removeItem('token')
      userStore.clear()
      
      // 如果是401错误，说明token过期，提示信息已经在api.js的拦截器中处理
      // 这里只需要静默跳转到登录页
      if (error.response?.status === 401) {
        console.log('[Router] Token已过期，跳转登录页（提示已由API拦截器处理）')
      }
      
      next('/')
    }
  } else if (token && to.path === '/') {
    // 已登录用户访问登录页，重定向到用户中心
    console.log('[Router] 已登录，跳转用户中心')
    next('/user')
  } else {
    console.log('[Router] 无需认证，直接访问')
    next()
  }
})

export default router