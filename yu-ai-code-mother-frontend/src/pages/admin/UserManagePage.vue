<template>
  <div id="userManagePage">
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
#userManagePage {
  padding: 24px;
  min-height: calc(100vh - 150px);
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 600;
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.page-desc {
  margin: 0;
  color: #888;
  font-size: 14px;
}

.search-card {
  background: var(--glass-bg);
  border-radius: var(--border-radius);
  padding: 20px 24px;
  margin-bottom: 20px;
  box-shadow: var(--glass-shadow);
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.search-input {
  border-radius: 8px;
  min-width: 180px;
}

.search-input :deep(.ant-input) {
  border-radius: 8px;
}

.search-btn {
  border-radius: 8px;
  background: var(--primary-gradient);
  border: none;
  font-weight: 500;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
  transition: var(--transition);
}

.search-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(102, 126, 234, 0.4);
}

.table-card {
  background: var(--glass-bg);
  border-radius: var(--border-radius);
  padding: 20px;
  box-shadow: var(--glass-shadow);
  overflow-x: auto;
}

.data-table {
  border-radius: 12px;
  overflow: hidden;
}

.data-table :deep(.ant-table-thead > tr > th) {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.08) 0%, rgba(118, 75, 162, 0.08) 100%);
  font-weight: 600;
  color: #1a1a2e;
  border-bottom: 2px solid rgba(102, 126, 234, 0.2);
}

.data-table :deep(.ant-table-tbody > tr > td) {
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
  transition: var(--transition-fast);
}

.data-table :deep(.ant-table-tbody > tr:hover > td) {
  background: rgba(102, 126, 234, 0.05);
}

.data-table :deep(.even-row) {
  background: rgba(255, 255, 255, 0.5);
}

.data-table :deep(.odd-row) {
  background: rgba(248, 250, 252, 0.5);
}

.user-avatar-cell {
  border: 2px solid transparent;
  background: var(--primary-gradient);
  padding: 2px;
}

.role-tag {
  border-radius: 12px;
  font-weight: 500;
}

.time-cell {
  color: #666;
  font-size: 13px;
}

.delete-btn {
  border-radius: 8px;
  transition: var(--transition);
}

.delete-btn:hover {
  transform: scale(1.05);
}

@media (max-width: 768px) {
  #userManagePage {
    padding: 16px;
  }

  .search-form {
    flex-direction: column;
  }

  .search-input {
    min-width: 100%;
  }
}
</style>