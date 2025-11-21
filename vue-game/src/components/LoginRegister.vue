<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import request from '../config/api'
import { useUserStore } from '../config/user'
import { ElMessage } from 'element-plus'

const router = useRouter()

const mode = ref('login') // 'login' | 'register' | 'forgot'

const isLoading = ref(false)
const showPassword = ref(false)
const showConfirmPassword = ref(false)

// hCaptcha 相关
const hcaptchaToken = ref('')
const hcaptchaLoaded = ref(false)
const hcaptchaWidgetId = ref(null)
const HCAPTCHA_SITE_KEY = 'dceab3a2-519c-4ec2-977e-1aea8b94e6c6'

// 表单数据
const loginForm = reactive({
  email: '',
  password: ''
})
const registerForm = reactive({
  email: '',
  password: '',
  confirmPassword: '',
  username: '',
  verificationCode: ''
})
const forgotPasswordForm = reactive({
  email: '',
  verificationCode: '',
  newPassword: '',
  confirmNewPassword: ''
})

// 表单验证
const loginErrors = reactive({
  email: '',
  password: ''
})
const registerErrors = reactive({
  email: '',
  password: '',
  confirmPassword: '',
  username: '',
  verificationCode: ''
})
const forgotPasswordErrors = reactive({
  email: '',
  verificationCode: '',
  newPassword: '',
  confirmNewPassword: ''
})

// 验证码相关
const isCodeSent = ref(false)
const countdown = ref(0)
const countdownTimer = ref(null)
const isForgotCodeSent = ref(false)
const forgotCountdown = ref(0)
const forgotCountdownTimer = ref(null)

// 计算属性
const isLoginValid = computed(() => loginForm.email.trim() && loginForm.password.trim())
const isRegisterValid = computed(() =>
  registerForm.email.trim() &&
  registerForm.password.trim() &&
  registerForm.confirmPassword.trim() &&
  registerForm.password === registerForm.confirmPassword &&
  registerForm.username.trim() &&
  registerForm.verificationCode.trim()
)
const isForgotPasswordValid = computed(() =>
  forgotPasswordForm.email.trim() &&
  forgotPasswordForm.verificationCode.trim() &&
  forgotPasswordForm.newPassword.trim() &&
  forgotPasswordForm.confirmNewPassword.trim() &&
  forgotPasswordForm.newPassword === forgotPasswordForm.confirmNewPassword
)

// 方法
// 渲染隐形 hCaptcha（绑定到注册按钮）
const renderHcaptcha = () => {
  nextTick(() => {
    const container = document.getElementById('hcaptcha-container')
    if (!container || !globalThis.hcaptcha) return

    // 如果已经有 widget，先移除
    if (hcaptchaWidgetId.value !== null) {
      try {
        globalThis.hcaptcha.remove(hcaptchaWidgetId.value)
      } catch (e) {
        console.log('移除旧的 hCaptcha 失败:', e)
      }
    }

    // 清空容器
    container.innerHTML = ''

    // 渲染新的隐形 hCaptcha
    try {
      hcaptchaWidgetId.value = globalThis.hcaptcha.render('hcaptcha-container', {
        sitekey: HCAPTCHA_SITE_KEY,
        size: 'invisible', // 设置为隐形模式
        callback: (token) => {
          hcaptchaToken.value = token
          // 验证成功后自动提交注册
          submitRegister()
        },
        'expired-callback': () => {
          hcaptchaToken.value = ''
          ElMessage.warning('验证已过期，请重新提交')
        },
        'error-callback': () => {
          hcaptchaToken.value = ''
          ElMessage.error('验证出错，请重试')
        }
      })
    } catch (e) {
      console.error('渲染 hCaptcha 失败:', e)
    }
  })
}

// 加载 hCaptcha 脚本
onMounted(() => {
  // 加载 hCaptcha 脚本
  if (document.querySelector('script[src*="hcaptcha.com"]')) {
    hcaptchaLoaded.value = true
    // 如果脚本已加载且当前是注册模式，渲染 hCaptcha
    if (mode.value === 'register') {
      renderHcaptcha()
    }
  } else {
    const script = document.createElement('script')
    script.src = 'https://js.hcaptcha.com/1/api.js'
    script.async = true
    script.defer = true
    script.onload = () => {
      hcaptchaLoaded.value = true
      // 如果当前是注册模式，渲染 hCaptcha
      if (mode.value === 'register') {
        renderHcaptcha()
      }
    }
    script.onerror = () => {
      hcaptchaLoaded.value = false
      console.error('加载 hCaptcha 脚本失败')
    }
    document.head.appendChild(script)
  }
})

onUnmounted(() => {
  // 清理 hCaptcha widget
  if (hcaptchaWidgetId.value !== null && globalThis.hcaptcha) {
    try {
      globalThis.hcaptcha.remove(hcaptchaWidgetId.value)
    } catch (e) {
      console.log('清理 hCaptcha 失败:', e)
    }
  }
})

// 监听模式切换，重新渲染 hCaptcha
watch(mode, async (newMode) => {
  if (newMode === 'register') {
    hcaptchaToken.value = ''
    await nextTick()
    // 等待 DOM 更新后，手动渲染 hCaptcha
    if (hcaptchaLoaded.value) {
      renderHcaptcha()
    }
  }
})

// 重置 hCaptcha
const resetHcaptcha = () => {
  hcaptchaToken.value = ''
  if (globalThis.hcaptcha && hcaptchaWidgetId.value !== null) {
    try {
      globalThis.hcaptcha.reset(hcaptchaWidgetId.value)
    } catch (e) {
      console.log('重置 hCaptcha 失败:', e)
    }
  }
}

const resetForms = () => {
  for (const key of Object.keys(loginForm)) loginForm[key] = ''
  for (const key of Object.keys(registerForm)) registerForm[key] = ''
  for (const key of Object.keys(forgotPasswordForm)) forgotPasswordForm[key] = ''
  for (const key of Object.keys(loginErrors)) loginErrors[key] = ''
  for (const key of Object.keys(registerErrors)) registerErrors[key] = ''
  for (const key of Object.keys(forgotPasswordErrors)) forgotPasswordErrors[key] = ''
  isCodeSent.value = false
  countdown.value = 0
  isForgotCodeSent.value = false
  forgotCountdown.value = 0
  if (countdownTimer.value) clearInterval(countdownTimer.value)
  if (forgotCountdownTimer.value) clearInterval(forgotCountdownTimer.value)
}

const switchMode = (m) => {
  if (mode.value !== m) {
    mode.value = m
    resetForms()
    resetHcaptcha()
  }
}

const validateEmail = (email) => /^[1-9]\d{4,}@qq\.com$/i.test(email)

// 辅助函数：验证邮箱字段
const validateEmailField = (email, errors) => {
  if (email.trim()) {
    if (validateEmail(email)) {
      errors.email = ''
      return true
    } else {
      errors.email = '请输入有效的QQ邮箱地址'
      return false
    }
  } else {
    errors.email = '请输入QQ邮箱'
    return false
  }
}

// 辅助函数：验证密码字段
const validatePasswordField = (password, errors, fieldName = 'password') => {
  if (password.trim()) {
    if (password.length >= 6) {
      errors[fieldName] = ''
      return true
    } else {
      errors[fieldName] = '密码长度至少6位'
      return false
    }
  } else {
    errors[fieldName] = fieldName === 'newPassword' ? '请输入新密码' : '请输入密码'
    return false
  }
}

// 辅助函数：验证确认密码
const validateConfirmPasswordField = (password, confirmPassword, errors, fieldName = 'confirmPassword') => {
  if (confirmPassword.trim()) {
    if (password === confirmPassword) {
      errors[fieldName] = ''
      return true
    } else {
      errors[fieldName] = '两次输入的密码不一致'
      return false
    }
  } else {
    errors[fieldName] = fieldName === 'confirmNewPassword' ? '请确认新密码' : '请确认密码'
    return false
  }
}

const validateLogin = () => {
  let isValid = true
  if (loginForm.email.trim()) {
    if (validateEmail(loginForm.email)) {
      loginErrors.email = ''
    } else {
      loginErrors.email = '请输入有效的QQ邮箱地址'
      isValid = false
    }
  } else {
    loginErrors.email = '请输入QQ邮箱'
    isValid = false
  }
  if (loginForm.password.trim()) {
    loginErrors.password = ''
  } else {
    loginErrors.password = '请输入密码'
    isValid = false
  }
  return isValid
}

const validateRegister = () => {
  let isValid = true

  // 验证邮箱
  isValid = validateEmailField(registerForm.email, registerErrors) && isValid

  // 验证用户名
  if (registerForm.username.trim()) {
    registerErrors.username = ''
  } else {
    registerErrors.username = '请输入用户名'
    isValid = false
  }

  // 验证密码
  isValid = validatePasswordField(registerForm.password, registerErrors) && isValid

  // 验证确认密码
  isValid = validateConfirmPasswordField(registerForm.password, registerForm.confirmPassword, registerErrors) && isValid

  // 验证验证码
  if (registerForm.verificationCode.trim()) {
    registerErrors.verificationCode = ''
  } else {
    registerErrors.verificationCode = '请输入验证码'
    isValid = false
  }

  return isValid
}

const validateForgotPassword = () => {
  let isValid = true

  // 验证邮箱
  isValid = validateEmailField(forgotPasswordForm.email, forgotPasswordErrors) && isValid

  // 验证验证码
  if (forgotPasswordForm.verificationCode.trim()) {
    forgotPasswordErrors.verificationCode = ''
  } else {
    forgotPasswordErrors.verificationCode = '请输入验证码'
    isValid = false
  }

  // 验证新密码
  isValid = validatePasswordField(forgotPasswordForm.newPassword, forgotPasswordErrors, 'newPassword') && isValid

  // 验证确认新密码
  isValid = validateConfirmPasswordField(forgotPasswordForm.newPassword, forgotPasswordForm.confirmNewPassword, forgotPasswordErrors, 'confirmNewPassword') && isValid

  return isValid
}

// 发送验证码
const sendVerificationCode = async () => {
  if (!registerForm.email.trim()) {
    ElMessage.warning('请先输入QQ邮箱')
    return
  }
  if (!validateEmail(registerForm.email)) {
    ElMessage.warning('请输入有效的QQ邮箱地址')
    return
  }
  isCodeSent.value = true
  countdown.value = 90
  countdownTimer.value = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(countdownTimer.value)
      countdownTimer.value = null
    }
  }, 1000)
  try {
    const response = await request.post('/email/sendCode', {
      email: registerForm.email
    }, {
      timeout: 30000
    })
    if (response.data.success) {
      ElMessage.success('验证码已发送到您的邮箱，请注意查收')
    } else {
      isCodeSent.value = false
      countdown.value = 0
      if (countdownTimer.value) clearInterval(countdownTimer.value)
      ElMessage.error(response.data.message || '发送验证码失败')
    }
  } catch (error) {
    console.error('发送验证码失败:', error)
    ElMessage.error('发送验证码失败，请稍后重试')
  }
}

// 发送忘记密码验证码
const sendForgotPasswordCode = async () => {
  if (!forgotPasswordForm.email.trim()) {
    ElMessage.warning('请先输入QQ邮箱')
    return
  }
  if (!validateEmail(forgotPasswordForm.email)) {
    ElMessage.warning('请输入有效的QQ邮箱地址')
    return
  }
  isForgotCodeSent.value = true
  forgotCountdown.value = 90
  forgotCountdownTimer.value = setInterval(() => {
    forgotCountdown.value--
    if (forgotCountdown.value <= 0) {
      clearInterval(forgotCountdownTimer.value)
      forgotCountdownTimer.value = null
    }
  }, 1000)
  try {
    const response = await request.post('/email/sendCode', {
      email: forgotPasswordForm.email
    }, {
      timeout: 30000
    })
    if (response.data.success) {
      ElMessage.success('验证码已发送到您的邮箱，请注意查收')
    } else {
      isForgotCodeSent.value = false
      forgotCountdown.value = 0
      if (forgotCountdownTimer.value) clearInterval(forgotCountdownTimer.value)
      ElMessage.error(response.data.message || '发送验证码失败')
    }
  } catch (error) {
    console.error('发送验证码失败:', error)
    ElMessage.error('发送验证码失败,请稍后重试')
  }
}

// 登录处理
const handleLogin = async () => {
  if (!validateLogin()) return
  isLoading.value = true
  try {
    const response = await request.post('/user/login', {
      email: loginForm.email,
      password: loginForm.password
    })
    if (response.data.success) {
      const userStore = useUserStore()
      userStore.setAuth({
        token: response.data.token,
        user: response.data.user
      })
      router.push('/user')
    } else {
      ElMessage.error(response.data.message || '登录失败')
    }
  } catch (error) {
    console.error('登录失败:', error)
    // 处理服务器返回的错误信息
    if (error.response && error.response.data) {
      ElMessage.error(error.response.data.message || '登录失败,请稍后重试')
    } else {
      ElMessage.error('网络错误,请稍后重试')
    }
  } finally {
    isLoading.value = false
  }
}

// 注册处理 - 触发人机验证
const handleRegister = async () => {
  if (!validateRegister()) return

  // 检查 hCaptcha 是否已加载
  if (!hcaptchaLoaded.value || !globalThis.hcaptcha || hcaptchaWidgetId.value === null) {
    ElMessage.warning('人机验证尚未加载完成，请稍后重试')
    return
  }

  // 清空之前的 token
  hcaptchaToken.value = ''

  // 触发隐形验证（会弹出验证窗口或直接通过）
  try {
    globalThis.hcaptcha.execute(hcaptchaWidgetId.value)
  } catch (e) {
    console.error('执行 hCaptcha 验证失败:', e)
    ElMessage.error('启动人机验证失败，请刷新页面重试')
  }
}

// 实际提交注册（在验证成功后调用）
const submitRegister = async () => {
  if (!hcaptchaToken.value) {
    ElMessage.warning('人机验证失败，请重试')
    return
  }

  isLoading.value = true
  try {
    const response = await request.post('/user/register', {
      email: registerForm.email,
      username: registerForm.username,
      password: registerForm.password,
      confirmPassword: registerForm.confirmPassword,
      code: registerForm.verificationCode,
      hcaptchaToken: hcaptchaToken.value
    })

    if (response.data.success) {
      // 注册成功，不自动登录，跳转到登录表单
      ElMessage.success('注册成功!请使用新账号登录')
      switchMode('login')
      resetForms()
    } else {
      ElMessage.error(response.data.message || '注册失败')
      resetHcaptcha()
    }
  } catch (error) {
    console.error('注册失败:', error)
    if (error.response && error.response.data) {
      ElMessage.error(error.response.data.message || '注册失败，请稍后重试')
    } else {
      ElMessage.error('网络错误，请稍后重试')
    }
    resetHcaptcha()
  } finally {
    isLoading.value = false
  }
}

// 忘记密码处理
const handleForgotPassword = async () => {
  if (!validateForgotPassword()) return

  isLoading.value = true
  try {
    const response = await request.post('/user/resetPassword', {
      email: forgotPasswordForm.email,
      code: forgotPasswordForm.verificationCode,
      newPassword: forgotPasswordForm.newPassword,
      confirmNewPassword: forgotPasswordForm.confirmNewPassword
    })

    if (response.data.success) {
      // 密码重置成功，跳转到登录表单
      ElMessage.success('密码重置成功！请使用新密码登录')
      switchMode('login')
      resetForms()
    } else {
      ElMessage.error(response.data.message || '密码重置失败')
    }
  } catch (error) {
    console.error('密码重置失败:', error)
    if (error.response && error.response.data) {
      ElMessage.error(error.response.data.message || '密码重置失败，请稍后重试')
    } else {
      ElMessage.error('网络错误，请稍后重试')
    }
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="login-register-container">
    <!-- 背景动画 -->
    <div class="background-animation">
      <div class="floating-shapes">
        <div class="shape shape-1"></div>
        <div class="shape shape-2"></div>
        <div class="shape shape-3"></div>
        <div class="shape shape-4"></div>
        <div class="shape shape-5"></div>
      </div>
    </div>

    <!-- 主卡片 -->
    <div class="auth-card">
      <!-- 标题区域 -->
      <div class="card-header">
        <div class="logo-section">
          <div class="game-icon"></div>
          <h1 class="title">游戏平台</h1>
        </div>
      </div>

      <!-- 模式切换 -->
      <div class="mode-switch">
        <span :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</span>
        <span> | </span>
        <span :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</span>
        <span> | </span>
        <span :class="{ active: mode === 'forgot' }" @click="switchMode('forgot')">忘记密码</span>
      </div>

      <!-- 表单区域 -->
      <div class="form-container">
        <!-- 登录表单 -->
        <form v-if="mode === 'login'" @submit.prevent="handleLogin" class="auth-form">
          <div class="form-group">
            <label class="form-label" for="login-email">QQ邮箱</label>
            <input id="login-email" v-model="loginForm.email" type="email" class="form-input"
              :class="{ error: loginErrors.email }" placeholder="请输入QQ邮箱" />
            <span v-if="loginErrors.email" class="error-message">{{ loginErrors.email }}</span>
          </div>
          <div class="form-group">
            <label class="form-label" for="login-password">密码</label>
            <input id="login-password" v-model="loginForm.password" :type="showPassword ? 'text' : 'password'"
              class="form-input" :class="{ error: loginErrors.password }" placeholder="请输入密码" />
            <span v-if="loginErrors.password" class="error-message">{{ loginErrors.password }}</span>
          </div>
          <button type="submit" class="submit-btn" :disabled="!isLoginValid || isLoading">
            <span v-if="isLoading">登录中...</span>
            <span v-else>登录</span>
          </button>
        </form>

        <!-- 注册表单 -->
        <form v-else-if="mode === 'register'" @submit.prevent="handleRegister" class="auth-form">
          <div class="form-group">
            <label class="form-label" for="register-email">QQ邮箱</label>
            <input id="register-email" v-model="registerForm.email" type="email" class="form-input"
              :class="{ error: registerErrors.email }" placeholder="请输入QQ邮箱" />
            <span v-if="registerErrors.email" class="error-message">{{ registerErrors.email }}</span>
          </div>
          <div class="form-group">
            <label class="form-label" for="register-username">用户名</label>
            <input id="register-username" v-model="registerForm.username" type="text" class="form-input"
              :class="{ error: registerErrors.username }" placeholder="请输入用户名" />
            <span v-if="registerErrors.username" class="error-message">{{ registerErrors.username }}</span>
          </div>
          <div class="form-group">
            <label class="form-label" for="register-password">密码</label>
            <input id="register-password" v-model="registerForm.password" :type="showPassword ? 'text' : 'password'"
              class="form-input" :class="{ error: registerErrors.password }" placeholder="请输入密码（至少6位）" />
            <span v-if="registerErrors.password" class="error-message">{{ registerErrors.password }}</span>
          </div>
          <div class="form-group">
            <label class="form-label" for="register-confirm-password">确认密码</label>
            <input id="register-confirm-password" v-model="registerForm.confirmPassword"
              :type="showConfirmPassword ? 'text' : 'password'" class="form-input"
              :class="{ error: registerErrors.confirmPassword }" placeholder="请再次输入密码" />
            <span v-if="registerErrors.confirmPassword" class="error-message">{{ registerErrors.confirmPassword
            }}</span>
          </div>
          <div class="form-group">
            <label class="form-label" for="register-verification-code">验证码</label>
            <div class="verification-wrapper">
              <input id="register-verification-code" v-model="registerForm.verificationCode" type="text"
                class="form-input" :class="{ error: registerErrors.verificationCode }" placeholder="请输入验证码"
                maxlength="6" />
              <button type="button" class="send-code-btn" :disabled="isCodeSent && countdown > 0"
                @click="sendVerificationCode">
                <span v-if="isCodeSent && countdown > 0">{{ countdown }}s</span>
                <span v-else>发送验证码</span>
              </button>
            </div>
            <span v-if="registerErrors.verificationCode" class="error-message">{{ registerErrors.verificationCode
            }}</span>
          </div>

          <!-- hCaptcha 隐形容器（不可见） -->
          <div id="hcaptcha-container" style="display: none;"></div>

          <button type="submit" class="submit-btn" :disabled="!isRegisterValid || isLoading || !hcaptchaLoaded">
            <span v-if="isLoading">注册中...</span>
            <span v-else>注册</span>
          </button>
        </form>

        <!-- 忘记密码表单 -->
        <form v-else @submit.prevent="handleForgotPassword" class="auth-form">
          <div class="form-group">
            <label class="form-label" for="forgot-email">QQ邮箱</label>
            <input id="forgot-email" v-model="forgotPasswordForm.email" type="email" class="form-input"
              :class="{ error: forgotPasswordErrors.email }" placeholder="请输入QQ邮箱" />
            <span v-if="forgotPasswordErrors.email" class="error-message">{{ forgotPasswordErrors.email }}</span>
          </div>
          <div class="form-group">
            <label class="form-label" for="forgot-verification-code">验证码</label>
            <div class="verification-wrapper">
              <input id="forgot-verification-code" v-model="forgotPasswordForm.verificationCode" type="text"
                class="form-input" :class="{ error: forgotPasswordErrors.verificationCode }" placeholder="请输入验证码"
                maxlength="6" />
              <button type="button" class="send-code-btn" :disabled="isForgotCodeSent && forgotCountdown > 0"
                @click="sendForgotPasswordCode">
                <span v-if="isForgotCodeSent && forgotCountdown > 0">{{ forgotCountdown }}s</span>
                <span v-else>发送验证码</span>
              </button>
            </div>
            <span v-if="forgotPasswordErrors.verificationCode" class="error-message">{{
              forgotPasswordErrors.verificationCode }}</span>
          </div>
          <div class="form-group">
            <label class="form-label" for="forgot-new-password">新密码</label>
            <input id="forgot-new-password" v-model="forgotPasswordForm.newPassword"
              :type="showPassword ? 'text' : 'password'" class="form-input"
              :class="{ error: forgotPasswordErrors.newPassword }" placeholder="请输入新密码（至少6位）" />
            <span v-if="forgotPasswordErrors.newPassword" class="error-message">{{ forgotPasswordErrors.newPassword
            }}</span>
          </div>
          <div class="form-group">
            <label class="form-label" for="forgot-confirm-new-password">确认新密码</label>
            <input id="forgot-confirm-new-password" v-model="forgotPasswordForm.confirmNewPassword"
              :type="showConfirmPassword ? 'text' : 'password'" class="form-input"
              :class="{ error: forgotPasswordErrors.confirmNewPassword }" placeholder="请再次输入新密码" />
            <span v-if="forgotPasswordErrors.confirmNewPassword" class="error-message">{{
              forgotPasswordErrors.confirmNewPassword }}</span>
          </div>
          <button type="submit" class="submit-btn" :disabled="!isForgotPasswordValid || isLoading">
            <span v-if="isLoading">重置中...</span>
            <span v-else>重置密码</span>
          </button>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-register-container {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

/* 背景动画 - 优化版 */
.background-animation {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
}

.floating-shapes {
  position: relative;
  width: 100%;
  height: 100%;
}

.shape {
  position: absolute;
  border-radius: 50%;
  background: linear-gradient(45deg, rgba(255, 107, 107, 0.1), rgba(78, 205, 196, 0.1));
  will-change: transform;
  animation: float 8s ease-in-out infinite;
}

.shape-1 {
  width: 80px;
  height: 80px;
  top: 20%;
  left: 10%;
  animation-delay: 0s;
  animation-name: float1;
}

.shape-2 {
  width: 120px;
  height: 120px;
  top: 60%;
  right: 15%;
  animation-delay: 2s;
  animation-name: float2;
}

.shape-3 {
  width: 60px;
  height: 60px;
  top: 80%;
  left: 20%;
  animation-delay: 4s;
  animation-name: float3;
}

.shape-4 {
  width: 100px;
  height: 100px;
  top: 10%;
  right: 30%;
  animation-delay: 1s;
  animation-name: float4;
}

.shape-5 {
  width: 70px;
  height: 70px;
  bottom: 20%;
  right: 10%;
  animation-delay: 3s;
  animation-name: float5;
}

/* 不同的浮动动画路径 - 使用transform优化性能 */
@keyframes float1 {

  0%,
  100% {
    transform: translate(0, 0) rotate(0deg) scale(1);
  }

  33% {
    transform: translate(15px, -25px) rotate(120deg) scale(1.1);
  }

  66% {
    transform: translate(-10px, 15px) rotate(240deg) scale(0.9);
  }
}

@keyframes float2 {

  0%,
  100% {
    transform: translate(0, 0) rotate(0deg) scale(1);
  }

  33% {
    transform: translate(-20px, 20px) rotate(-90deg) scale(0.85);
  }

  66% {
    transform: translate(15px, -15px) rotate(-180deg) scale(1.15);
  }
}

@keyframes float3 {

  0%,
  100% {
    transform: translate(0, 0) rotate(0deg) scale(1);
  }

  50% {
    transform: translate(10px, -30px) rotate(180deg) scale(1.2);
  }
}

@keyframes float4 {

  0%,
  100% {
    transform: translate(0, 0) rotate(0deg) scale(1);
  }

  25% {
    transform: translate(-15px, 10px) rotate(90deg) scale(0.9);
  }

  75% {
    transform: translate(20px, -10px) rotate(270deg) scale(1.1);
  }
}

@keyframes float5 {

  0%,
  100% {
    transform: translate(0, 0) rotate(0deg) scale(1);
  }

  40% {
    transform: translate(10px, 15px) rotate(-120deg) scale(1.15);
  }

  80% {
    transform: translate(-15px, -10px) rotate(-240deg) scale(0.85);
  }
}

/* 主卡片 - 优化动画 */
.auth-card {
  background: rgba(255, 255, 255, 0.95);
  -webkit-backdrop-filter: blur(20px);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  padding: 40px;
  width: 400px;
  max-width: 90vw;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
  position: relative;
  z-index: 1;
  animation: slideIn 0.8s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(50px) scale(0.9);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* 标题区域 */
.card-header {
  text-align: center;
  margin-bottom: 30px;
}

.logo-section {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 15px;
}

.game-icon {
  font-size: 2.5rem;
  margin-right: 15px;
  animation: bounce 2s ease-in-out infinite;
}

@keyframes bounce {

  0%,
  20%,
  50%,
  80%,
  100% {
    transform: translateY(0);
  }

  40% {
    transform: translateY(-10px);
  }

  60% {
    transform: translateY(-5px);
  }
}

.title {
  font-size: 2rem;
  font-weight: bold;
  background: linear-gradient(45deg, #667eea, #764ba2);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin: 0;
}

.subtitle {
  color: #666;
  font-size: 1rem;
  margin: 10px 0 0 0;
}

/* 表单样式 */
.form-container {
  margin-bottom: 20px;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
}

.form-label {
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
  font-size: 0.9rem;
}

.form-input {
  width: 100%;
  padding: 12px 15px;
  border: 2px solid #e1e5e9;
  border-radius: 10px;
  font-size: 1rem;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  background: rgba(255, 255, 255, 0.9);
}

.form-input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow:
    0 0 0 3px rgba(102, 126, 234, 0.1),
    0 4px 12px rgba(102, 126, 234, 0.15);
  transform: translateY(-2px) scale(1.01);
  background: rgba(255, 255, 255, 1);
}

.form-input.error {
  border-color: #ff6b6b;
  box-shadow: 0 0 0 3px rgba(255, 107, 107, 0.1);
  animation: shakeError 0.5s ease-in-out;
}

@keyframes shakeError {

  0%,
  100% {
    transform: translateX(0);
  }

  25% {
    transform: translateX(-8px);
  }

  75% {
    transform: translateX(8px);
  }
}

/* 验证码样式 */
.verification-wrapper {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.send-code-btn {
  padding: 12px 15px;
  background: linear-gradient(45deg, #667eea, #764ba2);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  white-space: nowrap;
  min-width: 100px;
}

.send-code-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 5px 15px rgba(102, 126, 234, 0.3);
}

.send-code-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  background: #ccc;
}

.error-message {
  color: #ff6b6b;
  font-size: 0.8rem;
  margin-top: 5px;
  animation: shake 0.5s ease-in-out;
}

@keyframes shake {

  0%,
  100% {
    transform: translateX(0);
  }

  25% {
    transform: translateX(-5px);
  }

  75% {
    transform: translateX(5px);
  }
}

/* 提交按钮 */
.submit-btn {
  background: linear-gradient(45deg, #667eea, #764ba2);
  color: white;
  border: none;
  padding: 15px;
  border-radius: 10px;
  font-size: 1.1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 10px 20px rgba(102, 126, 234, 0.3);
}

.submit-btn:active:not(:disabled) {
  transform: translateY(0);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 模式切换 */
.mode-switch {
  text-align: center;
  margin: 20px 0;
  font-size: 1.1rem;
  -webkit-user-select: none;
  user-select: none;
}

.mode-switch span {
  cursor: pointer;
  color: #888;
  padding: 0 8px;
  transition: color 0.2s;
}

.mode-switch span.active {
  color: #667eea;
  font-weight: bold;
  text-decoration: underline;
}

/* 忘记密码链接 */
.forgot-password-link {
  text-align: center;
  margin-top: 15px;
}

.forgot-btn {
  background: none;
  border: none;
  color: #667eea;
  font-size: 0.9rem;
  cursor: pointer;
  text-decoration: underline;
  transition: color 0.3s ease;
}

.forgot-btn:hover {
  color: #764ba2;
}

/* hCaptcha 容器样式 */
.hcaptcha-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 78px;
  margin: 10px 0;
}

.hcaptcha-wrapper>div {
  margin: 0 auto;
}

/* 性能优化 - 减少动画偏好设置支持 */
@media (prefers-reduced-motion: reduce) {

  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }

  .shape {
    animation: none;
  }
}

/* 响应式设计 */
@media (max-width: 480px) {
  .auth-card {
    padding: 30px 20px;
    width: 95vw;
  }

  .title {
    font-size: 1.8rem;
  }

  .verification-wrapper {
    flex-direction: column;
  }

  .send-code-btn {
    width: 100%;
  }
}
</style>
