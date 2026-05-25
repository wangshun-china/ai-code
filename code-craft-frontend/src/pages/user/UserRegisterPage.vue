<template>
  <AuthLayout
    title="创建账户"
    desc="填写以下信息完成注册"
    brand-title="加入我们"
    brand-desc="开启您的AI创作之旅"
    :features="features"
    mirror
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
      <div class="auth-tips">
        已有账号？
        <RouterLink to="/user/login" class="auth-link">立即登录</RouterLink>
      </div>
      <a-form-item>
        <a-button type="primary" html-type="submit" class="submit-btn" size="large">
          注册
        </a-button>
      </a-form-item>
    </a-form>
  </AuthLayout>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { userRegister } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import { reactive, type Component } from 'vue'
import { UserOutlined, LockOutlined, SafetyOutlined, RocketOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import AuthLayout from '@/components/AuthLayout.vue'

interface Feature { icon: Component; label: string }

const features: Feature[] = [
  { icon: RocketOutlined, label: '免费使用' },
  { icon: ThunderboltOutlined, label: '即时生成' },
  { icon: SafetyOutlined, label: '数据安全' },
]

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
    router.push({ path: '/user/login', replace: true })
  } else {
    message.error('注册失败，' + res.data.message)
  }
}
</script>
