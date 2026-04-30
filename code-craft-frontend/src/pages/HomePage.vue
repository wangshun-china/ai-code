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
    message.warning('请输入应用描述或上传课程材料')
    return
  }

  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }

  creating.value = true
  try {
    const initPrompt = userPrompt.value.trim() || '请根据我上传的课程大纲、教案、实验指导、设计稿或简历生成一个完整网页。'
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
          <span class="logo-text">EduCraft AI</span>
        </div>
        <p class="logo-desc">教育场景应用生成平台</p>
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
          本项目为教育场景演示分支<br>
          原型运行在云服务器环境<br>
          如遇到问题请联系：
        </p>
        <div class="contact-label">联系邮箱</div>
        <a href="mailto:2606209307@qq.com" class="contact-email">
          2606209307@qq.com
        </a>
        <div class="sponsor-card">
          <div class="sponsor-title">感谢天翼云为本项目提供了一定的支持</div>
          <a href="https://www.ctyun.cn/" target="_blank" rel="noopener noreferrer" class="sponsor-link">
            友情链接：天翼云 https://www.ctyun.cn/
          </a>
          <p class="sponsor-note">
            天翼云秉承央企使命，致力于成为数字经济主力军，投身科技强国伟大事业，为用户提供安全、普惠云服务
          </p>
        </div>
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
            <span>教育场景版</span>
          </div>
          <h1 class="create-title">
            上传课程材料或描述教学需求<br>
            <span class="highlight">AI 帮你生成应用</span>
          </h1>
          <p class="create-desc">
            面向课程主页、资源导航、实验指导、教师展示和校园活动专题页，
            通过自然语言与附件快速生成可运行网页
          </p>
        </div>

        <div class="create-right">
          <div class="input-box">
            <a-textarea
              v-model:value="userPrompt"
              placeholder="例如：请根据我上传的课程大纲生成一个课程主页，包含课程简介、教学安排、实验内容、资源下载和答疑入口..."
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
                  上传课程大纲 / 教案 / 设计稿
                </a-button>
              </a-upload>
              <span class="home-attachment-tip">创建后会先解析课程材料，再进入对话生成</span>
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
            <span class="templates-label">教育场景模板：</span>
            <div class="template-tags">
              <button
                class="tag-btn"
                @click="setPrompt('请根据课程大纲生成一个现代化课程主页，包含课程简介、教学目标、章节安排、资源下载、实验说明和答疑入口，整体风格清晰、专业、适合学生浏览。')"
              >
                课程主页
              </button>
              <button
                class="tag-btn"
                @click="setPrompt('生成一个实验指导专题页，包含实验目标、环境要求、操作步骤、注意事项、结果提交方式和常见问题，页面结构要便于学生按步骤完成实验。')"
              >
                实验指导页
              </button>
              <button
                class="tag-btn"
                @click="setPrompt('为教师生成一个个人展示主页，包含个人简介、研究方向、授课课程、成果展示和联系方式，风格专业、可信、适合高校教师形象展示。')"
              >
                教师主页
              </button>
              <button
                class="tag-btn"
                @click="setPrompt('生成一个校园活动专题页，包含活动介绍、日程安排、嘉宾信息、报名方式、精彩回顾和联系方式，风格年轻、活跃、适合校园传播。')"
              >
                活动专题页
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
    radial-gradient(circle at 26% 10%, rgba(201, 100, 66, 0.13), transparent 28%),
    linear-gradient(135deg, #efe8d8 0%, #f8f1e4 46%, #e6eef0 100%);
}

.sidebar {
  width: 288px;
  background:
    linear-gradient(180deg, rgba(255, 253, 247, 0.96), rgba(232, 241, 235, 0.94)),
    var(--ivory);
  border-right: 1px solid rgba(38, 56, 47, 0.12);
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
  border-bottom: 1px solid rgba(38, 56, 47, 0.1);
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
  color: var(--near-black);
}

.logo-desc {
  font-size: 13px;
  color: var(--olive-gray);
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
  color: var(--charcoal-warm);
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  transition: all var(--transition-fast);
  margin-bottom: 5px;
}

.nav-item:hover,
.nav-item.active {
  background-color: #fffdf7;
  color: var(--primary-dark);
  box-shadow: 0 10px 24px rgba(67, 49, 31, 0.08), 0 0 0 1px rgba(201, 100, 66, 0.16);
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
  background-color: rgba(255, 253, 247, 0.78);
  border-radius: 18px;
  border: 1px solid rgba(38, 56, 47, 0.1);
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
  color: var(--near-black);
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
  color: var(--olive-gray);
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
  background: linear-gradient(180deg, #fffaf0 0%, #f4ead7 100%);
  border-radius: 20px;
  border: 1px solid rgba(201, 100, 66, 0.28);
  box-shadow: 0 14px 34px rgba(67, 49, 31, 0.12), inset 0 0 0 1px rgba(255, 255, 255, 0.5);
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
  color: #3e3a32;
  line-height: 1.8;
  margin: 0 0 12px;
}

.contact-label {
  margin-bottom: 6px;
  color: #7a442f;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.contact-email {
  display: inline-block;
  padding: 8px 12px;
  background-color: #fffdf7;
  color: var(--primary-dark);
  font-size: 13px;
  font-weight: 600;
  border-radius: var(--radius-lg);
  box-shadow: 0 0 0 1px rgba(122, 68, 47, 0.2);
}

.contact-email,
.contact-email:hover {
  color: var(--primary-dark);
}

.contact-email:hover {
  background-color: #f4ead7;
}

.sponsor-card {
  margin-top: 14px;
  padding: 14px;
  background: #f8fbff;
  border: 1px solid rgba(55, 110, 180, 0.28);
  border-radius: 16px;
  box-shadow: inset 3px 0 0 #2f6fbb;
}

.sponsor-title {
  margin-bottom: 8px;
  color: #17385f;
  font-size: 12px;
  font-weight: 800;
  line-height: 1.5;
}

.sponsor-link {
  display: block;
  margin-bottom: 8px;
  color: #0f62b5;
  font-size: 12px;
  font-weight: 700;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.sponsor-link:hover {
  color: #08427a;
}

.sponsor-note {
  margin: 0;
  color: #34465f;
  font-size: 11px;
  line-height: 1.65;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid rgba(38, 56, 47, 0.1);
}

.github-link {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  color: var(--charcoal-warm);
  font-size: 14px;
  font-weight: 500;
  border-radius: var(--radius-lg);
  transition: all var(--transition-fast);
}

.github-link:hover {
  background-color: #fffdf7;
  color: var(--primary-dark);
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
  background: linear-gradient(135deg, #fffdf7 0%, #f6ead8 100%);
  border-radius: 32px;
  border: 1px solid rgba(122, 68, 47, 0.2);
  box-shadow: 0 24px 70px rgba(67, 49, 31, 0.16);
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
  border: 1px solid rgba(122, 68, 47, 0.28);
  border-radius: 18px;
  background-color: #fffaf0;
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
  background: #fffdf7;
  border-radius: 28px;
  border: 1px solid rgba(122, 68, 47, 0.16);
  padding: 28px;
  box-shadow: 0 16px 42px rgba(67, 49, 31, 0.1);
  min-height: 420px;
}

.content-section:nth-child(even) {
  background-color: #f7fbf8;
  border-color: rgba(38, 56, 47, 0.14);
}

.content-section:nth-child(even) .section-title h2 {
  color: var(--near-black);
}

.content-section:nth-child(even) .empty-box {
  color: var(--stone-gray);
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
