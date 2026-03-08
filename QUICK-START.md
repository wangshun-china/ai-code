# 快速开始：环境切换系统

## 第一步：准备环境

1. 确保已安装:
   - Docker 和 Docker Compose
   - Node.js 20+ (前端开发)
   - Java 21+ (后端开发)
   - Maven (后端构建)

2. 克隆项目并进入目录

## 第二步：初始设置

```bash
# 切换到本地开发环境（推荐开发者使用）
./env-switch.sh local-dev

# 或切换到本地部署环境（完整测试）
./env-switch.sh local-deploy
```

## 第三步：启动服务

### 本地开发环境
```bash
# 启动基础设施 (MySQL, Redis, Nacos)
./start-local-dev.sh

# 按照脚本提示启动后端和前端
# 1. 启动后端: 在IDE中运行 YuAiCodeMotherApplication 或使用 mvn spring-boot:run
# 2. 启动前端: cd yu-ai-code-mother-frontend && npm run dev
```

### 本地部署环境
```bash
# 一键启动所有服务
./start-local-deploy.sh

# 停止服务
./stop-local-deploy.sh
```

## 第四步：验证部署

### 本地开发环境
- 后端: http://localhost:8123/api/actuator/health
- 前端: http://localhost:5173
- Nacos: http://localhost:8848/nacos (账号: nacos, 密码: nacos)

### 本地部署环境
- 前端: http://localhost
- API文档: http://localhost/api/doc.html
- Nacos: http://localhost:8848/nacos
- 部署的子应用: http://localhost/随机字符串/ (例如: http://localhost/abc123/)

## 环境切换

```bash
# 查看当前环境
./check-env.sh

# 切换环境
./env-switch.sh local-dev
./env-switch.sh local-deploy
```

## 配置说明

- 环境变量文件: `.env` (当前激活的配置)
- 配置模板: `.env.local-dev`, `.env.local-deploy`
- 前端配置: `yu-ai-code-mother-frontend/.env.development`, `.env.production`

## 部署应用访问说明

### 开发环境
- **前端应用**: http://localhost:5173
- **后端API**: http://localhost:8126
- **子应用访问**: http://localhost:8126/随机字符串/
  - 示例: http://localhost:8126/abc123/
  - 说明: 开发阶段直接通过后端端口访问子应用

### 本地部署环境
- **前端应用**: http://localhost
- **后端API**: http://localhost/api/
- **子应用访问**: http://localhost/随机字符串/
  - 示例: http://localhost/abc123/
  - 说明: 通过nginx在80端口统一访问

### 线上部署 (Zeabur)
- **前端应用**: https://your-project.zeabur.app
- **后端API**: https://your-project.zeabur.app/api/
- **子应用访问**: https://your-project.zeabur.app/随机字符串/
  - 示例: https://your-project.zeabur.app/abc123/
  - 配置: 设置环境变量 `DEPLOY_HOST=https://your-project.zeabur.app`

### 注意事项
1. **随机字符串**: 部署时自动生成的6位字母数字组合 (如: abc123)
2. **URL格式**: 建议以斜杠结尾 (如: /abc123/)
3. **SPA支持**: 生成的Vue应用为单页应用，需要nginx正确配置前端路由
4. **环境切换**: 不同环境使用不同的 `deploy-host` 配置

## 常见问题

### 1. MySQL连接失败
- 检查MySQL容器是否运行: `docker-compose ps mysql`
- 验证密码: 开发环境使用 `root/root123456`，部署环境使用 `yu_ai_code_mother_user/12345678`

### 2. 端口冲突
- 开发环境: 确保8123、3306、6379、8848、5173端口可用
- 部署环境: 确保80、3306、6379、8848端口可用

### 3. AI服务不可用
- 检查`.env`文件中的`AI_DASHSCOPE_API_KEY`
- 确保已配置有效的阿里云DashScope API密钥

### 4. 前端代理配置
- 开发环境: 后端运行在8123端口，可能需要调整`vite.config.ts`中的代理设置
- 部署环境: 前端通过Nginx访问，无需配置代理

## 脚本列表

- `env-switch.sh` - 环境切换
- `start-local-dev.sh` - 启动开发环境
- `start-local-deploy.sh` - 启动部署环境
- `stop-local-deploy.sh` - 停止部署环境
- `check-env.sh` - 检查当前环境

## 获取帮助

- 详细文档: `ENV-SWITCH-README.md`
- 检查环境: `./check-env.sh`
- 查看日志: `docker-compose logs -f [服务名]`

## 下一步

1. 开发完成后，切换到部署环境进行完整测试
2. 配置CI/CD流水线实现自动部署
3. 添加云端部署支持 (Zeabur, Railway等)