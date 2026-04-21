FROM node:24-alpine

WORKDIR /app

# 使用阿里云镜像源
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories

# 安装必要的工具
RUN apk add --no-cache git

# 复制 package.json 和源码
COPY package*.json ./
COPY index.js ./

# 安装生产依赖
RUN npm install --production --registry=https://registry.npmmirror.com

# 创建代码输出目录
RUN mkdir -p /tmp/code_output

# 健康检查端点
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8020/health || exit 1

EXPOSE 8020

CMD ["node", "index.js"]
