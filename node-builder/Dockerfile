FROM node:20-alpine

WORKDIR /app
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories

# 安装必要的工具
RUN apk add --no-cache git

# 复制package.json和构建脚本
COPY package*.json ./
COPY index.js .

# 安装依赖
RUN npm install --production --registry=https://registry.npmmirror.com

# 健康检查端点
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:3000/health || exit 1

EXPOSE 3000

CMD ["node", "index.js"]