import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// 生产/部署环境配置（微服务架构）
// 微服务架构：不同API路由代理到不同服务
export default defineConfig({
  plugins: [vue(), vueDevTools()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      // 1. 用户相关的请求 -> 转发给 User 服务 (端口 8124)
      '/api/user': {
        target: 'http://localhost:8124',
        changeOrigin: true,
        secure: false,
      },
      // 2. 截图相关的请求 -> 转发给 Screenshot 服务 (端口 8127)
      '/api/screenshot': {
        target: 'http://localhost:8127',
        changeOrigin: true,
        secure: false,
      },
      // 3. 其他所有 API 请求 (比如代码生成) -> 转发给 App 核心服务 (端口 8126)
      '/api': {
        target: 'http://localhost:8126',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})