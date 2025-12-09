<script setup>
import { ref, watch } from 'vue'
import LoginVideo from '../LoginVideo.vue'
import LoginForm from './LoginForm.vue'
import RegisterForm from './RegisterForm.vue'
import ForgotPasswordForm from './ForgotPasswordForm.vue'

const mode = ref('login') // 'login' | 'register' | 'forgot'
const isFocused = ref(false)
const showPassword = ref(false)
const fieldType = ref('')

const loginVideoRef = ref(null)

const switchMode = (newMode) => {
  mode.value = newMode
  // 重置动画组件状态
  if (loginVideoRef.value?.resetState) {
    loginVideoRef.value.resetState()
  }
}

const handleFocus = (field) => {
  isFocused.value = true
  fieldType.value = field
}

const handleBlur = () => {
  isFocused.value = false
  fieldType.value = ''
}

const handleShowPasswordChange = (show) => {
  showPassword.value = show
}

const handleSuccess = () => {
  if (loginVideoRef.value?.playSuccessAnimation) {
    loginVideoRef.value.playSuccessAnimation()
  }
}

const handleFail = () => {
  if (loginVideoRef.value?.playFailureAnimation) {
    loginVideoRef.value.playFailureAnimation()
  }
}

// 监听模式切换，重置状态
watch(mode, () => {
  isFocused.value = false
  showPassword.value = false
  fieldType.value = ''
})
</script>

<template>
  <div class="auth-layout">
    <!-- 左侧动画区域 -->
    <div class="animation-section">
      <LoginVideo 
        ref="loginVideoRef"
        :isFocused="isFocused" 
        :showPassword="showPassword" 
        :fieldType="fieldType" 
      />
    </div>

    <!-- 右侧表单区域 - 白色背景 -->
    <div class="form-section">
      <!-- Logo图标 -->
      <div class="logo-icon">✦</div>
      
      <!-- 欢迎标题 - 根据模式显示不同内容 -->
      <h1 class="welcome-title">
        {{ mode === 'login' ? '欢迎回来！' : mode === 'register' ? '创建账号' : '重置密码' }}
      </h1>
      <p class="welcome-subtitle">
        {{ mode === 'login' ? '请输入您的登录信息' : mode === 'register' ? '填写以下信息完成注册' : '通过邮箱验证重置您的密码' }}
      </p>

      <!-- 模式切换按钮 - 忘记密码模式不显示 -->
      <div class="mode-tabs" v-if="mode !== 'forgot'">
        <button 
          :class="['tab-btn', { active: mode === 'login' }]" 
          @click="switchMode('login')"
        >登录</button>
        <button 
          :class="['tab-btn', { active: mode === 'register' }]" 
          @click="switchMode('register')"
        >注册</button>
      </div>

      <!-- 表单容器 -->
      <div class="form-container">
        <LoginForm 
          v-if="mode === 'login'"
          @success="handleSuccess"
          @fail="handleFail"
          @focus="handleFocus"
          @blur="handleBlur"
          @showPasswordChange="handleShowPasswordChange"
          @switchMode="switchMode"
        />
        
        <RegisterForm 
          v-else-if="mode === 'register'"
          @success="handleSuccess"
          @fail="handleFail"
          @focus="handleFocus"
          @blur="handleBlur"
          @showPasswordChange="handleShowPasswordChange"
          @switchMode="switchMode"
        />
        
        <ForgotPasswordForm 
          v-else
          @success="handleSuccess"
          @fail="handleFail"
          @focus="handleFocus"
          @blur="handleBlur"
          @showPasswordChange="handleShowPasswordChange"
          @switchMode="switchMode"
        />
      </div>


    </div>
  </div>
</template>

<style scoped>
.auth-layout {
  width: 100vw;
  height: 100vh;
  display: flex;
  overflow: hidden;
}

/* 左侧动画区域 */
.animation-section {
  flex: 1;
  display: flex;
  align-items: stretch;
  justify-content: stretch;
}

.animation-section :deep(.characters-section) {
  width: 100%;
  height: 100%;
}

/* 右侧表单区域 - 纯白色背景 */
.form-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #FFFFFF;
  padding: 40px 60px;
  overflow-y: auto;
}

/* Logo图标 */
.logo-icon {
  font-size: 2.5rem;
  color: #000;
  margin-bottom: 20px;
}

/* 欢迎标题 */
.welcome-title {
  font-size: 1.8rem;
  font-weight: 700;
  color: #000;
  margin: 0 0 8px 0;
}

.welcome-subtitle {
  font-size: 0.95rem;
  color: #666;
  margin: 0 0 30px 0;
}

/* 模式切换按钮 */
.mode-tabs {
  display: flex;
  gap: 12px;
  margin-bottom: 30px;
}

.tab-btn {
  padding: 12px 40px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  background: #fff;
  color: #333;
  font-size: 0.95rem;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tab-btn:hover {
  border-color: #999;
}

.tab-btn.active {
  background: #000;
  color: #fff;
  border-color: #000;
}

/* 表单容器 */
.form-container {
  width: 100%;
  max-width: 360px;
}

/* 响应式 - 小屏幕隐藏动画区域 */
@media (max-width: 900px) {
  .animation-section {
    display: none;
  }
  
  .form-section {
    flex: 1;
    padding: 30px 20px;
  }
}

/* 滚动条美化 */
.form-section::-webkit-scrollbar {
  width: 6px;
}

.form-section::-webkit-scrollbar-track {
  background: #f5f5f5;
}

.form-section::-webkit-scrollbar-thumb {
  background: #ddd;
  border-radius: 3px;
}
</style>
