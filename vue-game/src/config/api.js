import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../config/router'

// 开发环境使用相对路径，让 Vite 代理处理
const RAW_BASE = ''
globalThis.API_BASE_URL = RAW_BASE

const baseURL = '/api'  // 直接使用相对路径

const request = axios.create({
  baseURL,
  timeout: 10000
})

// AI请求实例 - 60秒超时
const aiRequest = axios.create({
  baseURL,
  timeout: 60000
})

// 用于防止重复提示的标志
let isTokenExpiredMessageShown = false

const addInterceptors = (instance) => {
  instance.interceptors.request.use(config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  }, error => Promise.reject(error instanceof Error ? error : new Error(String(error))))

  instance.interceptors.response.use(
    response => response,
    error => {
      // Token过期或未授权（401）
      if (error.response?.status === 401) {
        // 防止重复提示
        if (!isTokenExpiredMessageShown) {
          isTokenExpiredMessageShown = true

          ElMessage.error({
            message: '登录已过期，请重新登录',
            duration: 3000,
            onClose: () => {
              isTokenExpiredMessageShown = false
            }
          })

          // 清除token和用户信息
          localStorage.removeItem('token')

          // 延迟跳转到登录页
          setTimeout(() => {
            router.push('/')
          }, 1000)
        }
      }

      return Promise.reject(error instanceof Error ? error : new Error(String(error)))
    }
  )
}

addInterceptors(request)
addInterceptors(aiRequest)

export default request
export { aiRequest }
