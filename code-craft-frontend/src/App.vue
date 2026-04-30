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
  --primary-subtle: rgba(201, 100, 66, 0.12);
  --primary-gradient: none;
  --secondary-gradient: none;

  --parchment: #f5f4ed;
  --ivory: #faf9f5;
  --warm-sand: #e8e6dc;
  --near-black: #26382f;
  --dark-surface: #e3eee7;
  --charcoal-warm: #42584c;
  --olive-gray: #5e5d59;
  --stone-gray: #87867f;
  --warm-silver: #b0aea5;
  --border-cream: #f0eee6;
  --border-warm: #e8e6dc;
  --ring-warm: #d1cfc5;
  --error-crimson: #b53333;
  --focus-blue: #3898ec;

  --neutral-50: #faf9f5;
  --neutral-100: #f5f4ed;
  --neutral-200: #e8e6dc;
  --neutral-300: #d1cfc5;
  --neutral-400: #b0aea5;
  --neutral-500: #87867f;
  --neutral-600: #5e5d59;
  --neutral-700: #4d4c48;
  --neutral-800: #42584c;
  --neutral-900: #26382f;

  --bg-primary: var(--ivory);
  --bg-secondary: var(--parchment);
  --bg-tertiary: var(--warm-sand);
  --text-primary: var(--near-black);
  --text-secondary: var(--olive-gray);
  --text-tertiary: var(--stone-gray);
  --text-inverse: var(--ivory);
  --border-light: var(--border-cream);
  --border-medium: var(--border-warm);
  --border-focus: var(--focus-blue);

  --shadow-sm: 0 0 0 1px var(--border-cream);
  --shadow-md: 0 0 0 1px var(--ring-warm);
  --shadow-lg: rgba(20, 20, 19, 0.05) 0 4px 24px, 0 0 0 1px var(--border-warm);
  --shadow-xl: rgba(20, 20, 19, 0.08) 0 16px 56px, 0 0 0 1px var(--border-warm);
  --glass-bg: rgba(250, 249, 245, 0.9);
  --glass-shadow: rgba(20, 20, 19, 0.05) 0 4px 24px, 0 0 0 1px var(--border-cream);

  --font-serif: Georgia, 'Times New Roman', serif;
  --font-sans: 'Avenir Next', 'Trebuchet MS', 'Helvetica Neue', sans-serif;
  --font-mono: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;

  --radius-sm: 6px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --radius-xl: 16px;
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
  background:
    radial-gradient(circle at 10% 12%, rgba(201, 100, 66, 0.08), transparent 28%),
    radial-gradient(circle at 88% 2%, rgba(94, 93, 89, 0.08), transparent 26%),
    var(--parchment);
  color: var(--text-primary);
  line-height: 1.6;
  overflow-x: hidden;
}

p, span, div, li, td, th, label {
  color: inherit;
}

h1, h2, h3, h4, h5, h6 {
  color: var(--text-primary);
  font-family: var(--font-serif);
  font-weight: 500;
  letter-spacing: -0.01em;
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
  border-radius: var(--radius-lg);
  font-weight: 500;
  transition: all var(--transition-fast);
  box-shadow: 0 0 0 1px var(--ring-warm);
}

.ant-btn:not(.ant-btn-primary):not(.ant-btn-text):not(.ant-btn-link) {
  background: var(--warm-sand);
  border-color: transparent;
  color: var(--charcoal-warm);
}

.ant-btn-primary {
  background-color: var(--primary);
  border-color: var(--primary);
  color: var(--ivory);
  box-shadow: 0 0 0 1px var(--primary);
}

.ant-btn-primary:hover {
  background-color: var(--primary-dark);
  border-color: var(--primary-dark);
  color: var(--ivory);
}

.ant-input,
.ant-input-textarea,
.ant-input-affix-wrapper {
  border-radius: var(--radius-lg);
  border-color: var(--border-medium);
  background: var(--ivory);
  transition: all var(--transition-fast);
}

.ant-input:focus,
.ant-input-textarea:focus,
.ant-input-affix-wrapper-focused {
  border-color: var(--border-focus);
  box-shadow: 0 0 0 3px rgba(56, 152, 236, 0.14);
}

.ant-card,
.ant-modal-content {
  border-radius: var(--radius-xl);
  border: 1px solid var(--border-light);
  background: var(--ivory);
  box-shadow: var(--shadow-sm);
}

.ant-modal-header {
  background: transparent;
}

.ant-modal-title,
.ant-card-head-title {
  color: var(--near-black);
  font-family: var(--font-serif);
  font-weight: 500;
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
