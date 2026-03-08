#!/bin/bash
set -e

# 默认Nacos版本，如果.env中没有定义，使用此版本
DEFAULT_NACOS_VERSION="v2.0.4"

# 尝试加载 .env 文件中的 NACOS_VERSION
if [ -f .env ]; then
  source .env
fi

# 如果 NACOS_VERSION 未设置，使用默认值
NACOS_VERSION=${NACOS_VERSION:-$DEFAULT_NACOS_VERSION}

# 清理版本号（移除开头的v和-slim后缀）
CLEAN_VERSION=${NACOS_VERSION#v}
CLEAN_VERSION=${CLEAN_VERSION%-*}

SCHEMA_URL="https://raw.githubusercontent.com/alibaba/nacos/develop/distribution/conf/mysql-schema.sql"

TARGET_DIR="./mysql-init"
VERSIONED_FILE="${TARGET_DIR}/${CLEAN_VERSION}-mysql-schema.sql"
FINAL_FILE="${TARGET_DIR}/mysql-schema.sql"

echo "🔧 初始化Nacos数据库..."
echo "📦 Nacos版本: ${NACOS_VERSION}"
echo "📁 目标目录: ${TARGET_DIR}"

# 创建目录
mkdir -p "${TARGET_DIR}"

# 下载 schema 文件
echo "⬇️  下载Nacos MySQL schema (版本: ${CLEAN_VERSION})..."
echo "📥 从URL下载: ${SCHEMA_URL}"

if ! curl -sSL "$SCHEMA_URL" -o "${VERSIONED_FILE}"; then
  echo "❌ 下载schema文件失败，请检查网络连接"
  echo "💡 提示: 确保可以访问GitHub (https://raw.githubusercontent.com)"
  exit 1
fi

# 校验下载
if [ ! -s "${VERSIONED_FILE}" ]; then
  echo "❌ 下载的schema文件为空，请检查Nacos版本"
  exit 1
fi

# 拷贝为标准文件名供 MySQL 初始化使用
cp "${VERSIONED_FILE}" "${FINAL_FILE}"

# 删除原始版本号文件
rm -f "${VERSIONED_FILE}"

echo "✅ 下载完成: ${FINAL_FILE}"
echo "📊 文件大小: $(wc -l < "${FINAL_FILE}") 行"
echo ""
echo "📝 下一步:"
echo "1. 启动MySQL容器时，将挂载此文件到 /docker-entrypoint-initdb.d/"
echo "2. 确保在docker-compose.yml中配置了正确的卷挂载"
echo "3. 重新启动MySQL容器以应用schema"
