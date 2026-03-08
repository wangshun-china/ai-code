#!/bin/bash
# 检查当前环境配置

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "==========================================="
echo "当前环境配置检查"
echo "==========================================="

# 检查.env文件
if [ -f ".env" ]; then
    echo "✓ .env 文件存在"

    # 检查关键配置
    if grep -q "SPRING_PROFILES_ACTIVE" .env; then
        PROFILE=$(grep "SPRING_PROFILES_ACTIVE" .env | cut -d'=' -f2)
        echo "  Spring Profile: $PROFILE"
    else
        echo "  ⚠  SPRING_PROFILES_ACTIVE 未设置"
    fi

    # 检查数据库配置
    if grep -q "DB_HOST" .env; then
        DB_HOST=$(grep "DB_HOST" .env | cut -d'=' -f2)
        echo "  数据库主机: $DB_HOST"
    fi

    # 检查AI密钥
    if grep -q "AI_DASHSCOPE_API_KEY" .env; then
        KEY_PREFIX=$(grep "AI_DASHSCOPE_API_KEY" .env | cut -d'=' -f2 | cut -c1-10)
        if [ -n "$KEY_PREFIX" ] && [ "$KEY_PREFIX" != "your-dashscope" ]; then
            echo "  ✓ AI密钥已配置"
        else
            echo "  ⚠ AI密钥未配置或为示例值"
        fi
    fi
else
    echo "✗ .env 文件不存在"
fi

echo ""

# 检查前端配置
echo "前端环境配置:"
if [ -f "yu-ai-code-mother-frontend/.env.development" ]; then
    echo "  .env.development:"
    cat yu-ai-code-mother-frontend/.env.development | sed 's/^/    /'
fi

if [ -f "yu-ai-code-mother-frontend/.env.production" ]; then
    echo "  .env.production:"
    cat yu-ai-code-mother-frontend/.env.production | sed 's/^/    /'
fi

echo ""

# 检查Node.js版本
echo "Node.js版本检查:"
if command -v node &> /dev/null; then
    NODE_VERSION=$(node --version | cut -d'v' -f2)
    NODE_MAJOR=$(echo $NODE_VERSION | cut -d'.' -f1)
    if [ "$NODE_MAJOR" -ge 20 ]; then
        echo "  ✓ Node.js版本: $NODE_VERSION (符合要求 >=20.x)"
    else
        echo "  ⚠ Node.js版本: $NODE_VERSION (建议升级到20.x+)"
    fi
else
    echo "  ⚠ Node.js未安装 (需要20.x+版本)"
fi

# 检查npm版本
if command -v npm &> /dev/null; then
    NPM_VERSION=$(npm --version)
    echo "  npm版本: $NPM_VERSION"
fi

echo ""

# 检查Docker服务状态
if command -v docker-compose &> /dev/null; then
    echo "Docker服务状态:"
    if docker info > /dev/null 2>&1; then
        echo "  ✓ Docker服务运行正常"

        if [ -f "docker-compose.yml" ]; then
            echo "  容器状态:"
            docker-compose ps --services | while read service; do
                STATUS=$(docker-compose ps -q "$service" | xargs docker inspect --format='{{.State.Status}}' 2>/dev/null || echo "未运行")
                echo "    $service: $STATUS"
            done
        fi
    else
        echo "  ✗ Docker服务未运行"
    fi
else
    echo "  ⚠ docker-compose 未安装"
fi

echo ""
echo "当前环境建议:"
if [ -f ".env" ] && grep -q "SPRING_PROFILES_ACTIVE=local" .env; then
    echo "  环境: 本地开发模式"
    echo "  建议操作: ./start-local-dev.sh"
elif [ -f ".env" ] && grep -q "SPRING_PROFILES_ACTIVE=docker" .env; then
    echo "  环境: 本地部署模式"
    echo "  建议操作: ./start-local-deploy.sh"
else
    echo "  环境: 未知"
    echo "  建议操作: ./env-switch.sh local-dev 或 ./env-switch.sh local-deploy"
fi

echo "==========================================="