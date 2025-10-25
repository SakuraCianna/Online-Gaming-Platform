import { defineStore } from 'pinia'

export const useRoomStore = defineStore('room', {
  state: () => ({
    currentRoom: null, // 当前房间对象
    gameConfig: null, // 游戏配置（AI模式、难度、对手信息等）
  }),
  actions: {
    setCurrentRoom(room) {
      this.currentRoom = room
    },
    clearCurrentRoom() {
      this.currentRoom = null
    },
    setGameConfig(config) {
      this.gameConfig = config
    },
    clearGameConfig() {
      this.gameConfig = null
    }
  },
  persist: {
    key: 'room',
    storage: localStorage,
    paths: ['currentRoom', 'gameConfig'] // 持久化房间和游戏配置
  }
})