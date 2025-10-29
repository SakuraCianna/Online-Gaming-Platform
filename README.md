# 🎮 在线游戏平台 (Online Gaming Platform)

<div align="center">

![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-brightgreen.svg)
![Vue](https://img.shields.io/badge/Vue-3.5.17-green.svg)
![Java](https://img.shields.io/badge/Java-21-orange.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18.0-blue.svg)
![License](https://img.shields.io/badge/license-CC%20BY--NC--SA%204.0-lightgrey.svg)

一个现代化的在线多人游戏平台，支持多种经典游戏、实时对战、好友系统和 AI 对手。

[功能特性](#-功能特性) • [技术栈](#-技术栈) • [快速开始](#-快速开始) • [项目结构](#-项目结构) • [API 文档](#-api-文档)

</div>

---

## 📖 项目简介

在线游戏平台是一个功能完善的 Web 游戏平台，提供多种经典游戏的在线游玩体验。平台采用前后端分离架构，支持实时对战、好友互动、游戏记录统计等功能，并集成了 AI 对手功能，让玩家可以与智能 AI 进行游戏。

### 🎯 核心特点

- 🎲 **多款游戏**：2048、五子棋、扫雷、坦克大战等经典游戏
- 👥 **多人对战**：支持实时在线对战，使用 WebSocket 实现低延迟通信
- 🤖 **AI 对手**：集成 AI 模型，提供智能对手挑战
- 👫 **好友系统**：添加好友、实时聊天、邀请对战
- 🏆 **数据统计**：游戏记录、积分排行、等级系统
- 🔐 **安全认证**：JWT 认证、密码加密、人机验证
- 📱 **响应式设计**：适配各种屏幕尺寸

---

## ✨ 功能特性

### 🎮 游戏模块

#### 1. 2048 游戏
- 三种难度模式（简单/普通/困难）
- 游戏状态保存与恢复
- 积分计算与排行榜
- 实时统计（步数、时长）

#### 2. 五子棋（Gomoku）
- 双人对战模式
- AI 对手（支持多种难度）
- 房间系统（公开房/私密房）
- 落子悔棋功能
- 游戏回放

#### 3. 扫雷（Minesweeper）
- 四种难度（初级/中级/高级/自定义）
- 智能提示系统
- 计时与计分
- 最佳成绩记录

#### 4. 坦克大战（Tank Battle）
- 多人实时对战
- 地图系统
- 技能系统
- 团队模式

### 👥 社交功能

- **好友系统**
  - 好友添加与管理
  - 好友状态（在线/离线/游戏中）
  - 好友请求通知

- **聊天系统**
  - 实时私聊
  - 历史消息记录
  - 表情与图片支持

- **房间系统**
  - 创建/加入游戏房间
  - 房间邀请功能
  - 房间设置（公开/私密、人数限制）

### 📊 数据统计

- 个人游戏记录
- 胜率统计
- 积分与等级系统
- 排行榜（全服/好友）
- 游戏时长统计

### 🔐 用户系统

- 邮箱注册与验证
- 密码加密存储
- JWT Token 认证
- hCaptcha 人机验证
- 头像上传与管理
- 个人资料设置

---

## 🛠 技术栈

### 前端技术

| 技术         | 版本    | 说明                   |
| ------------ | ------- | ---------------------- |
| Vue.js       | 3.5.17  | 渐进式 JavaScript 框架 |
| Vite         | 7.0.4   | 下一代前端构建工具     |
| Element Plus | 2.10.5  | Vue 3 组件库           |
| Pinia        | 3.0.3   | Vue 状态管理           |
| Vue Router   | 4.5.1   | 官方路由管理器         |
| Phaser       | 3.90.0  | HTML5 游戏框架         |
| Axios        | 1.11.0  | HTTP 客户端            |
| STOMP.js     | 7.1.1   | WebSocket 消息协议     |
| SockJS       | 1.6.1   | WebSocket 兼容库       |
| Three.js     | 0.180.0 | 3D 图形库              |
| GSAP         | 3.13.0  | 动画库                 |

### 后端技术

| 技术             | 版本   | 说明              |
| ---------------- | ------ | ----------------- |
| Spring Boot      | 3.5.7  | Java 应用开发框架 |
| Java             | 21     | 编程语言          |
| MyBatis-Plus     | 3.5.12 | ORM 持久层框架    |
| PostgreSQL       | 18.0   | 关系型数据库      |
| Redis            | 7.x    | 缓存数据库        |
| RabbitMQ         | 3.x    | 消息队列          |
| Spring Security  | -      | 安全框架          |
| JWT              | 0.11.5 | Token 认证        |
| Spring WebSocket | -      | WebSocket 支持    |
| Spring AI        | 1.0.1  | AI 集成框架       |
| Redisson         | 3.52.0 | Redis 客户端      |
| Thymeleaf        | -      | 模板引擎（邮件）  |

### 开发工具

- **开发环境**: Windows 11 64-bit
- **IDE**: IntelliJ IDEA（后端）+ Cursor（全栈）
- **容器化**: Docker + Docker Compose
- **数据库工具**: Navicat
- **版本控制**: Git

---

## 🚀 快速开始

### 环境要求

确保你的开发环境满足以下要求：

- **Java**: JDK 21+
- **Node.js**: v18.0+
- **Maven**: 3.8+
- **Docker**: 20.0+
- **Docker Compose**: 2.0+

### 1️⃣ 克隆项目

```bash
git clone https://github.com/yourusername/online-gaming-platform.git
cd online-gaming-platform
```

### 2️⃣ 启动基础服务（Docker）

项目使用 Docker Compose 管理 PostgreSQL、Redis 和 RabbitMQ 服务。

```bash
# 启动所有服务
docker-compose up -d

# 查看服务状态
docker ps

# 查看日志
docker-compose logs -f
```

服务端口：
- PostgreSQL: `5432`
- Redis: `6379`
- RabbitMQ: `5672` (AMQP)
- RabbitMQ Management: `15672` (Web UI)

**RabbitMQ 管理界面**: http://localhost:15672
- 用户名: `guest`
- 密码: `guest`

### 3️⃣ 后端启动

#### 方式 1: 使用 IDEA

1. 打开 `spring-game` 项目
2. 等待 Maven 依赖下载完成
3. 找到 `GameApplication.java`
4. 点击运行按钮 ▶️

#### 方式 2: 使用命令行

```bash
cd spring-game

# 编译项目
mvn clean package -DskipTests

# 运行项目
java -jar target/game-0.0.1-SNAPSHOT.jar
```

后端服务将在 `http://localhost:8080` 启动

### 4️⃣ 前端启动

```bash
cd vue-game

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端服务将在 `http://localhost:5173` 启动

### 5️⃣ 访问应用

打开浏览器访问: http://localhost:5173

---

## 📁 项目结构

```
Online Gaming Platform/
├── spring-game/                 # 后端项目（Spring Boot）
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/game/
│   │   │   │   ├── config/      # 配置类（Security、Redis、WebSocket 等）
│   │   │   │   ├── controller/  # 控制器层
│   │   │   │   ├── service/     # 业务逻辑层
│   │   │   │   ├── mapper/      # 数据访问层
│   │   │   │   ├── entity/      # 实体类
│   │   │   │   ├── dto/         # 数据传输对象
│   │   │   │   ├── vo/          # 视图对象
│   │   │   │   ├── exception/   # 异常处理
│   │   │   │   ├── util/        # 工具类
│   │   │   │   ├── component/   # 组件
│   │   │   │   ├── listener/    # 监听器
│   │   │   │   └── Interceptor/ # 拦截器
│   │   │   └── resources/
│   │   │       ├── application.properties  # 配置文件
│   │   │       └── templates/              # 邮件模板
│   │   └── test/                # 测试代码
│   └── pom.xml                  # Maven 配置
│
├── vue-game/                    # 前端项目（Vue 3）
│   ├── src/
│   │   ├── components/          # Vue 组件
│   │   │   ├── Dashboard/       # 主面板组件
│   │   │   └── game/            # 游戏组件
│   │   ├── config/              # 配置文件
│   │   │   ├── api.js           # API 配置
│   │   │   ├── router.js        # 路由配置
│   │   │   ├── user.js          # 用户配置
│   │   │   ├── websocket.js     # WebSocket 配置
│   │   │   └── room.js          # 房间配置
│   │   ├── assets/              # 静态资源
│   │   ├── App.vue              # 根组件
│   │   └── main.js              # 入口文件
│   ├── public/                  # 公共资源
│   ├── package.json             # npm 配置
│   └── vite.config.js           # Vite 配置
│
├── docker-compose.yml           # Docker 编排文件
├── game.sql                     # 数据库初始化脚本
├── README.md                    # 项目说明文档
└── MySQL_to_PostgreSQL_Migration_Summary.md  # 数据库迁移文档
```

---

## 🗄️ 数据库设计

### 核心表结构

#### 用户表 (user)
```sql
- id: 用户ID（主键）
- username: 用户名
- email: 邮箱
- password: 密码（加密）
- avatar: 头像URL
- total_score: 总积分
- current_level: 当前等级
- status: 状态（正常/禁用）
- email_verified: 邮箱验证状态
- created_time: 创建时间
- updated_time: 更新时间
```

#### 好友表 (friend)
```sql
- id: 记录ID
- user_id: 用户ID
- friend_id: 好友ID
- status: 状态（待处理/已接受/已拒绝）
- message: 请求消息
- created_time: 创建时间
```

#### 游戏房间表 (game_room)
```sql
- id: 房间ID
- room_code: 房间码
- game_name: 游戏名称
- players: 当前玩家数
- max_players: 最大玩家数
- status: 状态（等待中/游戏中/已结束）
- creator_id: 创建者ID
- is_private: 是否私密房间
```

#### 游戏记录表
- game2048: 2048 游戏记录
- gomoku_game: 五子棋对局记录
- minesweeper: 扫雷游戏记录
- tank_battle: 坦克大战记录

### 数据库特性

- ✅ 自动更新时间戳（触发器）
- ✅ 游戏完成后自动更新积分（触发器）
- ✅ 根据积分自动更新等级（触发器）
- ✅ JSONB 存储游戏状态数据
- ✅ 完善的索引优化

---

## 🔌 API 文档

### 认证相关

| 方法 | 路径                 | 说明       |
| ---- | -------------------- | ---------- |
| POST | `/api/user/register` | 用户注册   |
| POST | `/api/user/login`    | 用户登录   |
| POST | `/api/user/reset`    | 重置密码   |
| POST | `/api/email/code`    | 发送验证码 |

### 用户相关

| 方法 | 路径               | 说明         |
| ---- | ------------------ | ------------ |
| GET  | `/api/user/info`   | 获取用户信息 |
| PUT  | `/api/user/update` | 更新用户信息 |
| GET  | `/api/user/search` | 搜索用户     |

### 好友相关

| 方法   | 路径                 | 说明         |
| ------ | -------------------- | ------------ |
| GET    | `/api/friend/list`   | 好友列表     |
| POST   | `/api/friend/add`    | 添加好友     |
| PUT    | `/api/friend/accept` | 接受好友请求 |
| DELETE | `/api/friend/delete` | 删除好友     |

### 游戏相关

| 方法 | 路径                    | 说明           |
| ---- | ----------------------- | -------------- |
| POST | `/api/game2048/start`   | 开始 2048 游戏 |
| PUT  | `/api/game2048/save`    | 保存游戏状态   |
| GET  | `/api/game2048/records` | 游戏记录       |
| POST | `/api/gomoku/create`    | 创建五子棋房间 |
| POST | `/api/gomoku/move`      | 下棋           |

### WebSocket 端点

| 端点                   | 说明           |
| ---------------------- | -------------- |
| `/ws`                  | WebSocket 连接 |
| `/topic/room/{roomId}` | 房间广播       |
| `/user/queue/messages` | 私人消息       |

**请求头**：
```
Authorization: Bearer {token}
```

---

## ⚙️ 配置说明

### 后端配置 (application.properties)

```properties
# 服务器配置
server.port=8080

# PostgreSQL 配置
spring.datasource.url=jdbc:postgresql://localhost:5432/game
spring.datasource.username=postgres
spring.datasource.password=123456
spring.datasource.driver-class-name=org.postgresql.Driver

# Redis 配置
spring.data.redis.host=127.0.0.1
spring.data.redis.port=6379

# RabbitMQ 配置
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest

# JWT 配置
jwt.secret=your-secret-key

# hCaptcha 配置
hcaptcha.secret=your-hcaptcha-secret

# 邮件配置
spring.mail.host=smtp.qq.com
spring.mail.username=your-email@qq.com
spring.mail.password=your-auth-code
```

### 前端配置 (vite.config.js)

```javascript
export default {
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws': {
        target: 'http://localhost:8080',
        ws: true
      }
    }
  }
}
```

---

## 🧪 测试

### 后端测试

```bash
cd spring-game

# 运行所有测试
mvn test

# 运行指定测试类
mvn test -Dtest=UserServiceTest
```

### 前端测试

```bash
cd vue-game

# 运行单元测试
npm run test

# 运行 E2E 测试
npm run test:e2e
```

---

## 📦 部署

### Docker 部署

```bash
# 构建镜像
docker-compose build

# 启动服务
docker-compose up -d

# 查看日志
docker-compose logs -f
```

### 生产环境部署

1. **后端打包**
```bash
cd spring-game
mvn clean package -Pprod
```

2. **前端打包**
```bash
cd vue-game
npm run build
```

3. **部署到服务器**
```bash
# 后端
java -jar spring-game/target/game-0.0.1-SNAPSHOT.jar

# 前端（使用 Nginx）
cp -r vue-game/dist/* /var/www/html/
```

---

## 🔒 安全特性

- ✅ **密码加密**：使用 BCrypt 加密存储
- ✅ **JWT 认证**：Token 过期验证
- ✅ **XSS 防护**：输入验证与转义
- ✅ **CSRF 防护**：Spring Security 配置
- ✅ **SQL 注入防护**：MyBatis 参数化查询
- ✅ **人机验证**：hCaptcha 集成
- ✅ **请求限流**：Redis + 布隆过滤器
- ✅ **敏感信息保护**：返回前清理敏感字段

---

## 📈 性能优化

- ✅ **Redis 缓存**：热点数据缓存
- ✅ **数据库索引**：优化查询性能
- ✅ **连接池**：HikariCP 数据库连接池
- ✅ **异步处理**：RabbitMQ 消息队列
- ✅ **前端优化**：路由懒加载、代码分割
- ✅ **静态资源**：CDN 加速
- ✅ **WebSocket**：长连接减少开销

---

## 🐛 常见问题

### 1. Docker 容器启动失败
```bash
# 查看日志
docker logs game-postgres

# 删除数据卷重新启动
docker-compose down -v
docker-compose up -d
```

### 2. 前端无法连接后端
- 检查后端是否启动：`http://localhost:8080`
- 检查 Vite 代理配置是否正确
- 查看浏览器控制台错误信息

### 3. 数据库连接失败
- 确认 PostgreSQL 容器正在运行
- 检查 `application.properties` 配置
- 验证数据库用户名密码

### 4. WebSocket 连接失败
- 检查防火墙设置
- 确认 STOMP 端点配置
- 查看后端 WebSocket 日志

---

## 🤝 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

### 代码规范

- **后端**：遵循阿里巴巴 Java 开发手册
- **前端**：遵循 Vue 官方风格指南
- **提交信息**：使用 Conventional Commits 规范

---

## 📝 开发规范

详见项目根目录的开发规范文档：
- 后端规范：遵循 Spring Boot 最佳实践
- 前端规范：Vue 3 Composition API 风格
- 数据库规范：PostgreSQL 命名规范

---

## 📄 许可证

本项目采用 **CC BY-NC-SA 4.0** 许可证（署名-非商业性使用-相同方式共享）

[![License: CC BY-NC-SA 4.0](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc-sa/4.0/)

**你可以**：
- ✅ **分享** — 在任何媒介以任何形式复制、发行本作品
- ✅ **演绎** — 修改、转换或以本作品为基础进行创作

**但需遵守**：
- 📝 **署名** — 必须给出适当的署名，提供指向本许可证的链接，同时标明是否作了修改
- 🚫 **非商业性使用** — 不得将本作品用于商业目的
- 🔄 **相同方式共享** — 如果你基于本作品进行修改，必须使用相同的许可证分发
- ⚠️ **没有附加限制** — 不得使用法律术语或技术措施从而限制其他人做许可证允许的事情

详见 [LICENSE](LICENSE) 文件

---

## 👥 作者

- **开发者**: Sakura_Cianna
- **邮箱**: 754515922@qq.com
- **GitHub**: [@Sakura_Cianna](https://github.com/Sakura_Cianna)

---

## 🙏 致谢

感谢以下开源项目：

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Vue.js](https://vuejs.org/)
- [Element Plus](https://element-plus.org/)
- [Phaser](https://phaser.io/)
- [MyBatis-Plus](https://baomidou.com/)
- [PostgreSQL](https://www.postgresql.org/)

---

## 📊 项目状态

![GitHub stars](https://img.shields.io/github/stars/yourusername/online-gaming-platform)
![GitHub forks](https://img.shields.io/github/forks/yourusername/online-gaming-platform)
![GitHub issues](https://img.shields.io/github/issues/yourusername/online-gaming-platform)

---

<div align="center">

**[⬆ 回到顶部](#-在线游戏平台-online-gaming-platform)**

Made with ❤️ by Sakura_Cianna

</div>
