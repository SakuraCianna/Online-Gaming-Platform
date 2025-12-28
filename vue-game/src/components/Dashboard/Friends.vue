<template>
  <div class="friends-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>我的好友</h2>
      <!-- 搜索我的好友 -->
      <div class="friend-search-box">
        <input v-model="friendSearchKeyword" type="text" placeholder="搜索我的好友" class="friend-search-input"
          @input="searchMyFriends" />
        <button class="friend-search-btn" @click="searchMyFriends">
          <Icon icon="lucide:search" width="18" />
        </button>
      </div>
      <!-- 添加好友按钮 -->
      <button class="add-friend-btn" @click="showAddFriendModal = true">
        <Icon icon="lucide:user-plus" width="18" />
        添加好友
      </button>
    </div>

    <!-- 好友列表 -->
    <div class="friends-content">
      <!-- 空状态 -->
      <div v-if="friends.length === 0" class="empty-state">
        <!-- 空状态图标 -->
        <div class="empty-icon">
          <Icon icon="lucide:users" width="64" color="#ddd" />
        </div>
        <h3>当前还没有好友</h3>
        <p>快去添加新好友吧</p>
        <button class="primary-btn" @click="showAddFriendModal = true">
          添加好友
        </button>
      </div>

      <!-- 好友列表 -->
      <div v-else class="friends-list">
        <div v-for="friend in paginatedFriends" :key="friend.id" class="friend-item">
          <!-- 好友信息 -->
          <div class="friend-info">
            <div class="avatar-container">
              <img :src="friend.avatar || '/image/default-avatar.jpg'" :alt="friend.name" class="avatar" />
              <div class="online-status" :class="{ 'online': friend.online }"></div>
            </div>

            <div class="friend-details">
              <div class="friend-name">{{ friend.name }}</div>
              <div class="friend-level">等级: {{ friend.level }}</div>
              <div class="friend-status">
                <span v-if="friend.status === 0" class="status-text pending">
                  <Icon icon="lucide:clock" width="10" />
                  待处理
                </span>
                <span v-else>
                  <span v-if="friend.online" class="status-text online">
                    <Icon icon="lucide:circle" width="10" />
                    {{ friend.currentGame ? `正在玩 ${friend.currentGame}` : '在线' }}
                  </span>
                  <span v-else class="status-text offline">
                    <Icon icon="lucide:circle" width="10" />
                    离线
                  </span>
                </span>
              </div>
              <div v-if="friend.status === 0" class="friend-message">
                请求信息：{{ friend.message }}
              </div>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="friend-actions">
            <template v-if="friend.status === 0">
              <!-- 通过 -->
              <button class="action-btn accept-btn" @click="handleFriendRequest(friend.id, 1)" title="通过">
                <Icon icon="lucide:check-circle" width="18" />
                <span class="btn-label">通过</span>
              </button>
              <!-- 拒绝 -->
              <button class="action-btn reject-btn" @click="handleFriendRequest(friend.id, 2)" title="拒绝">
                <Icon icon="lucide:x-circle" width="18" />
                <span class="btn-label">拒绝</span>
              </button>
            </template>
            <template v-else>
              <!-- 只有已通过的好友才显示聊天和删除 -->
              <button class="action-btn chat-btn" @click="onChatClick(friend)" title="聊天">
                <Icon icon="lucide:message-circle" width="18" />
                <span class="btn-label">聊天</span>
                <span v-if="unreadMap[String(friend.id)] > 0" class="unread-dot">
                  有新消息
                </span>
              </button>
              <button class="action-btn delete-btn" @click="confirmDelete(friend)" title="删除好友">
                <Icon icon="lucide:trash-2" width="18" />
                <span class="btn-label">删除</span>
              </button>
            </template>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="friends.length > 0" class="pagination">
        <button class="page-btn" :disabled="currentPage === 1" @click="currentPage--">
          <Icon icon="lucide:chevron-left" width="18" />
        </button>

        <span class="page-info">
          {{ currentPage }} / {{ totalPages }}
        </span>

        <button class="page-btn" :disabled="currentPage === totalPages" @click="currentPage++">
          <Icon icon="lucide:chevron-right" width="18" />
        </button>
      </div>
    </div>

    <!-- 添加好友模态框 -->
    <div v-if="showAddFriendModal" class="modal-overlay" @click="showAddFriendModal = false">
      <div class="modal-content" @click.stop style="max-width: 600px; width: 98%;">
        <div class="modal-header">
          <h3>添加好友</h3>
          <!-- 关闭按钮 -->
          <button class="close-btn" @click="showAddFriendModal = false">
            <Icon icon="lucide:x" width="18" />
          </button>
        </div>

        <div class="modal-body">
          <div class="search-section">
            <input v-model="searchKeyword" type="text" placeholder="输入用户名搜索" class="search-input"
              @input="searchUsers" />
            <!-- 搜索 -->
            <button class="search-btn" @click="searchUsers">
              <Icon icon="lucide:search" width="18" />
            </button>
          </div>

          <!-- 搜索结果展示部分 -->
          <div class="search-results">
            <div v-if="searchResult">
              <div class="search-result-item">
                <!-- 用户信息展示 -->
                <div class="user-info" style="display: flex; align-items: center;">
                  <img :src="searchResult.avatar || '/image/default-avatar.jpg'" :alt="searchResult.username"
                    class="user-avatar" style="width: 64px; height: 64px; border-radius: 50%; margin-right: 16px;" />
                  <div class="user-details">
                    <div class="user-name">用户名：{{ searchResult.username }}</div>
                    <div class="user-level">等级：{{ searchResult.current_level }}</div>
                    <div class="user-status">
                      状态：
                      <span v-if="searchResult.status === 1" style="color:green;">正常</span>
                      <span v-else style="color:red;">禁用</span>
                    </div>
                  </div>
                </div>
                <!-- 添加/确认添加逻辑 -->
                <div style="margin-top: 10px;">
                  <template v-if="!isFriend">
                    <template v-if="!showMessageInput">
                      <button class="add-btn" @click="showMessageInput = true" style="margin-top: 10px;">
                        添加
                      </button>
                    </template>
                    <template v-else>
                      <input v-model="addFriendMessage" type="text" class="add-friend-message-input"
                        placeholder="请输入添加好友的请求消息"
                        style="width: 100%; padding: 8px; border-radius: 6px; border: 1px solid #ddd; margin-bottom: 10px;" />
                      <div v-if="addFriendError" style="color: red; margin-bottom: 8px;">
                        {{ addFriendError }}
                      </div>
                      <button class="add-btn" @click="addFriend(searchResult)" style="margin-top: 0;">
                        确认添加
                      </button>
                    </template>
                  </template>
                  <template v-else>
                    <button class="add-btn" disabled style="margin-top: 10px;">已是好友</button>
                  </template>
                </div>
              </div>
            </div>
            <div v-else-if="searchKeyword">
              <div class="no-result">{{ errorMsg }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 删除确认模态框 -->
    <div v-if="showDeleteModal" class="modal-overlay" @click="showDeleteModal = false">
      <div class="modal-content delete-modal" @click.stop>
        <div class="modal-header">
          <h3>确认删除</h3>
          <button class="close-btn" @click="showDeleteModal = false">
            <Icon icon="lucide:x" width="18" />
          </button>
        </div>

        <div class="modal-body">
          <!-- 删除确认模态框的警告图标 -->
          <div class="delete-warning">
            <Icon icon="lucide:alert-triangle" width="48" color="#dc3545" />
            <p>确定要删除好友 <strong>{{ friendToDelete?.name }}</strong> 吗？</p>
            <p class="warning-text">删除后将无法恢复，且需要重新添加才能成为好友。</p>
          </div>
        </div>

        <div class="modal-footer">
          <button class="cancel-btn" @click="showDeleteModal = false">
            取消
          </button>
          <button class="delete-confirm-btn" @click="deleteFriend">
            确认删除
          </button>
        </div>
      </div>
    </div>

    <!-- 聊天模态框 -->
    <div v-if="showChatModal" class="modal-overlay" @click="showChatModal = false">
      <div class="modal-content chat-modal" @click.stop>
        <div class="modal-header">
          <div>
            <span class="chat-title-name">{{ currentChatFriend?.name }}</span>
            <span class="chat-title-status" :class="{
              online: currentChatFriend?.online,
              offline: !currentChatFriend?.online
            }">
              {{ chatFriendStatus }}
            </span>
          </div>
          <button class="close-btn" @click="showChatModal = false">
            <Icon icon="lucide:x" width="18" />
          </button>
        </div>
        <div class="modal-body chat-body">
          <div class="chat-messages">
            <div v-for="(msg, idx) in chatMessages" :key="idx" :class="['chat-message-wrapper', msg.self ? 'own' : '']">
              <div class="chat-message">
                <div class="message-content">{{ msg.content }}</div>
              </div>
              <div class="message-timestamp">
                {{ formatTime(msg.createdAt || msg.time) }}
              </div>
            </div>
          </div>
        </div>
        <div class="modal-footer chat-footer">
          <input v-model="newMessage" class="message-input" placeholder="输入消息..." @keyup.enter="sendMessage" />
          <button class="send-btn" @click="sendMessage">发送</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick, inject, onBeforeUnmount } from 'vue'
import request from '../../config/api'
import { Icon } from '@iconify/vue'
import { storeToRefs } from 'pinia'
import { useUserStore } from '../../config/user';
import { ElMessage } from 'element-plus'

// 定义组件名称，支持 keep-alive 缓存
defineOptions({
  name: 'Friends'
})

const userStore = useUserStore()
const { user, friends, friendsTotal } = storeToRefs(userStore)

const wsService = inject('wsService')

// 响应式数据
const searchKeyword = ref('')
const searchResult = ref(null)
const errorMsg = ref('')
const currentPage = ref(1)
const pageSize = ref(6)
const friendSearchKeyword = ref('')
const isFriend = ref(false)
const addFriendMessage = ref('你好，我想和你成为朋友')
const showMessageInput = ref(false)
const addFriendError = ref('')
const unreadMap = ref({}) // 记录每个好友的未读消息数

// 模态框状态
const showAddFriendModal = ref(false)
const showDeleteModal = ref(false)
const friendToDelete = ref(null)

// 聊天相关
const showChatModal = ref(false)
const currentChatFriend = ref(null)
const chatMessages = ref([]) // 当前聊天消息
const newMessage = ref('')   // 输入框内容

// 计算属性
const paginatedFriends = computed(() => {
  // 排序：在线好友优先
  return [...friends.value].sort((a, b) => {
    // 先按状态排序（待处理 > 已通过）
    if (a.status !== b.status) {
      return a.status - b.status
    }

    // 已通过的好友按在线状态排序
    if (a.status === 1 && b.status === 1) {
      // 在线的排在前面
      if (a.online !== b.online) {
        return b.online ? 1 : -1
      }
    }

    // 其他情况保持原顺序
    return 0
  })
})

const totalPages = computed(() => {
  return Math.ceil(friendsTotal.value / pageSize.value)
})

const chatFriendStatus = computed(() => {
  if (!currentChatFriend.value) return ''
  if (currentChatFriend.value.online) {
    return currentChatFriend.value.currentGame
      ? `在线(正在玩 ${currentChatFriend.value.currentGame})`
      : '在线'
  } else {
    return '离线'
  }
})

// 滚动到聊天底部
const scrollToBottom = () => {
  nextTick(() => {
    const chatBody = document.querySelector('.chat-body')
    if (chatBody) {
      chatBody.scrollTop = chatBody.scrollHeight
    }
  })
}

watch(chatMessages, () => {
  scrollToBottom()
}, { deep: true })

// 监听页码变化，重新请求
watch(currentPage, () => {
  loadFriends()
})

// 1. 加载好友列表(支持后端分页和用户ID)
const loadFriends = async () => {
  if (!user.value?.id) return
  try {
    const response = await request.get('/friend/getAllFriend', {
      params: {
        id: user.value.id,
        page: currentPage.value,
        pageSize: pageSize.value
      }
    });

    // 使用 userStore 设置好友列表
    userStore.setFriends(response.data.data || [], response.data.total || 0);
  } catch (error) {
    console.error('加载好友列表失败:', error);
    userStore.setFriends([], 0);
  }
};

// 2. 搜索陌生好友
const searchUsers = async () => {
  const username = searchKeyword.value.trim()
  if (!username) {
    searchResult.value = null
    errorMsg.value = ''
    return
  }
  try {
    const res = await request.get('/user/search', {
      params: {
        username,
        id: user.value.id
      }
    })
    if (res.data.code === 200 && res.data.data) {
      searchResult.value = res.data.data.user
      isFriend.value = res.data.data.isFriend
      errorMsg.value = ''
      addFriendMessage.value = '你好，我想和你成为朋友'
      showMessageInput.value = false
    } else {
      searchResult.value = null
      isFriend.value = false
      errorMsg.value = res.data.msg || '未找到该用户'
    }
  } catch (error) {
    searchResult.value = null
    errorMsg.value = '搜索用户失败，请稍后重试'
    console.error('搜索用户失败:', error)
  }
}

// 3. 发送好友请求
const addFriend = async (friend) => {
  addFriendError.value = ''
  try {
    const res = await request.post('/friend/add', {
      userId: user.value.id,
      friendId: friend.id,
      message: addFriendMessage.value
    })
    if (res.data.success) {
      // 成功逻辑
      showMessageInput.value = false
      addFriendMessage.value = '你好，我想和你成为朋友'
      showAddFriendModal.value = false
      ElMessage.success('好友请求已发送')
    } else {
      // 失败时显示错误
      addFriendError.value = res.data.message || '添加好友失败'
    }
  } catch (error) {
    addFriendError.value = '网络或服务器错误'
    console.error(error)
  }
}

const confirmDelete = (friend) => {
  friendToDelete.value = friend
  showDeleteModal.value = true
}

// 4. 删除好友
const deleteFriend = async () => {
  try {
    await request.delete('/friend/delete', {
      params: { userId: user.value.id, friendId: friendToDelete.value.id }
    })
    await loadFriends()
    showDeleteModal.value = false
    friendToDelete.value = null
    ElMessage.success('好友已删除')
  } catch (error) {
    ElMessage.error('删除好友失败')
    console.error('删除好友失败:', error)
  }
}

const handleFriendRequest = async (friendId, action) => {
  try {
    const res = await request.post('/friend/confirm', {
      userId: user.value.id,
      friendId,
      action // 1=通过, 2=拒绝
    });
    if (res.data.success) {
      await loadFriends();
      ElMessage.success(action === 1 ? '已通过好友请求' : '已拒绝好友请求');
    } else {
      ElMessage.error(res.data.message || '操作失败');
    }
  } catch (e) {
    ElMessage.error('网络错误，操作失败');
    console.error(e);
  }
};

const searchMyFriends = async () => {
  const keyword = friendSearchKeyword.value.trim();
  if (!keyword) {
    // 关键字为空时，重新加载全部好友
    await loadFriends();
    return;
  }
  try {
    const res = await request.get('/friend/search', {
      params: {
        id: user.value.id,
        keyword,
        page: currentPage.value,
        pageSize: pageSize.value
      }
    });
    if (res.data.code === 200 && Array.isArray(res.data.data.list)) {
      userStore.setFriends(res.data.data.list, res.data.data.total);
    } else {
      userStore.setFriends([], 0);
      ElMessage.warning('未找到相关好友')
    }
  } catch (e) {
    userStore.setFriends([], 0);
    ElMessage.error('搜索好友失败，请稍后重试');
    console.error('搜索好友失败:', e);
  }
};

const onChatClick = async (friend) => {
  currentChatFriend.value = friend
  showChatModal.value = true
  // 通知后端将该好友消息标记为已读
  await request.post('/chat/markFriendChatRead', {
    userId: user.value.id,
    friendId: friend.id
  })
  unreadMap.value[String(friend.id)] = 0 // 前端同步清零
  await loadChatHistory(friend.id)
  scrollToBottom()
}

const loadChatHistory = async (friendId) => {
  try {
    const res = await request.get('/chat/getFriendChatHistory', {
      params: {
        userId: user.value.id,      // 自己的id
        friendId: friendId          // 好友的id
      }
    })

    chatMessages.value = (res.data || []).map(msg => ({
      ...msg,
      self: msg.senderId === user.value.id
    }))
  } catch (e) {
    chatMessages.value = []
    console.error('加载聊天记录失败:', e)
  }
}

const sendMessage = async () => {
  if (!newMessage.value.trim()) return
  const msg = {
    userId: user.value.id,
    friendId: currentChatFriend.value.id,
    message: newMessage.value
  }
  await request.post('/chat/sendFriendMessage', msg)
  newMessage.value = ''
}

// 获取未读消息数（页面加载时）
const loadUnreadCount = async () => {
  try {
    const res = await request.get('/chat/unReadFriendChat', {
      params: { userId: user.value.id }
    })
    const map = {}
    if (Array.isArray(res.data)) {
      for (const item of res.data) {
        map[String(item.friendId)] = item.unread
      }
    }
    unreadMap.value = map
  } catch (e) {
    unreadMap.value = {}
    ElMessage.error('获取未读消息失败')
    console.error('获取未读消息失败:', e)
  }
}

// 生命周期
onMounted(() => {
  loadFriends()
  loadUnreadCount()

  // WebSocket 订阅
  if (wsService && user.value?.id) {
    // 好友请求推送
    wsService.subscribe(`/topic/friendRequest/${user.value.id}`, (msg) => {
      setTimeout(() => {
        loadFriends()
      }, 1000)
    })

    // 被删除好友推送
    wsService.subscribe(`/topic/friendDelete/${user.value.id}`, (msg) => {
      setTimeout(() => {
        loadFriends()
      }, 1000)
    })

    // 聊天推送
    wsService.subscribe(`/topic/chat/${user.value.id}`, (msg) => {
      const message = JSON.parse(msg.body)
      // 判断是否是当前聊天对象的消息（无论是自己发的还是对方发的）
      const isCurrentChat =
        currentChatFriend.value && showChatModal.value &&
        (
          // 自己发的消息
          (message.senderId === user.value.id && message.receiverId === currentChatFriend.value.id)
          // 对方发的消息
          || (message.senderId === currentChatFriend.value.id && message.receiverId === user.value.id)
        )

      if (isCurrentChat) {
        chatMessages.value.push({
          ...message,
          self: message.senderId === user.value.id
        })
      } else {
        // 聊天框没打开，增加未读数
        // 判断未读数应该加到哪个好友
        const fid = message.senderId === user.value.id ? message.receiverId : message.senderId
        unreadMap.value[fid] = (unreadMap.value[fid] || 0) + 1
      }
    })
  }
})

// 组件卸载时取消订阅
onBeforeUnmount(() => {
  if (wsService && user.value?.id) {
    wsService.unsubscribe(`/topic/friendRequest/${user.value.id}`)
    wsService.unsubscribe(`/topic/friendDelete/${user.value.id}`)
    wsService.unsubscribe(`/topic/chat/${user.value.id}`)
  }
})

function formatTime(time) {
  if (!time) return ''
  const date = typeof time === 'string' ? new Date(time) : time
  const now = new Date()
  const diff = now - date
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`

  // 超过7天显示完整日期
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hour = date.getHours().toString().padStart(2, '0')
  const minute = date.getMinutes().toString().padStart(2, '0')
  return `${month}月${day}日 ${hour}:${minute}`
}
</script>

<style scoped>
.friends-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 20px;
  overflow: hidden;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #e0e0e0;
}

.page-header h2 {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.add-friend-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.add-friend-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.friends-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: #666;
}

.empty-icon {
  font-size: 64px;
  color: #ddd;
  margin-bottom: 20px;
}

.empty-state h3 {
  font-size: 20px;
  margin-bottom: 10px;
  color: #333;
}

.empty-state p {
  font-size: 14px;
  margin-bottom: 30px;
  color: #666;
}

.primary-btn {
  padding: 12px 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.primary-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.friends-list {
  flex: 1;
  overflow-y: auto;
  padding-right: 10px;
}

.friend-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px;
  margin-bottom: 10px;
  background: #f8f9fa;
  border-radius: 12px;
  border: 1px solid #e9ecef;
  transition: all 0.3s ease;
}

.friend-item:hover {
  background: #f1f3f4;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.friend-info {
  display: flex;
  align-items: center;
  gap: 15px;
  flex: 1;
}

.avatar-container {
  position: relative;
}

.avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.online-status {
  position: absolute;
  bottom: 2px;
  right: 2px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #ccc;
  border: 2px solid #fff;
}

.online-status.online {
  background: #28a745;
}

/* 好友信息一行展示 */
.friend-details {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
}

.friend-name {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin-bottom: 0;
}

.friend-level {
  font-size: 14px;
  color: #666;
  margin-bottom: 0;
}

.friend-status {
  display: flex;
  align-items: center;
  gap: 4px;
}

.status-text {
  font-size: 14px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.status-text.online {
  color: #28a745;
}

.status-text.offline {
  color: #666;
}

.friend-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 14px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  background: #f8f9fa;
  color: #333;
  transition: background 0.2s, color 0.2s;
  margin-right: 8px;
}

/* 绿色通过按钮 */
.accept-btn {
  background: #4caf50;
  color: #fff;
}

.accept-btn:hover {
  background: #388e3c;
}

/* 橙色拒绝按钮 */
.reject-btn {
  background: #ff9800;
  color: #fff;
}

.reject-btn:hover {
  background: #f57c00;
}

/* 删除按钮（红色） */
.delete-btn {
  background: #dc3545;
  color: #fff;
}

.delete-btn:hover {
  background: #c82333;
}

.btn-label {
  margin-left: 2px;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 15px;
  margin-top: 20px;
  padding-top: 15px;
  border-top: 1px solid #e0e0e0;
}

.page-btn {
  width: 44px;
  height: 44px;
  border: 1px solid #ddd;
  background: white;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
  font-size: 16px;
}

.page-btn:hover:not(:disabled) {
  background: #f8f9fa;
  border-color: #667eea;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 14px;
  color: #666;
  min-width: 60px;
  text-align: center;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  z-index: 1000;
  -webkit-backdrop-filter: blur(4px);
  backdrop-filter: blur(4px);
  padding-top: 150px;
}

.modal-content {
  background: white;
  border-radius: 12px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.2);
  max-width: 800px;
  width: 100%;
  max-height: 100vh;
  overflow: hidden;
  animation: modalSlideIn 0.3s ease;
}

@keyframes modalSlideIn {
  from {
    opacity: 0;
    transform: scale(0.9) translateY(-20px);
  }

  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px;
  border-bottom: 1px solid #e0e0e0;
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.close-btn {
  width: 40px;
  height: 40px;
  border: none;
  background: #f8f9fa;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
  font-size: 16px;
}

.close-btn:hover {
  background: #e9ecef;
}

.modal-body {
  padding: 20px;
}

.search-section {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.search-input {
  flex: 1;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 14px;
}

.search-input:focus {
  border-color: #667eea;
  outline: none;
}

.search-btn {
  padding: 12px 16px;
  background: #667eea;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}

.search-results {
  max-height: 300px;
  overflow-y: auto;
}

.search-result-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  margin-bottom: 8px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
}

.user-name {
  font-weight: 600;
  color: #333;
}

.user-level {
  font-size: 12px;
  color: #666;
}

.add-btn {
  padding: 8px 16px;
  background: #28a745;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
}

.add-btn:disabled {
  background: #6c757d;
  cursor: not-allowed;
}

/* 删除确认模态框 */
.delete-modal {
  max-width: 400px;
}

.delete-warning {
  text-align: center;
  color: #666;
}

.delete-warning i {
  font-size: 48px;
  color: #dc3545;
  margin-bottom: 15px;
}

.warning-text {
  font-size: 12px;
  color: #999;
  margin-top: 10px;
}

.modal-footer {
  display: flex;
  gap: 10px;
  padding: 20px;
  border-top: 1px solid #e0e0e0;
}

.cancel-btn {
  flex: 1;
  padding: 12px;
  background: #f8f9fa;
  border: 1px solid #ddd;
  border-radius: 8px;
  cursor: pointer;
}

.delete-confirm-btn {
  flex: 1;
  padding: 12px;
  background: #dc3545;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}

/* 可选微调 */
.status-text,
.add-friend-btn {
  align-items: center;
  gap: 6px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .friends-container {
    padding: 15px;
  }

  .page-header {
    flex-direction: column;
    gap: 15px;
    align-items: stretch;
  }

  .friend-item {
    flex-direction: column;
    gap: 15px;
    align-items: stretch;
  }

  .friend-actions {
    justify-content: center;
  }

  .chat-modal {
    width: 95%;
    min-width: 320px;
    height: 90vh;
    max-height: 90vh;
  }

  .chat-message-wrapper {
    max-width: 85%;
  }

  .chat-body {
    padding: 12px;
  }

  .chat-footer {
    padding: 12px;
  }

  .send-btn {
    padding: 10px 16px;
    font-size: 13px;
  }
}

/* 移动端小屏幕适配 */
@media (max-width: 480px) {
  .friends-container {
    padding: 12px;
    padding-bottom: 80px;
  }

  .page-header {
    gap: 12px;
  }

  .page-header h2 {
    font-size: 20px;
    text-align: center;
  }

  .friend-search-box {
    margin: 0;
    width: 100%;
  }

  .friend-search-input {
    min-width: auto;
    width: 100%;
  }

  .add-friend-btn {
    width: 100%;
    justify-content: center;
    padding: 12px;
  }

  .friend-item {
    padding: 12px;
    margin-bottom: 8px;
  }

  .friend-info {
    gap: 12px;
  }

  .avatar {
    width: 50px;
    height: 50px;
  }

  .friend-details {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .friend-name {
    font-size: 16px;
  }

  .friend-level,
  .status-text {
    font-size: 12px;
  }

  .friend-actions {
    flex-wrap: wrap;
    gap: 8px;
  }

  .action-btn {
    flex: 1;
    min-width: 80px;
    padding: 8px 10px;
    font-size: 12px;
    margin-right: 0;
  }

  .btn-label {
    display: none;
  }

  .pagination {
    margin-top: 16px;
    padding-top: 12px;
  }

  .page-btn {
    width: 36px;
    height: 36px;
  }

  /* 模态框适配 */
  .modal-overlay {
    padding-top: 60px;
  }

  .modal-content {
    margin: 0 12px;
    max-height: 85vh;
  }

  .modal-header {
    padding: 16px;
  }

  .modal-header h3 {
    font-size: 16px;
  }

  .modal-body {
    padding: 16px;
  }

  .search-section {
    flex-direction: column;
  }

  .search-input {
    width: 100%;
  }

  .search-btn {
    width: 100%;
    justify-content: center;
    padding: 12px;
  }

  /* 聊天模态框适配 */
  .chat-modal {
    width: 100%;
    height: 100vh;
    max-height: 100vh;
    border-radius: 0;
    margin: 0;
  }

  .chat-title-name {
    font-size: 16px;
  }

  .chat-title-status {
    font-size: 12px;
  }

  .chat-message-wrapper {
    max-width: 90%;
  }

  .chat-message {
    padding: 10px 14px;
    font-size: 14px;
  }

  .chat-footer {
    padding: 10px;
    gap: 8px;
  }

  .message-input {
    padding: 10px 14px;
    font-size: 14px;
  }

  .send-btn {
    padding: 10px 14px;
    font-size: 14px;
  }

  .empty-state h3 {
    font-size: 18px;
  }

  .empty-state p {
    font-size: 13px;
  }

  .primary-btn {
    padding: 10px 20px;
    font-size: 13px;
  }
}

.friend-search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  justify-content: center;
  margin: 0 20px;
}

.friend-search-input {
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 14px;
  width: 100%;
  min-width: 350px;
  max-width: 600px;
}

.friend-search-btn {
  padding: 8px 12px;
  background: #667eea;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
}

.friend-message {
  color: #ff9800;
  font-size: 16px;
  font-weight: bold;
  margin-top: 4px;
}

/* 聊天按钮（蓝色） */
.chat-btn {
  background: #007bff;
  color: #fff;
}

.chat-btn:hover {
  background: #0056b3;
}

.chat-modal {
  max-width: 800px;
  min-width: 400px;
  width: 98%;
  height: min(700px, 80vh);
  max-height: 80vh;
  display: flex;
  flex-direction: column;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: linear-gradient(to bottom, #f7f7f7 0%, #fafafa 100%);
}

/* 美化滚动条 */
.chat-body::-webkit-scrollbar {
  width: 6px;
}

.chat-body::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 10px;
}

.chat-body::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 10px;
  transition: background 0.3s;
}

.chat-body::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

.chat-messages {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-message-wrapper {
  display: flex;
  flex-direction: column;
  max-width: 70%;
  align-self: flex-start;
  animation: messageSlideIn 0.3s ease;
}

.chat-message-wrapper.own {
  align-self: flex-end;
  align-items: flex-end;
}

@keyframes messageSlideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.chat-message {
  background: #fff;
  border-radius: 18px 18px 18px 4px;
  padding: 12px 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  color: #333;
  word-wrap: break-word;
  word-break: break-word;
}

.chat-message-wrapper.own .chat-message {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border-radius: 18px 18px 4px 18px;
  box-shadow: 0 2px 12px rgba(102, 126, 234, 0.3);
}

.message-content {
  line-height: 1.5;
}

.message-timestamp {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
  padding-left: 8px;
  user-select: none;
}

.chat-message-wrapper.own .message-timestamp {
  padding-left: 0;
  padding-right: 8px;
}

.chat-footer {
  display: flex;
  gap: 12px;
  padding: 16px;
  border-top: 1px solid #eee;
  background: #fafafa;
}

.message-input {
  flex: 1;
  padding: 12px 18px;
  border: 2px solid #e0e0e0;
  border-radius: 24px;
  font-size: 14px;
  transition: all 0.3s ease;
  background: #fff;
}

.message-input:focus {
  border-color: #667eea;
  outline: none;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.send-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border: none;
  border-radius: 24px;
  padding: 12px 24px;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 6px;
}

.send-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.send-btn:active {
  transform: translateY(0);
}

.chat-title-name {
  font-weight: bold;
  font-size: 18px;
  margin-right: 12px;
}

.chat-title-status {
  font-size: 14px;
  vertical-align: middle;
  padding: 2px 8px;
  border-radius: 8px;
}

.chat-title-status.online {
  color: #28a745;
  background: #e6f9ed;
}

.chat-title-status.offline {
  color: #999;
  background: #f2f2f2;
}

.unread-dot {
  background: #dc3545;
  color: #fff;
  border-radius: 8px;
  padding: 2px 8px;
  font-size: 12px;
  margin-left: 6px;
  vertical-align: middle;
}
</style>