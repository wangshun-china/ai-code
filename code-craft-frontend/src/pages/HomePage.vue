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

      <!-- 联系信息 - 放在侧边栏显眼位置 -->
      <div class="contact-box">
        <div class="contact-header">
          <MailOutlined />
          <span>联系方式</span>
        </div>
        <p class="contact-text">
          本项目运行在云服务器<br>
          使用阿里百炼平台 API<br>
          感谢天翼云为本项目提供了一定的赞助<br>
          如遇到问题请联系：
        </p>
        <div class="sponsor-link-row">
          友情链接：
          <a href="https://www.ctyun.cn/" target="_blank" rel="noopener noreferrer" class="sponsor-link">
            天翼云
          </a>
        </div>
        <p class="sponsor-note">
          天翼云秉承央企使命，致力于成为数字经济主力军，投身科技强国伟大事业，为用户提供安全、普惠云服务
        </p>
        <a href="mailto:2606209307@qq.com" class="contact-email">
          2606209307@qq.com
        </a>
      </div>

      <div class="sidebar-footer">
        <a href="https://github.com/xixi-box" target="_blank" class="github-link">
          <GithubOutlined />
          <span>GitHub</span>
        </a>
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
            描述你的想法<br>
            <span class="highlight">我们帮你实现</span>
          </h1>
          <p class="create-desc">
            输入你想要的网站或应用描述，AI 将自动生成完整的代码项目
          </p>
        </div>

        <div class="create-right">
          <div class="input-box">
            <a-textarea
              v-model:value="userPrompt"
              placeholder="例如：帮我创建一个极简风格的个人博客网站，要有文章列表、分类标签和关于页面..."
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
            <span class="templates-label">快速选择：</span>
            <div class="template-tags">
              <button
                class="tag-btn"
                @click="setPrompt('创建一个现代化的个人博客网站，包含文章列表、详情页、分类标签、搜索功能和个人简介页面。采用简洁的设计风格，支持响应式布局。')"
              >
                个人博客
              </button>
              <button
                class="tag-btn"
                @click="setPrompt('设计一个专业的企业官网，包含公司介绍、产品服务展示、新闻资讯、联系我们等页面。采用商务风格的设计，包含轮播图和产品展示。')"
              >
                企业官网
              </button>
              <button
                class="tag-btn"
                @click="setPrompt('构建一个功能完整的在线商城，包含商品展示、购物车、用户注册登录、订单管理等功能。设计现代化的商品卡片布局。')"
              >
                在线商城
              </button>
              <button
                class="tag-btn"
                @click="setPrompt('制作一个精美的作品展示网站，适合设计师、摄影师等创作者。包含作品画廊、项目详情页、个人简历等模块。')"
              >
                作品集
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
  background: transparent;
}

.sidebar {
  width: 288px;
  background: var(--near-black);
  border-right: 1px solid var(--dark-surface);
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
  border-bottom: 1px solid var(--dark-surface);
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
  color: var(--primary-light);
}

.logo-icon svg {
  width: 100%;
  height: 100%;
}

.logo-text {
  font-family: var(--font-serif);
  font-size: 25px;
  font-weight: 500;
  color: var(--ivory);
}

.logo-desc {
  font-size: 13px;
  color: var(--warm-silver);
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
  color: var(--warm-silver);
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  transition: all var(--transition-fast);
  margin-bottom: 5px;
}

.nav-item:hover,
.nav-item.active {
  background-color: var(--dark-surface);
  color: var(--ivory);
  box-shadow: 0 0 0 1px rgba(250, 249, 245, 0.08);
}

.nav-item.active :deep(.anticon) {
  color: var(--primary-light);
}

.nav-item :deep(.anticon) {
  font-size: 18px;
}

.user-box {
  margin: 0 16px 16px;
  padding: 16px;
  background-color: var(--dark-surface);
  border-radius: 18px;
  border: 1px solid rgba(250, 249, 245, 0.08);
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
  color: var(--ivory);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  font-size: 12px;
  color: var(--primary-light);
  font-weight: 500;
}

.logout-btn {
  color: var(--warm-silver);
  font-size: 13px;
}

.logout-btn:hover {
  color: var(--primary-light);
}

.login-btn-sidebar {
  height: 42px;
  font-weight: 600;
  border-radius: var(--radius-lg);
}

.contact-box {
  margin: 0 16px 16px;
  padding: 20px;
  background: var(--ivory);
  border-radius: 20px;
  border: 1px solid rgba(250, 249, 245, 0.18);
  box-shadow: inset 0 0 0 1px rgba(201, 100, 66, 0.08);
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
  color: var(--olive-gray);
  line-height: 1.8;
  margin: 0 0 12px;
}

.contact-email {
  display: inline-block;
  padding: 8px 12px;
  background-color: var(--warm-sand);
  color: var(--charcoal-warm);
  font-size: 13px;
  font-weight: 600;
  border-radius: var(--radius-lg);
  box-shadow: 0 0 0 1px var(--ring-warm);
}

.contact-email:hover {
  color: var(--primary-dark);
}

.sponsor-link-row {
  margin: 0 0 8px;
  color: var(--olive-gray);
  font-size: 12px;
  line-height: 1.6;
}

.sponsor-link {
  color: var(--primary);
  font-weight: 700;
}

.sponsor-link:hover {
  color: var(--primary-dark);
}

.sponsor-note {
  margin: 0 0 12px;
  color: rgba(75, 72, 64, 0.68);
  font-size: 11px;
  line-height: 1.65;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid var(--dark-surface);
}

.github-link {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  color: var(--warm-silver);
  font-size: 14px;
  font-weight: 500;
  border-radius: var(--radius-lg);
  transition: all var(--transition-fast);
}

.github-link:hover {
  background-color: var(--dark-surface);
  color: var(--ivory);
}

.main-content {
  flex: 1;
  margin-left: 288px;
  padding: 44px;
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
  background: var(--ivory);
  border-radius: 32px;
  border: 1px solid var(--border-cream);
  box-shadow: var(--shadow-lg);
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
  border: 1px solid rgba(201, 100, 66, 0.2);
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
  background-color: var(--warm-sand);
  color: var(--primary-dark);
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12px;
  margin-bottom: 26px;
  box-shadow: 0 0 0 1px var(--ring-warm);
}

.create-title {
  font-size: clamp(36px, 4.4vw, 64px);
  font-weight: 500;
  line-height: 1.08;
  color: var(--near-black);
  margin: 0 0 22px;
  letter-spacing: -0.03em;
}

.create-title .highlight {
  color: var(--primary);
}

.create-desc {
  font-size: 18px;
  color: var(--olive-gray);
  line-height: 1.75;
  margin: 0;
  max-width: 520px;
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
}

.prompt-textarea {
  padding: 20px;
  font-size: 15px;
  line-height: 1.7;
  border: 1px solid var(--border-warm);
  border-radius: 18px;
  background-color: var(--parchment);
  resize: none;
  transition: all var(--transition-fast);
  box-shadow: inset 0 0 0 1px rgba(20, 20, 19, 0.02);
}

.prompt-textarea:hover {
  border-color: var(--ring-warm);
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
  background: var(--warm-sand);
  border-color: var(--ring-warm);
  color: var(--charcoal-warm);
  font-weight: 600;
}

.home-attachment-btn:hover {
  color: var(--primary-dark);
  border-color: var(--primary-light);
}

.home-attachment-tip {
  color: var(--stone-gray);
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
  border: 1px solid var(--border-cream);
  border-radius: 14px;
  background: rgba(250, 249, 245, 0.6);
}

.home-attachment-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: var(--charcoal-warm);
  font-size: 13px;
}

.home-attachment-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-attachment-meta small {
  flex: 0 0 auto;
  color: var(--muted-warm);
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
  color: var(--stone-gray);
  font-weight: 600;
}

.template-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-btn {
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 600;
  color: var(--charcoal-warm);
  background-color: var(--warm-sand);
  border: 0;
  border-radius: var(--radius-full);
  cursor: pointer;
  transition: all var(--transition-fast);
  box-shadow: 0 0 0 1px var(--ring-warm);
}

.tag-btn:hover {
  color: var(--primary-dark);
  background-color: var(--ivory);
}

.content-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: 24px;
}

.content-section {
  background-color: var(--ivory);
  border-radius: 28px;
  border: 1px solid var(--border-cream);
  padding: 28px;
  box-shadow: var(--shadow-sm);
  min-height: 420px;
}

.content-section:nth-child(even) {
  background-color: var(--near-black);
  border-color: var(--dark-surface);
}

.content-section:nth-child(even) .section-title h2 {
  color: var(--ivory);
}

.content-section:nth-child(even) .empty-box {
  color: var(--warm-silver);
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
  color: var(--primary);
}

.section-title h2 {
  font-size: 28px;
  font-weight: 500;
  color: var(--near-black);
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
  color: var(--stone-gray);
  gap: 12px;
}

.empty-icon {
  font-size: 40px;
  color: var(--ring-warm);
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
