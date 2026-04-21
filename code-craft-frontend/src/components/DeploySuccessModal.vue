<template>
  <a-modal v-model:open="visible" title="部署成功" :footer="null" width="600px">
    <div class="deploy-success">
      <div class="success-icon">
        <CheckCircleOutlined style="color: #52c41a; font-size: 48px" />
      </div>
      <h3>网站部署成功！</h3>
      <p>你的网站已经成功部署，可以通过以下链接访问：</p>
      <div class="deploy-url">
        <a-input :value="deployUrl" readonly>
          <template #suffix>
            <a-button type="text" @click="handleCopyUrl">
              <CopyOutlined />
            </a-button>
          </template>
        </a-input>
      </div>
      <div class="deploy-actions">
        <a-button type="primary" @click="handleOpenSite">访问网站</a-button>
        <a-button @click="handleClose">关闭</a-button>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { message } from 'ant-design-vue'
import { CheckCircleOutlined, CopyOutlined } from '@ant-design/icons-vue'

interface Props {
  open: boolean
  deployUrl: string
}

interface Emits {
  (e: 'update:open', value: boolean): void
  (e: 'open-site'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const visible = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value),
})

const handleCopyUrl = async () => {
  try {
    // 尝试使用 Clipboard API（仅 HTTPS 或 localhost 可用）
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(props.deployUrl)
      message.success('链接已复制到剪贴板')
    } else {
      // HTTP 环境下使用备用方案
      const textArea = document.createElement('textarea')
      textArea.value = props.deployUrl
      textArea.style.position = 'fixed'
      textArea.style.left = '-9999px'
      document.body.appendChild(textArea)
      textArea.select()
      try {
        document.execCommand('copy')
        message.success('链接已复制到剪贴板')
      } catch (err) {
        message.warning('HTTP 环境下复制受限，请手动选择复制')
      }
      document.body.removeChild(textArea)
    }
  } catch (error) {
    console.error('复制失败：', error)
    message.warning('复制失败，请手动选择链接复制')
  }
}

const handleOpenSite = () => {
  emit('open-site')
}

const handleClose = () => {
  visible.value = false
}
</script>

<style scoped>
.deploy-success {
  text-align: center;
  padding: 24px;
}

.success-icon {
  margin-bottom: 16px;
}

.success-icon :deep(.anticon) {
  color: var(--primary) !important;
}

.deploy-success h3 {
  margin: 0 0 16px;
  font-family: var(--font-serif);
  font-size: 28px;
  font-weight: 500;
  color: var(--near-black);
}

.deploy-success p {
  margin: 0 0 24px;
  color: var(--olive-gray);
}

.deploy-url {
  margin-bottom: 24px;
  padding: 10px;
  background: var(--parchment);
  border: 1px solid var(--border-cream);
  border-radius: var(--radius-lg);
}

.deploy-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
