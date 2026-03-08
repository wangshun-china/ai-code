#!/bin/bash
# 启动本地开发环境

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "==========================================="
echo "启动本地开发环境"
echo "==========================================="

# 检查环境是否已切换为本地开发
if [ ! -f ".env" ]; then
    echo "错误: .env 文件不存在，请先运行: ./env-switch.sh local-dev"
    exit 1
fi

echo "1. 启动Docker基础设施 (MySQL, Redis, Nacos)..."
echo "   如果已运行，将跳过启动"

# 检查Docker是否运行
if ! docker info > /dev/null 2>&1; then
    echo "错误: Docker服务未运行，请启动Docker"
    exit 1
fi

# 启动基础设施服务（如果未运行）
if ! docker-compose ps mysql 2>/dev/null | grep -q "Up"; then
    echo "   - 启动MySQL..."
    docker-compose up -d mysql
    echo "     等待MySQL初始化..."
    sleep 10
else
    echo "   - MySQL 已运行"
fi

if ! docker-compose ps redis 2>/dev/null | grep -q "Up"; then
    echo "   - 启动Redis..."
    docker-compose up -d redis
    sleep 5
else
    echo "   - Redis 已运行"
fi

if ! docker-compose ps nacos 2>/dev/null | grep -q "Up"; then
    echo "   - 启动Nacos..."
    docker-compose up -d nacos
    echo "     等待Nacos初始化 (约60秒)..."
    sleep 60
else
    echo "   - Nacos 已运行"
fi

echo ""
echo "2. 基础设施状态:"
echo "   MySQL:   localhost:3306"
echo "   Redis:   localhost:6379"
echo "   Nacos:   http://localhost:8848/nacos (账号: nacos, 密码: nacos)"
echo ""
echo "3. 检查微服务..."
echo "   验证主应用服务是否运行在端口8126..."
if command -v curl &> /dev/null; then
    if curl -s http://localhost:8126/api/actuator/health > /dev/null 2>&1; then
        echo "   ✓ 主应用服务运行正常 (端口8126)"
    else
        echo "   ✗ 主应用服务未运行在端口8126"
        echo "     请启动微服务后再访问前端"
    fi
else
    echo "   ? 无法检查服务状态，请确保微服务已启动"
fi

echo ""
echo "4. 启动应用服务..."
echo ""
echo "请按以下步骤启动应用:"
echo ""
echo "=== 启动微服务 ==="
echo "1. 打开新的终端窗口"
echo "2. 运行: cd \"$SCRIPT_DIR\""
echo "3. 选择一种方式启动:"
echo "   - IDE: 分别启动各微服务应用:"
echo "     * 用户服务: YuAiCodeUserApplication (端口8124)"
echo "     * 主应用: YuAiCodeAppApplication (端口8126)"
echo "     * 截图服务: YuAiCodeScreenshotApplication (端口8127)"
echo "   - Maven: 分别启动各微服务:"
echo "     * mvn spring-boot:run -pl yu-ai-code-mother-microservice/yu-ai-code-user"
echo "     * mvn spring-boot:run -pl yu-ai-code-mother-microservice/yu-ai-code-app"
echo "     * mvn spring-boot:run -pl yu-ai-code-mother-microservice/yu-ai-code-screenshot"
echo "4. 各服务将在以下端口启动:"
echo "   - 用户服务: http://localhost:8124"
echo "   - 主应用: http://localhost:8126"
echo "   - 截图服务: http://localhost:8127"
echo ""
echo "=== 启动前端开发服务器 ==="
echo "1. 打开另一个终端窗口"
echo "2. 运行: cd \"$SCRIPT_DIR/yu-ai-code-mother-frontend\""
echo "3. 安装依赖 (如果需要): npm install"
echo "4. 运行: npm run dev"
echo "5. 前端将在: http://localhost:5173 启动"
echo ""
echo "=== 验证服务 ==="
echo "- 主应用健康检查: curl http://localhost:8126/api/actuator/health"
echo "- 用户服务健康检查: curl http://localhost:8124/api/actuator/health"
echo "- 截图服务健康检查: curl http://localhost:8127/api/actuator/health"
echo "- 前端访问: http://localhost:5173"
echo "- Nacos控制台: http://localhost:8848/nacos"
echo ""
echo "=== 停止基础设施 ==="
echo "docker-compose stop mysql redis nacos"
echo "或停止所有: docker-compose down"
echo ""
echo "==========================================="
echo "本地开发环境启动完成!"
echo "请按照上述步骤启动后端和前端应用"
echo "==========================================="