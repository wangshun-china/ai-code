<script setup lang="ts">
import { h } from 'vue'
import { ref, onMounted, computed } from 'vue'
import { Card, Row, Col, Table, Tag, DatePicker, Button, message } from 'ant-design-vue'
import { Chart } from '@antv/g2'
import type { Dayjs } from 'dayjs'
import { ReloadOutlined, LineChartOutlined, ApiOutlined, ClockCircleOutlined, DollarOutlined } from '@ant-design/icons-vue'
import { getAiMetrics } from '@/api/statisticsController'

const { RangePicker } = DatePicker

const loading = ref(false)
const metricsData = ref({
  totalRequests: 0,
  totalTokens: 0,
  inputTokens: 0,
  outputTokens: 0,
  avgResponseTime: 0,
  errorRate: 0,
  dailyStats: [],
  modelStats: [],
  userStats: []
})

const dateRange = ref<[Dayjs, Dayjs] | null>(null)

// 计算属性
const todayRequests = computed(() => {
  const today = new Date().toISOString().split('T')[0]
  const todayStat = metricsData.value.dailyStats.find((s: any) => s.date === today)
  return todayStat?.requests || 0
})

const todayTokens = computed(() => {
  const today = new Date().toISOString().split('T')[0]
  const todayStat = metricsData.value.dailyStats.find((s: any) => s.date === today)
  return todayStat?.tokens || 0
})

// 获取统计数据
const fetchMetrics = async () => {
  loading.value = true
  try {
    const params: any = {}
    if (dateRange.value && dateRange.value[0] && dateRange.value[1]) {
      // 将 Dayjs 对象格式化为字符串
      params.startDate = dateRange.value[0].format('YYYY-MM-DD')
      params.endDate = dateRange.value[1].format('YYYY-MM-DD')
    }
    const res = await getAiMetrics(params)
    if (res.data.code === 0 && res.data.data) {
      metricsData.value = res.data.data
      // 延迟渲染图表，确保 DOM 已更新
      setTimeout(() => {
        try {
          renderCharts()
        } catch (chartError) {
          console.error('图表渲染失败:', chartError)
        }
      }, 0)
    }
  } catch (error) {
    console.error('获取统计数据失败:', error)
    message.error('获取统计数据失败')
  } finally {
    loading.value = false
  }
}

// 渲染图表
let tokenChart: any = null
let requestChart: any = null

const renderCharts = () => {
  try {
    // 清理旧图表
    if (tokenChart) {
      tokenChart.destroy()
      tokenChart = null
    }
    if (requestChart) {
      requestChart.destroy()
      requestChart = null
    }

    // 如果没有数据，不渲染图表
    if (!metricsData.value.dailyStats || metricsData.value.dailyStats.length === 0) {
      return
    }

    // 只有1条数据时显示为柱状图，多条数据时显示为折线图
    const isSingleDay = metricsData.value.dailyStats.length === 1

    // Token 使用趋势图
    const tokenData = metricsData.value.dailyStats.map((s: any) => ({
      date: s.date,
      type: 'Input Tokens',
      value: Number(s.inputTokens) || 0
    })).concat(metricsData.value.dailyStats.map((s: any) => ({
      date: s.date,
      type: 'Output Tokens',
      value: Number(s.outputTokens) || 0
    })))

    tokenChart = new Chart({
      container: 'token-chart',
      autoFit: true,
      height: 300
    })

    tokenChart.data(tokenData)
    tokenChart.scale('value', { nice: true })
    tokenChart.tooltip({ shared: true })

    if (isSingleDay) {
      // 单天数据使用柱状图
      tokenChart.interval().position('type*value').color('type').adjust([{ type: 'dodge' }])
    } else {
      // 多天数据使用折线图
      tokenChart.line().position('date*value').color('type').shape('smooth')
      tokenChart.point().position('date*value').color('type').shape('circle')
    }
    tokenChart.render()

    // 请求趋势图
    const requestData = metricsData.value.dailyStats.map((s: any) => ({
      date: s.date,
      requests: Number(s.requests) || 0,
      errors: Number(s.errors) || 0
    }))

    requestChart = new Chart({
      container: 'request-chart',
      autoFit: true,
      height: 300
    })

    requestChart.data(requestData)
    requestChart.scale('requests', { nice: true })
    requestChart.tooltip({ shared: true })

    if (isSingleDay) {
      // 单天数据使用柱状图显示请求和错误
      requestChart.interval().position('date*requests').color('#0d9488')
    } else {
      // 多天数据使用折线图
      requestChart.interval().position('date*requests').color('#0d9488').label('requests')
      requestChart.line().position('date*errors').color('#ef4444').shape('smooth')
    }
    requestChart.render()
  } catch (error) {
    console.error('图表渲染出错:', error)
  }
}

// 表格列定义
const modelColumns = [
  { title: '模型名称', dataIndex: 'modelName', key: 'modelName' },
  { title: '请求次数', dataIndex: 'requests', key: 'requests', sorter: true },
  { title: 'Token 总数', dataIndex: 'totalTokens', key: 'totalTokens', sorter: true },
  { title: 'Input Tokens', dataIndex: 'inputTokens', key: 'inputTokens' },
  { title: 'Output Tokens', dataIndex: 'outputTokens', key: 'outputTokens' },
  { title: '平均响应时间', dataIndex: 'avgResponseTime', key: 'avgResponseTime', customRender: ({ text }: any) => `${text}ms` },
  { title: '错误次数', dataIndex: 'errors', key: 'errors', customRender: ({ text }: any) => h(Tag, { color: text > 0 ? 'red' : 'green' }, () => text) }
]

const userColumns = [
  { title: '用户ID', dataIndex: 'userId', key: 'userId' },
  { title: '用户名', dataIndex: 'userName', key: 'userName' },
  { title: '请求次数', dataIndex: 'requests', key: 'requests', sorter: true },
  { title: 'Token 消耗', dataIndex: 'totalTokens', key: 'totalTokens', sorter: true },
  { title: '应用数量', dataIndex: 'appCount', key: 'appCount' }
]

onMounted(() => {
  fetchMetrics()
})
</script>

<template>
  <div class="statistics-page">
    <div class="page-header">
      <h1 class="page-title">AI 使用统计</h1>
      <div class="filter-bar">
        <RangePicker v-model:value="dateRange" format="YYYY-MM-DD" />
        <Button type="primary" @click="fetchMetrics" :loading="loading">
          <template #icon><ReloadOutlined /></template>
          刷新
        </Button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <Row :gutter="[16, 16]" class="stats-row">
      <Col :xs="24" :sm="12" :lg="6">
        <Card class="stat-card">
          <div class="stat-header">
            <ApiOutlined class="stat-icon" />
            <div class="stat-content">
              <div class="stat-title">总请求次数</div>
              <div class="stat-value">{{ metricsData.totalRequests }}</div>
            </div>
          </div>
          <div class="stat-trend">
            <span class="trend-label">今日:</span>
            <span class="trend-value">{{ todayRequests }}</span>
          </div>
        </Card>
      </Col>
      <Col :xs="24" :sm="12" :lg="6">
        <Card class="stat-card">
          <div class="stat-header">
            <DollarOutlined class="stat-icon" />
            <div class="stat-content">
              <div class="stat-title">总 Token 消耗</div>
              <div class="stat-value">{{ metricsData.totalTokens }}</div>
            </div>
          </div>
          <div class="stat-trend">
            <span class="trend-label">今日:</span>
            <span class="trend-value">{{ todayTokens }}</span>
          </div>
        </Card>
      </Col>
      <Col :xs="24" :sm="12" :lg="6">
        <Card class="stat-card">
          <div class="stat-header">
            <ClockCircleOutlined class="stat-icon" />
            <div class="stat-content">
              <div class="stat-title">平均响应时间</div>
              <div class="stat-value">{{ metricsData.avgResponseTime }}ms</div>
            </div>
          </div>
          <div class="stat-trend">
            <span class="trend-label">Input:</span>
            <span class="trend-value">{{ metricsData.inputTokens }}</span>
            <span class="trend-label" style="margin-left: 8px;">Output:</span>
            <span class="trend-value">{{ metricsData.outputTokens }}</span>
          </div>
        </Card>
      </Col>
      <Col :xs="24" :sm="12" :lg="6">
        <Card class="stat-card">
          <div class="stat-header">
            <LineChartOutlined class="stat-icon" />
            <div class="stat-content">
              <div class="stat-title">错误率</div>
              <div class="stat-value">{{ metricsData.errorRate.toFixed(2) }}%</div>
            </div>
          </div>
          <div class="stat-trend">
            <span class="trend-label">状态:</span>
            <Tag :color="metricsData.errorRate < 5 ? 'green' : 'red'">
              {{ metricsData.errorRate < 5 ? '正常' : '异常' }}
            </Tag>
          </div>
        </Card>
      </Col>
    </Row>

    <!-- 图表区域 -->
    <Row :gutter="[16, 16]" class="charts-row">
      <Col :xs="24" :lg="12">
        <Card title="Token 使用趋势" class="chart-card">
          <div v-if="!metricsData.dailyStats || metricsData.dailyStats.length === 0" class="empty-chart">
            <p>暂无数据</p>
          </div>
          <div v-else id="token-chart" class="chart-container"></div>
        </Card>
      </Col>
      <Col :xs="24" :lg="12">
        <Card title="请求趋势" class="chart-card">
          <div v-if="!metricsData.dailyStats || metricsData.dailyStats.length === 0" class="empty-chart">
            <p>暂无数据</p>
          </div>
          <div v-else id="request-chart" class="chart-container"></div>
        </Card>
      </Col>
    </Row>

    <!-- 详细数据表格 -->
    <Row :gutter="[16, 16]" class="tables-row">
      <Col :xs="24" :lg="12">
        <Card title="模型使用情况" class="table-card">
          <Table
            :columns="modelColumns"
            :data-source="metricsData.modelStats"
            :loading="loading"
            :pagination="{ pageSize: 5 }"
            size="small"
          />
        </Card>
      </Col>
      <Col :xs="24" :lg="12">
        <Card title="用户 Token 消耗排行" class="table-card">
          <Table
            :columns="userColumns"
            :data-source="metricsData.userStats"
            :loading="loading"
            :pagination="{ pageSize: 5 }"
            size="small"
          />
        </Card>
      </Col>
    </Row>
  </div>
</template>

<style scoped>
.statistics-page {
  padding: 24px;
  background-color: var(--bg-primary);
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.filter-bar {
  display: flex;
  gap: 12px;
}

.stats-row {
  margin-bottom: 24px;
}

.stat-card {
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-light);
  transition: all var(--transition-fast);
}

.stat-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.stat-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  font-size: 32px;
  color: var(--primary);
}

.stat-content {
  flex: 1;
}

.stat-title {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 4px;
  font-weight: 500;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-trend {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--border-light);
  font-size: 13px;
}

.trend-label {
  color: var(--text-tertiary);
  font-weight: 500;
}

.trend-value {
  color: var(--primary);
  font-weight: 700;
  margin-left: 4px;
}

.charts-row {
  margin-bottom: 24px;
}

.chart-card {
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-light);
}

.chart-container {
  width: 100%;
  height: 300px;
}

.tables-row {
  margin-bottom: 24px;
}

.table-card {
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-light);
}

:deep(.ant-card-head) {
  border-bottom: 1px solid var(--border-light);
  font-weight: 600;
}

.empty-chart {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 300px;
  color: var(--text-tertiary);
  font-size: 14px;
}
</style>
