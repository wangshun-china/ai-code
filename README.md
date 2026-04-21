# Code Craft - AI 零代码应用生成平台

> 用户通过自然语言描述需求，AI 自动生成完整的前端应用代码。

## 核心功能

| 功能 | 说明 |
|------|------|
| 智能代码生成 | 输入自然语言，AI 自动生成 Vue 项目代码 |
| 可视化编辑 | 选中页面元素，对话式修改 |
| 实时预览 | 代码即改即看，实时渲染效果 |
| 一键部署 | 自动构建、部署、截图 |
| 源码下载 | 下载完整项目源码，支持二次开发 |

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 LTS | 支持 Record、虚拟线程 |
| Spring Boot | 3.5.4 | 核心框架 |
| Dubbo | 3.3.0 | RPC 框架，Triple 协议 |
| Nacos | 2.5.x | 注册中心 + 配置中心 |
| MyBatis-Flex | 1.11.x | ORM 框架 |
| MySQL | 8.0 | 主数据库 |
| Redis | 7.x | 分布式缓存、会话、限流 |
| Redisson | 3.51.0 | Redis 客户端，分布式锁 |
| LangChain4j | 1.1.0-beta7 | AI 应用开发框架 |
| LangGraph4j | 1.6.0-rc2 | AI 工作流引擎 |
| Selenium | 4.33+ | 网页截图 |
| 腾讯云 COS | - | 对象存储 |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5.17 | Composition API |
| TypeScript | 5.8 | 类型安全 |
| Vite | 7.0 | 构建工具 |
| Ant Design Vue | 4.2.6 | UI 组件库 |
| Pinia | 3.0 | 状态管理 |
| Vue Router | 4.5 | 路由 |
| Axios | 1.11 | HTTP 客户端 |
| Markdown-it | 14.1 | Markdown 渲染 |
| Highlight.js | 11.11 | 代码高亮 |

## 项目结构

```
code-craft/
├── code-craft-frontend/           # 前端项目 (Vue 3)
├── code-craft-microservice/       # 微服务模块
│   ├── code-craft-app/            # 应用服务 (8126) - AI 代码生成核心
│   ├── code-craft-user/           # 用户服务 (8124)
│   ├── code-craft-screenshot/     # 截图服务 (8127)
│   ├── code-craft-ai/             # AI 模块
│   ├── code-craft-common/         # 公共模块
│   ├── code-craft-model/          # 数据模型
│   └── code-craft-client/         # Dubbo 服务接口
├── sql/                           # 业务数据库初始化脚本
├── mysql-init/                    # Nacos 数据库初始化脚本
├── grafana/                       # Grafana 监控配置
├── .github/workflows/             # CI/CD 部署流程
├── .github/workflows/deploy.yml   # 生产环境部署
├── docker-compose.dev.yml         # 开发环境部署
├── LOCAL_DEV.md                   # 本地开发指南
└── AGENTS.md                      # AI Agent 设计文档
```

## 核心架构

```
┌─────────────────────────────────────────────────────────────┐
│  用户层: Vue 3 SPA + 响应式适配                               │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  接入层: Nginx (静态资源 + 反向代理 + SSL)                    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  微服务层                                                     │
│  user-service(8124)  app-service(8126)  screenshot(8127)    │
│  node-builder(8020) - Vue 项目构建服务                       │
│  ───────────────── Dubbo RPC + Nacos ─────────────────      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  AI 服务层: LangChain4j + Tool Calling + 流式输出            │
│  LLM: 通义千问 (DashScope) / DeepSeek API                    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  中间件层: Nacos + Redis + MySQL + 腾讯云 COS                 │
└─────────────────────────────────────────────────────────────┘
```

## 核心技术实现

### AI Agent 架构

基于 LangChain4j 实现 AI 代码生成服务，核心能力：

- **AI Service**: 通过注解定义 AI 接口，自动代理实现
- **Tool Calling**: AI 可调用以下工具：
  - `FileWriteTool` - 写入文件
  - `FileReadTool` - 读取文件内容
  - `FileModifyTool` - 修改文件内容
  - `FileDeleteTool` - 删除文件
  - `FileDirReadTool` - 读取目录结构
  - `ExitTool` - 任务完成退出
- **对话记忆**: Redis 存储多轮对话上下文
- **流式输出**: Reactor Flux + SSE 实时返回生成内容
- **智能路由**: 根据用户输入自动选择生成策略

### AI 安全护轨 (Guardrail)

- 输入护轨: 敏感词过滤、Prompt 注入检测
- 输出护轨: 代码格式验证、重试机制

### 多级缓存

- L1: Caffeine 本地缓存（热点数据，5 分钟过期）
- L2: Redis 分布式缓存（应用信息，30 分钟过期）

### 分布式限流

基于 Redisson RRateLimiter，支持 API、用户、IP 三种限流维度。

### API 文档与监控

| 功能 | 说明 |
|------|------|
| Knife4j | API 文档，访问 `/doc.html` |
| Actuator | Spring Boot 监控端点 |
| Grafana | 可视化监控大盘（提供 AI 模型监控配置） |

## 快速开始

### 环境要求

| 环境 | 版本 |
|------|------|
| JDK | 21+ |
| Maven | 3.9+ |
| Node.js | 18+ |
| MySQL | 8.0+ |
| Redis | 7+ |
| Nacos | 2.5+ |
| Docker | 24+ (可选) |

### 本地启动

**方式一：单体模式（推荐开发）**

```bash
# 1. 启动基础设施
docker-compose -f docker-compose.dev.yml up -d mysql redis nacos

# 2. 配置环境变量（复制并编辑 .env 文件）
# 配置: DASHSCOPE_API_KEY、数据库连接信息

# 3. 启动后端
mvn spring-boot:run

# 4. 启动前端
cd code-craft-frontend
npm install && npm run dev

# 访问 http://localhost:5173
```

**本地开发服务地址**

| 服务 | 地址 | 说明 |
|------|------|------|
| MySQL | localhost:3306 | 用户: ai_code_gen_user, 密码: 12345678 |
| Redis | localhost:6379 | 无密码 |
| Nacos | http://localhost:8848/nacos | 用户名/密码: nacos/nacos |
| Node Builder | http://localhost:8020 | Vue 项目构建服务 |
| 前端 | http://localhost:5173 | 开发环境 |
| API 文档 | http://localhost:8126/doc.html | Knife4j |

**方式二：微服务模式**

```bash
# 1. 启动基础设施
docker-compose -f docker-compose.dev.yml up -d

# 2. 构建项目
mvn clean install -DskipTests

# 3. 启动各服务（进入对应模块目录）
cd code-craft-microservice/code-craft-user
mvn spring-boot:run -Dspring-boot.run.profiles=local

cd code-craft-microservice/code-craft-app
mvn spring-boot:run -Dspring-boot.run.profiles=local

cd code-craft-microservice/code-craft-screenshot
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 4. 启动前端
cd code-craft-frontend && npm run dev
```

### 构建命令

```bash
# 后端完整构建
mvn clean install -DskipTests

# 前端构建
cd code-craft-frontend
npm run build    # 生产构建
npm run preview  # 预览构建结果
```

## Docker 部署

```bash
# 配置环境变量
cp .env .env.prod
# 编辑 .env.prod，配置 API 密钥和生产环境参数

# 启动生产环境
生产环境由 .github/workflows/deploy.yml 自动生成 docker-compose.yml 并部署

# 查看服务状态
docker compose ps

# 访问 http://localhost
```

部署包含服务：
- nginx: 反向代理
- user-service: 用户服务
- app-service: 应用服务
- screenshot-service: 截图服务
- node-builder: 前端构建服务
- mysql: 数据库
- redis: 缓存
- nacos: 注册/配置中心

### 数据库初始化

项目提供数据库初始化脚本：
- `sql/create_table.sql` - 业务表结构
- `mysql-init/mysql-schema.sql` - Nacos 表结构

## 配置说明

### 必要配置项

| 配置项 | 说明 |
|--------|------|
| DASHSCOPE_API_KEY | 阿里云通义千问 API Key |
| DEEPSEEK_API_KEY | DeepSeek API Key（可选） |
| MYSQL_HOST/PASSWORD | MySQL 连接信息 |
| REDIS_HOST/PASSWORD | Redis 连接信息 |
| COS_SECRET_ID/KEY | 腾讯云 COS 对象存储 |

配置文件位置：
- 本地开发: `.env`
- 生产部署: `.env.prod` 或 Docker Compose 环境变量

## CI/CD 部署

项目配置了 GitHub Actions 自动部署流程（`.github/workflows/deploy.yml`），支持：
- 自动构建前后端
- Docker 镜像构建与推送
- 自动部署到生产环境

---
