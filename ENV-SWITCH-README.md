# 环境切换系统

## 概述

本项目提供一键切换本地开发环境和本地部署环境的功能，方便在不同场景下进行开发、测试和部署。

## 环境说明

### 1. 本地开发环境 (local-dev)
- **目的**: 快速开发调试，修改代码后立即生效
- **架构**:
  - 后端: 微服务架构（用户服务: 8124，主应用: 8126，截图服务: 8127）
  - 数据库: Docker容器 (MySQL, Redis, Nacos)
  - 前端: Vue开发服务器 (端口: 5173)，代理到对应微服务
- **特点**: 热重载、快速启动、便于调试，完整的微服务环境

### 2. 本地部署环境 (local-deploy)
- **目的**: 模拟生产环境，完整测试所有服务
- **架构**:
  - 使用Docker Compose启动全套微服务
  - 包含: MySQL, Redis, Nacos, 3个微服务, Node构建器, 前端, Nginx
  - 前端通过Nginx访问
- **特点**: 完整环境、生产配置、集成测试

## 使用方法

### 第一步: 切换环境

```bash
# 切换到本地开发环境
./env-switch.sh local-dev

# 切换到本地部署环境
./env-switch.sh local-deploy
```

切换操作会:
1. 更新`.env`环境变量文件
2. 更新前端环境配置文件
3. 显示下一步操作指南

### 第二步: 启动环境

#### 本地开发环境
```bash
# 启动基础设施 (MySQL, Redis, Nacos)
./start-local-dev.sh

# 按照脚本提示启动后端和前端应用
# 后端: 在IDE中运行YuAiCodeMotherApplication 或使用 mvn spring-boot:run
# 前端: cd yu-ai-code-mother-frontend && npm run dev
```

#### 本地部署环境
```bash
# 一键启动所有服务
./start-local-deploy.sh

# 停止服务
docker-compose down
```

### 环境变量文件

- `.env.local-dev` - 本地开发环境配置模板
- `.env.local-deploy` - 本地部署环境配置模板
- `.env` - 当前激活的环境配置 (由切换脚本生成)

## 端口映射

### 本地开发环境
| 服务 | 端口 | 访问地址 |
|------|------|----------|
| 用户服务 | 8124 | http://localhost:8124/api/user/ |
| 主应用服务 | 8126 | http://localhost:8126/api/ |
| 截图服务 | 8127 | http://localhost:8127/api/screenshot |
| MySQL | 3306 | localhost:3306 |
| Redis | 6379 | localhost:6379 |
| Nacos | 8848 | http://localhost:8848/nacos |
| 前端开发服务器 | 5173 | http://localhost:5173 |

### 本地部署环境
| 服务 | 容器端口 | 外部访问 |
|------|----------|----------|
| Nginx | 80 | http://localhost |
| 用户服务 | 8124 | http://localhost/api/user/ |
| 主应用服务 | 8126 | http://localhost/api/ |
| 截图服务 | 8127 | http://localhost/api/screenshot |
| MySQL | 3306 | localhost:3306 |
| Redis | 6379 | localhost:6379 |
| Nacos | 8848 | http://localhost:8848/nacos |
| Node构建器 | 3000 | 内部使用 |

## 配置说明

### 后端配置
- **开发环境**: `src/main/resources/application.yml` (local profile)
- **Docker环境**: 各微服务的`application-docker.yml` (docker profile)
- **激活方式**: 通过`SPRING_PROFILES_ACTIVE`环境变量切换

### 前端配置
- **开发环境**: `yu-ai-code-mother-frontend/.env.development`
- **生产环境**: `yu-ai-code-mother-frontend/.env.production`
- **构建时**: 环境变量注入到编译结果中

## 常见问题

### 1. 切换环境后前端配置不生效
- 确保重新启动前端开发服务器
- 检查`.env.development`或`.env.production`文件内容

### 2. MySQL连接失败
- 开发环境: 确保Docker容器运行 (`docker-compose ps`)
- 检查`.env`文件中的数据库配置

### 3. 端口冲突
- 开发环境: 确保8123、3306、6379端口未被占用
- 部署环境: 确保80、3306、6379、8848端口未被占用

### 4. AI服务不可用
- 检查`.env`文件中的`AI_DASHSCOPE_API_KEY`是否正确
- 验证网络连接

## 高级配置

### 自定义环境变量
如需自定义配置，可编辑对应的环境模板文件:
- 开发配置: `.env.local-dev`
- 部署配置: `.env.local-deploy`

然后重新运行切换脚本。

### 扩展新环境
如需添加新环境（如测试环境、生产环境）:
1. 创建`.env.[环境名]`模板文件
2. 在`env-switch.sh`中添加对应的处理逻辑
3. 创建相应的启动脚本

## 脚本说明

| 脚本文件 | 功能 |
|----------|------|
| `env-switch.sh` | 环境切换主脚本 |
| `start-local-dev.sh` | 启动本地开发环境 |
| `start-local-deploy.sh` | 启动本地部署环境 |
| `docker-compose.yml` | 完整的服务编排配置 |

## 注意事项

1. **环境隔离**: 开发环境和部署环境使用不同的数据库实例，数据不共享
2. **配置同步**: 切换环境后，需要重启相关服务使配置生效
3. **密钥安全**: API密钥等敏感信息存储在`.env`文件中，不要提交到版本库
4. **端口管理**: 确保不同环境的端口不冲突

## 下一步计划

1. 添加云端部署环境支持 (Zeabur, Railway等)
2. 实现热更新机制，代码修改后自动重新部署
3. 添加环境配置验证功能
4. 集成CI/CD流水线

## 故障排除

如果遇到问题，请按以下步骤排查:

1. 检查当前环境: `cat .env | grep SPRING_PROFILES_ACTIVE`
2. 查看服务状态: `docker-compose ps` (部署环境)
3. 查看日志: `docker-compose logs -f [服务名]`
4. 验证配置: 检查`.env`文件中的关键配置
5. 重启服务: 停止所有服务后重新启动

如需更多帮助，请参考项目文档或联系维护者。