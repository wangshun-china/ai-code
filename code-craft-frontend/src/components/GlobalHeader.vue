<template>
  <header class="header">
    <div class="header-content">
      <!-- 仅显示用户操作 -->
      <div class="user-section">
        <template v-if="loginUserStore.loginUser.id">
          <a-dropdown placement="bottomRight">
            <div class="user-trigger">
              <a-avatar :src="loginUserStore.loginUser.userAvatar" class="user-avatar">
                {{ loginUserStore.loginUser.userName?.charAt(0) || 'U' }}
              </a-avatar>
            </div>
            <template #overlay>
              <a-menu class="user-dropdown">
                <a-menu-item @click="doLogout">
                  <LogoutOutlined />
                  <span>退出登录</span>
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>
        <template v-else>
          <a-button type="primary" href="/user/login" class="login-btn">
            登录
          </a-button>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { userLogout } from '@/api/userController.ts'
import { LogoutOutlined } from '@ant-design/icons-vue'

const loginUserStore = useLoginUserStore()
const router = useRouter()

const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({ userName: '未登录' })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
.header {
  display: none; /* 在桌面端隐藏，使用侧边栏导航 */
}

/* 只在移动端显示 */
@media (max-width: 768px) {
  .header {
    display: block;
    position: sticky;
    top: 0;
    z-index: 100;
    background-color: var(--ivory);
    border-bottom: 1px solid var(--border-cream);
  }

  .header-content {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    padding: 12px 16px;
    height: 56px;
  }

  .user-trigger {
    padding: 4px;
    cursor: pointer;
  }

  .user-avatar {
    width: 36px;
    height: 36px;
    background-color: var(--primary);
    color: var(--ivory);
  }

  .login-btn {
    height: 36px;
    padding: 0 20px;
    border-radius: var(--radius-lg);
    font-weight: 500;
    background-color: var(--primary);
    border-color: var(--primary);
  }

  .user-dropdown {
    border-radius: var(--radius-xl);
    box-shadow: var(--shadow-lg);
    border: 1px solid var(--border-cream);
    padding: 4px;
    min-width: 140px;
  }

  .user-dropdown :deep(.ant-dropdown-menu-item) {
    border-radius: var(--radius-md);
    padding: 8px 12px;
  }
}
</style>
