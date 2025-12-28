<template>
  <div class="settings-container">
    <div class="settings-header">
      <h2>个人设置</h2>
    </div>

    <div class="settings-content">
      <!-- 头像设置 -->
      <div class="settings-card">
        <div class="card-title">
          <span class="icon">👤</span>
          <span>头像设置</span>
        </div>
        <div class="avatar-section">
          <div class="current-avatar">
            <img :src="user?.avatar || '/image/default-avatar.jpg'" alt="头像" />
          </div>
          <div class="avatar-actions">
            <input 
              ref="avatarInput" 
              type="file" 
              accept="image/*" 
              style="display: none" 
              @change="handleAvatarChange"
            />
            <button class="btn-upload" @click="triggerAvatarUpload">
              <span class="btn-icon">📷</span>
              <span>更换头像</span>
            </button>
            <p class="avatar-tip">支持 JPG、PNG 格式，大小不超过 5MB</p>
          </div>
        </div>
      </div>

      <!-- 基本信息 -->
      <div class="settings-card">
        <div class="card-title">
          <span class="icon">📝</span>
          <span>基本信息</span>
        </div>
        <div class="form-group">
          <label>用户名</label>
          <input 
            v-model="formData.username" 
            type="text" 
            placeholder="请输入用户名"
            maxlength="20"
          />
        </div>
        <div class="form-group">
          <label>邮箱</label>
          <input 
            :value="user?.email" 
            type="email" 
            disabled 
            class="disabled"
          />
          <span class="field-tip">邮箱不可修改</span>
        </div>

        <button class="btn-save" :disabled="saving" @click="saveBasicInfo">
          {{ saving ? '保存中...' : '保存修改' }}
        </button>
      </div>

      <!-- 修改密码 -->
      <div class="settings-card">
        <div class="card-title">
          <span class="icon">🔒</span>
          <span>修改密码</span>
        </div>
        <div class="form-group">
          <label>当前密码</label>
          <input 
            v-model="passwordForm.oldPassword" 
            type="password" 
            placeholder="请输入当前密码"
          />
        </div>
        <div class="form-group">
          <label>新密码</label>
          <input 
            v-model="passwordForm.newPassword" 
            type="password" 
            placeholder="请输入新密码（至少6位）"
          />
        </div>
        <div class="form-group">
          <label>确认新密码</label>
          <input 
            v-model="passwordForm.confirmPassword" 
            type="password" 
            placeholder="请再次输入新密码"
          />
        </div>
        <button class="btn-save" :disabled="changingPassword" @click="changePassword">
          {{ changingPassword ? '修改中...' : '修改密码' }}
        </button>
      </div>

      <!-- 账号安全 -->
      <div class="settings-card">
        <div class="card-title">
          <span class="icon">🛡️</span>
          <span>账号安全</span>
        </div>
        <div class="security-item">
          <div class="security-info">
            <span class="security-label">登录状态</span>
            <span class="security-value online">在线</span>
          </div>
        </div>
        <div class="security-item">
          <div class="security-info">
            <span class="security-label">注册时间</span>
            <span class="security-value">{{ formatDate(user?.createTime) }}</span>
          </div>
        </div>
        <div class="security-item danger">
          <div class="security-info">
            <span class="security-label">退出登录</span>
            <span class="security-desc">退出当前账号</span>
          </div>
          <button class="btn-logout" @click="handleLogout">退出</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { storeToRefs } from 'pinia'
import { useUserStore } from '../../config/user'
import request from '../../config/api'

const router = useRouter()
const userStore = useUserStore()
const { user } = storeToRefs(userStore)

const avatarInput = ref(null)
const saving = ref(false)
const changingPassword = ref(false)

// 基本信息表单
const formData = reactive({
  username: ''
})

// 密码表单
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 监听用户信息变化，初始化表单数据
watch(user, (newUser) => {
  if (newUser) {
    formData.username = newUser.username || ''
  }
}, { immediate: true })

// 触发头像上传
function triggerAvatarUpload() {
  avatarInput.value?.click()
}

// 处理头像更换
async function handleAvatarChange(e) {
  const file = e.target.files?.[0]
  if (!file) return

  // 验证文件类型
  if (!['image/jpeg', 'image/png', 'image/gif'].includes(file.type)) {
    ElMessage.error('请上传 JPG、PNG 或 GIF 格式的图片')
    return
  }

  // 验证文件大小（5MB）
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 5MB')
    return
  }

  try {
    const uploadData = new FormData()
    uploadData.append('file', file)
    // 不传 userId，后端从 token 获取

    const response = await request.post('/user/avatar', uploadData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })

    if (response.data.success || response.data.code === 200) {
      userStore.setUserInfo({ ...user.value, avatar: response.data.avatar || response.data.data })
      ElMessage.success('头像更新成功')
    } else {
      ElMessage.error(response.data.message || '头像更新失败')
    }
  } catch (error) {
    ElMessage.error('头像上传失败，请重试')
  }

  e.target.value = ''
}

// 保存基本信息
async function saveBasicInfo() {
  if (!user.value?.id) {
    ElMessage.error('用户信息异常，请重新登录')
    return
  }

  if (!formData.username?.trim()) {
    ElMessage.warning('用户名不能为空')
    return
  }

  saving.value = true
  try {
    const response = await request.put('/user/update', {
      username: formData.username.trim()
    })

    if (response.data.success || response.data.code === 200) {
      userStore.setUserInfo({
        ...user.value,
        username: formData.username.trim()
      })
      ElMessage.success('保存成功')
    } else {
      ElMessage.error(response.data.message || '保存失败')
    }
  } catch (error) {
    ElMessage.error('保存失败，请重试')
  } finally {
    saving.value = false
  }
}

// 修改密码
async function changePassword() {
  if (!user.value?.id) {
    ElMessage.error('用户信息异常，请重新登录')
    return
  }

  if (!passwordForm.oldPassword) {
    ElMessage.warning('请输入当前密码')
    return
  }
  if (!passwordForm.newPassword || passwordForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少需要6位')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }

  changingPassword.value = true
  try {
    const response = await request.put('/user/password', {
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })

    if (response.data.success || response.data.code === 200) {
      ElMessage.success('密码修改成功')
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
    } else {
      ElMessage.error(response.data.message || '密码修改失败')
    }
  } catch (error) {
    ElMessage.error('密码修改失败，请重试')
  } finally {
    changingPassword.value = false
  }
}

// 退出登录
async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    userStore.logout()
    router.push('/')
    ElMessage.success('已退出登录')
  } catch {
    // 用户取消
  }
}

// 格式化日期
function formatDate(dateStr) {
  if (!dateStr) return '未知'
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
}
</script>


<style scoped>
.settings-container {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;
  height: 100%;
  overflow: hidden;
}

.settings-header {
  margin-bottom: 24px;
}

.settings-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: #333;
  margin: 0;
}

.settings-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.settings-card {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.card-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.card-title .icon {
  font-size: 20px;
}

/* 头像设置 */
.avatar-section {
  display: flex;
  align-items: center;
  gap: 24px;
}

.current-avatar {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  flex-shrink: 0;
}

.current-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.btn-upload {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-upload:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.avatar-tip {
  font-size: 12px;
  color: #999;
  margin: 0;
}

/* 表单样式 */
.form-group {
  margin-bottom: 20px;
  position: relative;
}

.form-group label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 12px 16px;
  border: 2px solid #e8e8e8;
  border-radius: 10px;
  font-size: 14px;
  transition: all 0.2s ease;
  box-sizing: border-box;
}

.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.form-group input.disabled {
  background: #f5f5f5;
  color: #999;
  cursor: not-allowed;
}

.form-group textarea {
  resize: vertical;
  min-height: 80px;
}

.field-tip {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
  display: block;
}

.char-count {
  position: absolute;
  right: 12px;
  bottom: 8px;
  font-size: 12px;
  color: #999;
}

.btn-save {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 12px 32px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-save:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.btn-save:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 安全设置 */
.security-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
}

.security-item:last-child {
  border-bottom: none;
}

.security-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.security-label {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.security-value {
  font-size: 14px;
  color: #666;
}

.security-value.online {
  color: #52c41a;
}

.security-desc {
  font-size: 12px;
  color: #999;
}

.btn-logout {
  padding: 8px 20px;
  background: #ff4d4f;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-logout:hover {
  background: #ff7875;
}

/* 响应式适配 */
@media (max-width: 768px) {
  .settings-container {
    padding: 16px;
  }

  .settings-header h2 {
    font-size: 20px;
  }

  .settings-card {
    padding: 16px;
    border-radius: 12px;
  }

  .card-title {
    font-size: 16px;
  }

  .avatar-section {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .current-avatar {
    width: 80px;
    height: 80px;
  }
}

/* 移动端适配 */
@media (max-width: 480px) {
  .settings-container {
    padding: 12px;
    padding-bottom: 100px;
  }

  .settings-header {
    margin-bottom: 16px;
  }

  .settings-header h2 {
    font-size: 18px;
  }

  .settings-content {
    gap: 16px;
  }

  .settings-card {
    padding: 14px;
    border-radius: 10px;
  }

  .card-title {
    font-size: 15px;
    margin-bottom: 16px;
    padding-bottom: 10px;
    gap: 8px;
  }

  .card-title .icon {
    font-size: 18px;
  }

  /* 头像区域 */
  .avatar-section {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .current-avatar {
    width: 90px;
    height: 90px;
  }

  .avatar-actions {
    align-items: center;
  }

  .btn-upload {
    padding: 10px 24px;
    font-size: 14px;
  }

  /* 表单 */
  .form-group {
    margin-bottom: 16px;
  }

  .form-group label {
    font-size: 13px;
    margin-bottom: 6px;
  }

  .form-group input,
  .form-group textarea {
    padding: 10px 12px;
    font-size: 16px; /* 防止iOS自动缩放 */
    border-radius: 8px;
  }

  .form-group textarea {
    min-height: 70px;
  }

  .btn-save {
    width: 100%;
    padding: 12px;
    font-size: 15px;
  }

  /* 安全设置 */
  .security-item {
    padding: 12px 0;
    flex-wrap: wrap;
    gap: 10px;
  }

  .security-item.danger {
    flex-direction: column;
    align-items: flex-start;
  }

  .security-item.danger .btn-logout {
    width: 100%;
    margin-top: 8px;
  }

  .security-label {
    font-size: 13px;
  }

  .security-value {
    font-size: 13px;
  }

  .btn-logout {
    padding: 10px 16px;
  }
}
</style>
