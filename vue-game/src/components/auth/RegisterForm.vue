<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import request from '../../config/api'
import { ElMessage } from 'element-plus'

const emit = defineEmits(['success', 'fail', 'focus', 'blur', 'showPasswordChange', 'switchMode'])

const isLoading = ref(false)
const showPassword = ref(false)

// hCaptcha
const hcaptchaToken = ref('')
const hcaptchaLoaded = ref(false)
const hcaptchaWidgetId = ref(null)
const HCAPTCHA_SITE_KEY = 'dceab3a2-519c-4ec2-977e-1aea8b94e6c6'

// 验证码倒计时
const isCodeSent = ref(false)
const countdown = ref(0)
let countdownTimer = null

const form = reactive({
  email: '',
  username: '',
  password: '',
  confirmPassword: '',
  verificationCode: ''
})

const errors = reactive({
  email: '',
  username: '',
  password: '',
  confirmPassword: '',
  verificationCode: ''
})

const isValid = computed(() =>
  form.email.trim() &&
  form.username.trim() &&
  form.password.trim() &&
  form.confirmPassword.trim() &&
  form.password === form.confirmPassword &&
  form.verificationCode.trim()
)

const validateEmail = (email) => /^[1-9]\d{4,}@qq\.com$/i.test(email)

const validate = () => {
  let valid = true

  if (!form.email.trim()) {
    errors.email = '请输入QQ邮箱'
    valid = false
  } else if (!validateEmail(form.email)) {
    errors.email = '请输入有效的QQ邮箱地址'
    valid = false
  } else {
    errors.email = ''
  }

  if (!form.username.trim()) {
    errors.username = '请输入用户名'
    valid = false
  } else {
    errors.username = ''
  }

  if (!form.password.trim()) {
    errors.password = '请输入密码'
    valid = false
  } else if (form.password.length < 6) {
    errors.password = '密码长度至少6位'
    valid = false
  } else {
    errors.password = ''
  }

  if (!form.confirmPassword.trim()) {
    errors.confirmPassword = '请确认密码'
    valid = false
  } else if (form.password !== form.confirmPassword) {
    errors.confirmPassword = '两次输入的密码不一致'
    valid = false
  } else {
    errors.confirmPassword = ''
  }

  if (!form.verificationCode.trim()) {
    errors.verificationCode = '请输入验证码'
    valid = false
  } else {
    errors.verificationCode = ''
  }

  return valid
}

// 当前操作类型：'register' 或 'sendCode'
const currentAction = ref('register')

// hCaptcha 相关
const renderHcaptcha = () => {
  nextTick(() => {
    const container = document.getElementById('hcaptcha-container')
    if (!container || !globalThis.hcaptcha) return

    if (hcaptchaWidgetId.value !== null) {
      try {
        globalThis.hcaptcha.remove(hcaptchaWidgetId.value)
      } catch (e) {}
    }

    container.innerHTML = ''

    try {
      hcaptchaWidgetId.value = globalThis.hcaptcha.render('hcaptcha-container', {
        sitekey: HCAPTCHA_SITE_KEY,
        size: 'invisible',
        callback: (token) => {
          hcaptchaToken.value = token
          // 根据操作类型执行不同逻辑
          if (currentAction.value === 'sendCode') {
            doSendCode()
          } else {
            submitRegister()
          }
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

const resetHcaptcha = () => {
  hcaptchaToken.value = ''
  if (globalThis.hcaptcha && hcaptchaWidgetId.value !== null) {
    try {
      globalThis.hcaptcha.reset(hcaptchaWidgetId.value)
    } catch (e) {}
  }
}

onMounted(() => {
  if (document.querySelector('script[src*="hcaptcha.com"]')) {
    hcaptchaLoaded.value = true
    renderHcaptcha()
  } else {
    const script = document.createElement('script')
    script.src = 'https://js.hcaptcha.com/1/api.js'
    script.async = true
    script.defer = true
    script.onload = () => {
      hcaptchaLoaded.value = true
      renderHcaptcha()
    }
    document.head.appendChild(script)
  }
})

onUnmounted(() => {
  if (hcaptchaWidgetId.value !== null && globalThis.hcaptcha) {
    try {
      globalThis.hcaptcha.remove(hcaptchaWidgetId.value)
    } catch (e) {}
  }
  if (countdownTimer) clearInterval(countdownTimer)
})

// 发送验证码（先触发人机验证）
const sendVerificationCode = () => {
  if (!form.email.trim()) {
    ElMessage.warning('请先输入QQ邮箱')
    return
  }
  if (!validateEmail(form.email)) {
    ElMessage.warning('请输入有效的QQ邮箱地址')
    return
  }
  if (!hcaptchaLoaded.value || !globalThis.hcaptcha || hcaptchaWidgetId.value === null) {
    ElMessage.warning('人机验证尚未加载完成，请稍后重试')
    return
  }

  currentAction.value = 'sendCode'
  hcaptchaToken.value = ''
  try {
    globalThis.hcaptcha.execute(hcaptchaWidgetId.value)
  } catch (e) {
    ElMessage.error('启动人机验证失败，请刷新页面重试')
  }
}

// 实际发送验证码
const doSendCode = async () => {
  if (!hcaptchaToken.value) {
    ElMessage.warning('人机验证失败，请重试')
    return
  }

  isCodeSent.value = true
  countdown.value = 90
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)

  try {
    const response = await request.post('/email/sendCode', { 
      email: form.email,
      hcaptchaToken: hcaptchaToken.value
    }, { timeout: 30000 })
    if (response.data.success) {
      ElMessage.success('验证码已发送到您的邮箱')
    } else {
      isCodeSent.value = false
      countdown.value = 0
      if (countdownTimer) clearInterval(countdownTimer)
      ElMessage.error(response.data.message || '发送验证码失败')
    }
  } catch (error) {
    isCodeSent.value = false
    countdown.value = 0
    if (countdownTimer) clearInterval(countdownTimer)
    ElMessage.error('发送验证码失败，请稍后重试')
  } finally {
    resetHcaptcha()
  }
}

// 触发人机验证（注册）
const handleSubmit = () => {
  if (!validate()) return

  if (!hcaptchaLoaded.value || !globalThis.hcaptcha || hcaptchaWidgetId.value === null) {
    ElMessage.warning('人机验证尚未加载完成，请稍后重试')
    return
  }

  currentAction.value = 'register'
  hcaptchaToken.value = ''
  try {
    globalThis.hcaptcha.execute(hcaptchaWidgetId.value)
  } catch (e) {
    ElMessage.error('启动人机验证失败，请刷新页面重试')
  }
}

// 实际提交注册
const submitRegister = async () => {
  if (!hcaptchaToken.value) {
    ElMessage.warning('人机验证失败，请重试')
    return
  }

  isLoading.value = true
  try {
    const response = await request.post('/user/register', {
      email: form.email,
      username: form.username,
      password: form.password,
      confirmPassword: form.confirmPassword,
      code: form.verificationCode,
      hcaptchaToken: hcaptchaToken.value
    })

    if (response.data.success) {
      ElMessage.success('注册成功！请使用新账号登录')
      emit('success')
      emit('switchMode', 'login')
    } else {
      ElMessage.error(response.data.message || '注册失败')
      emit('fail')
      resetHcaptcha()
    }
  } catch (error) {
    const msg = error.response?.data?.message || '网络错误，请稍后重试'
    ElMessage.error(msg)
    emit('fail')
    resetHcaptcha()
  } finally {
    isLoading.value = false
  }
}

const handleFocus = (field) => {
  emit('focus', field)
}

const handleBlur = () => {
  emit('blur')
}

const togglePassword = () => {
  showPassword.value = !showPassword.value
  emit('showPasswordChange', showPassword.value)
}
</script>

<template>
  <form @submit.prevent="handleSubmit" class="auth-form">
    <div class="form-group">
      <label class="form-label">QQ邮箱</label>
      <input v-model="form.email" type="email" class="form-input" :class="{ error: errors.email }"
        placeholder="请输入QQ邮箱" @focus="handleFocus('email')" @blur="handleBlur" />
      <span v-if="errors.email" class="error-message">{{ errors.email }}</span>
    </div>

    <div class="form-group">
      <label class="form-label">用户名</label>
      <input v-model="form.username" type="text" class="form-input" :class="{ error: errors.username }"
        placeholder="请输入用户名" @focus="handleFocus('username')" @blur="handleBlur" />
      <span v-if="errors.username" class="error-message">{{ errors.username }}</span>
    </div>

    <div class="form-group">
      <label class="form-label">密码</label>
      <div class="password-wrapper">
        <input v-model="form.password" :type="showPassword ? 'text' : 'password'" class="form-input"
          :class="{ error: errors.password }" placeholder="请输入密码（至少6位）" @focus="handleFocus('password')"
          @blur="handleBlur" />
        <button type="button" class="toggle-password" @click="togglePassword">
          <svg v-if="showPassword" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
            <line x1="1" y1="1" x2="23" y2="23"/>
          </svg>
          <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
            <circle cx="12" cy="12" r="3"/>
          </svg>
        </button>
      </div>
      <span v-if="errors.password" class="error-message">{{ errors.password }}</span>
    </div>

    <div class="form-group">
      <label class="form-label">确认密码</label>
      <input v-model="form.confirmPassword" :type="showPassword ? 'text' : 'password'" class="form-input"
        :class="{ error: errors.confirmPassword }" placeholder="请再次输入密码" @focus="handleFocus('password')"
        @blur="handleBlur" />
      <span v-if="errors.confirmPassword" class="error-message">{{ errors.confirmPassword }}</span>
    </div>

    <div class="form-group">
      <label class="form-label">验证码</label>
      <div class="verification-wrapper">
        <input v-model="form.verificationCode" type="text" class="form-input"
          :class="{ error: errors.verificationCode }" placeholder="请输入验证码" maxlength="6"
          @focus="handleFocus('email')" @blur="handleBlur" />
        <button type="button" class="send-code-btn" :disabled="isCodeSent && countdown > 0"
          @click="sendVerificationCode">
          <span v-if="isCodeSent && countdown > 0">{{ countdown }}s</span>
          <span v-else>发送验证码</span>
        </button>
      </div>
      <span v-if="errors.verificationCode" class="error-message">{{ errors.verificationCode }}</span>
    </div>

    <div id="hcaptcha-container" style="display: none;"></div>

    <button type="submit" class="submit-btn" :disabled="!isValid || isLoading || !hcaptchaLoaded">
      <span v-if="isLoading">注册中...</span>
      <span v-else>注册</span>
    </button>
  </form>
</template>

<style scoped>
.auth-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-group {
  display: flex;
  flex-direction: column;
}

.form-label {
  font-weight: 500;
  color: #333;
  margin-bottom: 8px;
  font-size: 0.9rem;
}

.form-input {
  width: 100%;
  padding: 14px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 0.95rem;
  transition: border-color 0.2s ease;
  background: #fff;
  box-sizing: border-box;
  color: #333;
}

.form-input::placeholder {
  color: #999;
}

.form-input:focus {
  outline: none;
  border-color: #333;
}

.form-input.error {
  border-color: #e53935;
}

.password-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.password-wrapper .form-input {
  padding-right: 60px;
}

.toggle-password {
  position: absolute;
  right: 14px;
  background: none;
  border: none;
  color: #666;
  cursor: pointer;
  font-size: 0.85rem;
}

.toggle-password:hover {
  color: #333;
}

.verification-wrapper {
  display: flex;
  gap: 10px;
}

.verification-wrapper .form-input {
  flex: 1;
}

.send-code-btn {
  padding: 14px 16px;
  background: #000;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  min-width: 100px;
  transition: background 0.2s ease;
}

.send-code-btn:hover:not(:disabled) {
  background: #333;
}

.send-code-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.error-message {
  color: #e53935;
  font-size: 0.8rem;
  margin-top: 6px;
}

.submit-btn {
  background: #000;
  color: #fff;
  border: none;
  padding: 14px;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s ease;
  margin-top: 8px;
}

.submit-btn:hover:not(:disabled) {
  background: #333;
}

.submit-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

/* 移动端适配 */
@media (max-width: 480px) {
  .auth-form {
    gap: 14px;
  }

  .form-label {
    font-size: 0.85rem;
    margin-bottom: 6px;
  }

  .form-input {
    padding: 12px 14px;
    font-size: 16px; /* 防止 iOS 自动缩放 */
    border-radius: 6px;
  }

  .verification-wrapper {
    flex-direction: column;
    gap: 8px;
  }

  .send-code-btn {
    width: 100%;
    padding: 12px;
    min-width: auto;
  }

  .submit-btn {
    padding: 12px;
    font-size: 0.95rem;
    margin-top: 4px;
  }
}
</style>
