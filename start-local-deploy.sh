#!/bin/bash
# 启动本地部署环境（Docker Compose）
# 策略：精准分流构建 (Screenshot走代理，其他直连国内源)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "==========================================="
echo "🚀 启动本地部署 (最终修复版)"
echo "==========================================="

# 1. 环境检查
if [ ! -f ".env" ]; then
    echo "❌ 错误: .env 文件不存在"
    exit 1
fi

echo "1. 检查Docker服务..."
if ! docker info > /dev/null 2>&1; then
    echo "❌ 错误: Docker服务未运行"
    exit 1
fi

# 2. 网络配置 (基于刚才 ipconfig 的真实结果)
# ⚠️ 注意：如果你换了 Wi-Fi，这个 IP 可能会变，到时候记得改这里
HOST_IP="172.29.176.1"
PROXY_PORT=7897
PROXY_URL="http://${HOST_IP}:${PROXY_PORT}"

echo "2. 网络配置确认:"
echo "   👉 物理机 IP: ${HOST_IP}"
echo "   👉 代理地址: ${PROXY_URL}"

# 3. 清理环境
echo "3. 停止并清理旧容器..."
docker-compose down --remove-orphans

echo "4. 开始分步构建镜像..."

# -----------------------------------------------------------------
# 🔥 阶段一：构建需要翻墙的服务 (Screenshot)
# -----------------------------------------------------------------
echo "🏗️  [1/2] 正在构建 Screenshot 服务 (注入代理下载 Chrome)..."
# 使用 --no-cache 确保重新下载
docker-compose build --no-cache \
  --build-arg HTTP_PROXY="${PROXY_URL}" \
  --build-arg HTTPS_PROXY="${PROXY_URL}" \
  --build-arg http_proxy="${PROXY_URL}" \
  --build-arg https_proxy="${PROXY_URL}" \
  screenshot-service

if [ $? -ne 0 ]; then
    echo "❌ Screenshot 服务构建失败，请检查代理是否连通！"
    exit 1
fi

# -----------------------------------------------------------------
# 🔥 阶段二：构建走国内源的服务 (Node, Java, Frontend)
# -----------------------------------------------------------------
echo "🏗️  [2/2] 正在构建其他服务 (直连国内源，速度飞快)..."
# 注意：这里【不传】任何代理参数，让 node-builder 走 Dockerfile 里的淘宝源
docker-compose build \
  app-service user-service node-builder frontend

if [ $? -ne 0 ]; then
    echo "❌ 其他服务构建失败！"
    exit 1
fi

# 5. 启动服务
echo "5. 启动所有容器..."
docker-compose up -d --force-recreate

echo "==========================================="
echo "✅ 部署完成！"
echo "👉 前端访问: http://localhost"
echo "👉 Nacos:   http://localhost:8848/nacos"
echo "-------------------------------------------"
echo "查看日志: docker-compose logs -f"
echo "==========================================="