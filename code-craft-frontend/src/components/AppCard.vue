<template>
  <div class="app-card" :class="{ 'app-card--featured': featured }" @click="handleViewChat">
    <div class="card-preview">
      <img v-if="app.cover" :src="app.cover" :alt="app.appName" />
      <div v-else class="preview-placeholder">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
      <div v-if="featured" class="featured-tag">
        <StarFilled />
        <span>精选</span>
      </div>
    </div>

    <div class="card-content">
      <h3 class="app-name">{{ app.appName || '未命名应用' }}</h3>
      <div class="app-meta">
        <a-avatar :src="app.user?.userAvatar" :size="20" class="author-avatar">
          {{ app.user?.userName?.charAt(0) || 'U' }}
        </a-avatar>
        <span class="author-name">{{ app.user?.userName || '未知用户' }}</span>
      </div>
    </div>

    <div class="card-actions">
      <a-button type="primary" size="small" @click.stop="handleViewChat">
        查看
      </a-button>
      <a-button v-if="app.deployKey" size="small" @click.stop="handleViewWork">
        访问
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { StarFilled } from '@ant-design/icons-vue'

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
</script>

<style scoped>
.app-card {
  background: rgba(255, 255, 255, 0.86);
  border-radius: 18px;
  border: 1px solid rgba(90, 74, 58, 0.08);
  overflow: hidden;
  cursor: pointer;
  transition: all var(--transition-base);
  position: relative;
  box-shadow: 0 14px 30px rgba(76, 60, 42, 0.07);
  backdrop-filter: blur(10px);
}

.app-card:hover {
  border-color: rgba(180, 106, 61, 0.18);
  box-shadow: 0 18px 34px rgba(76, 60, 42, 0.1);
  transform: translateY(-2px);
}

.app-card--featured {
  border-color: rgba(180, 106, 61, 0.22);
}

.app-card--featured:hover {
  border-color: #b46a3d;
}

/* Preview */
.card-preview {
  aspect-ratio: 16 / 10;
  background:
    radial-gradient(circle at 20% 20%, rgba(180, 106, 61, 0.12), transparent 32%),
    linear-gradient(135deg, #f7f1e8 0%, #f3ece2 100%);
  position: relative;
  overflow: hidden;
}

.card-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--transition-slow);
}

.app-card:hover .card-preview img {
  transform: scale(1.03);
}

.preview-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #978a7d;
}

.preview-placeholder svg {
  width: 48px;
  height: 48px;
}

/* Featured Tag */
.featured-tag {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background-color: #b46a3d;
  color: #fffaf5;
  font-size: 12px;
  font-weight: 600;
  border-radius: var(--radius-full);
  box-shadow: var(--shadow-sm);
}

.featured-tag :deep(.anticon) {
  font-size: 12px;
}

/* Content */
.card-content {
  padding: 18px 18px 14px;
}

.app-name {
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 500;
  color: #2f2a24;
  margin: 0 0 10px;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.author-avatar {
  flex-shrink: 0;
  background-color: #b46a3d;
  color: #fffaf5;
  font-size: 11px;
}

.author-name {
  font-size: 13px;
  color: #7f7468;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Actions */
.card-actions {
  display: flex;
  gap: 8px;
  padding: 0 18px 18px;
  opacity: 0;
  transform: translateY(10px);
  transition: all var(--transition-fast);
}

.app-card:hover .card-actions {
  opacity: 1;
  transform: translateY(0);
}

.card-actions :deep(.ant-btn) {
  flex: 1;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 500;
}

.card-actions :deep(.ant-btn-primary) {
  background-color: #b46a3d;
  border-color: #b46a3d;
}

/* Mobile: always show actions */
@media (max-width: 768px) {
  .card-actions {
    opacity: 1;
    transform: none;
    padding-top: 8px;
    border-top: 1px solid var(--border-light);
  }
}
</style>
