<template>
  <div class="app-card" :class="{ 'app-card--featured': featured }" @mouseenter="handleMouseEnter" @mouseleave="handleMouseLeave">
    <div class="app-preview" :style="cardStyle">
      <img v-if="app.cover" :src="app.cover" :alt="app.appName" />
      <div v-else class="app-placeholder">
        <span class="placeholder-icon">🤖</span>
        <div class="placeholder-bg"></div>
      </div>
      <div class="shine-effect" v-if="showShine"></div>
      <div class="app-overlay">
        <a-space>
          <a-button type="primary" @click="handleViewChat" class="overlay-btn">查看对话</a-button>
          <a-button v-if="app.deployKey" type="default" @click="handleViewWork" class="overlay-btn">查看作品</a-button>
        </a-space>
      </div>
    </div>
    <div class="app-info">
      <div class="app-info-left">
        <a-avatar :src="app.user?.userAvatar" :size="44" class="user-avatar">
          {{ app.user?.userName?.charAt(0) || 'U' }}
        </a-avatar>
      </div>
      <div class="app-info-right">
        <h3 class="app-title">{{ app.appName || '未命名应用' }}</h3>
        <p class="app-author">
          <span class="author-icon">👤</span>
          {{ app.user?.userName || (featured ? '官方' : '未知用户') }}
        </p>
      </div>
      <div v-if="featured" class="featured-badge">
        <span>精选</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

interface Props {
  app: API.AppVO
  featured?: boolean
}

interface Emits {
  (e: 'view-chat', appId: string | number | undefined): void
  (e: 'view-work', app: API.AppVO): void
}

const props = withDefaults(defineProps<Props>(), {
  featured: false,
})

const emit = defineEmits<Emits>()

const handleViewChat = () => {
  emit('view-chat', props.app.id)
}

const handleViewWork = () => {
  emit('view-work', props.app)
}

// 3D 悬停效果
const cardStyle = ref({})
const showShine = ref(false)

const handleMouseEnter = (e: MouseEvent) => {
  showShine.value = true
}

const handleMouseLeave = () => {
  cardStyle.value = {}
  showShine.value = false
}
</script>

<style scoped>
.app-card {
  background: var(--glass-bg);
  border-radius: var(--border-radius);
  overflow: hidden;
  box-shadow: var(--glass-shadow);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  transition: var(--transition);
  cursor: pointer;
  position: relative;
}

.app-card:hover {
  transform: translateY(-8px);
  box-shadow: var(--glass-shadow-hover);
}

.app-card--featured {
  border: 1px solid rgba(102, 126, 234, 0.3);
}

.app-card--featured::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--primary-gradient);
  z-index: 10;
}

.app-preview {
  height: 180px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  position: relative;
}

.app-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s ease;
}

.app-card:hover .app-preview img {
  transform: scale(1.05);
}

.app-placeholder {
  font-size: 48px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

.placeholder-icon {
  position: relative;
  z-index: 2;
  animation: bounce 2s ease-in-out infinite;
}

@keyframes bounce {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

.placeholder-bg {
  position: absolute;
  width: 120px;
  height: 120px;
  background: var(--primary-gradient);
  border-radius: 50%;
  opacity: 0.15;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
    opacity: 0.15;
  }
  50% {
    transform: scale(1.1);
    opacity: 0.25;
  }
}

.shine-effect {
  position: absolute;
  top: 0;
  left: -100%;
  width: 50%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
  animation: shine 0.8s ease-out;
  z-index: 5;
}

@keyframes shine {
  0% {
    left: -100%;
  }
  100% {
    left: 150%;
  }
}

.app-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.85) 0%, rgba(118, 75, 162, 0.85) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.app-card:hover .app-overlay {
  opacity: 1;
}

.overlay-btn {
  border-radius: 20px;
  font-weight: 500;
  transition: var(--transition);
}

.overlay-btn:hover {
  transform: scale(1.05);
}

.app-info {
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
}

.app-info-left {
  flex-shrink: 0;
}

.user-avatar {
  border: 2px solid transparent;
  background: var(--primary-gradient);
  padding: 2px;
}

.app-info-right {
  flex: 1;
  min-width: 0;
}

.app-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 4px;
  color: #1a1a2e;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-author {
  font-size: 13px;
  color: #888;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: flex;
  align-items: center;
  gap: 4px;
}

.author-icon {
  font-size: 12px;
}

.featured-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  background: var(--primary-gradient);
  color: white;
  font-size: 11px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

/* 响应式 */
@media (max-width: 480px) {
  .app-preview {
    height: 150px;
  }

  .app-info {
    padding: 12px 16px;
  }
}
</style>