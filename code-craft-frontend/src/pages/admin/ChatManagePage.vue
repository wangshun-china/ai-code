<template>
  <div id="chatManagePage">
    <div class="page-header">
      <h2 class="page-title">对话管理</h2>
      <p class="page-desc">管理所有对话历史，支持搜索和删除操作</p>
    </div>
    <!-- 搜索表单 -->
    <div class="search-card">
      <a-form layout="inline" :model="searchParams" @finish="doSearch" class="search-form">
        <a-form-item label="消息内容">
          <a-input v-model:value="searchParams.message" placeholder="输入消息内容" class="search-input" />
        </a-form-item>
        <a-form-item label="消息类型">
          <a-select
            v-model:value="searchParams.messageType"
            placeholder="选择消息类型"
            class="search-select"
          >
            <a-select-option value="">全部</a-select-option>
            <a-select-option value="user">用户消息</a-select-option>
            <a-select-option value="assistant">AI消息</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="应用ID">
          <a-input v-model:value="searchParams.appId" placeholder="输入应用ID" class="search-input small" />
        </a-form-item>
        <a-form-item label="用户ID">
          <a-input v-model:value="searchParams.userId" placeholder="输入用户ID" class="search-input small" />
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
        :scroll="{ x: 1400 }"
        class="data-table"
        :row-class-name="(_record, index) => index % 2 === 0 ? 'even-row' : 'odd-row'"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'message'">
            <a-tooltip :title="record.message">
              <div class="message-text">{{ record.message }}</div>
            </a-tooltip>
          </template>
          <template v-else-if="column.dataIndex === 'messageType'">
            <a-tag :color="record.messageType === 'user' ? 'blue' : 'green'" class="type-tag">
              {{ record.messageType === 'user' ? '👤 用户消息' : '🤖 AI消息' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            <span class="time-cell">{{ formatTime(record.createTime) }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="primary" size="small" @click="viewAppChat(record.appId)" class="action-btn">
                <template #icon><EyeOutlined /></template>
                查看对话
              </a-button>
              <a-popconfirm title="确定要删除这条消息吗？" @confirm="deleteMessage(record.id)">
                <a-button danger size="small" class="action-btn delete-btn">
                  <template #icon><DeleteOutlined /></template>
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { listAllChatHistoryByPageForAdmin } from '@/api/chatHistoryController'
import { formatTime } from '@/utils/time'
import { useAdminTable } from '@/composables/useAdminTable'
import { SearchOutlined, EyeOutlined, DeleteOutlined } from '@ant-design/icons-vue'

const router = useRouter()

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80, fixed: 'left' },
  { title: '消息内容', dataIndex: 'message', width: 300 },
  { title: '消息类型', dataIndex: 'messageType', width: 120 },
  { title: '应用ID', dataIndex: 'appId', width: 80 },
  { title: '用户ID', dataIndex: 'userId', width: 80 },
  { title: '创建时间', dataIndex: 'createTime', width: 160 },
  { title: '操作', key: 'action', width: 180, fixed: 'right' },
]

const { data, searchParams, pagination, doTableChange, doSearch, fetchData, onMountedFetch } =
  useAdminTable<API.ChatHistory, API.ChatHistoryQueryRequest>(listAllChatHistoryByPageForAdmin)

onMountedFetch()

const viewAppChat = (appId: number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}`)
  }
}

const deleteMessage = async (id: number | undefined) => {
  if (!id) return
  try {
    message.success('删除成功')
    fetchData()
  } catch (error) {
    console.error('删除失败：', error)
    message.error('删除失败')
  }
}
</script>

<style scoped>
#chatManagePage {
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
  min-width: 150px;
}

.search-input.small {
  min-width: 100px;
}

.search-select {
  min-width: 120px;
}

.search-input :deep(.ant-input),
.search-select :deep(.ant-select-selector) {
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
  vertical-align: middle;
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

.message-text {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #555;
}

.type-tag {
  border-radius: 12px;
  font-weight: 500;
}

.time-cell {
  color: #666;
  font-size: 13px;
}

.action-btn {
  border-radius: 8px;
  transition: var(--transition);
}

.action-btn:hover {
  transform: scale(1.05);
}

.delete-btn {
  padding: 4px 8px;
}

@media (max-width: 768px) {
  #chatManagePage {
    padding: 16px;
  }

  .search-form {
    flex-direction: column;
  }

  .search-input, .search-select {
    min-width: 100%;
  }
}
</style>