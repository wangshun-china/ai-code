<template>
  <div id="userRegisterPage">
    <div class="register-container">
      <!-- 左侧装饰区域 -->
      <div class="decoration-panel">
        <div class="decoration-content">
          <div class="floating-shapes">
            <div class="shape shape-1"></div>
            <div class="shape shape-2"></div>
            <div class="shape shape-3"></div>
            <div class="shape shape-4"></div>
          </div>
          <div class="brand-section">
            <h2 class="brand-title">加入我们</h2>
            <p class="brand-desc">开启您的AI创作之旅</p>
            <div class="feature-list">
              <div class="feature-item">
                <RocketOutlined class="feature-icon" />
                <span>免费使用</span>
              </div>
              <div class="feature-item">
                <ThunderboltOutlined class="feature-icon" />
                <span>即时生成</span>
              </div>
              <div class="feature-item">
                <SafetyOutlined class="feature-icon" />
                <span>数据安全</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <!-- 右侧表单区域 -->
      <div class="form-panel">
        <div class="form-card">
          <div class="form-header">
            <h2 class="title">创建账户</h2>
            <div class="desc">填写以下信息完成注册</div>
          </div>
          <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
            <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
              <a-input v-model:value="formState.userAccount" placeholder="请输入账号" size="large">
                <template #prefix>
                  <UserOutlined class="input-icon" />
                </template>
              </a-input>
            </a-form-item>
            <a-form-item
              name="userPassword"
              :rules="[
                { required: true, message: '请输入密码' },
                { min: 8, message: '密码不能小于 8 位' },
              ]"
            >
              <a-input-password
                v-model:value="formState.userPassword"
                placeholder="请输入密码"
                size="large"
              >
                <template #prefix>
                  <LockOutlined class="input-icon" />
                </template>
              </a-input-password>
            </a-form-item>
            <a-form-item
              name="checkPassword"
              :rules="[
                { required: true, message: '请确认密码' },
                { min: 8, message: '密码不能小于 8 位' },
                { validator: validateCheckPassword },
              ]"
            >
              <a-input-password
                v-model:value="formState.checkPassword"
                placeholder="请确认密码"
                size="large"
              >
                <template #prefix>
                  <SafetyOutlined class="input-icon" />
                </template>
              </a-input-password>
            </a-form-item>
            <div class="tips">
              已有账号？
              <RouterLink to="/user/login" class="login-link">立即登录</RouterLink>
            </div>
            <a-form-item>
              <a-button type="primary" html-type="submit" class="submit-btn" size="large">
                注册
              </a-button>
            </a-form-item>
          </a-form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { userRegister } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import { reactive } from 'vue'
import { UserOutlined, LockOutlined, SafetyOutlined, RocketOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'

const router = useRouter()

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

const validateCheckPassword = (rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value && value !== formState.userPassword) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const handleSubmit = async (values: API.UserRegisterRequest) => {
  const res = await userRegister(values)
  if (res.data.code === 0) {
    message.success('注册成功')
    router.push({
      path: '/user/login',
      replace: true,
    })
  } else {
    message.error('注册失败，' + res.data.message)
  }
}
</script>

<style scoped>
#userRegisterPage {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #764ba2 0%, #f093fb 50%, #667eea 100%);
  padding: 20px;
}

.register-container {
  display: flex;
  width: 100%;
  max-width: 1000px;
  min-height: 650px;
  background: var(--glass-bg);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 24px;
  overflow: hidden;
  box-shadow: 0 25px 80px rgba(0, 0, 0, 0.3);
}

/* 左侧装饰区域 */
.decoration-panel {
  flex: 1;
  background: linear-gradient(135deg, rgba(118, 75, 162, 0.2) 0%, rgba(240, 147, 251, 0.2) 100%);
  position: relative;
  overflow: hidden;
  display: none;
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
  color: white;
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
}

.shape-1 {
  width: 100px;
  height: 100px;
  background: rgba(255, 255, 255, 0.1);
  top: 10%;
  left: 10%;
  animation-delay: 0s;
}

.shape-2 {
  width: 150px;
  height: 150px;
  background: rgba(255, 255, 255, 0.08);
  top: 60%;
  right: 10%;
  animation-delay: 2s;
}

.shape-3 {
  width: 80px;
  height: 80px;
  background: rgba(255, 255, 255, 0.12);
  bottom: 20%;
  left: 20%;
  animation-delay: 4s;
}

.shape-4 {
  width: 60px;
  height: 60px;
  background: rgba(255, 255, 255, 0.15);
  top: 30%;
  right: 30%;
  animation-delay: 1s;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0) rotate(0deg);
  }
  50% {
    transform: translateY(-20px) rotate(10deg);
  }
}

.brand-section {
  position: relative;
  z-index: 2;
}

.brand-title {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 12px;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);
}

.brand-desc {
  font-size: 16px;
  opacity: 0.9;
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
  background: rgba(255, 255, 255, 0.15);
  border-radius: 30px;
  backdrop-filter: blur(10px);
  transition: var(--transition);
}

.feature-item:hover {
  background: rgba(255, 255, 255, 0.25);
  transform: translateX(5px);
}

.feature-icon {
  font-size: 20px;
}

/* 右侧表单区域 */
.form-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.form-card {
  width: 100%;
  max-width: 380px;
}

.form-header {
  text-align: center;
  margin-bottom: 32px;
}

.title {
  font-size: 28px;
  font-weight: 600;
  margin-bottom: 8px;
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.desc {
  color: #888;
  font-size: 14px;
}

.input-icon {
  color: #aaa;
}

:deep(.ant-input-affix-wrapper),
:deep(.ant-input) {
  border-radius: 12px;
  border: 1px solid #e8e8e8;
  transition: var(--transition);
}

:deep(.ant-input-affix-wrapper:hover),
:deep(.ant-input:hover) {
  border-color: #764ba2;
}

:deep(.ant-input-affix-wrapper-focused),
:deep(.ant-input:focus) {
  border-color: #764ba2;
  box-shadow: 0 0 0 3px rgba(118, 75, 162, 0.1);
}

.tips {
  text-align: center;
  color: #999;
  font-size: 13px;
  margin-bottom: 16px;
}

.login-link {
  color: #764ba2;
  font-weight: 500;
  text-decoration: none;
  transition: var(--transition);
}

.login-link:hover {
  color: #667eea;
}

.submit-btn {
  width: 100%;
  height: 48px;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 500;
  background: linear-gradient(135deg, #764ba2 0%, #667eea 100%);
  border: none;
  box-shadow: 0 4px 15px rgba(118, 75, 162, 0.4);
  transition: var(--transition);
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 25px rgba(118, 75, 162, 0.5);
}

@media (max-width: 768px) {
  .register-container {
    max-width: 450px;
    min-height: auto;
  }
}
</style>