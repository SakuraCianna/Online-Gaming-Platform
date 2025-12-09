<script setup>
import { ref, reactive, computed, onUnmounted } from 'vue'
import request from '../../config/api'
import { ElMessage } from 'element-plus'

const emit = defineEmits(['success', 'fail', 'focus', 'blur', 'showPasswordChange', 'switchMode'])

const isLoading = ref(false)
const showPassword = ref(false)

// 验证码倒计时
const isCodeSent = ref(false)
const countdown = ref(0)
let countdownTimer = null

const form = reactive({
  email: '',
  verificationCode: '',
  newPassword: '',
  confirmNewPassword: ''
})

const errors = reactive({
  email: '',
  verificationCode: '',
  newPassword: '',
  confirmNewPassword: ''
})

const isValid = computed(() =>
  form.email.trim() &&
  form.verificationCode.trim() &&
  form.newPassword.trim() &&
  form.confirmNewPassword.trim() &&
  form.newPassword === form.confirmNewPassword
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

  if (!form.verificationCode.trim()) {
    errors.verificationCode = '请输入验证码'
    valid = false
  } else {
    errors.verificationCode = ''
  }

  if (!form.newPassword.trim()) {
    errors.newPassword = '请输入新密码'
    valid = false
  } else if (form.newPassword.length < 6) {
    errors.newPassword = '密码长度至少6位'
    valid = false
  } else {
    errors.newPassword = ''
  }

  if (!form.confirmNewPassword.trim()) {
    errors.confirmNewPassword = '请确认新密码'
    valid = false
  } else if (form.newPassword !== form.confirmNewPassword) {
    errors.confirmNewPassword = '两次输入的密码不一致'
    valid = false
  } else {
    errors.confirmNewPassword = ''
  }

  return valid
}

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

// 发送验证码
const sendVerificationCode = async () => {
  if (!form.email.trim()) {
    ElMessage.warning('请先输入QQ邮箱')
    return
  }
  if (!validateEmail(form.email)) {
    ElMessage.warning('请输入有效的QQ邮箱地址')
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
    const response = await request.post('/email/sendCode', { email: form.email }, { timeout: 30000 })
    if (response.data.success) {
      ElMessage.success('验证码已发送到您的邮箱')
    } else {
      isCodeSent.value = false
      countdown.value = 0
      if (countdownTimer) clearInterval(countdownTimer)
      ElMessage.error(response.data.message || '发送验证码失败')
    }
  } catch (error) {
    ElMessage.error('发送验证码失败，请稍后重试')
  }
}

const handleSubmit = async () => {
  if (!validate()) return

  isLoading.value = true
  try {
    const response = await request.post('/user/resetPassword', {
      email: form.email,
      code: form.verificationCode,
      newPassword: form.newPassword,
      confirmNewPassword: form.confirmNewPassword
    })

    if (response.data.success) {
      ElMessage.success('密码重置成功！请使用新密码登录')
      emit('success')
      emit('switchMode', 'login')
    } else {
      ElMessage.error(response.data.message || '密码重置失败')
      emit('fail')
    }
  } catch (error) {
    const msg = error.response?.data?.message || '网络错误，请稍后重试'
    ElMessage.error(msg)
    emit('fail')
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

    <div class="form-group">
      <label class="form-label">新密码</label>
      <div class="password-wrapper">
        <input v-model="form.newPassword" :type="showPassword ? 'text' : 'password'" class="form-input"
          :class="{ error: errors.newPassword }" placeholder="请输入新密码（至少6位）" @focus="handleFocus('password')"
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
      <span v-if="errors.newPassword" class="error-message">{{ errors.newPassword }}</span>
    </div>

    <div class="form-group">
      <label class="form-label">确认新密码</label>
      <input v-model="form.confirmNewPassword" :type="showPassword ? 'text' : 'password'" class="form-input"
        :class="{ error: errors.confirmNewPassword }" placeholder="请再次输入新密码" @focus="handleFocus('password')"
        @blur="handleBlur" />
      <span v-if="errors.confirmNewPassword" class="error-message">{{ errors.confirmNewPassword }}</span>
    </div>

    <button type="submit" class="submit-btn" :disabled="!isValid || isLoading">
      <span v-if="isLoading">重置中...</span>
      <span v-else>重置密码</span>
    </button>

    <div class="form-footer">
      <span class="link" @click="emit('switchMode', 'login')">返回登录</span>
    </div>
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

.form-footer {
  text-align: center;
  margin-top: 8px;
}

.link {
  color: #666;
  cursor: pointer;
  font-size: 0.9rem;
}

.link:hover {
  color: #000;
  text-decoration: underline;
}
</style>
