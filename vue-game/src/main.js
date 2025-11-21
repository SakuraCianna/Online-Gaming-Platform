import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import router from './config/router'
import App from './App.vue'
import './assets/main.css'
import 'element-plus/dist/index.css'
import VueKonva from 'vue-konva'
import { useUserStore } from './config/user'

const app = createApp(App)
const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

app.use(pinia)

// 在 Pinia 初始化后，同步 token 到 localStorage（兼容已登录用户）
const userStore = useUserStore()
if (userStore.token && !localStorage.getItem('token')) {
  localStorage.setItem('token', userStore.token)
}

app.use(ElementPlus, { locale: zhCn })
app.use(VueKonva)
app.use(router)
app.mount('#app')
