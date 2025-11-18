---
trigger: model_decision
description: 当我询问的内容与我的项目有关时，就引用我的规则
---

# Vue游戏平台开发规范 - Cursor Rules
**额外提示**：用AI的的时候不需要创建md格式的任何文档，不需要写任何说明，回答问题时要时刻保持中立，这个方法好就是好，不好就是不好，要说明理由让我信服，要给我原因让我知道前因后果，还有就是能简洁回答的问题优先简洁回答，前后端都要遵循开闭原则，时刻保持最新的代码编写规范，保证最佳代码实践
## 项目技术栈
- 前端: Vue 3.5 (Composition API) + Vite 7 + Element Plus + Pinia + Phaser 3
- 后端: Spring Boot 3.5 + Java 21 + MyBatis-Plus + Redis + MySQL 8
- 安全: JWT + Spring Security + hCaptcha
- 实时通信: WebSocket (STOMP + SockJS)
- 操作系统为Windows 11版本 64 位操作系统, 基于 x64 的AMD处理器

## 后端开发规范（Java/Spring Boot）

### 1. 分层架构职责
- **Controller层**: 只负责接收请求、调用Service、返回响应，不写业务逻辑
- **Service层**: 业务逻辑处理、参数校验、事务管理，必须添加 `@Transactional` 注解
- **Mapper层**: 数据库CRUD操作，使用MyBatis-Plus

### 2. 依赖注入（必须遵守）
```java
// ✅ 使用构造器注入
@RestController
public class UserController {
    private final UserService userService;
    
    public UserController(UserService userService) {
        this.userService = userService;
    }
}

// ❌ 避免字段注入
@Autowired
private UserService userService;  // 不要这样写
```

### 3. 异常处理
```java
// Service层抛出BusinessException
if (email == null || email.trim().isEmpty()) {
    throw new BusinessException(400, "邮箱不能为空");
}
if (user.getStatus().equals(0)) {
    throw new BusinessException(403, "账号已被封禁");
}
```

### 4. 响应格式
```java
// Controller层统一返回 ResponseEntity<Map<String, Object>>
@PostMapping("/login")
public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> request) {
    Map<String, Object> result = userService.LoginService(request);
    result.put("code", 200);
    return ResponseEntity.ok(result);
}

// 响应格式: { code: 200, success: true, message: "成功", data: {}, token: "xxx" }
```

### 5. 敏感信息处理（关键）
```java
// 返回前端前必须清理敏感字段
user.setPassword(null);
user.setEmailVerified(null);
user.setVerificationCode(null);
user.setVerificationExpireTime(null);
```

### 6. 参数校验位置
- Service层做详细的参数校验（非空、格式、逻辑、业务校验）
- Controller层只做简单调度，不写复杂校验逻辑

### 7. 状态码规范
- 200: 成功
- 400: 参数错误、业务校验失败
- 401: 未认证、token失效
- 403: 无权限、账号被封禁
- 404: 资源不存在
- 500: 服务器错误

## 前端开发规范（Vue 3）

### 1. 组件规范
- 组件命名: PascalCase，如 `GameCenter.vue`, `UserMain.vue`
- 使用 Composition API 的 `<script setup>` 语法
- 变量命名: camelCase，常量: UPPER_SNAKE_CASE

### 2. API调用规范
```javascript
// 使用统一的request实例（自动携带token）
import request from '@/config/api'

const login = async (email, password) => {
  try {
    const response = await request.post('/user/login', { email, password })
    
    // 必须检查success字段
    if (response.data.success) {
      localStorage.setItem('token', response.data.token)
      userStore.setUserInfo(response.data.user)
      ElMessage.success(response.data.message)
    } else {
      ElMessage.error(response.data.message)
    }
  } catch (error) {
    ElMessage.error('操作失败，请稍后重试')
  }
}
```

### 3. 状态管理
```javascript
// 使用Pinia + persist插件
export const useUserStore = defineStore('user', {
  state: () => ({ userInfo: null, token: null }),
  actions: { setUserInfo(info) { this.userInfo = info } },
  persist: true  // 启用持久化
})
```

### 4. 响应数据处理
```javascript
// 注意区分不同的响应结构
// 方式1: { success: true, message: "...", user: {}, token: "..." }
// 方式2: { code: 200, msg: "...", data: {} }

// 必须先检查状态再使用数据
if (response.data.success && response.data.code === 200) {
  // 处理数据
}
```

### 5. WebSocket通信
```javascript
// 使用STOMP over SockJS
const client = new Client({
  webSocketFactory: () => new SockJS('/ws'),
  connectHeaders: { Authorization: `Bearer ${token}` }
})
```

## 代码风格规范

### 后端（Java）
- 包命名: 全小写 `com.game.controller`
- 类命名: PascalCase `UserController`
- 方法命名: camelCase，Service层方法建议 `动词+Service`，如 `LoginService()`
- 注释: 优先使用中文注释

### 前端（JavaScript/Vue）
- 使用2空格缩进
- 优先使用中文注释
- API路径: RESTful风格，如 `/api/user/login`

## 安全规范

### JWT Token
- 存储在 localStorage
- 由axios拦截器自动添加到 `Authorization: Bearer {token}` header
- 登录成功后生成: `JwtUtils.generateToken(userId)`

### 密码安全
- 使用 `PasswordUtils.encrypt()` 加密
- 使用 `PasswordUtils.matches()` 验证
- 不在日志中输出密码

### 人机验证
- 注册时必须验证 hCaptcha token
```java
boolean isHumanVerified = hCaptchaUtil.verify(hcaptchaToken);
if (!isHumanVerified) {
    throw new BusinessException(400, "人机验证失败");
}
```

## 常见错误提醒

### 后端
❌ Controller层写业务逻辑 → ✅ 业务逻辑放Service层
❌ 使用@Autowired字段注入 → ✅ 使用构造器注入
❌ 忘记清理敏感信息 → ✅ 返回前必须清理password等字段
❌ Service层没有@Transactional → ✅ 必须添加事务注解
❌ 直接返回Entity → ✅ 清理敏感字段或使用VO

### 前端
❌ 不检查success直接用data → ✅ 先检查success和code
❌ Token丢失 → ✅ 检查localStorage和拦截器配置
❌ 跨域问题 → ✅ 开发环境用Vite代理（vite.config.js）

## API代理配置（重要）
```javascript
// vite.config.js - 开发环境代理
server: {
  proxy: {
    '/api': { target: 'http://localhost:8080', changeOrigin: true },
    '/ws': { target: 'http://localhost:8080', ws: true }
  }
}
```

## 数据库规范
- 表名: 小写下划线 `user_info`, `game_record`
- 主键: 统一命名 `id`
- 时间字段: `create_time`, `update_time`
- 为常用查询字段（如email）添加索引

## 性能优化要点
- 前端: 路由懒加载、防抖节流、虚拟列表
- 后端: Redis缓存热点数据、避免N+1查询、异步处理耗时任务（RabbitMQ）
- 数据库: 合理使用索引、避免SELECT *

## Git提交规范
```
<type>(<scope>): <subject>

feat(game): 添加五子棋游戏
fix(user): 修复登录token过期
docs(api): 更新API文档
```

---