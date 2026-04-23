import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getLoginUser } from '@/api/userController.ts'

/**
 * 登录用户信息
 */
export const useLoginUserStore = defineStore('loginUser', () => {
  // 默认值
  const loginUser = ref<API.LoginUserVO>({
    userName: '未登录',
  })

  // 获取登录用户信息
  async function fetchLoginUser(options?: { force?: boolean }) {
    const res = await getLoginUser({
      params: options?.force ? { refreshAuth: true } : undefined,
    })
    if (res.data.code === 0 && res.data.data) {
      loginUser.value = res.data.data
    }
    return loginUser.value
  }

  // 更新登录用户信息
  function setLoginUser(newLoginUser: any) {
    loginUser.value = newLoginUser
  }

  return { loginUser, fetchLoginUser, setLoginUser }
})
