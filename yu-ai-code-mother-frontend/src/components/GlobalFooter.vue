<template>
  <a-layout-footer class="footer">
    <div class="footer-content">
      <div class="footer-decoration">
        <div class="decoration-line"></div>
      </div>
      <p class="copyright">
        <a
          href="https://github.com/xixi-box"
          target="_blank"
          rel="noopener noreferrer"
          class="author-link"
        >
          <GithubOutlined class="github-icon" />
          <span>王顺 GitHub</span>
        </a>
      </p>
      <p class="footer-slogan">让创意触手可及</p>
    </div>
  </a-layout-footer>
</template>

<script setup lang="ts">
import { GithubOutlined } from '@ant-design/icons-vue'
</script>

<style scoped>
.footer {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.05) 0%, rgba(118, 75, 162, 0.05) 100%);
  backdrop-filter: blur(10px);
  text-align: center;
  padding: 30px 20px;
  margin-top: 40px;
  border-top: 1px solid rgba(102, 126, 234, 0.15);
  position: relative;
}

.footer-content {
  max-width: 600px;
  margin: 0 auto;
}

.footer-decoration {
  margin-bottom: 16px;
}

.decoration-line {
  height: 3px;
  width: 60px;
  margin: 0 auto;
  background: var(--primary-gradient);
  border-radius: 2px;
}

.copyright {
  margin: 0 0 8px;
}

.author-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #666;
  font-size: 14px;
  text-decoration: none;
  padding: 8px 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.6);
  transition: var(--transition);
}

.author-link:hover {
  color: #667eea;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.2);
}

.github-icon {
  font-size: 18px;
}

.footer-slogan {
  margin: 12px 0 0;
  font-size: 13px;
  color: #999;
  font-style: italic;
}
</style>