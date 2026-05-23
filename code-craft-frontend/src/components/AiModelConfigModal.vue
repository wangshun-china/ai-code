<template>
  <a-modal
    :open="open"
    title="模型 Key 与可用模型管理"
    width="860px"
    :footer="null"
    @cancel="emit('update:open', false)"
  >
    <div class="ai-model-config">
      <a-alert
        type="info"
        show-icon
        :message="isAdmin ? '管理员可查看所有 Key 明文，管理系统默认和自定义配置。' : '系统默认配置不可修改，你可以新增自定义配置并切换使用。自定义配置的 Key 已脱敏。'"
      />

      <div class="credential-list">
        <div class="credential-head">
          <span></span>
          <span>名称</span>
          <span>API Key</span>
          <span>Base URL</span>
          <span>可用模型</span>
          <span>操作</span>
        </div>
        <div
          v-for="item in credentials"
          :key="item.id"
          class="credential-row"
          :class="{ active: item.defaultSelected }"
        >
          <a-radio
            :checked="item.defaultSelected"
            :disabled="loading || saving"
            @change="selectCredential(item)"
          />
          <strong>{{ item.name }}</strong>
          <code class="key-cell">{{ item.apiKey }}</code>
          <span class="base-url-cell">{{ item.baseUrl }}</span>
          <div class="model-tags">
            <a-tag v-for="model in item.modelNames" :key="model">{{ model }}</a-tag>
          </div>
          <div class="row-actions">
            <a-button v-if="!item.systemDefault || isAdmin" size="small" @click="editCredential(item)">编辑</a-button>
            <a-popconfirm
              v-if="!item.systemDefault"
              title="确认删除这套模型配置？"
              @confirm="deleteCredential(item)"
            >
              <a-button size="small" danger>删除</a-button>
            </a-popconfirm>
            <span v-if="item.systemDefault" class="system-tip">系统默认</span>
          </div>
        </div>
      </div>

      <a-divider />

      <a-form layout="vertical" class="credential-form">
        <a-form-item label="配置名称">
          <a-input v-model:value="form.name" placeholder="例如：我的百炼 Key / 团队备用 Key" />
        </a-form-item>
        <a-form-item label="API Key">
          <a-input
            v-model:value="form.apiKey"
            :placeholder="form.id ? '留空则保持原 API Key' : '请输入 API Key'"
            autocomplete="off"
          />
        </a-form-item>
        <a-form-item label="Base URL">
          <a-input
            v-model:value="form.baseUrl"
            placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1"
          />
        </a-form-item>
        <a-form-item label="可用模型名称">
          <a-textarea
            v-model:value="form.modelNamesText"
            :rows="4"
            placeholder="一行一个或用逗号分隔，例如：&#10;model-name-a&#10;model-name-b"
          />
        </a-form-item>
        <div class="form-actions">
          <a-button @click="resetForm">清空</a-button>
          <a-button type="primary" :loading="saving" @click="saveCredential">
            {{ form.id ? '保存修改' : '添加并设为默认' }}
          </a-button>
        </div>
      </a-form>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  listAiModelCredentials,
  removeAiModelCredential,
  saveAiModelCredential,
  selectAiModelCredential,
} from '@/api/aiModelController'

const props = defineProps<{
  open: boolean
  userId?: number
  isAdmin?: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  saved: []
}>()

const DEFAULT_BASE_URL = 'https://dashscope.aliyuncs.com/compatible-mode/v1'

const loading = ref(false)
const saving = ref(false)
const credentials = ref<API.AiModelCredentialVO[]>([])

const form = reactive({
  id: undefined as number | undefined,
  name: '',
  apiKey: '',
  baseUrl: DEFAULT_BASE_URL,
  modelNamesText: '',
})

watch(
  () => props.open,
  (open) => {
    if (open) {
      loadCredentials()
    }
  },
)

const parseModelNames = (text: string) =>
  text
    .split(/[,，\n\r]+/)
    .map((item) => item.trim())
    .filter(Boolean)

const credentialParams = () => (props.userId ? { userId: props.userId } : {})

const loadCredentials = async () => {
  loading.value = true
  try {
    const res = await listAiModelCredentials(credentialParams())
    if (res.data.code !== 0) {
      message.error(res.data.message || '加载模型配置失败')
      return
    }
    credentials.value = res.data.data || []
  } finally {
    loading.value = false
  }
}

const selectCredential = async (item: API.AiModelCredentialVO) => {
  if (item.defaultSelected) {
    return
  }
  const res = await selectAiModelCredential({
    ...credentialParams(),
    credentialId: item.id || 0,
  })
  if (res.data.code !== 0) {
    message.error(res.data.message || '切换默认模型配置失败')
    return
  }
  message.success('已切换默认模型配置')
  await loadCredentials()
  emit('saved')
}

const editCredential = (item: API.AiModelCredentialVO) => {
  form.id = item.id
  form.name = item.name || ''
  form.apiKey = item.apiKey === '***' || item.apiKey === '*****' ? '' : item.apiKey || ''
  form.baseUrl = item.baseUrl || DEFAULT_BASE_URL
  form.modelNamesText = (item.modelNames || []).join('\n')
}

const deleteCredential = async (item: API.AiModelCredentialVO) => {
  if (!item.id) {
    return
  }
  const res = await removeAiModelCredential({
    ...credentialParams(),
    credentialId: item.id,
  })
  if (res.data.code !== 0) {
    message.error(res.data.message || '删除失败')
    return
  }
  message.success('已删除模型配置')
  resetForm()
  await loadCredentials()
  emit('saved')
}

const resetForm = () => {
  form.id = undefined
  form.name = ''
  form.apiKey = ''
  form.baseUrl = DEFAULT_BASE_URL
  form.modelNamesText = ''
}

const saveCredential = async () => {
  const modelNames = parseModelNames(form.modelNamesText)
  if ((!form.id && !form.apiKey.trim()) || !form.baseUrl.trim() || modelNames.length === 0) {
    message.warning(form.id ? '请填写 Base URL 和至少一个模型名称' : '请填写 API Key、Base URL 和至少一个模型名称')
    return
  }
  saving.value = true
  try {
    const res = await saveAiModelCredential(
      credentialParams(),
      {
        id: form.id,
        name: form.name.trim() || '自定义模型配置',
        apiKey: form.apiKey.trim(),
        baseUrl: form.baseUrl.trim(),
        modelNames,
      },
    )
    if (res.data.code !== 0) {
      message.error(res.data.message || '保存失败')
      return
    }
    message.success(form.id ? '模型配置已更新' : '模型配置已添加并设为默认')
    resetForm()
    await loadCredentials()
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.ai-model-config {
  display: grid;
  gap: 16px;
}

.credential-list {
  display: grid;
  gap: 8px;
}

.credential-head,
.credential-row {
  display: grid;
  grid-template-columns: 32px 120px 160px 190px minmax(0, 1fr) 116px;
  gap: 10px;
  align-items: center;
}

.credential-head {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.credential-row {
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
}

.credential-row.active {
  border-color: #0ea5e9;
  box-shadow: 0 0 0 3px rgba(14, 165, 233, 0.12);
}

.key-cell,
.base-url-cell {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #334155;
}

.model-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  max-height: 74px;
  overflow: auto;
}

.row-actions {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
}

.system-tip {
  color: #64748b;
  font-size: 12px;
}

.credential-form {
  padding: 14px;
  border-radius: 14px;
  background: #f8fafc;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
