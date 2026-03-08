# WSL2 部署指南

## 问题修复

### 1. Docker构建错误修复
**错误信息**：
```
RUN mkdir -p /tmp/code_output /tmp/code_deploy /logs && chown -R ...
```

**问题原因**：
Dockerfile中在切换到非root用户后尝试执行`chown`命令，导致权限不足。

**已修复**：
- 将目录创建和权限设置移到`USER spring:spring`之前
- 更新了所有微服务的Dockerfile：
  - `Dockerfile.user`
  - `Dockerfile.app`
  - `Dockerfile.screenshot`

### 2. 镜像命名问题
**现象**：
```
unpacking to docker.io/library/yu-ai-code-mother-node-builder:latest
```

**说明**：
这是Docker Compose自动生成的镜像名称，不影响功能。如需自定义名称，可在`docker-compose.yml`中为每个服务添加`image:`字段。

## 环境变量配置

### 配置文件说明
项目提供两个环境变量模板：
- `.env.zeabur.example` - Zeabur云部署配置
- `.env.local.example` - 本地WSL2部署配置

### 必需修改的内容
复制模板文件并填写以下**必需**配置：

```bash
# 复制本地部署模板
cp .env.local.example .env

# 编辑.env文件，至少修改以下内容：
```

| 变量名 | 说明 | 获取方式 | 本地部署示例值 |
|--------|------|----------|----------------|
| `AI_DASHSCOPE_API_KEY` | **必需** 阿里云DashScope API密钥 | [阿里云控制台](https://help.aliyun.com/zh/dashscope/developer-reference/activate-dashscope-and-create-an-api-key) | `sk-xxx` |
| `DEEPSEEK_API_KEY` | 可选 DeepSeek API密钥 | [DeepSeek平台](https://platform.deepseek.com/api_keys) | `sk-xxx` |
| `COS_SECRET_ID` | 可选 腾讯云COS SecretId | [腾讯云控制台](https://console.cloud.tencent.com/cam/capi) | `AKIDxxx` |
| `COS_SECRET_KEY` | 可选 腾讯云COS SecretKey | 同上 | `xxx` |
| `DEPLOY_HOST` | 部署域名 | 本地使用`localhost` | `http://localhost` |

### 数据库配置（使用Docker Compose）
以下配置已预设，无需修改（除非有特殊需求）：

```env
# 数据库
DB_HOST=mysql
DB_PORT=3306
DB_NAME=yu_ai_code_mother
DB_USER=yu_ai_code_mother_user
DB_PASSWORD=12345678

# Redis
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=

# Nacos
NACOS_HOST=nacos
NACOS_PORT=8848

# Node构建器
NODE_BUILDER_URL=http://node-builder:3000/build
```

## 完整部署步骤

### 步骤1：准备环境
```bash
# 进入项目目录（在WSL2中）
cd /mnt/g/project/yu-ai-code-mother

# 确保Docker服务运行
sudo service docker start
# 或使用Docker Desktop for Windows
```

### 步骤2：配置环境变量
```bash
# 使用本地部署模板
cp .env.local.example .env

# 编辑.env文件，填写AI API密钥等
nano .env  # 或使用其他编辑器
```

### 步骤3：初始化Nacos数据库
```bash
# 运行Nacos数据库初始化脚本
# 该脚本会下载Nacos所需的MySQL表结构
./mysql-init.sh

# 验证文件是否生成
ls -la mysql-init/mysql-schema.sql

# 如果下载失败（网络问题），可以手动下载：
# 1. 访问：https://raw.githubusercontent.com/alibaba/nacos/2.0.4/distribution/conf/mysql-schema.sql
# 2. 将内容保存为 mysql-init/mysql-schema.sql
# 3. 确保文件编码为UTF-8
```

### 步骤4：启动所有服务
```bash
# 首次启动（构建镜像并启动容器）
docker-compose up -d --build

# 查看构建进度
docker-compose logs -f

# 或分阶段构建（推荐）
docker-compose build --parallel
docker-compose up -d
```

### 步骤5：等待服务就绪
首次启动需要时间：
1. **MySQL**（约30秒）→ **Redis** → **Nacos**（约60秒）
2. **Node构建器** → **微服务**（约90秒）
3. **前端** → **Nginx**

总时间约3-5分钟。

### 步骤6：验证部署
```bash
# 检查所有容器状态
docker-compose ps

# 查看服务日志
docker-compose logs -f app-service

# 验证服务健康
curl http://localhost/health                    # Nginx
curl http://localhost/api/actuator/health       # 主应用服务
curl http://localhost:8124/api/actuator/health  # 用户服务
```

### 步骤7：访问应用
- **前端界面**：http://localhost
- **API文档**：http://localhost/api/doc.html
- **Nacos控制台**：http://localhost:8848/nacos
  - 账号：`nacos`
  - 密码：`nacos`
- **MySQL管理**：`docker-compose exec mysql mysql -u root -p`
  - 密码：`root123456`

## 常见问题排查

### 1. 构建失败
```bash
# 单独构建有问题的服务
docker-compose build user-service

# 查看详细错误
docker-compose build --no-cache user-service

# 清理所有镜像重新构建
docker-compose down -v --rmi all
docker-compose up -d --build
```

### 2. 服务启动失败
```bash
# 查看特定服务日志
docker-compose logs -f app-service

# 进入容器调试
docker-compose exec app-service sh

# 检查服务端口
netstat -tlnp | grep 8126
```

### 3. 数据库连接问题
```bash
# 检查MySQL容器
docker-compose exec mysql mysql -u root -proot123456 -e "SHOW DATABASES;"

# 验证表结构
docker-compose exec mysql mysql -u yu_ai_code_mother_user -p12345678 yu_ai_code_mother -e "SHOW TABLES;"
```

### 4. AI服务不可用
- 检查`AI_DASHSCOPE_API_KEY`是否正确
- 验证网络连接：`curl https://dashscope.aliyuncs.com`
- 查看AI服务日志：`docker-compose logs -f app-service | grep -i ai`

### 5. 一键部署功能失败
- 检查Node构建器服务：`curl http://localhost:3000/health`
- 验证目录权限：`docker-compose exec app-service ls -la /tmp/`
- 查看构建日志：`docker-compose logs -f node-builder`

## 管理命令

### 日常操作
```bash
# 启动所有服务
docker-compose start

# 停止所有服务
docker-compose stop

# 重启特定服务
docker-compose restart app-service

# 查看服务状态
docker-compose ps
```

### 数据管理
```bash
# 备份数据库
docker-compose exec mysql mysqldump -u root -proot123456 yu_ai_code_mother > backup.sql

# 恢复数据库
docker-compose exec -T mysql mysql -u root -proot123456 yu_ai_code_mother < backup.sql

# 清理生成的文件
docker-compose exec app-service rm -rf /tmp/code_output/*
docker-compose exec app-service rm -rf /tmp/code_deploy/*
```

### 更新部署
```bash
# 拉取最新代码
git pull

# 重新构建并部署
docker-compose down
docker-compose up -d --build

# 只更新前端
docker-compose build frontend
docker-compose up -d frontend nginx
```

## 性能优化建议

### 1. 资源分配
```yaml
# 在docker-compose.yml中调整（如果需要）
services:
  app-service:
    deploy:
      resources:
        limits:
          memory: 1G
        reservations:
          memory: 512M
```

### 2. 构建缓存
```bash
# 使用构建缓存加速后续构建
docker-compose build --parallel --no-cache

# 清理无用镜像
docker system prune -f
```

### 3. 数据库优化
```sql
-- 在MySQL中执行
ALTER TABLE app ADD INDEX idx_user_id (user_id);
ALTER TABLE chat_history ADD INDEX idx_app_id (app_id);
```

## 故障恢复

### 完全重新部署
```bash
# 停止并删除所有容器、卷、镜像
docker-compose down -v --rmi all

# 重新构建启动
docker-compose up -d --build

# 等待服务就绪
sleep 300  # 等待5分钟
docker-compose ps
```

### 保留数据重新部署
```bash
# 保留数据卷，只重建容器
docker-compose down
docker-compose up -d --build
```

## 获取帮助

如果遇到问题：

1. **查看日志**：`docker-compose logs -f [服务名]`
2. **检查状态**：`docker-compose ps`
3. **验证配置**：确保`.env`文件中的API密钥正确
4. **网络诊断**：`docker-compose exec app-service ping mysql`

关键文件位置：
- `docker-compose.yml` - 服务编排配置
- `nginx/nginx.conf` - Nginx反向代理配置
- `node-builder/` - Vue项目构建服务
- 各微服务的`application-docker.yml` - Docker环境配置