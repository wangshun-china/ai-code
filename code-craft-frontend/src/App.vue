<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayout.vue'
</script>

<template>
  <BasicLayout />
</template>

<style>
/* 全局 CSS 变量 - 现代化简洁风格 */
:root {
  /* 主色调 - 青绿色系，现代专业 */
  --primary: #0d9488;
  --primary-light: #14b8a6;
  --primary-dark: #0f766e;
  --primary-subtle: rgba(13, 148, 136, 0.08);

  /* 渐变色 */
  --primary-gradient: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  --secondary-gradient: linear-gradient(135deg, #10b981 0%, #059669 100%);

  /* 中性色 */
  --neutral-50: #fafafa;
  --neutral-100: #f5f5f5;
  --neutral-200: #e5e5e5;
  --neutral-300: #d4d4d4;
  --neutral-400: #a3a3a3;
  --neutral-500: #737373;
  --neutral-600: #525252;
  --neutral-700: #404040;
  --neutral-800: #262626;
  --neutral-900: #171717;

  /* 背景色 */
  --bg-primary: #ffffff;
  --bg-secondary: #fafafa;
  --bg-tertiary: #f3f4f6;

  /* 文字色 - 高对比度 */
  --text-primary: #000000;
  --text-secondary: #333333;
  --text-tertiary: #555555;
  --text-inverse: #ffffff;

  /* 边框 */
  --border-light: #e5e5e5;
  --border-medium: #d4d4d4;
  --border-focus: var(--primary);

  /* 阴影 */
  --shadow-sm: 0 1px 2px 0 rgb(0 0 0 / 0.05);
  --shadow-md: 0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1);
  --shadow-lg: 0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1);
  --shadow-xl: 0 20px 25px -5px rgb(0 0 0 / 0.1), 0 8px 10px -6px rgb(0 0 0 / 0.1);

  /* 玻璃态效果 */
  --glass-bg: rgba(255, 255, 255, 0.85);
  --glass-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);

  /* 圆角 */
  --radius-sm: 6px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --radius-xl: 16px;
  --radius-full: 9999px;

  /* 过渡 */
  --transition-fast: 150ms ease;
  --transition-base: 250ms ease;
  --transition-slow: 350ms ease;
}

/* 全局样式 */
* {
  box-sizing: border-box;
}

html {
  font-size: 16px;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

body {
  margin: 0;
  padding: 0;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  background-color: var(--bg-secondary);
  color: var(--text-primary);
  line-height: 1.6;
  overflow-x: hidden;
}

/* 确保所有文字都有高对比度 */
p, span, div, li, td, th, label {
  color: inherit;
}

h1, h2, h3, h4, h5, h6 {
  color: var(--text-primary);
  font-weight: 600;
}

/* 覆盖 Ant Design 的默认文字颜色 */
.ant-typography {
  color: var(--text-primary);
}

.ant-form-item-label > label {
  color: var(--text-primary);
  font-weight: 500;
}

.ant-input::placeholder {
  color: #888888;
}

/* 滚动条样式 - 简洁 */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: var(--neutral-300);
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: var(--neutral-400);
}

/* 选中文字样式 */
::selection {
  background-color: var(--primary-subtle);
  color: var(--primary-dark);
}

/* 链接基础样式 */
a {
  color: var(--primary);
  text-decoration: none;
  transition: color var(--transition-fast);
}

a:hover {
  color: var(--primary-dark);
}

/* 按钮基础样式覆盖 */
.ant-btn {
  border-radius: var(--radius-md);
  font-weight: 500;
  transition: all var(--transition-fast);
}

.ant-btn-primary {
  background-color: var(--primary);
  border-color: var(--primary);
}

.ant-btn-primary:hover {
  background-color: var(--primary-dark);
  border-color: var(--primary-dark);
}

/* 输入框样式 */
.ant-input,
.ant-input-textarea {
  border-radius: var(--radius-md);
  border-color: var(--border-light);
  transition: all var(--transition-fast);
}

.ant-input:focus,
.ant-input-textarea:focus {
  border-color: var(--border-focus);
  box-shadow: 0 0 0 3px rgba(13, 148, 136, 0.1);
}

/* 卡片样式 */
.ant-card {
  border-radius: var(--radius-lg);
  border-color: var(--border-light);
  box-shadow: var(--shadow-sm);
}

/* 菜单样式 */
.ant-menu {
  border-bottom: none;
}

/* 分页样式 */
.ant-pagination-item {
  border-radius: var(--radius-md);
}

/* Alert 样式 */
.ant-alert {
  border-radius: var(--radius-md);
}
</style>
