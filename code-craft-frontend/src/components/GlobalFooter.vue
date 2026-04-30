<template>
  <footer class="footer">
    <div class="footer-content">
      <div class="footer-brand">
        <div class="brand-logo">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2L2 7L12 12L22 7L12 2Z" fill="currentColor" fill-opacity="0.2"/>
            <path d="M2 17L12 22L22 17" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 12L12 17L22 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <span>EduCraft AI</span>
        </div>
        <p class="brand-tagline">让课程与内容更快成为可访问应用</p>
      </div>

      <div class="footer-links">
        <a href="https://github.com/xixi-box" target="_blank" rel="noopener" class="footer-link">
          <GithubOutlined />
          <span>GitHub</span>
        </a>
      </div>

      <div class="footer-bottom">
        <p class="copyright">© 2024 EduCraft AI. All rights reserved.</p>
      </div>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { GithubOutlined } from '@ant-design/icons-vue'
</script>

<style scoped>
.footer {
  background:
    linear-gradient(180deg, rgba(255, 253, 247, 0.96), rgba(232, 241, 235, 0.94)),
    var(--ivory);
  border-top: 1px solid rgba(38, 56, 47, 0.12);
  padding: 48px 24px 24px;
  margin-top: 0;
}

.footer-content {
  max-width: 1280px;
  margin: 0 auto;
  text-align: center;
}

.footer-brand {
  margin-bottom: 24px;
}

.brand-logo {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.brand-logo svg {
  width: 28px;
  height: 28px;
  color: var(--primary-light);
}

.brand-logo span {
  font-family: var(--font-serif);
  font-size: 22px;
  font-weight: 500;
  color: var(--near-black);
}

.brand-tagline {
  font-size: 14px;
  color: var(--olive-gray);
  margin: 0;
}

.footer-links {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-bottom: 32px;
}

.footer-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  color: var(--charcoal-warm);
  font-size: 14px;
  font-weight: 500;
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
}

.footer-link:hover {
  color: var(--primary-dark);
  background-color: #fffdf7;
}

.footer-link :deep(.anticon) {
  font-size: 18px;
}

.footer-bottom {
  padding-top: 24px;
  border-top: 1px solid rgba(38, 56, 47, 0.1);
}

.copyright {
  font-size: 13px;
  color: var(--stone-gray);
  margin: 0;
}
</style>
