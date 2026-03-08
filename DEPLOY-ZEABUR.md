# Zeabur 部署指南

## 概述

本项目已容器化，可以在 Zeabur 平台上部署。Zeabur 是一个容器化部署平台，支持 Docker Compose 和自定义 Dockerfile。

## 部署架构

在 Zeabur 上部署时，建议使用以下架构：

1. **使用 Zeabur 托管数据库服务**（MySQL、Redis），而不是运行自己的容器
2. **微服务容器**：用户服务、主应用服务、截图服务
3. **Node 构建器容器**：用于构建 Vue 项目
4. **前端静态文件容器**：构建后的 Vue 应用
5. **Nginx 容器**：反向代理和负载均衡

## 前置要求

1. Zeabur 账号
2. 已安装 Git，项目已推送到 GitHub/GitLab
3. 获取以下 API 密钥：
   - 阿里云 DashScope API 密钥（用于 AI 服务）
   - DeepSeek API 密钥（备用 AI 服务）
   - 腾讯云 COS 密钥（可选，用于对象存储）

## 部署步骤

### 方法一：使用 Docker Compose（推荐）

1. **准备环境变量**
   - 复制 `.env.zeabur.example` 为 `.env.zeabur`
   - 填写所有必要的环境变量

2. **修改 docker-compose.yml**
   - 注释掉 MySQL 和 Redis 服务（如果使用 Zeabur 托管服务）
   - 更新环境变量引用，使用 Zeabur 服务发现
   - 示例修改：
     ```yaml
     # 移除或注释以下服务：
     # mysql:
     # redis:

     # 更新微服务环境变量：
     environment:
       DB_HOST: ${DB_HOST}
       DB_PORT: ${DB_PORT}
       # ... 其他变量
     ```

3. **在 Zeabur 控制台创建项目**
   - 连接到你的 Git 仓库
   - 选择 "Docker Compose" 部署方式
   - 上传 `docker-compose.yml` 文件
   - 添加环境变量（从 `.env.zeabur` 复制）

4. **配置持久化存储**
   - 为 `/tmp/code_output` 和 `/tmp/code_deploy` 目录配置持久化卷
   - Zeabur 可能提供卷存储，或使用对象存储替代

5. **部署应用**
   - 启动部署，等待所有服务启动完成
   - 检查日志，确保服务正常运行

### 方法二：使用自定义 Dockerfile

1. **创建 zeabur.yaml 配置文件**
   ```yaml
   version: '3.8'
   services:
     user-service:
       build:
         context: .
         dockerfile: yu-ai-code-mother-microservice/Dockerfile.user
       ports:
         - "8124"
       environment:
         - SPRING_PROFILES_ACTIVE=docker
         # ... 其他环境变量

     app-service:
       build:
         context: .
         dockerfile: yu-ai-code-mother-microservice/Dockerfile.app
       ports:
         - "8126"
       environment:
         - SPRING_PROFILES_ACTIVE=docker
         # ... 其他环境变量

     # ... 其他服务
   ```

2. **在 Zeabur 控制台部署**
   - 选择 "Custom Dockerfile" 部署方式
   - 配置服务端口映射
   - 设置环境变量

## 环境变量配置

### 必需的环境变量

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `AI_DASHSCOPE_API_KEY` | 阿里云 DashScope API 密钥 | sk-xxx |
| `DB_HOST` | MySQL 主机地址 | your-mysql.zeabur.internal |
| `DB_PASSWORD` | MySQL 密码 | your-password |
| `REDIS_HOST` | Redis 主机地址 | your-redis.zeabur.internal |
| `DEPLOY_HOST` | 部署域名 | https://your-project.zeabur.app |

### 可选的环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `DEEPSEEK_API_KEY` | DeepSeek API 密钥 | 空 |
| `COS_SECRET_ID` | 腾讯云 COS SecretId | 空 |
| `COS_SECRET_KEY` | 腾讯云 COS SecretKey | 空 |
| `NODE_BUILDER_URL` | Node 构建器地址 | http://node-builder:3000/build |

## 服务端口映射

| 服务 | 容器端口 | 外部访问路径 |
|------|----------|--------------|
| 用户服务 | 8124 | /user-api/ |
| 主应用服务 | 8126 | /api/ |
| 截图服务 | 8127 | /screenshot-api/ |
| Nginx | 80 | / (前端) |
| Node 构建器 | 3000 | 内部使用 |

## 持久化存储

以下目录需要持久化存储：

1. `/tmp/code_output` - 生成的代码文件
2. `/tmp/code_deploy` - 部署的静态应用
3. `/logs` - 应用日志

在 Zeabur 中，可以通过以下方式实现：
- 使用 Zeabur 的卷存储（Volume）
- 使用对象存储（如 COS）替代文件存储

## 健康检查

所有服务都配置了健康检查端点：

- 微服务：`/api/actuator/health`
- Nginx：`/health`
- Node 构建器：`/health`

## 故障排除

### 常见问题

1. **数据库连接失败**
   - 检查 Zeabur 数据库服务状态
   - 验证环境变量是否正确
   - 确认网络连通性

2. **Nacos 启动失败**
   - Zeabur 可能不支持 Nacos 的服务发现模式
   - 可以考虑使用 Zeabur 内置的服务发现
   - 或改为直接连接（配置服务地址）

3. **Node 构建器超时**
   - 增加构建超时时间
   - 检查网络代理设置
   - 确认 npm 源可用

4. **内存不足**
   - 调整 Java 堆内存设置（`JAVA_OPTS`）
   - 增加容器内存限制

### 日志查看

在 Zeabur 控制台可以查看各个容器的日志：
1. 进入项目详情页
2. 选择服务实例
3. 查看 "Logs" 标签页

## 性能优化建议

1. **数据库优化**
   - 使用 Zeabur 提供的 MySQL 性能优化配置
   - 启用查询缓存

2. **缓存策略**
   - 合理设置 Redis TTL
   - 使用多级缓存（Redis + Caffeine）

3. **容器资源**
   - 为 Java 服务分配足够内存（建议 1GB+）
   - 设置合理的 CPU 限制

4. **构建优化**
   - 使用 npm 缓存加速构建
   - 预构建基础镜像

## 后续维护

1. **监控**
   - 配置 Zeabur 监控告警
   - 设置健康检查通知

2. **备份**
   - 定期备份数据库
   - 备份生成的代码文件

3. **更新**
   - 通过 Git 推送更新代码
   - Zeabur 会自动重新构建部署

## 联系支持

如果在部署过程中遇到问题：
1. 查看 Zeabur 官方文档
2. 检查项目日志
3. 联系 Zeabur 技术支持