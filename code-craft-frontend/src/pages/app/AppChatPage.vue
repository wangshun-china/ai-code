<template>
  <div id="appChatPage">
    <!-- 顶部栏 -->
    <div class="header-bar">
      <div class="header-left">
        <h1 class="app-name">{{ appInfo?.appName || '网站生成器' }}</h1>
        <a-tag v-if="appInfo?.codeGenType" color="blue" class="code-gen-type-tag">
          {{ formatCodeGenType(appInfo.codeGenType) }}
        </a-tag>
        <a-select
          v-if="isOwner"
          class="model-select"
          size="small"
          :value="appInfo?.modelKey || DEFAULT_AI_MODEL"
          :options="AI_MODEL_OPTIONS"
          :loading="modelSwitching"
          :disabled="isGenerating || isPlanning || modelSwitching"
          @change="handleModelChange"
        />
      </div>
      <div class="header-right">
        <a-button type="default" class="header-btn" @click="showAppDetail">
          <template #icon><InfoCircleOutlined /></template>
          应用详情
        </a-button>
        <a-button
          type="primary"
          ghost
          class="header-btn"
          @click="downloadCode"
          :loading="downloading"
          :disabled="!isOwner"
        >
          <template #icon><DownloadOutlined /></template>
          下载代码
        </a-button>
        <a-button type="primary" class="header-btn primary" @click="deployApp" :loading="deploying">
          <template #icon><CloudUploadOutlined /></template>
          部署
        </a-button>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="main-content">
      <div class="workspace-shell" ref="workspaceShell">
        <!-- 左侧源码区域 -->
        <div class="source-section" :style="{ width: `calc(${sourcePanelWidth}% - 6px)` }">
          <div class="source-header">
            <h3>源码工作区</h3>
            <div class="source-actions">
              <a-button type="link" size="small" @click="loadSourceFiles" :loading="sourceFilesLoading">
                刷新
              </a-button>
              <a-button v-if="openSourceTabs.length > 0" type="link" size="small" @click="closeAllSourceTabs">
                清空 Tab
              </a-button>
            </div>
          </div>
          <div class="source-workspace">
            <div class="source-tree">
              <a-empty
                v-if="!sourceFilesLoading && fileTreeData.length === 0"
                description="暂无生成文件"
                :image="undefined"
              />
              <a-spin v-else-if="sourceFilesLoading" />
              <a-tree
                v-else
                :tree-data="fileTreeData"
                :selected-keys="activeSourcePath ? [activeSourcePath] : []"
                default-expand-all
                block-node
                @select="handleFileSelect"
              />
            </div>
            <div class="source-editor">
              <div v-if="openSourceTabs.length > 0" class="source-tabs">
                <button
                  v-for="file in openSourceTabs"
                  :key="file.path"
                  class="source-tab"
                  :class="{ active: file.path === activeSourcePath }"
                  @click="activeSourcePath = file.path"
                >
                  <span>{{ file.name }}</span>
                  <span class="source-tab-close" @click.stop="closeSourceTab(file.path)">×</span>
                </button>
              </div>
              <div v-if="openSourceTabs.length > 0" class="code-panel">
                <div class="code-path">{{ activeSourceFile?.path }}</div>
                <pre class="code-view"><code>{{ activeSourceFile?.content }}</code></pre>
              </div>
              <div v-else class="code-empty">
                <div class="code-empty-title">选择一个文件查看源码</div>
                <p>左侧文件树会展示当前应用生成目录内的文本文件。</p>
              </div>
            </div>
          </div>
        </div>

        <div class="workspace-resizer" @mousedown="startResize" title="拖动调整源码和预览宽度"></div>

        <!-- 右侧网页展示区域 -->
        <div class="preview-section" :style="{ width: `calc(${100 - sourcePanelWidth}% - 6px)` }">
          <div class="preview-header">
            <h3>生成后的网页展示</h3>
            <div class="preview-actions">
              <a-button
                v-if="isOwner && previewUrl"
                type="link"
                :danger="isEditMode"
                @click="toggleEditMode"
                :class="{ 'edit-mode-active': isEditMode }"
                class="edit-btn"
              >
                <template #icon><EditOutlined /></template>
                {{ isEditMode ? '退出编辑' : '编辑模式' }}
              </a-button>
              <a-button v-if="previewUrl" type="link" @click="openInNewTab" class="open-btn">
                <template #icon><ExportOutlined /></template>
                新窗口打开
              </a-button>
            </div>
          </div>
          <div class="preview-content">
            <div v-if="!previewUrl && !isGenerating" class="preview-placeholder">
              <div class="placeholder-icon">🌐</div>
              <p>网站文件生成完成后将在这里展示</p>
            </div>
            <div v-else-if="isGenerating" class="preview-loading">
              <a-spin size="large" />
              <p>正在生成网站...</p>
            </div>
            <div v-else class="iframe-wrapper">
              <iframe
                :src="previewUrl"
                class="preview-iframe"
                frameborder="0"
                @load="onIframeLoad"
              ></iframe>
            </div>
          </div>
        </div>
      </div>

      <button
        v-if="chatMode === 'minimized'"
        class="chat-minimized-pill"
        type="button"
        @click="restoreChat"
      >
        <span>AI 对话</span>
        <small>{{ isGenerating ? '生成中...' : '点击展开' }}</small>
      </button>

      <div v-else class="chat-float" :class="chatWindowClass">
        <div class="chat-window-header">
          <div>
            <div class="chat-window-title">AI 对话</div>
            <small>{{ chatMode === 'maximized' ? '全屏专注模式' : '悬浮工作模式' }}</small>
          </div>
          <div class="chat-window-actions">
            <a-button type="text" size="small" @click="toggleChatMaximize">
              {{ chatMode === 'maximized' ? '还原' : '最大化' }}
            </a-button>
            <a-button type="text" size="small" @click="minimizeChat">最小化</a-button>
          </div>
        </div>

        <div class="chat-section">
          <!-- 消息区域 -->
          <div class="messages-container" ref="messagesContainer">
            <div v-if="hasMoreHistory" class="load-more-container">
              <a-button type="link" @click="loadMoreHistory" :loading="loadingHistory" size="small">
                加载更多历史消息
              </a-button>
            </div>
            <div v-for="(message, index) in messages" :key="index" class="message-item">
              <div v-if="message.type === 'user'" class="user-message">
                <div class="message-content">{{ message.content }}</div>
                <div class="message-avatar user-avatar">
                  <a-avatar :src="loginUserStore.loginUser.userAvatar" />
                </div>
              </div>
              <div v-else class="ai-message">
                <div class="message-avatar ai-avatar">
                  <a-avatar :src="aiAvatar" />
                </div>
                <div class="message-content">
                  <MarkdownRenderer v-if="message.content" :content="message.content" />
                  <div v-if="message.loading" class="loading-indicator">
                    <a-spin size="small" />
                    <span>{{ isPlanning ? 'AI 正在生成方案...' : 'AI 正在思考...' }}</span>
                  </div>
                  <div v-if="message.planPending" class="plan-actions">
                    <a-button
                      type="primary"
                      size="small"
                      :loading="isGenerating"
                      @click="confirmGenerationPlan(message)"
                    >
                      确认生成
                    </a-button>
                    <span>不满意可以继续补充需求，我会重新出方案。</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 选中元素信息展示 -->
          <a-alert
            v-if="selectedElementInfo"
            class="selected-element-alert"
            type="info"
            closable
            @close="clearSelectedElement"
          >
            <template #message>
              <div class="selected-element-info">
                <div class="element-header">
                  <span class="element-tag">选中元素：{{ selectedElementInfo.tagName.toLowerCase() }}</span>
                  <span v-if="selectedElementInfo.id" class="element-id">#{{ selectedElementInfo.id }}</span>
                  <span v-if="selectedElementInfo.className" class="element-class">
                    .{{ selectedElementInfo.className.split(' ').join('.') }}
                  </span>
                </div>
                <div class="element-details">
                  <div v-if="selectedElementInfo.textContent" class="element-item">
                    内容: {{ selectedElementInfo.textContent.substring(0, 50) }}
                    {{ selectedElementInfo.textContent.length > 50 ? '...' : '' }}
                  </div>
                  <div v-if="selectedElementInfo.pagePath" class="element-item">
                    页面路径: {{ selectedElementInfo.pagePath }}
                  </div>
                  <div class="element-item">
                    选择器: <code class="element-selector-code">{{ selectedElementInfo.selector }}</code>
                  </div>
                </div>
              </div>
            </template>
          </a-alert>

          <!-- 用户消息输入框 -->
          <div class="input-container">
            <div class="input-wrapper">
              <a-tooltip v-if="!isOwner" title="无法在别人的作品下对话哦~" placement="top">
                <a-textarea
                  v-model:value="userInput"
                  :placeholder="getInputPlaceholder()"
                  :rows="4"
                  :maxlength="1000"
                  @keydown.enter.exact.prevent="handleSendMessage"
                  :disabled="isGenerating || isPlanning || !isOwner"
                  class="chat-input"
                />
              </a-tooltip>
              <a-textarea
                v-else
                v-model:value="userInput"
                :placeholder="getInputPlaceholder()"
                :rows="4"
                :maxlength="1000"
                @keydown.enter.exact.prevent="handleSendMessage"
                :disabled="isGenerating || isPlanning"
                class="chat-input"
              />
              <div class="input-actions">
                <a-segmented
                  v-if="isOwner"
                  v-model:value="messageMode"
                  :options="messageModeOptions"
                  size="small"
                  class="message-mode-switch"
                  :disabled="isGenerating || isPlanning || !!selectedElementInfo"
                />
                <a-upload
                  v-if="isOwner"
                  :show-upload-list="false"
                  :custom-request="handleAttachmentUpload"
                  accept=".png,.jpg,.jpeg,.webp,.gif,.pdf,.docx,.txt,.md,.markdown,.json,.csv,.html,.css,.js,.ts,.vue"
                  :disabled="isGenerating || isPlanning || attachmentUploading"
                >
                  <a-button
                    type="default"
                    shape="circle"
                    class="attachment-upload-btn"
                    :loading="attachmentUploading"
                    :disabled="isGenerating || isPlanning"
                  >
                    <template #icon><PaperClipOutlined /></template>
                  </a-button>
                </a-upload>
                <a-button
                  type="primary"
                  @click="handleSendMessage"
                  :loading="isGenerating || isPlanning"
                  :disabled="!isOwner"
                  class="send-btn"
                >
                  <template #icon><SendOutlined /></template>
                </a-button>
              </div>
            </div>
            <div class="input-hint">Enter 发送，Shift + Enter 换行</div>
            <div v-if="appAttachments.length > 0" class="attachment-list">
              <a-tag
                v-for="attachment in appAttachments"
                :key="attachment.id"
                closable
                class="attachment-tag"
                @close="(event: MouseEvent) => handleAttachmentClose(event, attachment.id)"
              >
                <PaperClipOutlined />
                <span>{{ attachment.fileName }}</span>
                <small>{{ attachment.parseStatus === 'success' ? '已解析' : attachment.parseStatus }}</small>
              </a-tag>
            </div>
            <div v-else-if="isOwner" class="attachment-hint">
              可上传课程大纲、教案、实验指导、设计稿或文本，让 AI 按附件生成页面
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 应用详情弹窗 -->
    <AppDetailModal
      v-model:open="appDetailVisible"
      :app="appInfo"
      :show-actions="isOwner || isAdmin"
      @edit="editApp"
      @delete="deleteApp"
    />

    <!-- 部署成功弹窗 -->
    <DeploySuccessModal
      v-model:open="deployModalVisible"
      :deploy-url="deployUrl"
      @open-site="openDeployedSite"
    />

    <transition name="deploy-terminal-fade">
      <div v-if="deployTerminalVisible" class="deploy-terminal">
        <div class="deploy-terminal-header">
          <span>部署终端</span>
          <a-tag color="processing">{{ deployTask?.status || 'running' }}</a-tag>
        </div>
        <div class="deploy-terminal-body">
          <div v-for="(line, index) in deployLogs" :key="index" class="deploy-terminal-line">
            <span class="terminal-prompt">$</span>
            <span>{{ line }}</span>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { formatCodeGenType } from '@/utils/codeGenTypes'
import { AI_MODEL_OPTIONS, DEFAULT_AI_MODEL } from '@/utils/aiModels'
import { VisualEditor, type ElementInfo } from '@/utils/visualEditor'
import { useAppChat } from '@/composables/useAppChat'

import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import DeploySuccessModal from '@/components/DeploySuccessModal.vue'
import aiAvatar from '@/assets/aiAvatar.png'

import {
  CloudUploadOutlined,
  SendOutlined,
  ExportOutlined,
  InfoCircleOutlined,
  DownloadOutlined,
  EditOutlined,
  PaperClipOutlined,
} from '@ant-design/icons-vue'

const loginUserStore = useLoginUserStore()

const {
  appInfo,
  messages,
  userInput,
  isGenerating,
  isPlanning,
  messageMode,
  messagesContainer,
  appAttachments,
  attachmentUploading,
  modelSwitching,
  loadingHistory,
  hasMoreHistory,
  previewUrl,
  previewReady,
  sourceFiles,
  sourceFilesLoading,
  openSourceTabs,
  activeSourcePath,
  activeSourceFile,
  deploying,
  deployModalVisible,
  deployUrl,
  deployTerminalVisible,
  deployLogs,
  deployTask,
  downloading,
  appDetailVisible,
  isOwner,
  isAdmin,
  showAppDetail,
  loadMoreHistory,
  fetchAppInfo,
  sendMessage,
  confirmGenerationPlan,
  uploadAttachment,
  deleteAttachment,
  switchModel,
  loadSourceFiles,
  openSourceFile,
  closeSourceTab,
  closeAllSourceTabs,
  downloadCode,
  deployApp,
  openInNewTab,
  openDeployedSite,
  editApp,
  deleteApp,
} = useAppChat()

const isEditMode = ref(false)
const selectedElementInfo = ref<ElementInfo | null>(null)
const workspaceShell = ref<HTMLElement>()
const sourcePanelWidth = ref(46)
const messageModeOptions = [
  { label: '聊天', value: 'chat' },
  { label: '改代码', value: 'generate' },
]
type ChatMode = 'floating' | 'maximized' | 'minimized'
const chatMode = ref<ChatMode>('floating')
const visualEditor = new VisualEditor({
  onElementSelected: (elementInfo: ElementInfo) => {
    selectedElementInfo.value = elementInfo
    messageMode.value = 'generate'
  },
})

const chatWindowClass = computed(() => ({
  'chat-float-floating': chatMode.value === 'floating',
  'chat-float-maximized': chatMode.value === 'maximized',
}))

const fileTreeData = computed(() => {
  const mapNode = (file: API.AppSourceFileNodeVO): any => ({
    title: file.name,
    key: file.path,
    isLeaf: !file.directory,
    fileNode: file,
    children: file.children?.map(mapNode),
  })
  return sourceFiles.value.map(mapNode)
})

const handleFileSelect = (_selectedKeys: string[], info: any) => {
  const fileNode = info?.node?.fileNode as API.AppSourceFileNodeVO | undefined
  if (fileNode && !fileNode.directory) {
    openSourceFile(fileNode)
  }
}

const handleModelChange = (value: unknown) => {
  if (typeof value === 'string') {
    switchModel(value)
  }
}

const toggleChatMaximize = () => {
  chatMode.value = chatMode.value === 'maximized' ? 'floating' : 'maximized'
}

const minimizeChat = () => {
  chatMode.value = 'minimized'
}

const restoreChat = () => {
  chatMode.value = 'floating'
}

const stopResize = () => {
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  window.removeEventListener('mousemove', handleResizeMove)
  window.removeEventListener('mouseup', stopResize)
}

const handleResizeMove = (event: MouseEvent) => {
  const shell = workspaceShell.value
  if (!shell) {
    return
  }
  const rect = shell.getBoundingClientRect()
  const nextWidth = ((event.clientX - rect.left) / rect.width) * 100
  sourcePanelWidth.value = Math.min(72, Math.max(28, nextWidth))
}

const startResize = (event: MouseEvent) => {
  if (window.innerWidth <= 1024) {
    return
  }
  event.preventDefault()
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  window.addEventListener('mousemove', handleResizeMove)
  window.addEventListener('mouseup', stopResize)
}

const handleSendMessage = async () => {
  await sendMessage(selectedElementInfo.value)
  if (selectedElementInfo.value) {
    clearSelectedElement()
    if (isEditMode.value) {
      toggleEditMode()
    }
  }
}

const handleAttachmentUpload = (options: any) => {
  uploadAttachment(options)
}

const handleAttachmentClose = (event: MouseEvent, attachmentId?: number) => {
  event.preventDefault()
  deleteAttachment(attachmentId)
}

const onIframeLoad = () => {
  previewReady.value = true
  const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement
  if (iframe) {
    visualEditor.init(iframe)
    visualEditor.onIframeLoad()
  }
}

const toggleEditMode = () => {
  const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement
  if (!iframe || !previewReady.value) {
    return
  }
  const newEditMode = visualEditor.toggleEditMode()
  isEditMode.value = newEditMode
}

const clearSelectedElement = () => {
  selectedElementInfo.value = null
  visualEditor.clearSelection()
}

const getInputPlaceholder = () => {
  if (selectedElementInfo.value) {
    return `正在编辑 ${selectedElementInfo.value.tagName.toLowerCase()} 元素，描述您想要的修改...`
  }
  if (messageMode.value === 'chat') {
    return '先聊需求、问方案或讨论当前应用；聊天模式不会修改代码'
  }
  return '描述要生成或修改的代码，发送后会进入方案确认/生成流程'
}

const handleWindowMessage = (event: MessageEvent) => {
  visualEditor.handleIframeMessage(event)
}

onMounted(() => {
  fetchAppInfo()
  window.addEventListener('message', handleWindowMessage)
})

onUnmounted(() => {
  window.removeEventListener('message', handleWindowMessage)
  stopResize()
})
</script>

<style scoped>
#appChatPage {
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 16px;
  background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
}

/* 顶部栏 */
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--glass-bg);
  border-radius: var(--border-radius-sm);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  margin-bottom: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.code-gen-type-tag {
  font-size: 12px;
  border-radius: 12px;
}

.model-select {
  min-width: 210px;
}

.app-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.header-right {
  display: flex;
  gap: 12px;
}

.header-btn {
  border-radius: 20px;
  font-weight: 500;
  transition: var(--transition);
}

.header-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
}

.header-btn.primary {
  background: var(--primary-gradient);
  border: none;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
}

/* 主要内容区域 */
.main-content {
  flex: 1;
  display: flex;
  gap: 16px;
  overflow: hidden;
}

/* 左侧对话区域 */
.chat-section {
  flex: 2;
  display: flex;
  flex-direction: column;
  background: var(--glass-bg);
  border-radius: var(--border-radius);
  box-shadow: var(--glass-shadow);
  overflow: hidden;
}

.messages-container {
  flex: 0.9;
  padding: 16px;
  overflow-y: auto;
  scroll-behavior: smooth;
}

.message-item {
  margin-bottom: 16px;
}

.user-message {
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  gap: 10px;
}

.ai-message {
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  gap: 10px;
}

.message-content {
  max-width: 70%;
  padding: 14px 18px;
  border-radius: 18px;
  line-height: 1.6;
  word-wrap: break-word;
}

.user-message .message-content {
  background: var(--primary-gradient);
  color: white;
  border-bottom-right-radius: 4px;
}

.ai-message .message-content {
  background: #f1f5f9;
  color: #000000;
  border-bottom-left-radius: 4px;
  font-weight: 400;
}

.message-avatar {
  flex-shrink: 0;
}

.user-avatar :deep(.ant-avatar) {
  background: var(--primary-gradient);
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

.ai-avatar :deep(.ant-avatar) {
  background: var(--secondary-gradient);
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.3);
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #333333;
  font-weight: 500;
}

.plan-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(20, 20, 19, 0.08);
}

.plan-actions span {
  color: #5f5b52;
  font-size: 13px;
}

.load-more-container {
  text-align: center;
  padding: 8px 0;
  margin-bottom: 16px;
}

/* 输入区域 */
.input-container {
  padding: 16px;
  background: white;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.input-wrapper {
  position: relative;
}

.chat-input {
  border-radius: var(--border-radius-sm);
  border: 2px solid #e8e8e8;
  padding-right: 60px;
  transition: var(--transition);
}

.chat-input:focus {
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.input-actions {
  position: absolute;
  bottom: 10px;
  right: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.message-mode-switch {
  padding: 2px;
  border: 1px solid var(--border-warm);
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 0 0 1px var(--ring-warm);
}

.input-hint {
  margin-top: 6px;
  padding-right: 4px;
  color: var(--muted-warm);
  font-size: 12px;
  text-align: right;
}

.send-btn,
.attachment-upload-btn {
  width: 44px;
  height: 44px;
  border-radius: 50%;
}

.send-btn {
  background: var(--primary-gradient);
  border: none;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
  transition: var(--transition);
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.1);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.attachment-upload-btn {
  border-color: var(--border-warm);
  color: var(--charcoal-warm);
  background: var(--ivory);
  box-shadow: 0 0 0 1px var(--ring-warm);
}

.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.attachment-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 5px 8px;
  border-radius: 999px;
  background: var(--warm-sand);
  border-color: var(--ring-warm);
  color: var(--charcoal-warm);
}

.attachment-tag span {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-tag small {
  color: var(--muted-warm);
}

.attachment-hint {
  margin-top: 8px;
  color: var(--muted-warm);
  font-size: 12px;
}

/* 右侧预览区域 */
.preview-section {
  flex: 3;
  display: flex;
  flex-direction: column;
  background: var(--glass-bg);
  border-radius: var(--border-radius);
  box-shadow: var(--glass-shadow);
  overflow: hidden;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  background: rgba(255, 255, 255, 0.5);
}

.preview-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1a1a2e;
}

.preview-actions {
  display: flex;
  gap: 8px;
}

.edit-btn, .open-btn {
  border-radius: 16px;
  transition: var(--transition);
}

.edit-mode-active {
  background-color: #52c41a !important;
  border-color: #52c41a !important;
  color: white !important;
}

.preview-content {
  flex: 1;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
}

.preview-placeholder, .preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #333333;
}

.placeholder-icon {
  font-size: 56px;
  margin-bottom: 16px;
  animation: float 3s ease-in-out infinite;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-15px); }
}

.preview-loading p {
  margin-top: 16px;
  font-size: 15px;
  color: #333333;
  font-weight: 500;
}

.iframe-wrapper {
  width: 100%;
  height: 100%;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  margin: 12px;
  width: calc(100% - 24px);
  height: calc(100% - 24px);
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
  background: white;
  border-radius: 8px;
}

.source-section {
  flex: 1.1;
  display: flex;
  flex-direction: column;
  background: var(--glass-bg);
  border-radius: var(--border-radius);
  box-shadow: var(--glass-shadow);
  overflow: hidden;
  min-width: 220px;
}

.source-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.source-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.source-tree {
  flex: 1;
  padding: 10px;
  overflow: auto;
}

.source-tabs {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  overflow-x: auto;
  background: rgba(255, 255, 255, 0.55);
}

.source-tab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 180px;
  padding: 7px 10px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: #333333;
  font-size: 13px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.source-tab span:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-tab.active {
  background: white;
  border-color: #e8e8e8;
}

.source-tab-close {
  color: #888888;
  font-size: 14px;
  line-height: 1;
}

.source-tab-close:hover {
  color: #b53333;
}

.code-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #141413;
}

.code-path {
  padding: 10px 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  color: #b0aea5;
  font-family: var(--font-mono);
  font-size: 12px;
}

.code-view {
  flex: 1;
  margin: 0;
  padding: 18px;
  overflow: auto;
  color: #f5f4ed;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.selected-element-alert {
  margin: 0 16px;
  border-radius: var(--border-radius-sm);
  border: none;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.08) 0%, rgba(118, 75, 162, 0.08) 100%);
}

.selected-element-info {
  line-height: 1.5;
}

.element-header {
  margin-bottom: 8px;
}

.element-details {
  margin-top: 8px;
}

.element-item {
  margin-bottom: 4px;
  font-size: 13px;
  color: #333333;
  font-weight: 500;
}

.element-tag {
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 14px;
  font-weight: 600;
  color: #667eea;
}

.element-id { color: #10b981; margin-left: 4px; }
.element-class { color: #f59e0b; margin-left: 4px; }

.element-selector-code {
  font-family: 'Monaco', 'Menlo', monospace;
  background: rgba(102, 126, 234, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
  color: #667eea;
}

.deploy-terminal {
  position: fixed;
  right: 28px;
  bottom: 28px;
  z-index: 1100;
  width: min(520px, calc(100vw - 32px));
  max-height: 320px;
  overflow: hidden;
  border-radius: 16px;
  background: #07111f;
  box-shadow: 0 20px 60px rgba(7, 17, 31, 0.35);
  border: 1px solid rgba(148, 163, 184, 0.28);
}

.deploy-terminal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  color: #e2e8f0;
  font-weight: 700;
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.96), rgba(30, 41, 59, 0.9));
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
}

.deploy-terminal-body {
  max-height: 260px;
  overflow-y: auto;
  padding: 14px;
  font-family: 'JetBrains Mono', 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
  line-height: 1.7;
  color: #cbd5e1;
}

.deploy-terminal-line {
  display: flex;
  gap: 8px;
  word-break: break-word;
}

.terminal-prompt {
  color: #38bdf8;
  flex: 0 0 auto;
}

.deploy-terminal-fade-enter-active,
.deploy-terminal-fade-leave-active {
  transition: opacity 0.24s ease, transform 0.24s ease;
}

.deploy-terminal-fade-enter-from,
.deploy-terminal-fade-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.98);
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .main-content {
    flex-direction: column;
  }

  .chat-section, .source-section, .preview-section {
    flex: none;
    height: 50vh;
  }

  .source-section {
    height: 36vh;
  }
}

@media (max-width: 768px) {
  .header-bar {
    padding: 12px;
    flex-wrap: wrap;
    gap: 12px;
  }

  .app-name {
    font-size: 16px;
  }

  .header-btn {
    font-size: 13px;
    padding: 4px 12px;
  }

  .message-content {
    max-width: 85%;
  }
}

/* Claude-inspired visual layer */
#appChatPage {
  background:
    radial-gradient(circle at 8% 8%, rgba(201, 100, 66, 0.08), transparent 24%),
    var(--parchment);
  color: var(--near-black);
}

.header-bar,
.chat-section,
.source-section,
.preview-section {
  background: var(--ivory);
  border: 1px solid var(--border-cream);
  box-shadow: var(--shadow-lg);
}

.header-bar {
  border-radius: 24px;
}

.app-name {
  font-family: var(--font-serif);
  font-size: 24px;
  font-weight: 500;
  background: none;
  -webkit-text-fill-color: var(--near-black);
  color: var(--near-black);
}

.code-gen-type-tag {
  background: var(--warm-sand);
  border-color: var(--ring-warm);
  color: var(--charcoal-warm);
}

.header-btn {
  border-radius: var(--radius-lg);
}

.header-btn.primary {
  background: var(--primary);
  border: 1px solid var(--primary);
  box-shadow: 0 0 0 1px var(--primary);
}

.header-btn:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

.chat-section,
.source-section,
.preview-section {
  border-radius: 28px;
}

.chat-section {
  flex: 1.45;
}

.source-section {
  flex: 0.85;
  background: var(--near-black);
  border-color: var(--dark-surface);
  min-width: 230px;
}

.source-header {
  background: var(--dark-surface);
  border-bottom: 1px solid rgba(250, 249, 245, 0.08);
}

.source-header h3 {
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 500;
  color: var(--ivory);
}

.source-tree {
  color: var(--warm-silver);
}

.source-tree :deep(.ant-tree) {
  background: transparent;
  color: var(--warm-silver);
}

.source-tree :deep(.ant-tree-node-content-wrapper) {
  border-radius: 8px;
}

.source-tree :deep(.ant-tree-node-content-wrapper:hover),
.source-tree :deep(.ant-tree-node-selected) {
  background: var(--dark-surface) !important;
  color: var(--ivory) !important;
}

.preview-section {
  flex: 2.35;
}

.preview-header {
  background: rgba(232, 230, 220, 0.42);
  border-bottom: 1px solid var(--border-cream);
}

.source-tabs {
  background: var(--warm-sand);
  border-bottom: 1px solid var(--border-cream);
}

.source-tab {
  color: var(--charcoal-warm);
}

.source-tab.active {
  background: var(--ivory);
  border-color: var(--ring-warm);
}

.preview-header h3 {
  font-family: var(--font-serif);
  font-size: 22px;
  font-weight: 500;
  color: var(--near-black);
}

.messages-container {
  background: var(--ivory);
}

.message-content {
  border-radius: 18px;
  line-height: 1.65;
}

.user-message .message-content {
  background: var(--primary);
  color: var(--ivory);
  box-shadow: 0 0 0 1px var(--primary);
}

.ai-message .message-content {
  background: var(--parchment);
  color: var(--near-black);
  border: 1px solid var(--border-cream);
}

.user-avatar :deep(.ant-avatar),
.ai-avatar :deep(.ant-avatar) {
  background: var(--warm-sand);
  color: var(--charcoal-warm);
  box-shadow: 0 0 0 1px var(--ring-warm);
}

.input-container {
  background: var(--ivory);
  border-top: 1px solid var(--border-cream);
}

.chat-input {
  border: 1px solid var(--border-warm);
  border-radius: 18px;
  background: var(--parchment);
  padding-right: 112px;
}

.chat-input:focus {
  border-color: var(--focus-blue);
  box-shadow: 0 0 0 3px rgba(56, 152, 236, 0.14);
}

.send-btn {
  background: var(--primary);
  border: 1px solid var(--primary);
  box-shadow: 0 0 0 1px var(--primary);
}

.preview-content,
.preview-placeholder,
.preview-loading {
  background: var(--parchment);
}

.iframe-wrapper {
  border-radius: 20px;
  box-shadow: var(--shadow-lg);
  background: var(--ivory);
}

.preview-iframe {
  border-radius: 16px;
}

.selected-element-alert {
  background: var(--warm-sand);
  border: 1px solid var(--ring-warm);
}

.element-tag,
.element-selector-code {
  color: var(--primary-dark);
}

.deploy-terminal {
  background: var(--near-black);
  border-color: var(--dark-surface);
  box-shadow: rgba(20, 20, 19, 0.22) 0 18px 60px;
}

.deploy-terminal-header {
  background: var(--dark-surface);
  color: var(--ivory);
}

.terminal-prompt {
  color: var(--primary-light);
}

/* Floating chat + resizable source/preview workbench */
#appChatPage {
  position: relative;
  overflow: hidden;
}

.main-content {
  position: relative;
  display: block;
  min-height: 0;
}

.workspace-shell {
  height: 100%;
  display: flex;
  gap: 0;
  min-height: 0;
}

.workspace-resizer {
  width: 12px;
  flex: 0 0 12px;
  cursor: col-resize;
  position: relative;
}

.workspace-resizer::before {
  content: '';
  position: absolute;
  top: 18px;
  bottom: 18px;
  left: 50%;
  width: 2px;
  transform: translateX(-50%);
  border-radius: 999px;
  background: var(--ring-warm);
  opacity: 0.75;
  transition: width 0.2s ease, opacity 0.2s ease;
}

.workspace-resizer:hover::before {
  width: 4px;
  opacity: 1;
}

.source-section,
.preview-section {
  flex: none !important;
  height: 100%;
  min-width: 0;
}

.source-section {
  min-width: 0;
}

.source-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.source-workspace {
  flex: 1;
  display: flex;
  min-height: 0;
}

.source-tree {
  flex: 0 0 240px;
  width: 240px;
  min-width: 180px;
  border-right: 1px solid rgba(250, 249, 245, 0.08);
}

.source-editor {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #141413;
}

.source-tabs {
  flex: 0 0 auto;
}

.code-panel {
  flex: 1;
  min-height: 0;
  height: auto;
}

.code-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  color: var(--warm-silver);
  text-align: center;
  background:
    radial-gradient(circle at 50% 35%, rgba(250, 249, 245, 0.06), transparent 30%),
    #141413;
}

.code-empty-title {
  margin-bottom: 8px;
  color: var(--ivory);
  font-family: var(--font-serif);
  font-size: 22px;
}

.code-empty p {
  max-width: 320px;
  margin: 0;
  line-height: 1.7;
}

.chat-float {
  position: absolute;
  z-index: 40;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--ivory);
  border: 1px solid var(--border-cream);
  box-shadow: 0 28px 80px rgba(20, 20, 19, 0.24);
  transition:
    width 0.24s ease,
    height 0.24s ease,
    inset 0.24s ease,
    transform 0.24s ease,
    border-radius 0.24s ease;
}

.chat-float-floating {
  top: 50%;
  left: 50%;
  width: min(680px, calc(100vw - 48px));
  height: min(560px, calc(100vh - 170px));
  transform: translate(-50%, -50%);
  border-radius: 30px;
}

.chat-float-maximized {
  inset: 0;
  width: auto;
  height: auto;
  transform: none;
  border-radius: 28px;
}

.chat-window-header {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  background: rgba(232, 230, 220, 0.5);
  border-bottom: 1px solid var(--border-cream);
}

.chat-window-title {
  color: var(--near-black);
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 500;
}

.chat-window-header small {
  color: var(--muted-warm);
}

.chat-window-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.chat-float .chat-section {
  flex: 1 !important;
  height: auto !important;
  min-height: 0;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.chat-float .messages-container {
  flex: 1;
  min-height: 0;
}

.chat-float .input-container {
  flex: 0 0 auto;
}

.chat-minimized-pill {
  position: absolute;
  left: 50%;
  bottom: 18px;
  z-index: 45;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 12px 18px;
  border: 1px solid var(--border-cream);
  border-radius: 999px;
  color: var(--near-black);
  background: var(--ivory);
  box-shadow: 0 18px 50px rgba(20, 20, 19, 0.18);
  cursor: pointer;
  transform: translateX(-50%);
}

.chat-minimized-pill span {
  font-family: var(--font-serif);
  font-size: 18px;
}

.chat-minimized-pill small {
  color: var(--muted-warm);
}

@media (max-width: 1024px) {
  .workspace-shell {
    flex-direction: column;
    gap: 12px;
  }

  .workspace-resizer {
    display: none;
  }

  .source-section,
  .preview-section {
    width: 100% !important;
    flex: none !important;
  }

  .source-section {
    height: 45%;
  }

  .preview-section {
    height: 55%;
  }

  .source-tree {
    flex-basis: 220px;
    width: 220px;
  }
}

@media (max-width: 768px) {
  .source-workspace {
    flex-direction: column;
  }

  .source-tree {
    flex: 0 0 170px;
    width: 100%;
    border-right: 0;
    border-bottom: 1px solid rgba(250, 249, 245, 0.08);
  }

  .chat-float-floating {
    inset: auto 12px 12px 12px;
    width: auto;
    height: 58vh;
    transform: none;
    border-radius: 24px;
  }

  .chat-float-maximized {
    inset: 0;
  }

  .chat-window-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .chat-minimized-pill {
    right: 16px;
    left: 16px;
    justify-content: center;
    transform: none;
  }
}
</style>
