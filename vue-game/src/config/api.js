import axios from 'axios'

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
    error => Promise.reject(error instanceof Error ? error : new Error(String(error)))
  )
}

addInterceptors(request)
addInterceptors(aiRequest)

export default request
export { aiRequest }
