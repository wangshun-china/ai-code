#!/bin/bash
# 停止本地部署环境

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "==========================================="
echo "停止本地部署环境"
echo "==========================================="

if [ ! -f "docker-compose.yml" ]; then
    echo "错误: docker-compose.yml 文件不存在"
    exit 1
fi

echo "停止所有服务并清理..."
docker-compose down --remove-orphans

echo ""
echo "服务已停止"
echo "==========================================="