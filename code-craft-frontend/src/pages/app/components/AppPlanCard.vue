<template>
  <div v-if="hasStructuredPlan" class="plan-card">
    <div class="plan-card-header">
      <div>
        <div class="plan-card-kicker">实现方案</div>
        <h4>{{ plan?.requirementSummary || '已生成结构化实现方案' }}</h4>
      </div>
      <a-tag v-if="plan?.matchedTemplates?.length" color="blue">
        {{ plan.matchedTemplates.length }} 个参考模板
      </a-tag>
    </div>
    <div v-if="plan?.visualStyle" class="plan-style">
      {{ plan.visualStyle }}
    </div>
    <div class="plan-grid">
      <div v-if="hasPlanItems(plan?.pages)" class="plan-section">
        <strong>页面规划</strong>
        <ul>
          <li v-for="item in plan?.pages" :key="item">{{ item }}</li>
        </ul>
      </div>
      <div v-if="hasPlanItems(plan?.components)" class="plan-section">
        <strong>组件拆分</strong>
        <ul>
          <li v-for="item in plan?.components" :key="item">{{ item }}</li>
        </ul>
      </div>
      <div v-if="hasPlanItems(plan?.interactions)" class="plan-section">
        <strong>关键交互</strong>
        <ul>
          <li v-for="item in plan?.interactions" :key="item">{{ item }}</li>
        </ul>
      </div>
      <div v-if="hasPlanItems(plan?.acceptanceCriteria)" class="plan-section">
        <strong>验收标准</strong>
        <ul>
          <li v-for="item in plan?.acceptanceCriteria" :key="item">{{ item }}</li>
        </ul>
      </div>
    </div>
    <div v-if="hasPlanItems(plan?.risks) || hasPlanItems(plan?.questions)" class="plan-notes">
      <a-alert
        type="warning"
        show-icon
        :message="[...(plan?.risks || []), ...(plan?.questions || [])].join('；')"
      />
    </div>
    <div v-if="hasPlanItems(plan?.filesToChange)" class="plan-files">
      <span v-for="file in plan?.filesToChange" :key="file">{{ file }}</span>
    </div>
  </div>
  <MarkdownRenderer v-else-if="fallbackContent" :content="fallbackContent" />
  <div v-if="planPending" class="plan-actions">
    <a-button type="primary" size="small" :loading="generating" @click="emit('confirm')">
      确认生成
    </a-button>
    <span>不满意可以继续补充需求，我会重新出方案。</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'

const props = defineProps<{
  plan?: API.AppGenerationPlanVO
  fallbackContent?: string
  planPending?: boolean
  generating?: boolean
}>()

const emit = defineEmits<{
  confirm: []
}>()

const hasPlanItems = (items?: string[]) => Array.isArray(items) && items.filter(Boolean).length > 0

const hasStructuredPlan = computed(() => {
  const plan = props.plan
  return !!(
    plan &&
    (plan.requirementSummary ||
      plan.visualStyle ||
      hasPlanItems(plan.pages) ||
      hasPlanItems(plan.components) ||
      hasPlanItems(plan.interactions) ||
      hasPlanItems(plan.acceptanceCriteria) ||
      hasPlanItems(plan.filesToChange))
  )
})
</script>

<style scoped>
.plan-card {
  padding: 16px;
  border: 1px solid rgba(14, 116, 144, 0.18);
  border-radius: 18px;
  background:
    linear-gradient(135deg, rgba(236, 253, 245, 0.92), rgba(255, 251, 235, 0.86)),
    #ffffff;
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.08);
}

.plan-card-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.plan-card-kicker {
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.plan-card h4 {
  margin: 4px 0 0;
  color: #172018;
  font-size: 16px;
  line-height: 1.5;
}

.plan-style {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  color: #334155;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.plan-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.plan-section {
  padding: 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.plan-section strong {
  display: block;
  margin-bottom: 8px;
  color: #164e63;
}

.plan-section ul {
  margin: 0;
  padding-left: 18px;
  color: #3f3b35;
}

.plan-section li + li {
  margin-top: 4px;
}

.plan-notes {
  margin-top: 12px;
}

.plan-files {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.plan-files span {
  padding: 4px 9px;
  border-radius: 999px;
  background: #0f766e;
  color: #ffffff;
  font-size: 12px;
  font-weight: 700;
}

.plan-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(20, 20, 19, 0.08);
}

.plan-actions span {
  color: #5f5b52;
  font-size: 13px;
}

@media (max-width: 768px) {
  .plan-grid {
    grid-template-columns: 1fr;
  }

  .plan-actions {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
