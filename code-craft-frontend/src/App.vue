<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayout.vue'
</script>

<template>
  <BasicLayout />
</template>

<style>
:root {
  --primary: #c96442;
  --primary-light: #d97757;
  --primary-dark: #a84f33;
  --primary-subtle: rgba(201, 100, 66, 0.10);
  --primary-gradient: none;
  --secondary-gradient: none;

  --parchment: #f5f5f3;
  --ivory: #ffffff;
  --warm-sand: #f7f7f5;
  --near-black: #1d1d1f;
  --dark-surface: #2c2c2e;
  --charcoal-warm: #3a3a3c;
  --olive-gray: #6e6e73;
  --stone-gray: #86868b;
  --warm-silver: #aeaeb2;
  --border-cream: rgba(0, 0, 0, 0.06);
  --border-warm: rgba(0, 0, 0, 0.10);
  --ring-warm: rgba(0, 0, 0, 0.08);
  --error-crimson: #b53333;
  --focus-blue: #3898ec;

  --neutral-50: #ffffff;
  --neutral-100: #f5f5f3;
  --neutral-200: #e8e8e6;
  --neutral-300: #d1d1cf;
  --neutral-400: #aeaeb2;
  --neutral-500: #86868b;
  --neutral-600: #6e6e73;
  --neutral-700: #3a3a3c;
  --neutral-800: #2c2c2e;
  --neutral-900: #1d1d1f;

  --bg-primary: var(--ivory);
  --bg-secondary: var(--parchment);
  --bg-tertiary: var(--warm-sand);
  --text-primary: var(--near-black);
  --text-secondary: var(--olive-gray);
  --text-tertiary: var(--stone-gray);
  --text-inverse: #ffffff;
  --border-light: var(--border-cream);
  --border-medium: var(--border-warm);
  --border-focus: var(--focus-blue);

  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.04);
  --shadow-md: 0 2px 8px rgba(0, 0, 0, 0.06);
  --shadow-lg: 0 1px 2px rgba(0, 0, 0, 0.04), 0 8px 24px rgba(0, 0, 0, 0.06);
  --shadow-xl: 0 2px 4px rgba(0, 0, 0, 0.04), 0 16px 48px rgba(0, 0, 0, 0.08);
  --glass-bg: rgba(255, 255, 255, 0.92);
  --glass-shadow: 0 1px 2px rgba(0, 0, 0, 0.04), 0 8px 24px rgba(0, 0, 0, 0.06);

  --font-serif: Georgia, 'Times New Roman', serif;
  --font-sans: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', 'Helvetica Neue', 'Arial', sans-serif;
  --font-mono: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;

  --radius-sm: 6px;
  --radius-md: 8px;
  --radius-lg: 14px;
  --radius-xl: 20px;
  --radius-full: 9999px;
  --transition-fast: 150ms ease;
  --transition-base: 250ms ease;
  --transition-slow: 350ms ease;
}

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
  font-family: var(--font-sans);
  background: var(--parchment);
  color: var(--text-primary);
  line-height: 1.5;
  overflow-x: hidden;
}

p, span, div, li, td, th, label {
  color: inherit;
}

h1, h2, h3, h4, h5, h6 {
  color: var(--text-primary);
  font-family: var(--font-sans);
  font-weight: 600;
  letter-spacing: -0.025em;
}

.ant-typography {
  color: var(--text-primary);
}

.ant-form-item-label > label {
  color: var(--charcoal-warm);
  font-weight: 500;
}

.ant-input::placeholder,
.ant-input-textarea::placeholder {
  color: var(--stone-gray);
}

::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: var(--ring-warm);
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: var(--stone-gray);
}

::selection {
  background-color: var(--primary-subtle);
  color: var(--near-black);
}

a {
  color: var(--primary);
  text-decoration: none;
  transition: color var(--transition-fast);
}

a:hover {
  color: var(--primary-dark);
}

.ant-btn {
  border-radius: var(--radius-md);
  font-weight: 500;
  transition: all var(--transition-fast);
}

.ant-btn:not(.ant-btn-primary):not(.ant-btn-text):not(.ant-btn-link) {
  background: #ffffff;
  border-color: rgba(0, 0, 0, 0.12);
  color: var(--charcoal-warm);
}

.ant-btn-primary {
  background-color: var(--primary);
  border-color: var(--primary);
  color: #ffffff;
}

.ant-btn-primary:hover {
  background-color: var(--primary-dark);
  border-color: var(--primary-dark);
  color: #ffffff;
}

.ant-input,
.ant-input-textarea,
.ant-input-affix-wrapper {
  border-radius: var(--radius-md);
  border-color: rgba(0, 0, 0, 0.12);
  background: #ffffff;
  transition: all var(--transition-fast);
}

.ant-input:focus,
.ant-input-textarea:focus,
.ant-input-affix-wrapper-focused {
  border-color: var(--border-focus);
  box-shadow: 0 0 0 3px rgba(56, 152, 236, 0.12);
}

.ant-card,
.ant-modal-content {
  border-radius: var(--radius-xl);
  border: 1px solid rgba(0, 0, 0, 0.06);
  background: #ffffff;
  box-shadow: var(--shadow-lg);
}

.ant-modal-header {
  background: transparent;
}

.ant-modal-title,
.ant-card-head-title {
  color: var(--near-black);
  font-weight: 600;
}

.ant-menu {
  border-bottom: none;
  background: transparent;
}

.ant-pagination-item,
.ant-alert,
.ant-tag {
  border-radius: var(--radius-md);
}
</style>
