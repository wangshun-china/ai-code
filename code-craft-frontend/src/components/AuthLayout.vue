<template>
  <div class="auth-page" :class="{ 'auth-page--mirror': mirror }">
    <div class="auth-container">
      <div class="decoration-panel">
        <div class="decoration-content">
          <div class="floating-shapes">
            <div class="shape shape-1"></div>
            <div class="shape shape-2"></div>
            <div class="shape shape-3"></div>
            <div class="shape shape-4"></div>
          </div>
          <div class="brand-section">
            <h2 class="brand-title">{{ brandTitle }}</h2>
            <p class="brand-desc">{{ brandDesc }}</p>
            <div class="feature-list">
              <div v-for="feature in features" :key="feature.label" class="feature-item">
                <component :is="feature.icon" class="feature-icon" />
                <span>{{ feature.label }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="form-panel">
        <div class="form-card">
          <div class="form-header">
            <h2 class="title">{{ title }}</h2>
            <div class="desc">{{ desc }}</div>
          </div>
          <slot />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

interface Feature {
  icon: Component
  label: string
}

withDefaults(defineProps<{
  title: string
  desc: string
  brandTitle: string
  brandDesc: string
  features: Feature[]
  mirror?: boolean
}>(), {
  mirror: false,
})
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--parchment);
  padding: 20px;
}

.auth-container {
  display: flex;
  width: 100%;
  max-width: 1000px;
  min-height: 600px;
  background: var(--ivory);
  border: 1px solid var(--border-cream);
  border-radius: 24px;
  overflow: hidden;
  box-shadow: var(--shadow-xl);
}

.decoration-panel {
  flex: 1;
  background: var(--near-black);
  position: relative;
  overflow: hidden;
  display: none;
}

.decoration-panel::after {
  content: '';
  position: absolute;
  width: 260px;
  height: 260px;
  left: -70px;
  bottom: -80px;
  border: 1px solid rgba(201, 100, 66, 0.38);
  border-radius: 45% 55% 50% 50%;
}

.auth-page--mirror .decoration-panel::after {
  left: auto;
  bottom: auto;
  right: -70px;
  top: -80px;
  border-radius: 52% 48% 44% 56%;
}

@media (min-width: 768px) {
  .decoration-panel {
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

.decoration-content {
  position: relative;
  z-index: 2;
  text-align: center;
  padding: 40px;
  color: var(--warm-silver);
}

.floating-shapes {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  overflow: hidden;
}

.shape {
  position: absolute;
  border-radius: 50%;
  animation: float 6s ease-in-out infinite;
  background: rgba(201, 100, 66, 0.16);
  border: 1px solid rgba(250, 249, 245, 0.08);
}

.shape-1 { width: 100px; height: 100px; top: 10%; left: 10%; animation-delay: 0s; }
.shape-2 { width: 150px; height: 150px; top: 60%; right: 10%; animation-delay: 2s; }
.shape-3 { width: 80px; height: 80px; bottom: 20%; left: 20%; animation-delay: 4s; }
.shape-4 { width: 60px; height: 60px; top: 30%; right: 30%; animation-delay: 1s; }

@keyframes float {
  0%, 100% { transform: translateY(0) rotate(0deg); }
  50% { transform: translateY(-20px) rotate(10deg); }
}

.brand-section { position: relative; z-index: 2; }

.brand-title {
  font-size: 32px;
  font-weight: 500;
  margin-bottom: 12px;
  color: var(--ivory);
  font-family: var(--font-serif);
}

.brand-desc {
  font-size: 16px;
  color: var(--warm-silver);
  margin-bottom: 40px;
}

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  align-items: center;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 24px;
  background: var(--dark-surface);
  color: var(--warm-silver);
  border: 1px solid rgba(250, 249, 245, 0.08);
  border-radius: 30px;
  transition: var(--transition);
}

.feature-item:hover {
  background: #3d3d3a;
  transform: translateX(4px);
}

.feature-icon { font-size: 20px; }

.form-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: var(--ivory);
}

.form-card { width: 100%; max-width: 380px; }

.form-header { text-align: center; margin-bottom: 32px; }

.title {
  font-size: 34px;
  font-weight: 500;
  margin-bottom: 8px;
  font-family: var(--font-serif);
  color: var(--near-black);
}

.desc {
  color: var(--olive-gray);
  font-size: 14px;
  font-weight: 500;
}

.auth-page :deep(.ant-input-affix-wrapper),
.auth-page :deep(.ant-input) {
  border-radius: 12px;
  border: 1px solid rgba(0, 0, 0, 0.10);
  transition: var(--transition);
}

.auth-page :deep(.ant-input-affix-wrapper:hover),
.auth-page :deep(.ant-input:hover) {
  border-color: var(--ring-warm);
}

.auth-page :deep(.ant-input-affix-wrapper-focused),
.auth-page :deep(.ant-input:focus) {
  border-color: var(--focus-blue);
  box-shadow: 0 0 0 3px rgba(56, 152, 236, 0.14);
}

.auth-page :deep(.auth-tips) {
  text-align: center;
  color: var(--olive-gray);
  font-size: 13px;
  margin-bottom: 16px;
  font-weight: 500;
}

.auth-page :deep(.auth-link) {
  color: var(--primary);
  font-weight: 500;
  text-decoration: none;
  transition: var(--transition);
}

.auth-page :deep(.auth-link:hover) {
  color: var(--primary-dark);
}

.auth-page :deep(.submit-btn) {
  width: 100%;
  height: 48px;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 500;
  background: var(--primary);
  box-shadow: 0 0 0 1px var(--primary);
  border: none;
  transition: var(--transition);
}

.auth-page :deep(.submit-btn:hover) {
  background: var(--primary-dark);
  box-shadow: 0 0 0 1px var(--primary-dark);
  transform: translateY(-2px);
}

.auth-page :deep(.input-icon) {
  color: var(--olive-gray);
}

@media (max-width: 768px) {
  .auth-container {
    max-width: 450px;
    min-height: auto;
  }
}
</style>
