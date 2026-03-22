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
          <span>CraftAI</span>
        </div>
        <p class="brand-tagline">让创意触手可及</p>
      </div>

      <div class="footer-links">
        <a href="https://github.com/xixi-box" target="_blank" rel="noopener" class="footer-link">
          <GithubOutlined />
          <span>GitHub</span>
        </a>
      </div>

      <div class="footer-bottom">
        <p class="copyright">© 2024 CraftAI. All rights reserved.</p>
      </div>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { GithubOutlined } from '@ant-design/icons-vue'
</script>

<style scoped>
.footer {
  background-color: var(--bg-primary);
  border-top: 1px solid var(--border-light);
  padding: 48px 24px 24px;
  margin-top: 80px;
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
  color: var(--primary);
}

.brand-logo span {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.brand-tagline {
  font-size: 14px;
  color: var(--text-tertiary);
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
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
}

.footer-link:hover {
  color: var(--primary);
  background-color: var(--primary-subtle);
}

.footer-link :deep(.anticon) {
  font-size: 18px;
}

.footer-bottom {
  padding-top: 24px;
  border-top: 1px solid var(--border-light);
}

.copyright {
  font-size: 13px;
  color: var(--text-tertiary);
  margin: 0;
}
</style>
