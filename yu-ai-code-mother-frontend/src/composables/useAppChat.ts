import { ref, nextTick, computed, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { getAppVoById, deployApp as deployAppApi, deleteApp as deleteAppApi } from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import { CodeGenTypeEnum } from '@/utils/codeGenTypes'
import request from '@/request'
import { API_BASE_URL, getStaticPreviewUrl } from '@/config/env'

/**
 * 消息类型
 */
export interface Message {
  type: 'user' | 'ai'
  content: string
  loading?: boolean
  createTime?: string
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
  const messagesContainer = ref<HTMLElement>()

  // 对话历史相关
  const loadingHistory = ref(false)
  const hasMoreHistory = ref(false)
  const lastCreateTime = ref<string>()
  const historyLoaded = ref(false)

  // 预览相关
  const previewUrl = ref('')
  const previewReady = ref(false)

  // 部署相关
  const deploying = ref(false)
  const deployModalVisible = ref(false)
  const deployUrl = ref('')

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
  const generateCode = async (userMessage: string, aiMessageIndex: number) => {
    let eventSource: EventSource | null = null
    let streamCompleted = false

    try {
      const baseURL = request.defaults.baseURL || API_BASE_URL
      const params = new URLSearchParams({
        appId: appId.value || '',
        message: userMessage,
      })
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
          handleError(error, aiMessageIndex)
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
          messages.value[aiMessageIndex].content = `❌ ${errorMessage}`
          messages.value[aiMessageIndex].loading = false
          message.error(errorMessage)
          streamCompleted = true
          isGenerating.value = false
          eventSource?.close()
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
          handleError(new Error('SSE连接错误'), aiMessageIndex)
        }
      }
    } catch (error) {
      console.error('创建 EventSource 失败：', error)
      handleError(error, aiMessageIndex)
    }
  }

  // 错误处理函数
  const handleError = (error: unknown, aiMessageIndex: number) => {
    console.error('生成代码失败：', error)
    messages.value[aiMessageIndex].content = '抱歉，生成过程中出现了错误，请重试。'
    messages.value[aiMessageIndex].loading = false
    message.error('生成失败，请重试')
    isGenerating.value = false
  }

  // 发送初始消息
  const sendInitialMessage = async (prompt: string) => {
    messages.value.push({ type: 'user', content: prompt })
    const aiMessageIndex = messages.value.length
    messages.value.push({ type: 'ai', content: '', loading: true })
    await nextTick()
    scrollToBottom()
    isGenerating.value = true
    await generateCode(prompt, aiMessageIndex)
  }

  // 发送消息
  const sendMessage = async (selectedElementInfo?: any) => {
    if (!userInput.value.trim() || isGenerating.value) return

    let messageContent = userInput.value.trim()
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
    const aiMessageIndex = messages.value.length
    messages.value.push({ type: 'ai', content: '', loading: true })

    await nextTick()
    scrollToBottom()
    isGenerating.value = true
    await generateCode(messageContent, aiMessageIndex)
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

  // 部署应用
  const deployApp = async () => {
    if (!appId.value) {
      message.error('应用ID不存在')
      return
    }
    deploying.value = true
    try {
      const res = await deployAppApi({ appId: appId.value as unknown as number })
      if (res.data.code === 0 && res.data.data) {
        deployUrl.value = res.data.data
        deployModalVisible.value = true
        message.success('部署成功')
      } else {
        message.error('部署失败：' + res.data.message)
      }
    } catch (error) {
      console.error('部署失败：', error)
      message.error('部署失败，请重试')
    } finally {
      deploying.value = false
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

  return {
    // 状态
    appInfo,
    appId,
    messages,
    userInput,
    isGenerating,
    messagesContainer,
    loadingHistory,
    hasMoreHistory,
    previewUrl,
    previewReady,
    deploying,
    deployModalVisible,
    deployUrl,
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
    updatePreview,
    scrollToBottom,
    downloadCode,
    deployApp,
    openInNewTab,
    openDeployedSite,
    editApp,
    deleteApp,
  }
}