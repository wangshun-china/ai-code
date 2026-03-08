# 构建阶段
FROM node:20-alpine AS builder

WORKDIR /app

# 复制package文件
COPY package*.json ./

# 安装依赖
RUN npm install --no-audit --no-fund

# 复制源代码
COPY . .

# 构建生产版本
RUN npm run build-only

# 生产阶段 - 仅包含静态文件
FROM nginx:alpine

# 复制构建好的静态文件到nginx目录
COPY --from=builder /app/dist /usr/share/nginx/html/dist

# 复制自定义nginx配置（可选）
# COPY nginx.conf /etc/nginx/conf.d/default.conf

# 暴露端口
EXPOSE 80

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:80/dist || exit 1

# nginx在基础镜像中已启动，无需额外命令