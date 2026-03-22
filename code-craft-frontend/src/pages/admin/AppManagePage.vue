<template>
  <div id="appManagePage">
    <div class="page-header">
      <h2 class="page-title">应用管理</h2>
      <p class="page-desc">管理所有应用，支持编辑、精选和删除操作</p>
    </div>
    <!-- 搜索表单 -->
    <div class="search-card">
      <a-form layout="inline" :model="searchParams" @finish="doSearch" class="search-form">
        <a-form-item label="应用名称">
          <a-input v-model:value="searchParams.appName" placeholder="输入应用名称" class="search-input" />
        </a-form-item>
        <a-form-item label="创建者">
          <a-input v-model:value="searchParams.userId" placeholder="输入用户ID" class="search-input" />
        </a-form-item>
        <a-form-item label="生成类型">
          <a-select
            v-model:value="searchParams.codeGenType"
            placeholder="选择生成类型"
            class="search-select"
          >
            <a-select-option value="">全部</a-select-option>
            <a-select-option
              v-for="option in CODE_GEN_TYPE_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </a-select-option>
          </a-select>
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
        :scroll="{ x: 1200 }"
        class="data-table"
        :row-class-name="(_record, index) => index % 2 === 0 ? 'even-row' : 'odd-row'"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'cover'">
            <a-image v-if="record.cover" :src="record.cover" :width="80" :height="60" class="cover-image" />
            <div v-else class="no-cover">
              <span>📷</span>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'initPrompt'">
            <a-tooltip :title="record.initPrompt">
              <div class="prompt-text">{{ record.initPrompt }}</div>
            </a-tooltip>
          </template>
          <template v-else-if="column.dataIndex === 'codeGenType'">
            <a-tag color="blue" class="type-tag">{{ formatCodeGenType(record.codeGenType) }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'priority'">
            <a-tag v-if="record.priority === 99" color="gold" class="priority-tag">⭐ 精选</a-tag>
            <span v-else class="priority-num">{{ record.priority || 0 }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'deployedTime'">
            <span v-if="record.deployedTime" class="time-cell">{{ formatTime(record.deployedTime) }}</span>
            <span v-else class="not-deployed">未部署</span>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            <span class="time-cell">{{ formatTime(record.createTime) }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'user'">
            <UserInfo :user="record.user" size="small" />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="primary" size="small" @click="editApp(record)" class="action-btn">
                <template #icon><EditOutlined /></template>
                编辑
              </a-button>
              <a-button
                type="default"
                size="small"
                @click="toggleFeatured(record)"
                :class="['action-btn', record.priority === 99 ? 'featured-active' : 'featured-btn']"
              >
                {{ record.priority === 99 ? '取消精选' : '精选' }}
              </a-button>
              <a-popconfirm title="确定要删除这个应用吗？" @confirm="deleteApp(record.id)">
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
import { listAppVoByPageByAdmin, deleteAppByAdmin, updateAppByAdmin } from '@/api/appController'
import { CODE_GEN_TYPE_OPTIONS, formatCodeGenType } from '@/utils/codeGenTypes'
import { formatTime } from '@/utils/time'
import UserInfo from '@/components/UserInfo.vue'
import { useAdminTable } from '@/composables/useAdminTable'
import { SearchOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'

const router = useRouter()

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80, fixed: 'left' },
  { title: '应用名称', dataIndex: 'appName', width: 150 },
  { title: '封面', dataIndex: 'cover', width: 100 },
  { title: '初始提示词', dataIndex: 'initPrompt', width: 200 },
  { title: '生成类型', dataIndex: 'codeGenType', width: 100 },
  { title: '优先级', dataIndex: 'priority', width: 90 },
  { title: '部署时间', dataIndex: 'deployedTime', width: 160 },
  { title: '创建者', dataIndex: 'user', width: 120 },
  { title: '创建时间', dataIndex: 'createTime', width: 160 },
  { title: '操作', key: 'action', width: 200, fixed: 'right' },
]

const { data, searchParams, pagination, doTableChange, doSearch, fetchData, onMountedFetch } =
  useAdminTable<API.AppVO, API.AppQueryRequest>(listAppVoByPageByAdmin)

onMountedFetch()

const editApp = (app: API.AppVO) => {
  router.push(`/app/edit/${app.id}`)
}

const toggleFeatured = async (app: API.AppVO) => {
  if (!app.id) return
  const newPriority = app.priority === 99 ? 0 : 99
  try {
    const res = await updateAppByAdmin({ id: app.id, priority: newPriority })
    if (res.data.code === 0) {
      message.success(
        newPriority === 99 ? '已设为精选，首页约5分钟后更新' : '已取消精选，首页约5分钟后更新',
      )
      fetchData()
    } else {
      message.error('操作失败：' + res.data.message)
    }
  } catch (error) {
    console.error('操作失败：', error)
    message.error('操作失败')
  }
}

const deleteApp = async (id: number | undefined) => {
  if (!id) return
  try {
    const res = await deleteAppByAdmin({ id })
    if (res.data.code === 0) {
      message.success('删除成功')
      fetchData()
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    console.error('删除失败：', error)
    message.error('删除失败')
  }
}
</script>

<style scoped>
#appManagePage {
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
  color: #333333;
  font-size: 14px;
  font-weight: 500;
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

.search-select {
  min-width: 150px;
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

.cover-image {
  border-radius: 8px;
  object-fit: cover;
}

.no-cover {
  width: 80px;
  height: 60px;
  background: linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 20px;
}

.prompt-text {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #333333;
  font-weight: 500;
}

.type-tag, .priority-tag {
  border-radius: 12px;
}

.priority-num {
  color: #888;
}

.time-cell {
  color: #666;
  font-size: 13px;
}

.not-deployed {
  color: #aaa;
  font-size: 13px;
  font-style: italic;
}

.action-btn {
  border-radius: 8px;
  transition: var(--transition);
}

.action-btn:hover {
  transform: scale(1.05);
}

.featured-btn {
  border-color: #faad14;
  color: #faad14;
}

.featured-btn:hover {
  background: #faad14;
  color: white;
}

.featured-active {
  background: linear-gradient(135deg, #faad14 0%, #d48806 100%);
  border-color: #faad14;
  color: white;
}

.delete-btn {
  padding: 4px 8px;
}

@media (max-width: 768px) {
  #appManagePage {
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