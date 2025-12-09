<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import request from '../../config/api'
import { useUserStore } from '../../config/user'
import { ElMessage } from 'element-plus'

const emit = defineEmits(['success', 'fail', 'focus', 'blur', 'showPasswordChange', 'switchMode'])
const router = useRouter()

const isLoading = ref(false)
const showPassword = ref(false)

const form = reactive({
  email: '',
  password: ''
})

const errors = reactive({
  email: '',
  password: ''
})

const isValid = computed(() => form.email.trim() && form.password.trim())

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
  
  if (!form.password.trim()) {
    errors.password = '请输入密码'
    valid = false
  } else {
    errors.password = ''
  }
  
  return valid
}

const handleSubmit = async () => {
  if (!validate()) return
  
  isLoading.value = true
  try {
    const response = await request.post('/user/login', {
      email: form.email,
      password: form.password
    })
    
    if (response.data.success) {
      const userStore = useUserStore()
      userStore.setAuth({
        token: response.data.token,
        user: response.data.user
      })
      emit('success')
      router.push('/user')
    } else {
      ElMessage.error(response.data.message || '登录失败')
      emit('fail')
    }
  } catch (error) {
    const msg = error.response?.data?.message || '网络错误,请稍后重试'
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
      <label class="form-label" for="login-email">QQ邮箱</label>
      <input 
        id="login-email" 
        v-model="form.email" 
        type="email" 
        class="form-input"
        :class="{ error: errors.email }" 
        placeholder="请输入QQ邮箱"
        @focus="handleFocus('email')"
        @blur="handleBlur"
      />
      <span v-if="errors.email" class="error-message">{{ errors.email }}</span>
    </div>
    
    <div class="form-group">
      <label class="form-label" for="login-password">密码</label>
      <div class="password-wrapper">
        <input 
          id="login-password" 
          v-model="form.password" 
          :type="showPassword ? 'text' : 'password'"
          class="form-input" 
          :class="{ error: errors.password }" 
          placeholder="请输入密码"
          @focus="handleFocus('password')"
          @blur="handleBlur"
        />
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
    
    <button type="submit" class="submit-btn" :disabled="!isValid || isLoading">
      <span v-if="isLoading">登录中...</span>
      <span v-else>登 录</span>
    </button>
    
    <div class="forgot-link">
      <span @click="emit('switchMode', 'forgot')">忘记密码？</span>
    </div>
  </form>
</template>

<style scoped>
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
  padding-right: 50px;
}

.toggle-password {
  position: absolute;
  right: 14px;
  background: none;
  border: none;
  color: #666;
  cursor: pointer;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.toggle-password:hover {
  color: #333;
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

.forgot-link {
  text-align: center;
  margin-top: 4px;
}

.forgot-link span {
  color: #666;
  cursor: pointer;
  font-size: 0.85rem;
}

.forgot-link span:hover {
  color: #000;
  text-decoration: underline;
}
</style>
