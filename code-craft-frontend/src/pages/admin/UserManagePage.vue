<template>
  <div id="userManagePage" class="admin-page">
    <div class="page-header">
      <h2 class="page-title">用户管理</h2>
      <p class="page-desc">管理系统用户，支持搜索和删除操作</p>
    </div>
    <!-- 搜索表单 -->
    <div class="search-card">
      <a-form layout="inline" :model="searchParams" @finish="doSearch" class="search-form">
        <a-form-item label="账号">
          <a-input v-model:value="searchParams.userAccount" placeholder="输入账号" class="search-input" />
        </a-form-item>
        <a-form-item label="用户名">
          <a-input v-model:value="searchParams.userName" placeholder="输入用户名" class="search-input" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" class="search-btn">
            <template #icon><SearchOutlined /></template>
            搜索
          </a-button>
        </a-form-item>
      </a-form>
    </div>
    <!-- 表格 -->
    <div class="table-card">
      <a-table
        :columns="columns"
        :data-source="data"
        :pagination="pagination"
        @change="doTableChange"
        class="data-table"
        :row-class-name="(_record, index) => index % 2 === 0 ? 'even-row' : 'odd-row'"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'userAvatar'">
            <a-avatar :src="record.userAvatar" :size="48" class="user-avatar-cell" />
          </template>
          <template v-else-if="column.dataIndex === 'userRole'">
            <a-tag v-if="record.userRole === 'admin'" color="green" class="role-tag">管理员</a-tag>
            <a-tag v-else color="blue" class="role-tag">普通用户</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            <span class="time-cell">{{ formatTime(record.createTime) }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button danger @click="doDelete(record.id)" class="delete-btn">
              <template #icon><DeleteOutlined /></template>
              删除
            </a-button>
          </template>
        </template>
      </a-table>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { deleteUser, listUserVoByPage } from '@/api/userController'
import { message } from 'ant-design-vue'
import { formatTime } from '@/utils/time'
import { useAdminTable } from '@/composables/useAdminTable'
import { SearchOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import '@/assets/admin-common.css'

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '账号', dataIndex: 'userAccount', width: 120 },
  { title: '用户名', dataIndex: 'userName', width: 120 },
  { title: '头像', dataIndex: 'userAvatar', width: 80 },
  { title: '简介', dataIndex: 'userProfile', ellipsis: true },
  { title: '用户角色', dataIndex: 'userRole', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 100 },
]

const { data, searchParams, pagination, doTableChange, doSearch, onMountedFetch } = useAdminTable<
  API.UserVO,
  API.UserQueryRequest
>(listUserVoByPage)

onMountedFetch()

const doDelete = async (id: string) => {
  if (!id) return
  const res = await deleteUser({ id: Number(id) })
  if (res.data.code === 0) {
    message.success('删除成功')
    ;(searchParams as any).pageNum = 1
    doSearch()
  } else {
    message.error('删除失败')
  }
}
</script>

<style scoped>
.user-avatar-cell {
  border: 2px solid transparent;
  background: var(--primary);
  padding: 2px;
}

.role-tag {
  border-radius: 12px;
  font-weight: 500;
}

.search-input {
  min-width: 180px;
}
</style>
