<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { UploadProps } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  addApp,
  listMyAppVoByPage,
  listGoodAppVoByPage,
  uploadAppAttachment,
} from '@/api/appController'
import { getDeployUrl } from '@/config/env'
import { AI_MODEL_OPTIONS, DEFAULT_AI_MODEL } from '@/utils/aiModels'
import AppCard from '@/components/AppCard.vue'
import { ThunderboltOutlined, AppstoreOutlined, StarOutlined, MailOutlined, GithubOutlined, HomeOutlined, SettingOutlined, BarChartOutlined, PaperClipOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { userLogout } from '@/api/userController'
import { PlusOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const userPrompt = ref('')
const creating = ref(false)
const pendingAttachments = ref<File[]>([])
const AI_SLOW_REQUEST_TIMEOUT = 180000
const selectedModelKey = ref(DEFAULT_AI_MODEL)

const myApps = ref<API.AppVO[]>([])
const myAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

const featuredApps = ref<API.AppVO[]>([])
const featuredAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

const templateOptions = [
  {
    title: '个人博客',
    description: '文章列表、分类标签、关于页面',
    prompt: '创建一个现代化的个人博客网站，包含文章列表、详情页、分类标签、搜索功能和个人简介页面。采用简洁的设计风格，支持响应式布局。',
  },
  {
    title: '企业官网',
    description: '品牌介绍、服务展示、联系入口',
    prompt: '设计一个专业的企业官网，包含公司介绍、产品服务展示、新闻资讯、联系我们等页面。采用商务风格的设计，包含轮播图和产品展示。',
  },
  {
    title: '在线商城',
    description: '商品卡片、购物流程、订单管理',
    prompt: '构建一个功能完整的在线商城，包含商品展示、购物车、用户注册登录、订单管理等功能。设计现代化的商品卡片布局。',
  },
  {
    title: '作品集',
    description: '项目展示、履历简介、视觉表达',
    prompt: '制作一个精美的作品展示网站，适合设计师、摄影师等创作者。包含作品画廊、项目详情页、个人简历等模块。',
  },
]

const heroHighlights = [
  { value: '自然语言', label: '描述需求即可开始' },
  { value: '附件驱动', label: '设计稿、PDF、DOCX' },
  { value: '一键部署', label: '生成后直接上线访问' },
]

const setPrompt = (prompt: string) => {
  userPrompt.value = prompt
}

const beforeAttachmentSelect: UploadProps['beforeUpload'] = (file) => {
  if (pendingAttachments.value.length >= 5) {
    message.warning('最多上传 5 个附件')
    return false
  }
  const rawFile = file as File
  if (rawFile.size > 10 * 1024 * 1024) {
    message.warning('单个附件不能超过 10MB')
    return false
  }
  pendingAttachments.value.push(rawFile)
  return false
}

const removePendingAttachment = (index: number) => {
  pendingAttachments.value.splice(index, 1)
}

const uploadPendingAttachments = async (appId: string) => {
  if (pendingAttachments.value.length === 0) {
    return
  }
  message.loading({
    content: `正在解析 ${pendingAttachments.value.length} 个附件...`,
    key: 'home-attachment-upload',
    duration: 0,
  })
  for (const file of pendingAttachments.value) {
    const formData = new FormData()
    formData.append('file', file)
    const res = await uploadAppAttachment(
      { appId: appId as unknown as number },
      formData,
      { timeout: AI_SLOW_REQUEST_TIMEOUT }
    )
    if (res.data.code !== 0) {
      throw new Error(res.data.message || `附件 ${file.name} 上传失败`)
    }
  }
  message.success({
    content: '附件解析完成',
    key: 'home-attachment-upload',
  })
}

const createApp = async () => {
  if (!userPrompt.value.trim() && pendingAttachments.value.length === 0) {
    message.warning('请输入应用描述')
    return
  }

  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }

  creating.value = true
  try {
    const initPrompt = userPrompt.value.trim() || '请根据我上传的设计稿、简历或文档生成一个完整网页。'
    const res = await addApp({
      initPrompt,
      modelKey: selectedModelKey.value,
    })

    if (res.data.code === 0 && res.data.data) {
      const appId = String(res.data.data)
      await uploadPendingAttachments(appId)
      message.success('应用创建成功')
      await router.push(`/app/chat/${appId}`)
    } else {
      message.error('创建失败：' + res.data.message)
    }
  } catch (error) {
    console.error('创建应用失败：', error)
    message.error('创建失败，请重试')
  } finally {
    creating.value = false
  }
}

const loadMyApps = async () => {
  if (!loginUserStore.loginUser.id) {
    return
  }

  try {
    const res = await listMyAppVoByPage({
      pageNum: myAppsPage.current,
      pageSize: myAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      myApps.value = res.data.data.records || []
      myAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载我的应用失败：', error)
  }
}

const loadFeaturedApps = async () => {
  try {
    const res = await listGoodAppVoByPage({
      pageNum: featuredAppsPage.current,
      pageSize: featuredAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      featuredApps.value = res.data.data.records || []
      featuredAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载精选应用失败：', error)
  }
}

const viewChat = (appId: string | number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}?view=1`)
  }
}

const viewWork = (app: API.AppVO) => {
  if (app.deployKey) {
    const url = getDeployUrl(app.deployKey)
    window.open(url, '_blank')
  }
}

const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({ userName: '未登录' })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败')
  }
}

onMounted(() => {
  loadMyApps()
  loadFeaturedApps()
})
</script>

<template>
  <div class="home-layout">
    <!-- 左侧边栏 -->
    <aside class="sidebar">
      <div class="sidebar-header">
        <div class="logo">
          <div class="logo-icon">
            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2L2 7L12 12L22 7L12 2Z" fill="currentColor" fill-opacity="0.2"/>
              <path d="M2 17L12 22L22 17" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M2 12L12 17L22 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <span class="logo-text">CraftAI</span>
        </div>
        <p class="logo-desc">AI 应用生成平台</p>
      </div>

      <nav class="sidebar-nav">
        <RouterLink to="/" class="nav-item active">
          <HomeOutlined />
          <span>首页</span>
        </RouterLink>
        <template v-if="loginUserStore.loginUser.userRole === 'admin'">
          <a href="/admin/userManage" class="nav-item">
            <SettingOutlined />
            <span>用户管理</span>
          </a>
          <a href="/admin/appManage" class="nav-item">
            <AppstoreOutlined />
            <span>应用管理</span>
          </a>
          <a href="/admin/chatManage" class="nav-item">
            <MailOutlined />
            <span>对话管理</span>
          </a>
          <a href="/admin/statistics" class="nav-item">
            <BarChartOutlined />
            <span>AI使用统计</span>
          </a>
        </template>
      </nav>

      <!-- 用户信息 / 登录按钮 -->
      <div class="user-box" v-if="loginUserStore.loginUser.id">
        <div class="user-info">
          <a-avatar :src="loginUserStore.loginUser.userAvatar" class="user-avatar">
            {{ loginUserStore.loginUser.userName?.charAt(0) || 'U' }}
          </a-avatar>
          <div class="user-detail">
            <span class="user-name">{{ loginUserStore.loginUser.userName }}</span>
            <span class="user-role">{{ loginUserStore.loginUser.userRole === 'admin' ? '管理员' : '用户' }}</span>
          </div>
        </div>
        <a-button type="text" size="small" @click="doLogout" class="logout-btn">
          退出
        </a-button>
      </div>
      <div class="user-box" v-else>
        <a-button type="primary" block href="/user/login" class="login-btn-sidebar">
          登录
        </a-button>
      </div>

      <!-- 联系信息 -->
      <div class="contact-box">
        <div class="contact-header">
          <MailOutlined />
          <span>联系方式</span>
        </div>
        <p class="contact-text">
          CraftAI 支持自然语言生成、附件生成与一键部署，
          适合快速搭建博客、官网、作品集和活动页面。
        </p>
        <div class="contact-label">反馈与交流</div>
        <a href="mailto:2606209307@qq.com" class="contact-email">
          2606209307@qq.com
        </a>
        <div class="contact-actions">
          <a href="https://github.com/xixi-box" target="_blank" rel="noopener" class="github-link">
            <GithubOutlined />
            <span>查看 GitHub</span>
          </a>
        </div>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="main-content">
      <!-- 创建区域 - 左右分栏 -->
      <section class="create-section">
        <div class="create-left">
          <div class="section-badge">
            <ThunderboltOutlined />
            <span>AI 驱动</span>
          </div>
          <h1 class="create-title">
            一句需求<br>
            <span class="highlight">生成完整应用</span>
          </h1>
          <p class="create-desc">
            通过自然语言和附件材料快速生成网页，支持方案确认、
            在线修改与一键部署，让想法更快变成可访问成果。
          </p>
          <div class="hero-highlights">
            <div
              v-for="item in heroHighlights"
              :key="item.value"
              class="hero-highlight-card"
            >
              <strong>{{ item.value }}</strong>
              <span>{{ item.label }}</span>
            </div>
          </div>
        </div>

        <div class="create-right">
          <div class="input-box">
            <a-textarea
              v-model:value="userPrompt"
              placeholder="例如：请帮我生成一个极简风格的个人博客网站，包含文章列表、分类标签、关于页面和订阅入口..."
              :rows="5"
              :maxlength="1000"
              class="prompt-textarea"
            />
            <div class="home-attachment-panel">
              <a-select
                v-model:value="selectedModelKey"
                class="home-model-select"
                :options="AI_MODEL_OPTIONS"
                :disabled="creating"
              />
              <a-upload
                :show-upload-list="false"
                :before-upload="beforeAttachmentSelect"
                accept=".png,.jpg,.jpeg,.webp,.gif,.pdf,.docx,.txt,.md,.markdown,.json,.csv,.html,.css,.js,.ts,.vue"
                :disabled="creating"
              >
                <a-button class="home-attachment-btn" :disabled="creating">
                  <template #icon><PaperClipOutlined /></template>
                  上传设计稿 / 简历 / 文档
                </a-button>
              </a-upload>
              <span class="home-attachment-tip">创建后会先解析附件，再进入对话生成</span>
            </div>
            <div v-if="pendingAttachments.length > 0" class="home-attachment-list">
              <div
                v-for="(file, index) in pendingAttachments"
                :key="`${file.name}-${file.size}-${index}`"
                class="home-attachment-item"
              >
                <div class="home-attachment-meta">
                  <PaperClipOutlined />
                  <span>{{ file.name }}</span>
                  <small>{{ (file.size / 1024 / 1024).toFixed(2) }}MB</small>
                </div>
                <a-button
                  type="text"
                  size="small"
                  :disabled="creating"
                  @click="removePendingAttachment(index)"
                >
                  <template #icon><DeleteOutlined /></template>
                </a-button>
              </div>
            </div>
            <a-button
              type="primary"
              size="large"
              @click="createApp"
              :loading="creating"
              class="submit-btn"
              block
            >
              <template #icon><PlusOutlined /></template>
              开始创建
            </a-button>
          </div>

          <!-- 快捷模板 -->
          <div class="templates">
            <span class="templates-label">精选场景：</span>
            <div class="template-grid">
              <button
                v-for="item in templateOptions"
                :key="item.title"
                class="template-card"
                @click="setPrompt(item.prompt)"
              >
                <strong>{{ item.title }}</strong>
                <span>{{ item.description }}</span>
              </button>
            </div>
          </div>
        </div>
      </section>

      <!-- 应用展示区域 -->
      <div class="content-grid">
        <!-- 我的应用 -->
        <section class="content-section" v-if="loginUserStore.loginUser.id">
          <div class="section-header">
            <div class="section-title">
              <AppstoreOutlined />
              <h2>我的应用</h2>
            </div>
            <a-pagination
              v-if="myAppsPage.total > myAppsPage.pageSize"
              v-model:current="myAppsPage.current"
              v-model:page-size="myAppsPage.pageSize"
              :total="myAppsPage.total"
              :show-size-changer="false"
              size="small"
              @change="loadMyApps"
            />
          </div>

          <div v-if="myApps.length > 0" class="app-list">
            <AppCard
              v-for="app in myApps"
              :key="app.id"
              :app="app"
              @view-chat="viewChat"
              @view-work="viewWork"
            />
          </div>

          <div v-else class="empty-box">
            <AppstoreOutlined class="empty-icon" />
            <p>还没有应用，快来创建第一个吧</p>
          </div>
        </section>

        <!-- 精选案例 -->
        <section class="content-section">
          <div class="section-header">
            <div class="section-title">
              <StarOutlined />
              <h2>精选案例</h2>
            </div>
            <a-pagination
              v-if="featuredAppsPage.total > featuredAppsPage.pageSize"
              v-model:current="featuredAppsPage.current"
              v-model:page-size="featuredAppsPage.pageSize"
              :total="featuredAppsPage.total"
              :show-size-changer="false"
              size="small"
              @change="loadFeaturedApps"
            />
          </div>

          <div v-if="featuredApps.length > 0" class="app-list">
            <AppCard
              v-for="app in featuredApps"
              :key="app.id"
              :app="app"
              :featured="true"
              @view-chat="viewChat"
              @view-work="viewWork"
            />
          </div>

          <div v-else class="empty-box">
            <StarOutlined class="empty-icon" />
            <p>暂无精选案例</p>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<style scoped>
.home-layout {
  display: flex;
  min-height: 100vh;
  background:
    radial-gradient(circle at 18% 12%, rgba(180, 106, 61, 0.12), transparent 26%),
    radial-gradient(circle at 82% 8%, rgba(213, 195, 167, 0.18), transparent 22%),
    linear-gradient(135deg, #f7f4ee 0%, #f5f1ea 48%, #eef2f0 100%);
}

.sidebar {
  width: 288px;
  background: rgba(248, 244, 236, 0.88);
  border-right: 1px solid rgba(90, 74, 58, 0.1);
  backdrop-filter: blur(18px);
  display: flex;
  flex-direction: column;
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 100;
}

.sidebar-header {
  padding: 34px 24px 26px;
  border-bottom: 1px solid rgba(90, 74, 58, 0.08);
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.logo-icon {
  width: 38px;
  height: 38px;
  color: #b46a3d;
}

.logo-icon svg {
  width: 100%;
  height: 100%;
}

.logo-text {
  font-family: var(--font-serif);
  font-size: 25px;
  font-weight: 500;
  color: #2f2a24;
}

.logo-desc {
  font-size: 13px;
  color: #7d7266;
  margin: 0;
  padding-left: 50px;
}

.sidebar-nav {
  padding: 18px 12px;
  flex: 1;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: var(--radius-lg);
  color: #6d6458;
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  transition: all var(--transition-fast);
  margin-bottom: 5px;
}

.nav-item:hover,
.nav-item.active {
  background-color: rgba(180, 106, 61, 0.12);
  color: #2f2a24;
  box-shadow: 0 0 0 1px rgba(180, 106, 61, 0.08);
}

.nav-item.active :deep(.anticon) {
  color: #b46a3d;
}

.nav-item :deep(.anticon) {
  font-size: 18px;
}

.user-box {
  margin: 0 16px 16px;
  padding: 16px;
  background-color: rgba(255, 255, 255, 0.72);
  border-radius: 18px;
  border: 1px solid rgba(90, 74, 58, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar {
  width: 40px;
  height: 40px;
  background-color: var(--primary);
  color: var(--ivory);
  font-weight: 600;
}

.user-detail {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: #2f2a24;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  font-size: 12px;
  color: #b46a3d;
  font-weight: 500;
}

.logout-btn {
  color: #7d7266;
  font-size: 13px;
}

.logout-btn:hover {
  color: #b46a3d;
}

.login-btn-sidebar {
  height: 42px;
  font-weight: 600;
  border-radius: var(--radius-lg);
}

.contact-box {
  margin: 0 16px 16px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.76);
  border-radius: 20px;
  border: 1px solid rgba(90, 74, 58, 0.1);
  box-shadow: 0 18px 38px rgba(78, 61, 44, 0.08);
}

.contact-header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--primary);
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 12px;
}

.contact-text {
  font-size: 12px;
  color: #5d5449;
  line-height: 1.75;
  margin: 0 0 12px;
}

.contact-label {
  margin-bottom: 6px;
  color: #8f6549;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.contact-email {
  display: inline-block;
  padding: 8px 12px;
  background-color: #f4ece3;
  color: #2f2a24;
  font-size: 13px;
  font-weight: 600;
  border-radius: var(--radius-lg);
  box-shadow: 0 0 0 1px rgba(90, 74, 58, 0.12);
}

.contact-email,
.contact-email:hover {
  color: #2f2a24;
}

.contact-email:hover {
  background-color: #ead9c7;
}

.contact-actions {
  margin-top: 14px;
  display: flex;
  gap: 10px;
}

.github-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  color: #6d6458;
  font-size: 13px;
  font-weight: 600;
  border-radius: var(--radius-lg);
  transition: all var(--transition-fast);
  background: rgba(244, 236, 227, 0.84);
  box-shadow: 0 0 0 1px rgba(90, 74, 58, 0.08);
}

.github-link:hover {
  background-color: rgba(234, 217, 199, 0.95);
  color: #2f2a24;
}

.main-content {
  flex: 1;
  margin-left: 288px;
  padding: 38px;
  max-width: calc(100% - 288px);
  min-height: 100vh;
}

.create-section {
  display: grid;
  grid-template-columns: 0.95fr 1.15fr;
  gap: 58px;
  align-items: start;
  margin-bottom: 34px;
  padding: 48px;
  background: rgba(255, 255, 255, 0.68);
  border-radius: 32px;
  border: 1px solid rgba(90, 74, 58, 0.1);
  box-shadow: 0 28px 64px rgba(76, 60, 42, 0.1);
  backdrop-filter: blur(18px);
  position: relative;
  overflow: hidden;
}

.create-section::after {
  content: '';
  position: absolute;
  right: -90px;
  top: -110px;
  width: 280px;
  height: 280px;
  border: 1px solid rgba(180, 106, 61, 0.14);
  border-radius: 44% 56% 52% 48%;
  transform: rotate(-18deg);
}

.create-left {
  padding-top: 10px;
  position: relative;
  z-index: 1;
}

.section-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 14px;
  background-color: rgba(180, 106, 61, 0.12);
  color: #8f5a35;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12px;
  margin-bottom: 26px;
  box-shadow: 0 0 0 1px rgba(180, 106, 61, 0.12);
}

.create-title {
  font-size: clamp(36px, 4.4vw, 64px);
  font-weight: 500;
  line-height: 1.08;
  color: #2f2a24;
  margin: 0 0 22px;
  letter-spacing: -0.03em;
}

.create-title .highlight {
  color: #b46a3d;
}

.create-desc {
  font-size: 18px;
  color: #6d6458;
  line-height: 1.75;
  margin: 0 0 26px;
  max-width: 520px;
}

.hero-highlights {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  max-width: 560px;
}

.hero-highlight-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(90, 74, 58, 0.08);
  box-shadow: 0 10px 22px rgba(76, 60, 42, 0.05);
}

.hero-highlight-card strong {
  font-size: 13px;
  font-weight: 700;
  color: #2f2a24;
}

.hero-highlight-card span {
  font-size: 12px;
  line-height: 1.55;
  color: #8a7d70;
}

.create-right {
  display: flex;
  flex-direction: column;
  gap: 22px;
  position: relative;
  z-index: 1;
}

.input-box {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 22px;
  border-radius: 24px;
  background: rgba(250, 247, 241, 0.9);
  box-shadow: inset 0 0 0 1px rgba(90, 74, 58, 0.06);
}

.prompt-textarea {
  padding: 20px;
  font-size: 15px;
  line-height: 1.7;
  border: 1px solid rgba(90, 74, 58, 0.16);
  border-radius: 18px;
  background-color: rgba(255, 255, 255, 0.92);
  resize: none;
  transition: all var(--transition-fast);
  box-shadow: inset 0 0 0 1px rgba(20, 20, 19, 0.02);
}

.prompt-textarea:hover {
  border-color: rgba(180, 106, 61, 0.22);
}

.prompt-textarea:focus {
  border-color: var(--focus-blue);
  box-shadow: 0 0 0 3px rgba(56, 152, 236, 0.14);
  outline: none;
}

.home-attachment-panel {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: -4px;
}

.home-model-select {
  min-width: 220px;
}

.home-attachment-btn {
  height: 40px;
  border-radius: var(--radius-full);
  background: rgba(244, 236, 227, 0.95);
  border-color: rgba(180, 106, 61, 0.12);
  color: #4f463d;
  font-weight: 600;
}

.home-attachment-btn:hover {
  color: #8f5a35;
  border-color: rgba(180, 106, 61, 0.26);
}

.home-attachment-tip {
  color: #86796c;
  font-size: 12px;
}

.home-attachment-list {
  display: grid;
  gap: 8px;
}

.home-attachment-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid rgba(90, 74, 58, 0.1);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.72);
}

.home-attachment-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: #3d352d;
  font-size: 13px;
}

.home-attachment-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-attachment-meta small {
  flex: 0 0 auto;
  color: #8f8578;
}

.submit-btn {
  height: 54px;
  font-size: 16px;
  font-weight: 700;
  border-radius: var(--radius-lg);
}

.submit-btn:hover {
  transform: translateY(-2px);
}

.templates {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.templates-label {
  font-size: 13px;
  color: #86796c;
  font-weight: 600;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.template-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 16px 18px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(90, 74, 58, 0.08);
  border-radius: 18px;
  cursor: pointer;
  transition: all var(--transition-fast);
  text-align: left;
  box-shadow: 0 12px 24px rgba(76, 60, 42, 0.05);
}

.template-card strong {
  font-size: 14px;
  font-weight: 700;
  color: #2f2a24;
}

.template-card span {
  font-size: 12px;
  line-height: 1.6;
  color: #857b70;
}

.template-card:hover {
  transform: translateY(-2px);
  border-color: rgba(180, 106, 61, 0.18);
  box-shadow: 0 16px 28px rgba(76, 60, 42, 0.08);
}

.content-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: 24px;
}

.content-section {
  background: rgba(255, 255, 255, 0.72);
  border-radius: 28px;
  border: 1px solid rgba(90, 74, 58, 0.08);
  padding: 28px;
  box-shadow: 0 18px 40px rgba(76, 60, 42, 0.08);
  min-height: 420px;
  backdrop-filter: blur(14px);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 26px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.section-title :deep(.anticon) {
  font-size: 22px;
  color: #b46a3d;
}

.section-title h2 {
  font-size: 28px;
  font-weight: 500;
  color: #2f2a24;
  margin: 0;
}

.app-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.empty-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 62px 20px;
  color: #8a7d70;
  gap: 12px;
}

.empty-icon {
  font-size: 40px;
  color: rgba(180, 106, 61, 0.3);
}

@media (max-width: 1024px) {
  .sidebar {
    width: 244px;
  }

  .main-content {
    margin-left: 244px;
    max-width: calc(100% - 244px);
    padding: 24px;
  }

  .create-section {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .template-grid {
    grid-template-columns: 1fr;
  }

  .hero-highlights {
    grid-template-columns: 1fr;
    max-width: none;
  }

  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .sidebar {
    display: none;
  }

  .main-content {
    margin-left: 0;
    max-width: 100%;
    padding: 16px;
  }

  .create-section {
    padding: 26px;
    border-radius: 24px;
  }

  .content-section {
    padding: 22px;
    border-radius: 22px;
  }
}
</style>
