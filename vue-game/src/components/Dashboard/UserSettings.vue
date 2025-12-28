<template>
  <div class="user-settings">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>个人设置</h2>
    </div>

    <!-- 设置列表 - 行式布局 -->
    <div class="settings-list">
      <!-- 头像设置行 -->
      <div class="setting-row avatar-row">
        <div class="setting-icon">
          <span>👤</span>
        </div>
        <div class="setting-label">
          <span class="label">头像</span>
          <span class="desc">更换个人头像</span>
        </div>
        <div class="setting-content">
          <img :src="user?.avatar || '/image/default-avatar.jpg'" class="avatar-preview" />
        </div>
        <div class="setting-action">
          <input 
            ref="avatarInput" 
            type="file" 
            accept="image/*" 
            style="display: none" 
            @change="handleAvatarChange"
          />
          <button class="btn-action" @click="triggerAvatarUpload">📷 更换</button>
        </div>
      </div>

      <!-- 用户名设置行 -->
      <div class="setting-row">
        <div class="setting-icon">
          <span>📝</span>
        </div>
        <div class="setting-label">
          <span class="label">用户名</span>
          <span class="desc">修改显示名称</span>
        </div>
        <div class="setting-content">
          <input 
            v-model="formData.username" 
            type="text" 
            class="inline-input"
            placeholder="请输入用户名"
            maxlength="20"
          />
        </div>
        <div class="setting-action">
          <button class="btn-action primary" :disabled="saving" @click="saveBasicInfo">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>

      <!-- 邮箱显示行 -->
      <div class="setting-row disabled">
        <div class="setting-icon">
          <span>📧</span>
        </div>
        <div class="setting-label">
          <span class="label">邮箱</span>
          <span class="desc">账号绑定邮箱</span>
        </div>
        <div class="setting-content">
          <span class="value">{{ user?.email || '未设置' }}</span>
        </div>
        <div class="setting-action">
          <span class="status-tag">🔒 不可修改</span>
        </div>
      </div>

      <!-- 分隔标题 -->
      <div class="section-title">
        <span class="icon">🔒</span>
        <span>密码安全</span>
      </div>

      <!-- 当前密码行 -->
      <div class="setting-row">
        <div class="setting-icon">
          <span>🔑</span>
        </div>
        <div class="setting-label">
          <span class="label">当前密码</span>
          <span class="desc">验证身份</span>
        </div>
        <div class="setting-content">
          <input 
            v-model="passwordForm.oldPassword" 
            type="password" 
            class="inline-input"
            placeholder="请输入当前密码"
          />
        </div>
        <div class="setting-action"></div>
      </div>

      <!-- 新密码行 -->
      <div class="setting-row">
        <div class="setting-icon">
          <span>🔐</span>
        </div>
        <div class="setting-label">
          <span class="label">新密码</span>
          <span class="desc">至少6位字符</span>
        </div>
        <div class="setting-content">
          <input 
            v-model="passwordForm.newPassword" 
            type="password" 
            class="inline-input"
            placeholder="请输入新密码"
          />
        </div>
        <div class="setting-action"></div>
      </div>

      <!-- 确认密码行 -->
      <div class="setting-row">
        <div class="setting-icon">
          <span>✅</span>
        </div>
        <div class="setting-label">
          <span class="label">确认密码</span>
          <span class="desc">再次输入新密码</span>
        </div>
        <div class="setting-content">
          <input 
            v-model="passwordForm.confirmPassword" 
            type="password" 
            class="inline-input"
            placeholder="请确认新密码"
          />
        </div>
        <div class="setting-action">
          <button class="btn-action primary" :disabled="changingPassword" @click="changePassword">
            {{ changingPassword ? '修改中...' : '修改密码' }}
          </button>
        </div>
      </div>

      <!-- 分隔标题 -->
      <div class="section-title">
        <span class="icon">🛡️</span>
        <span>账号状态</span>
      </div>

      <!-- 登录状态行 -->
      <div class="setting-row">
        <div class="setting-icon">
          <span>🟢</span>
        </div>
        <div class="setting-label">
          <span class="label">登录状态</span>
          <span class="desc">当前会话状态</span>
        </div>
        <div class="setting-content">
          <span class="status-badge online">在线</span>
        </div>
        <div class="setting-action"></div>
      </div>

      <!-- 注册时间行 -->
      <div class="setting-row">
        <div class="setting-icon">
          <span>📅</span>
        </div>
        <div class="setting-label">
          <span class="label">注册时间</span>
          <span class="desc">账号创建日期</span>
        </div>
        <div class="setting-content">
          <span class="value">{{ formatDate(user?.createTime) }}</span>
        </div>
        <div class="setting-action"></div>
      </div>

      <!-- 退出登录行 -->
      <div class="setting-row danger">
        <div class="setting-icon">
          <span>🚪</span>
        </div>
        <div class="setting-label">
          <span class="label">退出登录</span>
          <span class="desc">退出当前账号</span>
        </div>
        <div class="setting-content"></div>
        <div class="setting-action">
          <button class="btn-action danger" @click="handleLogout">退出登录</button>
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
.user-settings {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.page-header {
  margin-bottom: 16px;
}

.page-header h2 {
  font-size: 20px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

/* 设置列表 */
.settings-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.settings-list::-webkit-scrollbar {
  display: none;
}

/* 分隔标题 */
.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.section-title .icon {
  font-size: 18px;
}

/* 设置行 */
.setting-row {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 18px 24px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  transition: all 0.2s;
  border-left: 4px solid #667eea;
}

.setting-row:hover {
  box-shadow: 0 4px 16px rgba(0,0,0,0.1);
}

.setting-row.disabled {
  border-left-color: #9ca3af;
  opacity: 0.8;
}

.setting-row.danger {
  border-left-color: #ef4444;
}

.setting-row.avatar-row {
  border-left-color: #10b981;
}

/* 图标 */
.setting-icon {
  font-size: 28px;
  min-width: 40px;
  text-align: center;
}

/* 标签 */
.setting-label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 120px;
}

.setting-label .label {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.setting-label .desc {
  font-size: 13px;
  color: #999;
}

/* 内容区 */
.setting-content {
  flex: 1;
  display: flex;
  align-items: center;
}

.avatar-preview {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid #e5e7eb;
}

.inline-input {
  width: 100%;
  max-width: 300px;
  padding: 10px 16px;
  border: 2px solid #e8e8e8;
  border-radius: 10px;
  font-size: 15px;
  transition: all 0.2s;
}

.inline-input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.value {
  font-size: 15px;
  color: #666;
}

.status-badge {
  padding: 6px 14px;
  border-radius: 16px;
  font-size: 14px;
  font-weight: 600;
}

.status-badge.online {
  background: #d1fae5;
  color: #059669;
}

.status-tag {
  font-size: 13px;
  color: #9ca3af;
}

/* 操作按钮 */
.setting-action {
  min-width: 100px;
  display: flex;
  justify-content: flex-end;
}

.btn-action {
  padding: 10px 20px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  background: #f5f5f5;
  color: #666;
  white-space: nowrap;
}

.btn-action:hover:not(:disabled) {
  background: #e8e8e8;
}

.btn-action.primary {
  background: #667eea;
  color: #fff;
}

.btn-action.primary:hover:not(:disabled) {
  background: #5a6fd6;
}

.btn-action.danger {
  background: #ef4444;
  color: #fff;
}

.btn-action.danger:hover {
  background: #dc2626;
}

.btn-action:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 响应式 */
@media (max-width: 768px) {
  .user-settings {
    padding: 16px;
  }

  .setting-row {
    flex-wrap: wrap;
    gap: 12px;
    padding: 16px;
  }

  .setting-icon {
    font-size: 24px;
    min-width: 32px;
  }

  .setting-label {
    min-width: 100px;
  }

  .setting-content {
    flex: 1 1 100%;
    order: 3;
  }

  .inline-input {
    max-width: none;
  }

  .setting-action {
    min-width: auto;
  }
}

@media (max-width: 480px) {
  .user-settings {
    padding: 12px;
    padding-bottom: 80px;
  }

  .page-header h2 {
    font-size: 18px;
  }

  .setting-row {
    padding: 12px;
    gap: 10px;
  }

  .setting-icon {
    font-size: 20px;
    min-width: 28px;
  }

  .setting-label .label {
    font-size: 14px;
  }

  .setting-label .desc {
    font-size: 12px;
  }

  .inline-input {
    padding: 10px 12px;
    font-size: 16px;
  }

  .avatar-preview {
    width: 40px;
    height: 40px;
  }

  .btn-action {
    padding: 10px 16px;
    font-size: 13px;
  }

  .section-title {
    font-size: 14px;
    padding: 12px 0 6px;
  }
}
</style>
