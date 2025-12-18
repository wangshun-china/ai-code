import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), vueDevTools()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      // 1. /api/user 开头 → 转发到用户服务（8124）
      '/api/user': {
        target: 'http://localhost:8124',
        changeOrigin: true,
        secure: false,
      },
      //  2. /api/app 开头 → 转发到主应用（8125）
      '/api/app': {
        target: 'http://localhost:8125',
        changeOrigin: true,
        secure: false,
      },
      '/api': {
        target: 'http://localhost:8125',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})
