# Code Craft - AI 零代码应用生成平台

> 用户通过自然语言描述需求，AI 自动生成完整的前端应用代码。

## 核心功能

| 功能 | 说明 |
|------|------|
| 智能代码生成 | 输入自然语言，AI 自动生成 Vue 项目代码 |
| 可视化编辑 | 选中页面元素，对话式修改 |
| 实时预览 | 代码即改即看，实时渲染效果 |
| 源码工作区 | 源码文件树、源码 Tab 查看、预览区左右分屏，支持拖拽调整宽度 |
| 悬浮对话窗 | 聊天框默认居中悬浮，支持最大化和最小化，不遮断源码/预览工作流 |
| 一键部署 | 异步部署任务、临时终端日志、构建/部署/截图状态反馈 |
| 源码下载 | 下载完整项目源码，支持二次开发 |

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 LTS | 支持 Record、虚拟线程 |
| Spring Boot | 3.5.x | 核心框架 |
| Dubbo | 3.3.0 | RPC 框架，Triple 协议 |
| Nacos | 2.5.x | 注册中心 + 配置中心 |
| MyBatis-Flex | 1.11.x | ORM 框架 |
| MySQL | 8.0 | 主数据库 |
| Redis | 7.x | 分布式缓存、会话、限流 |
| Redisson | 3.51.0 | Redis 客户端，分布式锁 |
| LangChain4j | 1.1.x | AI 应用开发框架 |
| LangGraph4j | 1.6.x | AI 工作流引擎 |
| Playwright | - | 网页截图 |
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

### 构建与部署

| 技术 | 说明 |
|------|------|
| Docker Compose | 本地基础设施与生产容器编排 |
| GitHub Actions | 构建镜像、推送阿里云 ACR、部署到自托管 Runner |
| 阿里云 ACR | 生产镜像仓库 |
| Node Builder | 独立 Node.js 构建服务，默认端口 8020 |
| Nginx | 前端静态资源、API 反向代理、部署应用访问入口 |

## 项目结构

```
code-craft/
├── code-craft-frontend/           # 前端项目 (Vue 3)
├── code-craft-microservice/       # 微服务模块
│   ├── code-craft-app/            # 应用服务 (8126) - AI 代码生成、源码浏览、部署任务
│   ├── code-craft-user/           # 用户服务 (8124)
│   ├── code-craft-screenshot/     # 截图服务 (8127)
│   ├── code-craft-ai/             # AI 模块
│   ├── code-craft-common/         # 公共模块
│   ├── code-craft-model/          # 数据模型
│   └── code-craft-client/         # Dubbo 服务接口
├── node-builder/                  # 生成 Vue 项目的独立构建服务 (8020)
├── sql/                           # 业务数据库初始化脚本
├── mysql-init/                    # Nacos 数据库初始化脚本
├── nginx/                         # Nginx 相关配置
├── grafana/                       # Grafana 监控配置
├── docs/                          # 项目文档
├── .github/workflows/             # CI/CD 部署流程
├── .github/workflows/deploy.yml   # 生产环境部署
├── docker-compose.dev.yml         # 开发环境部署
├── LOCAL_DEV.md                   # 本地开发指南
├── DESIGN.md                      # 当前前端视觉风格说明
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

### 生成应用工作区

- **悬浮聊天窗**: 默认居中悬浮，支持最大化和最小化，避免长期占用源码/预览空间
- **源码浏览**: 后端提供只读文件树与文本文件内容接口，限制只能访问当前应用生成目录
- **源码/预览分屏**: 左侧查看生成源码，右侧保留网页预览，中间拖拽条可调整宽度
- **可视化编辑**: 预览 iframe 内选中页面元素后，把元素上下文带入下一轮对话

### 多级缓存

- L1: Caffeine 本地缓存（热点数据，5 分钟过期）
- L2: Redis 分布式缓存（应用信息，30 分钟过期）

### 分布式限流

基于 Redisson RRateLimiter，支持 API、用户、IP 三种限流维度。

### 部署任务与版本记录

- 应用服务记录部署任务状态、部署日志和错误信息
- 前端轮询部署任务，展示临时终端日志，完成后弹出成功或失败提示
- 应用生命周期与版本记录由数据库表持久化，便于后续扩展回滚和版本管理

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
# 1. 创建本地生成目录
mkdir -p tmp/code_output tmp/code_deploy

# 2. 启动基础设施和 node-builder
docker compose -f docker-compose.dev.yml up -d

# 3. 配置环境变量（复制并编辑 .env 文件）
# 配置: DASHSCOPE_API_KEY、数据库连接信息、CODE_OUTPUT_DIR、CODE_DEPLOY_DIR

# 4. 启动后端
mvn spring-boot:run

# 5. 启动前端
cd code-craft-frontend
npm install && npm run dev

# 访问 http://localhost:5173
```

**本地开发服务地址**

| 服务 | 地址 | 说明 |
|------|------|------|
| MySQL | localhost:3307 | 用户: ai_code_gen_user, 密码: 12345678 |
| Redis | localhost:6379 | 无密码 |
| Nacos | http://localhost:8848/nacos | 用户名/密码: nacos/nacos |
| Node Builder | http://localhost:8020/health | Vue 项目构建服务 |
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

生产环境不再维护根目录 `docker-compose.prod.yml`。当前做法是由 `.github/workflows/deploy.yml` 在目标服务器的部署目录内生成 `docker-compose.yml`，然后拉取阿里云 ACR 镜像并启动服务。

手动触发入口：
- GitHub Actions -> `Build and Deploy` -> `Run workflow`
- `target_env=aliyun`：部署到阿里云 Runner
- `target_env=wsl`：部署到本地/测试 Runner

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

项目提供两类数据库初始化能力：
- `sql/create_table.sql`：业务库基础表结构，Docker 首次启动 MySQL 时执行
- `mysql-init/mysql-schema.sql`：Nacos 依赖表结构，由 `nacos-db-init` 容器确保 Nacos 启动前完成
- `code-craft-microservice/code-craft-app/src/main/resources/db/migration/`：Flyway 迁移脚本，用于应用侧后续表结构演进

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
- 构建 user/app/screenshot/frontend/node-builder 镜像
- 推送镜像到阿里云 ACR
- 在目标 Runner 上生成生产 `docker-compose.yml`
- 拉取最新镜像并按 MySQL、Redis、Nacos、后端、前端、Nginx 顺序启动

当前部署策略是全量部署 latest 镜像；灰度、蓝绿、单服务回滚等能力尚未内置到工作流。

---
