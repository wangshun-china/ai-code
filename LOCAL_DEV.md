# 本地开发环境配置指南

## 快速启动

```bash
# 启动基础设施服务
docker compose -f docker-compose.dev.yml up -d

# 等待服务就绪（约 2-3 分钟）
docker compose -f docker-compose.dev.yml logs -f nacos
```

## 服务地址

| 服务 | 地址 | 说明 |
|------|------|------|
| MySQL | localhost:3306 | 用户: yu_ai_code_mother_user, 密码: 12345678 |
| Redis | localhost:6379 | 无密码 |
| Nacos | http://localhost:8848/nacos | 用户名/密码: nacos/nacos |
| Node Builder | http://localhost:3000 | Vue 项目构建服务 |

## 文件共享配置（关键！）

由于 Java 在本地 IDE 运行，node-builder 在 Docker 容器中运行，两者需要共享代码文件。

### 方式一：使用本地目录挂载（推荐）

1. 在项目根目录创建临时目录：
```bash
mkdir -p tmp/code_output tmp/code_deploy
```

2. 配置环境变量（IDE 中设置）：
```
CODE_OUTPUT_DIR=G:\project\yu-ai-code-mother\tmp\code_output
CODE_DEPLOY_DIR=G:\project\yu-ai-code-mother\tmp\code_deploy
NODE_BUILDER_URL=http://localhost:3000/build
```

3. 修改 `docker-compose.dev.yml` 中 node-builder 的 volumes：
```yaml
volumes:
  - ./tmp/code_output:/tmp/code_output
  - ./tmp/code_deploy:/tmp/code_deploy
```

4. 确保 Docker Desktop 文件共享包含项目目录：
   - 打开 Docker Desktop -> Settings -> Resources -> File Sharing
   - 添加 `G:\project\yu-ai-code-mother`

### 方式二：全部在 Docker 中运行

如果文件共享配置复杂，可以把 Java 服务也放到 Docker 中运行：
```bash
docker compose -f docker-compose.prod.yml up -d
```

## IDE 运行配置

### IntelliJ IDEA

1. 打开 Run/Debug Configurations
2. 添加 Spring Boot 配置
3. 设置 Environment variables：
   ```
   CODE_OUTPUT_DIR=G:\project\yu-ai-code-mother\tmp\code_output;CODE_DEPLOY_DIR=G:\project\yu-ai-code-mother\tmp\code_deploy;NODE_BUILDER_URL=http://localhost:3000/build;AI_DASHSCOPE_API_KEY=your-api-key
   ```
4. Active profiles: `local`

### 需要启动的服务

按顺序启动以下微服务：

| 服务 | 端口 | 模块 |
|------|------|------|
| yu-ai-code-user | 8124 | yu-ai-code-mother-microservice/yu-ai-code-user |
| yu-ai-code-app | 8126 | yu-ai-code-mother-microservice/yu-ai-code-app |
| yu-ai-code-screenshot | 8127 | yu-ai-code-mother-microservice/yu-ai-code-screenshot |

## 前端开发

```bash
cd yu-ai-code-mother-frontend
npm install
npm run dev
```

访问: http://localhost:5173

## 部署的应用访问

生成的应用通过 `DeployResourceController` 托管，访问地址：
```
http://localhost:8126/{deployKey}/
```

无需 nginx。

## 常见问题

### Q: node-builder 找不到项目目录
A: 确保文件共享已正确配置，且 Java 服务使用的目录与 Docker 挂载的目录一致。

### Q: Nacos 启动失败
A: 等待 MySQL 完全就绪后再启动 Nacos，首次启动需要初始化数据库，可能需要 1-2 分钟。

### Q: Windows 路径问题
A: 使用正斜杠 `/` 或双反斜杠 `\\`，推荐在环境变量中使用正斜杠。