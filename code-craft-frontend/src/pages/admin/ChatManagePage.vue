<template>
  <div id="chatManagePage" class="admin-page">
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
import '@/assets/admin-common.css'

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
  useAdminTable<API.ChatHistory, API.ChatHistoryQueryRequest>(listAllChatHistoryByPageForAdmin, {
    ensureAdmin: true,
  })

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
.message-text {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--charcoal-warm);
  font-weight: 500;
}

.type-tag {
  border-radius: 12px;
  font-weight: 500;
}

.search-input.small {
  min-width: 100px;
}
</style>
