#!/bin/bash
# 环境切换脚本
# 用法: ./env-switch.sh [local-dev|local-deploy]

set -e

ENV=$1
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "==========================================="
echo "切换环境: $ENV"
echo "==========================================="

case "$ENV" in
    "local-dev")
        # 切换到本地开发环境
        echo "1. 复制本地开发环境变量..."
        cp -f .env.local-dev .env

        echo "2. 更新前端开发环境配置..."
        # 前端开发环境变量
        cat > yu-ai-code-mother-frontend/.env.development << EOF
VITE_DEPLOY_DOMAIN=http://localhost
VITE_API_BASE_URL=/api
EOF

        echo "3. 更新Vite开发配置 (代理到单体应用端口8123)..."
        cp -f yu-ai-code-mother-frontend/vite.config.dev.ts yu-ai-code-mother-frontend/vite.config.ts

        echo "4. 环境切换完成!"
        echo ""
        echo "本地开发环境配置说明:"
        echo "- 后端: 运行Spring Boot应用 (SPRING_PROFILES_ACTIVE=local)"
        echo "- 数据库: 需要本地安装并启动MySQL (localhost:3306)"
        echo "- Redis: 需要本地安装并启动Redis (localhost:6379)"
        echo "- 前端: 运行 npm run dev"
        echo ""
        echo "启动命令:"
        echo "  # 后端: 在IDE中运行YuAiCodeMotherApplication 或使用:"
        echo "  # mvn spring-boot:run -pl yu-ai-code-mother"
        echo "  # 前端: cd yu-ai-code-mother-frontend && npm run dev"
        ;;

    "local-deploy")
        # 切换到本地部署环境
        echo "1. 复制本地部署环境变量..."
        cp -f .env.local-deploy .env

        echo "2. 更新前端生产环境配置..."
        # 前端生产环境变量（用于Docker构建）
        cat > yu-ai-code-mother-frontend/.env.production << EOF
VITE_DEPLOY_DOMAIN=/deploy
VITE_API_BASE_URL=/api
EOF

        echo "3. 更新Vite生产配置 (微服务代理)..."
        cp -f yu-ai-code-mother-frontend/vite.config.prod.ts yu-ai-code-mother-frontend/vite.config.ts

        echo "4. 环境切换完成!"
        echo ""
        echo "本地部署环境配置说明:"
        echo "- 使用Docker Compose启动所有服务"
        echo "- 包含: MySQL, Redis, Nacos, 微服务, 前端, Nginx"
        echo ""
        echo "启动命令:"
        echo "  docker-compose up -d --build"
        echo ""
        echo "停止命令:"
        echo "  docker-compose down"
        ;;

    *)
        echo "错误: 未知环境 '$ENV'"
        echo ""
        echo "可用环境:"
        echo "  local-dev     本地开发环境（直接运行应用）"
        echo "  local-deploy  本地部署环境（Docker Compose）"
        echo ""
        echo "用法: ./env-switch.sh [local-dev|local-deploy]"
        exit 1
        ;;
esac

echo "==========================================="
echo "当前环境: $ENV"
echo "下次启动应用时将使用新配置"
echo "==========================================="