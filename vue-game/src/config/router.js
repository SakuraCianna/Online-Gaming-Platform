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
    path: '/game/tank',
    name: 'TankBattle',
    component: () => import('../components/game/TankBattle.vue'),
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
  await new Promise(resolve => setTimeout(resolve, 0))

  const userStore = useUserStore()
  const token = userStore.token

  if (to.meta.requiresAuth) {
    if (!token) {
      next('/')
      return
    }
    try {
      // 用 headers 传递 token
      await request.get('/user/verifyToken', {
        headers: {
          Authorization: `Bearer ${token}`
        }
      })
      next()
    } catch (error) {
      console.error('Token 验证失败:', error)
      userStore.clear()
      next('/')
    }
  } else if (token && to.path === '/') {
    next('/user')
  } else {
    next()
  }
})

export default router