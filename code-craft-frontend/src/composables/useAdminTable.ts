import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ensureAdminAccess, isNoAuthCode } from '@/composables/useAdminGuard'

type AdminTableOptions = {
  ensureAdmin?: boolean
}

/**
 * 管理页面表格通用逻辑
 * @param fetchFn 数据获取函数
 */
export function useAdminTable<T, P extends { pageNum?: number; pageSize?: number }>(
  fetchFn: (params: P) => Promise<any>,
  options: AdminTableOptions = {},
) {
  // 数据
  const data = ref<T[]>([])
  const total = ref(0)
  const loading = ref(false)

  // 搜索条件
  const searchParams = reactive({
    pageNum: 1,
    pageSize: 10,
  }) as P

  // 获取数据
  const fetchData = async (params?: Partial<P>) => {
    loading.value = true
    try {
      if (options.ensureAdmin) {
        const hasAccess = await ensureAdminAccess({ silent: true })
        if (!hasAccess) {
          data.value = []
          total.value = 0
          message.error('没有管理员权限，请重新登录后再试')
          return
        }
      }

      const requestParams = {
        ...searchParams,
        ...params,
      } as P
      let res = await fetchFn(requestParams)

      if (isNoAuthCode(res.data?.code) && options.ensureAdmin) {
        const hasAccess = await ensureAdminAccess({ silent: true, force: true })
        if (hasAccess) {
          res = await fetchFn(requestParams)
        }
      }

      if (res.data.data) {
        data.value = res.data.data.records ?? []
        total.value = res.data.data.totalRow ?? 0
      } else {
        message.error('获取数据失败，' + res.data.message)
      }
    } catch (error) {
      console.error('获取数据失败：', error)
      message.error('获取数据失败')
    } finally {
      loading.value = false
    }
  }

  // 页面加载时请求一次
  const onMountedFetch = () => {
    onMounted(() => {
      fetchData()
    })
  }

  // 分页参数
  const pagination = computed(() => ({
    current: searchParams.pageNum ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
  }))

  // 表格分页变化时的操作
  const doTableChange = (page: { current: number; pageSize: number }) => {
    searchParams.pageNum = page.current
    searchParams.pageSize = page.pageSize
    fetchData()
  }

  // 搜索数据
  const doSearch = () => {
    // 重置页码
    searchParams.pageNum = 1
    fetchData()
  }

  return {
    data,
    total,
    loading,
    searchParams,
    pagination,
    fetchData,
    onMountedFetch,
    doTableChange,
    doSearch,
  }
}
