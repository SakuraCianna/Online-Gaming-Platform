import { defineStore } from 'pinia'
import request from './api'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: '',
    user: null,
    friends: [],
    friendsTotal: 0,
    hbTimer: null, // 心跳定时器
  }),
  getters: {
    isAuthenticated: (state) => !!state.token,
  },
  actions: {
    setAuth({ token, user }) {
      this.token = token || ''
      this.user = user || null
      // 同步更新 localStorage.token（api.js 依赖此 key）
      if (token) {
        localStorage.setItem('token', token)
      } else {
        localStorage.removeItem('token')
      }
    },
    setUser(user) {
      this.user = user || null
    },
    updateUser(patch) {
      if (!this.user) this.user = {}
      this.user = { ...this.user, ...patch }
    },
    clear() {
      this.token = ''
      this.user = null
      this.friends = []
      this.friendsTotal = 0
      this.stopPresence()
      // 同步清除 localStorage.token
      localStorage.removeItem('token')
    },
    setFriends(friendsList, total = 0) {
      this.friends = (friendsList || []).map(f => ({
        ...f,
        online: f.online ?? f.isOnline ?? false,  // 统一使用 online 字段名
        currentGame: f.currentGame ?? null
      }))
      this.friendsTotal = total
    },
    updateFriendStatus(userId, online, currentGame = null) {
      const index = this.friends.findIndex(f => f.id === userId)
      if (index !== -1) {
        this.friends[index] = {
          ...this.friends[index],
          online: online,
          currentGame: currentGame === undefined ? null : currentGame
        }
      }
    },
    // 心跳相关
    async heartbeatOnce() {
      if (!this.user?.id) return
      try {
        await request.post('/user/state/heartbeat')
      } catch {
        // 心跳失败静默处理
      }
    },
    startPresence() {
      this.stopPresence()
      this.heartbeatOnce()
      this.hbTimer = setInterval(() => this.heartbeatOnce(), 20000) // 20s 心跳
    },
    stopPresence() {
      if (this.hbTimer) {
        clearInterval(this.hbTimer)
        this.hbTimer = null
      }
    },
    // 游戏状态相关
    async setCurrentGame(game) {
      if (!this.user?.id) return
      await request.post('/user/state/game/start', null, { params: { game } })
    },
    async clearCurrentGame() {
      if (!this.user?.id) return
      await request.post('/user/state/game/stop')
    },
    // 在线/离线标记
    async markOnline() {
      if (!this.user?.id) return
      await request.post('/user/state/online')
    },
    async markOffline() {
      if (!this.user?.id) return
      await request.post('/user/state/offline')
    },
  },
  persist: true
})
