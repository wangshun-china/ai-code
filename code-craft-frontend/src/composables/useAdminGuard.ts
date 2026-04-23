import { message } from 'ant-design-vue'
import router from '@/router'
import { useLoginUserStore } from '@/stores/loginUser'

type EnsureAdminOptions = {
  silent?: boolean
  redirect?: boolean
  force?: boolean
}

export const isNoAuthCode = (code?: number) => code === 40101 || code === 40300

export async function ensureAdminAccess(options: EnsureAdminOptions = {}) {
  const loginUserStore = useLoginUserStore()
  const loginUser = await loginUserStore.fetchLoginUser({ force: options.force })
  const isAdmin = loginUser?.userRole === 'admin'

  if (!isAdmin) {
    if (!options.silent) {
      message.error('管理员权限已变更，请重新登录或刷新权限')
    }
    if (options.redirect) {
      router.replace(`/user/login?redirect=${router.currentRoute.value.fullPath}`)
    }
  }

  return isAdmin
}
