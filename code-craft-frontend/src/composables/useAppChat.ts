import { ref, nextTick, computed, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  getAppVoById,
  deployApp as deployAppApi,
  deleteApp as deleteAppApi,
  getDeployTask,
  listAppSourceFiles,
  getAppSourceFileContent,
  generateAppPlan,
  uploadAppAttachment,
  listAppAttachments,
  deleteAppAttachment,
  updateApp,
  chatToAppMessage,
} from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import { CodeGenTypeEnum } from '@/utils/codeGenTypes'
import request from '@/request'
import { API_BASE_URL, getStaticPreviewUrl } from '@/config/env'

const AI_SLOW_REQUEST_TIMEOUT = 180000
const PLAN_TRIGGER_KEYWORDS = [
  '方案',
  '分析',
  '规划',
  '重新设计',
  '重构',
  '整体',
  '架构',
  '根据附件',
  '附件',
  '简历',
  '设计稿',
  'pdf',
  '文档',
]
export type AppChatMode = 'chat' | 'generate'

/**
 * 消息类型
 */
export interface Message {
  type: 'user' | 'ai'
  content: string
  loading?: boolean
  createTime?: string
  plan?: API.AppGenerationPlanVO
  planPending?: boolean
}

/**
 * 应用对话页面逻辑
 */
export function useAppChat() {
  const route = useRoute()
  const router = useRouter()
  const loginUserStore = useLoginUserStore()

  // 应用信息
  const appInfo = ref<API.AppVO>()
  const appId = ref<any>()

  // 对话相关
  const messages = ref<Message[]>([])
  const userInput = ref('')
  const isGenerating = ref(false)
  const isPlanning = ref(false)
  const messageMode = ref<AppChatMode>('chat')
  const messagesContainer = ref<HTMLElement>()
  const appAttachments = ref<API.AppAttachmentVO[]>([])
  const attachmentUploading = ref(false)
  const hasNewAttachmentContext = ref(false)
  const modelSwitching = ref(false)

  // 对话历史相关
  const loadingHistory = ref(false)
  const hasMoreHistory = ref(false)
  const lastCreateTime = ref<string>()
  const historyLoaded = ref(false)

  // 预览相关
  const previewUrl = ref('')
  const previewReady = ref(false)
  const sourceFiles = ref<API.AppSourceFileNodeVO[]>([])
  const sourceFilesLoading = ref(false)
  const openSourceTabs = ref<API.AppSourceFileContentVO[]>([])
  const activeSourcePath = ref<string>()
  const activeSourceFile = computed(() => {
    return openSourceTabs.value.find((file) => file.path === activeSourcePath.value)
  })

  // 部署相关
  const deploying = ref(false)
  const deployModalVisible = ref(false)
  const deployUrl = ref('')
  const deployTerminalVisible = ref(false)
  const deployLogs = ref<string[]>([])
  const deployTask = ref<API.AppDeployTaskVO>()
  const deployDisabled = computed(() => {
    const status = appInfo.value?.status
    return (
      deploying.value ||
      isGenerating.value ||
      isPlanning.value ||
      status === 'generating' ||
      status === 'building' ||
      status === 'deploying'
    )
  })
  let deployPollTimer: number | undefined

  // 下载相关
  const downloading = ref(false)

  // 应用详情相关
  const appDetailVisible = ref(false)

  // 权限相关
  const isOwner = computed(() => {
    return appInfo.value?.userId === loginUserStore.loginUser.id
  })

  const isAdmin = computed(() => {
    return loginUserStore.loginUser.userRole === 'admin'
  })

  // 显示应用详情
  const showAppDetail = () => {
    appDetailVisible.value = true
  }

  // 加载对话历史
  const loadChatHistory = async (isLoadMore = false) => {
    if (!appId.value || loadingHistory.value) return
    loadingHistory.value = true
    try {
      const params: API.listAppChatHistoryParams = {
        appId: appId.value,
        pageSize: 10,
      }
      if (isLoadMore && lastCreateTime.value) {
        params.lastCreateTime = lastCreateTime.value
      }
      const res = await listAppChatHistory(params)
      if (res.data.code === 0 && res.data.data) {
        const chatHistories = res.data.data.records || []
        if (chatHistories.length > 0) {
          const historyMessages: Message[] = chatHistories
            .map((chat) => ({
              type: (chat.messageType === 'user' ? 'user' : 'ai') as 'user' | 'ai',
              content: chat.message || '',
              createTime: chat.createTime,
            }))
            .reverse()
          if (isLoadMore) {
            messages.value.unshift(...historyMessages)
          } else {
            messages.value = historyMessages
          }
          lastCreateTime.value = chatHistories[chatHistories.length - 1]?.createTime
          hasMoreHistory.value = chatHistories.length === 10
        } else {
          hasMoreHistory.value = false
        }
        historyLoaded.value = true
      }
    } catch (error) {
      console.error('加载对话历史失败：', error)
      message.error('加载对话历史失败')
    } finally {
      loadingHistory.value = false
    }
  }

  // 加载更多历史消息
  const loadMoreHistory = async () => {
    await loadChatHistory(true)
  }

  // 获取应用信息
  const fetchAppInfo = async () => {
    const id = route.params.id as string
    if (!id) {
      message.error('应用ID不存在')
      router.push('/')
      return
    }

    appId.value = id

    try {
      const res = await getAppVoById({ id: id as unknown as number })
      if (res.data.code === 0 && res.data.data) {
        appInfo.value = res.data.data
        await loadChatHistory()
        if (messages.value.length >= 2) {
          updatePreview()
        }
        await loadSourceFiles()
        if (isOwner.value) {
          await loadAttachments()
        }
        if (
          appInfo.value.initPrompt &&
          isOwner.value &&
          messages.value.length === 0 &&
          historyLoaded.value
        ) {
          await sendInitialMessage(appInfo.value.initPrompt)
        }
      } else {
        message.error('获取应用信息失败')
        router.push('/')
      }
    } catch (error) {
      console.error('获取应用信息失败：', error)
      message.error('获取应用信息失败')
      router.push('/')
    }
  }

  // 生成代码 - 使用 EventSource 处理流式响应
  const generateCode = async (userMessage: string, aiMessageIndex: number, planId?: string) => {
    let eventSource: EventSource | null = null
    let streamCompleted = false

    try {
      const baseURL = request.defaults.baseURL || API_BASE_URL
      const params = new URLSearchParams({
        appId: appId.value || '',
        message: userMessage,
      })
      if (planId) {
        params.set('planId', planId)
      }
      const url = `${baseURL}/app/chat/gen/code?${params}`

      eventSource = new EventSource(url, { withCredentials: true })
      let fullContent = ''

      eventSource.onmessage = function (event) {
        if (streamCompleted) return
        try {
          const parsed = JSON.parse(event.data)
          const content = parsed.d
          if (content !== undefined && content !== null) {
            fullContent += content
            messages.value[aiMessageIndex].content = fullContent
            messages.value[aiMessageIndex].loading = false
            scrollToBottom()
          }
        } catch (error) {
          console.error('解析消息失败:', error)
          handleError(error, aiMessageIndex, fullContent)
        }
      }

      eventSource.addEventListener('done', function () {
        if (streamCompleted) return
        streamCompleted = true
        isGenerating.value = false
        eventSource?.close()
        setTimeout(async () => {
          await fetchAppInfo()
          updatePreview()
        }, 1000)
      })

      eventSource.addEventListener('business-error', function (event: MessageEvent) {
        if (streamCompleted) return
        try {
          const errorData = JSON.parse(event.data)
          console.error('SSE业务错误事件:', errorData)
          const errorMessage = errorData.message || '生成过程中出现错误'
          const currentContent = messages.value[aiMessageIndex].content || fullContent
          messages.value[aiMessageIndex].content = appendGenerationError(currentContent, errorMessage)
          messages.value[aiMessageIndex].loading = false
          message.error(errorMessage)
          streamCompleted = true
          isGenerating.value = false
          eventSource?.close()
          setTimeout(() => {
            void fetchAppInfo()
          }, 500)
        } catch (parseError) {
          console.error('解析错误事件失败:', parseError)
          handleError(new Error('服务器返回错误'), aiMessageIndex)
        }
      })

      eventSource.onerror = function () {
        if (streamCompleted || !isGenerating.value) return
        if (eventSource?.readyState === EventSource.CONNECTING) {
          streamCompleted = true
          isGenerating.value = false
          eventSource?.close()
          setTimeout(async () => {
            await fetchAppInfo()
            updatePreview()
          }, 1000)
        } else {
          handleError(new Error('SSE连接错误'), aiMessageIndex, fullContent)
        }
      }
    } catch (error) {
      console.error('创建 EventSource 失败：', error)
      handleError(error, aiMessageIndex)
    }
  }

  // 错误处理函数
  const handleError = (error: unknown, aiMessageIndex: number, existingContent = '') => {
    console.error('生成代码失败：', error)
    const currentContent = messages.value[aiMessageIndex].content || existingContent
    messages.value[aiMessageIndex].content = appendGenerationError(
      currentContent,
      '生成过程中出现了错误，请重试。',
    )
    messages.value[aiMessageIndex].loading = false
    message.error('生成失败，请重试')
    isGenerating.value = false
    setTimeout(() => {
      void fetchAppInfo()
    }, 500)
  }

  const appendGenerationError = (content: string, errorMessage: string) => {
    const errorContent = `❌ ${errorMessage}`
    return content ? `${content}\n\n${errorContent}` : errorContent
  }

  const requestGenerationPlan = async (messageContent: string) => {
    if (!appId.value || isPlanning.value || isGenerating.value) return
    isPlanning.value = true
    const aiMessageIndex = messages.value.length
    messages.value.push({ type: 'ai', content: '', loading: true })
    await nextTick()
    scrollToBottom()
    try {
      const res = await generateAppPlan({
        appId: appId.value as unknown as number,
        message: messageContent,
      }, {
        timeout: AI_SLOW_REQUEST_TIMEOUT,
      })
      if (res.data.code === 0 && res.data.data) {
        const plan = res.data.data
        messages.value[aiMessageIndex] = {
          type: 'ai',
          content: plan.plan || '方案生成完成，请确认是否开始生成代码。',
          loading: false,
          plan,
          planPending: true,
        }
        scrollToBottom()
      } else {
        throw new Error(res.data.message || '方案生成失败')
      }
    } catch (error) {
      console.error('生成方案失败：', error)
      const errorMessage = extractRequestErrorMessage(error, '方案生成失败，请重试。')
      messages.value[aiMessageIndex].content = `❌ ${errorMessage}`
      messages.value[aiMessageIndex].loading = false
      message.error(errorMessage)
    } finally {
      isPlanning.value = false
    }
  }

  const extractRequestErrorMessage = (error: unknown, fallback: string) => {
    const responseMessage = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
    if (responseMessage) {
      return responseMessage
    }
    if (error instanceof Error && error.message) {
      return error.message
    }
    return fallback
  }

  const confirmGenerationPlan = async (messageItem: Message) => {
    if (!messageItem.plan || isGenerating.value || isPlanning.value) return
    messageItem.planPending = false
    hasNewAttachmentContext.value = false
    const aiMessageIndex = messages.value.length
    messages.value.push({ type: 'ai', content: '', loading: true })
    await nextTick()
    scrollToBottom()
    isGenerating.value = true
    await generateCode(messageItem.plan.message || '', aiMessageIndex, messageItem.plan.planId)
  }

  // 发送初始消息
  const sendInitialMessage = async (prompt: string) => {
    messages.value.push({ type: 'user', content: prompt })
    await nextTick()
    scrollToBottom()
    await requestGenerationPlan(prompt)
  }

  const shouldRequestPlan = (messageContent: string, selectedElementInfo?: any) => {
    if (selectedElementInfo) {
      return false
    }
    const status = appInfo.value?.status
    if (!status || status === 'draft' || status === 'generate_failed') {
      return true
    }
    if (hasNewAttachmentContext.value) {
      return true
    }
    const normalizedMessage = messageContent.toLowerCase()
    return PLAN_TRIGGER_KEYWORDS.some((keyword) => normalizedMessage.includes(keyword.toLowerCase()))
  }

  const requestPlainChat = async (messageContent: string, aiMessageIndex: number) => {
    try {
      const res = await chatToAppMessage({
        appId: appId.value as unknown as number,
        message: messageContent,
      }, {
        timeout: AI_SLOW_REQUEST_TIMEOUT,
      })
      if (res.data.code === 0) {
        messages.value[aiMessageIndex].content = res.data.data || '已收到。'
        messages.value[aiMessageIndex].loading = false
        scrollToBottom()
      } else {
        throw new Error(res.data.message || '聊天失败')
      }
    } catch (error) {
      console.error('普通聊天失败：', error)
      messages.value[aiMessageIndex].content = getErrorMessage(error, '聊天失败，请稍后重试。')
      messages.value[aiMessageIndex].loading = false
      message.error(getErrorMessage(error, '聊天失败'))
    }
  }

  // 发送消息
  const sendMessage = async (selectedElementInfo?: any) => {
    if ((!userInput.value.trim() && appAttachments.value.length === 0) || isGenerating.value) return

    const isGenerateMode = messageMode.value === 'generate' || !!selectedElementInfo
    let messageContent = userInput.value.trim()
    if (!messageContent) {
      messageContent = isGenerateMode ? '请根据已上传附件生成网页。' : '请根据已上传附件进行需求分析。'
    }
    if (selectedElementInfo) {
      let elementContext = `\n\n选中元素信息：`
      if (selectedElementInfo.pagePath) {
        elementContext += `\n- 页面路径: ${selectedElementInfo.pagePath}`
      }
      elementContext += `\n- 标签: ${selectedElementInfo.tagName.toLowerCase()}\n- 选择器: ${selectedElementInfo.selector}`
      if (selectedElementInfo.textContent) {
        elementContext += `\n- 当前内容: ${selectedElementInfo.textContent.substring(0, 100)}`
      }
      messageContent += elementContext
    }
    userInput.value = ''

    messages.value.push({ type: 'user', content: messageContent })

    await nextTick()
    scrollToBottom()
    if (!isGenerateMode) {
      const aiMessageIndex = messages.value.length
      messages.value.push({ type: 'ai', content: '', loading: true })
      await requestPlainChat(messageContent, aiMessageIndex)
      return
    }
    if (!shouldRequestPlan(messageContent, selectedElementInfo)) {
      const aiMessageIndex = messages.value.length
      messages.value.push({ type: 'ai', content: '', loading: true })
      isGenerating.value = true
      await generateCode(messageContent, aiMessageIndex)
      return
    }
    await requestGenerationPlan(messageContent)
  }

  const loadAttachments = async () => {
    if (!appId.value) {
      appAttachments.value = []
      return
    }
    try {
      const res = await listAppAttachments({ appId: appId.value as unknown as number })
      if (res.data.code === 0) {
        appAttachments.value = res.data.data || []
      }
    } catch (error) {
      console.warn('加载附件失败：', error)
      appAttachments.value = []
    }
  }

  const uploadAttachment = async (options: any) => {
    if (!appId.value || !options?.file) {
      return
    }
    const file = options.file as File
    attachmentUploading.value = true
    try {
      const formData = new FormData()
      formData.append('file', file)
      const res = await uploadAppAttachment(
        { appId: appId.value as unknown as number },
        formData,
        { timeout: AI_SLOW_REQUEST_TIMEOUT }
      )
      if (res.data.code === 0 && res.data.data) {
        appAttachments.value.push(res.data.data)
        hasNewAttachmentContext.value = true
        message.success('附件解析完成，已加入生成上下文')
        options.onSuccess?.(res.data.data, file)
      } else {
        throw new Error(res.data.message || '附件上传失败')
      }
    } catch (error) {
      console.error('附件上传失败：', error)
      message.error(getErrorMessage(error, '附件上传失败'))
      options.onError?.(error)
    } finally {
      attachmentUploading.value = false
    }
  }

  const deleteAttachment = async (attachmentId?: number) => {
    if (!attachmentId) {
      return
    }
    try {
      const res = await deleteAppAttachment({ id: attachmentId })
      if (res.data.code === 0) {
        appAttachments.value = appAttachments.value.filter((item) => item.id !== attachmentId)
        message.success('附件已移除')
      } else {
        message.error(res.data.message || '删除附件失败')
      }
    } catch (error) {
      console.error('删除附件失败：', error)
      message.error('删除附件失败')
    }
  }

  const switchModel = async (modelKey: string) => {
    if (!appInfo.value?.id || !modelKey || modelKey === appInfo.value.modelKey) {
      return
    }
    modelSwitching.value = true
    try {
      const res = await updateApp({
        id: appInfo.value.id,
        appName: appInfo.value.appName,
        modelKey,
      })
      if (res.data.code === 0) {
        appInfo.value = {
          ...appInfo.value,
          modelKey,
        }
        message.success('模型已切换，后续生成将使用新模型')
      } else {
        throw new Error(res.data.message || '模型切换失败')
      }
    } catch (error) {
      console.error('模型切换失败：', error)
      message.error(getErrorMessage(error, '模型切换失败'))
      await fetchAppInfo()
    } finally {
      modelSwitching.value = false
    }
  }

  // 更新预览
  const updatePreview = () => {
    if (appId.value) {
      const codeGenType = appInfo.value?.codeGenType || CodeGenTypeEnum.HTML
      const newPreviewUrl = getStaticPreviewUrl(codeGenType, appId.value)
      previewUrl.value = newPreviewUrl
      previewReady.value = true
    }
  }

  const loadSourceFiles = async () => {
    if (!appId.value) {
      sourceFiles.value = []
      return
    }
    sourceFilesLoading.value = true
    try {
      const res = await listAppSourceFiles({ appId: appId.value as unknown as number })
      if (res.data.code === 0) {
        sourceFiles.value = res.data.data || []
      }
    } catch (error) {
      console.warn('加载源码文件树失败：', error)
      sourceFiles.value = []
    } finally {
      sourceFilesLoading.value = false
    }
  }

  const openSourceFile = async (file: API.AppSourceFileNodeVO) => {
    if (!appId.value || file.directory || !file.path) {
      return
    }
    const cachedFile = openSourceTabs.value.find((item) => item.path === file.path)
    if (cachedFile) {
      activeSourcePath.value = cachedFile.path
      return
    }
    try {
      const res = await getAppSourceFileContent({
        appId: appId.value as unknown as number,
        path: file.path,
      })
      if (res.data.code === 0 && res.data.data) {
        openSourceTabs.value.push(res.data.data)
        activeSourcePath.value = res.data.data.path
      } else {
        message.error('读取文件失败：' + res.data.message)
      }
    } catch (error) {
      console.error('读取文件失败：', error)
      message.error('读取文件失败')
    }
  }

  const closeSourceTab = (path?: string) => {
    if (!path) {
      return
    }
    const currentIndex = openSourceTabs.value.findIndex((item) => item.path === path)
    if (currentIndex < 0) {
      return
    }
    openSourceTabs.value.splice(currentIndex, 1)
    if (activeSourcePath.value === path) {
      const nextFile = openSourceTabs.value[currentIndex] || openSourceTabs.value[currentIndex - 1]
      activeSourcePath.value = nextFile?.path
    }
  }

  const closeAllSourceTabs = () => {
    openSourceTabs.value = []
    activeSourcePath.value = undefined
  }

  // 滚动到底部
  const scrollToBottom = () => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  }

  // 下载代码
  const downloadCode = async () => {
    if (!appId.value) {
      message.error('应用ID不存在')
      return
    }
    downloading.value = true
    try {
      const apiUrl = request.defaults.baseURL || ''
      const url = `${apiUrl}/app/download/${appId.value}`
      const response = await fetch(url, { method: 'GET', credentials: 'include' })
      if (!response.ok) {
        throw new Error(`下载失败: ${response.status}`)
      }
      const contentDisposition = response.headers.get('Content-Disposition')
      const fileName =
        contentDisposition?.match(/filename="(.+)"/)?.[1] || `app-${appId.value}.zip`
      const blob = await response.blob()
      const downloadUrl = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = downloadUrl
      link.download = fileName
      link.click()
      URL.revokeObjectURL(downloadUrl)
      message.success('代码下载成功')
    } catch (error) {
      console.error('下载失败：', error)
      message.error('下载失败，请重试')
    } finally {
      downloading.value = false
    }
  }

  const getErrorMessage = (error: any, defaultMessage: string) => {
    if (error?.code === 'ECONNABORTED') {
      return '请求超时，AI 处理附件或生成方案时间较长，请稍后重试'
    }
    return error?.response?.data?.message || error?.message || defaultMessage
  }

  const stopDeployPolling = () => {
    if (deployPollTimer) {
      window.clearTimeout(deployPollTimer)
      deployPollTimer = undefined
    }
  }

  const hideDeployTerminalLater = () => {
    window.setTimeout(() => {
      deployTerminalVisible.value = false
    }, 1200)
  }

  const syncDeployLogs = (task: API.AppDeployTaskVO) => {
    const lines = (task.logText || '')
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)
    if (lines.length > 0) {
      deployLogs.value = lines
    }
  }

  const pollDeployTask = async (taskId: number) => {
    stopDeployPolling()
    const poll = async () => {
      try {
        const res = await getDeployTask({ taskId })
        if (res.data.code === 0 && res.data.data) {
          const task = res.data.data
          deployTask.value = task
          syncDeployLogs(task)
          if (task.deployUrl) {
            deployUrl.value = task.deployUrl
          }

          if (task.status === 'success') {
            stopDeployPolling()
            deploying.value = false
            hideDeployTerminalLater()
            deployModalVisible.value = true
            message.success('部署成功')
            await fetchAppInfo()
            return
          }

          if (task.status === 'failed') {
            stopDeployPolling()
            deploying.value = false
            hideDeployTerminalLater()
            Modal.error({
              title: '部署失败',
              content: task.errorMessage || '部署失败，请查看终端日志',
            })
            return
          }
        }
      } catch (error) {
        deployLogs.value.push(`轮询部署任务失败：${getErrorMessage(error, '网络异常')}`)
      }
      deployPollTimer = window.setTimeout(poll, 1000)
    }
    await poll()
  }

  // 部署应用
  const deployApp = async () => {
    if (!appId.value) {
      message.error('应用ID不存在')
      return
    }
    if (deployDisabled.value) {
      message.warning('应用正在生成、构建或部署中，请等待当前任务完成后再部署')
      return
    }
    deploying.value = true
    deployTerminalVisible.value = true
    deployLogs.value = ['正在提交部署任务...']
    deployTask.value = undefined
    try {
      const res = await deployAppApi({ appId: appId.value as unknown as number })
      if (res.data.code === 0 && res.data.data) {
        const deployResult = res.data.data
        if (!deployResult.taskId) {
          throw new Error('部署任务 ID 为空')
        }
        deployUrl.value = deployResult.deployUrl || ''
        deployTask.value = {
          id: deployResult.taskId,
          appId: deployResult.appId,
          deployKey: deployResult.deployKey,
          deployUrl: deployResult.deployUrl,
          status: deployResult.status,
        }
        deployLogs.value.push('部署任务已创建，等待执行...')
        await pollDeployTask(deployResult.taskId)
      } else {
        throw new Error(res.data.message || '部署失败')
      }
    } catch (error) {
      console.error('部署失败：', error)
      stopDeployPolling()
      deploying.value = false
      deployLogs.value.push(`部署失败：${getErrorMessage(error, '请重试')}`)
      hideDeployTerminalLater()
      Modal.error({
        title: '部署失败',
        content: getErrorMessage(error, '部署失败，请重试'),
      })
    }
  }

  // 在新窗口打开预览
  const openInNewTab = () => {
    if (previewUrl.value) {
      window.open(previewUrl.value, '_blank')
    }
  }

  // 打开部署的网站
  const openDeployedSite = () => {
    if (deployUrl.value) {
      window.open(deployUrl.value, '_blank')
    }
  }

  // 编辑应用
  const editApp = () => {
    if (appInfo.value?.id) {
      router.push(`/app/edit/${appInfo.value.id}`)
    }
  }

  // 删除应用
  const deleteApp = async () => {
    if (!appInfo.value?.id) return
    try {
      const res = await deleteAppApi({ id: appInfo.value.id })
      if (res.data.code === 0) {
        message.success('删除成功')
        appDetailVisible.value = false
        router.push('/')
      } else {
        message.error('删除失败：' + res.data.message)
      }
    } catch (error) {
      console.error('删除失败：', error)
      message.error('删除失败')
    }
  }

  onUnmounted(() => {
    stopDeployPolling()
  })

  return {
    // 状态
    appInfo,
    appId,
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
    deployDisabled,
    downloading,
    appDetailVisible,
    isOwner,
    isAdmin,

    // 方法
    showAppDetail,
    loadChatHistory,
    loadMoreHistory,
    fetchAppInfo,
    sendInitialMessage,
    sendMessage,
    confirmGenerationPlan,
    uploadAttachment,
    deleteAttachment,
    switchModel,
    updatePreview,
    loadSourceFiles,
    openSourceFile,
    closeSourceTab,
    closeAllSourceTabs,
    scrollToBottom,
    downloadCode,
    deployApp,
    openInNewTab,
    openDeployedSite,
    editApp,
    deleteApp,
  }
}
