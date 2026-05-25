<template>
  <AuthLayout
    title="欢迎回来"
    desc="登录您的账户继续创作之旅"
    brand-title="AI 应用生成平台"
    brand-desc="不写一行代码，生成完整应用"
    :features="features"
  >
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
          { min: 8, message: '密码长度不能小于 8 位' },
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
      <div class="auth-tips">
        没有账号？
        <RouterLink to="/user/register" class="auth-link">立即注册</RouterLink>
      </div>
      <a-form-item>
        <a-button type="primary" html-type="submit" class="submit-btn" size="large">
          登录
        </a-button>
      </a-form-item>
    </a-form>
  </AuthLayout>
</template>

<script lang="ts" setup>
import { reactive, type Component } from 'vue'
import { userLogin } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined, RocketOutlined, ThunderboltOutlined, SafetyOutlined } from '@ant-design/icons-vue'
import AuthLayout from '@/components/AuthLayout.vue'

interface Feature { icon: Component; label: string }

const features: Feature[] = [
  { icon: RocketOutlined, label: '智能生成' },
  { icon: ThunderboltOutlined, label: '快速部署' },
  { icon: SafetyOutlined, label: '安全可靠' },
]

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const router = useRouter()
const loginUserStore = useLoginUserStore()

const handleSubmit = async (values: any) => {
  const res = await userLogin(values)
  if (res.data.code === 0 && res.data.data) {
    await loginUserStore.fetchLoginUser()
    message.success('登录成功')
    router.push({ path: '/', replace: true })
  } else {
    message.error('登录失败，' + res.data.message)
  }
}
</script>
