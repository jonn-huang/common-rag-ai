<template>
  <div class="word-frequency">
    <el-card class="box-card" shadow="never">
      <!-- 统计口径与操作 -->
      <div class="page-header">
        <div class="page-header__info">
          <div class="page-header__title">热点词分析</div>
          <div class="page-header__desc">
            统计最近 {{ windowDays }} 天 AI 问答的真实提问，已过滤停用词与噪音词；同一问题重复提问按真实次数累计。
          </div>
        </div>
        <div class="page-header__actions">
          <el-select v-model="trendDays" class="trend-days-select" @change="loadTrendData">
            <el-option :value="7" label="近 7 天趋势" />
            <el-option :value="14" label="近 14 天趋势" />
            <el-option :value="30" label="近 30 天趋势" />
          </el-select>
          <el-button :loading="isLoading" @click="refreshAll">刷新</el-button>
          <el-button type="primary" :loading="isRebuilding" @click="handleRebuild">重新统计</el-button>
          <el-button type="danger" plain @click="handleClean">清空数据</el-button>
        </div>
      </div>

      <!-- 概览指标 -->
      <div class="summary">
        <div class="summary__item">
          <span class="summary__label">热词条数</span>
          <span class="summary__value">{{ listData.length }}</span>
        </div>
        <div class="summary__item">
          <span class="summary__label">累计出现次数</span>
          <span class="summary__value">{{ totalCount }}</span>
        </div>
        <div class="summary__item">
          <span class="summary__label">最高词频</span>
          <span class="summary__value">{{ maxCount }}</span>
        </div>
        <div class="summary__item">
          <span class="summary__label">最近统计时间</span>
          <span class="summary__value summary__value--text">{{ latestUpdate || '-' }}</span>
        </div>
      </div>

      <!-- 图表区 -->
      <div class="charts-container">
        <div class="chart-item">
          <div class="chart-item__header">
            <h3>词云展示</h3>
            <span class="chart-item__hint">字号越大代表出现次数越多</span>
          </div>
          <div class="chart-item__body">
            <div ref="wordCloudRef" v-loading="isLoading" class="chart-box"></div>
            <div v-if="!hasData" class="chart-item__empty">
              暂无提问日志，请先在问答页提问，或点击「重新统计」
            </div>
          </div>
        </div>

        <div class="chart-item">
          <div class="chart-item__header">
            <h3>TOP10 热词</h3>
            <span class="chart-item__hint">按真实出现次数降序</span>
          </div>
          <div class="chart-item__body">
            <div ref="barChartRef" v-loading="isLoading" class="chart-box"></div>
            <div v-if="!hasData" class="chart-item__empty">暂无可统计的热词</div>
          </div>
        </div>

        <div class="chart-item">
          <div class="chart-item__header">
            <h3>词频分布</h3>
            <span class="chart-item__hint">按当前最高词频动态分档</span>
          </div>
          <div class="chart-item__body">
            <div ref="pieChartRef" v-loading="isLoading" class="chart-box"></div>
            <div v-if="!hasData" class="chart-item__empty">暂无可统计的热词</div>
          </div>
        </div>

        <div class="chart-item">
          <div class="chart-item__header">
            <h3>热词趋势</h3>
            <span class="chart-item__hint">按天统计 TOP{{ trendTopN }} 词的真实出现次数</span>
          </div>
          <div class="chart-item__body">
            <div ref="lineChartRef" v-loading="isTrendLoading" class="chart-box"></div>
            <div v-if="!hasTrendData" class="chart-item__empty">
              所选区间内没有提问记录，暂无趋势数据
            </div>
          </div>
        </div>
      </div>

      <!-- 明细表格 -->
      <div class="detail-panel">
        <div class="detail-panel__header">
          <div class="detail-panel__title">热词明细</div>
          <div class="detail-panel__actions">
            <el-input
              v-model="queryParams.word"
              class="detail-panel__search"
              placeholder="搜索热词"
              clearable
              @keyup.enter="handleSearch"
              @clear="handleSearch"
            />
            <el-select v-model="queryParams.orderBy" class="detail-panel__order" @change="handleSearch">
              <el-option value="count" label="按次数排序" />
              <el-option value="updateTime" label="按统计时间排序" />
            </el-select>
            <el-button type="primary" plain @click="handleSearch">查询</el-button>
          </div>
        </div>

        <el-table
          v-loading="isDetailLoading"
          :data="detailList"
          border
          height="320"
          style="width: 100%"
        >
          <el-table-column prop="word" label="热词" min-width="160" show-overflow-tooltip />
          <el-table-column prop="countNum" label="出现次数" width="120" align="right" />
          <el-table-column label="占比" width="120" align="right">
            <template #default="scope">
              {{ formatPercent(scope.row.countNum) }}
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="最近出现时间" width="200" />
        </el-table>

        <div class="pagination">
          <el-pagination
            v-model:current-page="queryParams.page"
            v-model:page-size="queryParams.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="detailTotal"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="loadDetailData"
          />
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import 'echarts-wordcloud'
import {
  cleanFrequencyApi,
  getFrequencyTrendApi,
  getFrequencyWindowApi,
  listFrequencyApi,
  queryFrequencyApi,
  rebuildFrequencyApi,
  type FrequencyInfo,
  type FrequencyTrend,
} from '@/api/FrequencyApi'

/** 词云最多展示的词数，避免长尾词挤满画布 */
const WORD_CLOUD_LIMIT = 60

/** 趋势图展示的词条数 */
const trendTopN = 5

const wordCloudRef = ref<HTMLElement | null>(null)
const barChartRef = ref<HTMLElement | null>(null)
const pieChartRef = ref<HTMLElement | null>(null)
const lineChartRef = ref<HTMLElement | null>(null)

const charts: Record<string, echarts.ECharts | null> = {
  wordCloud: null,
  barChart: null,
  pieChart: null,
  lineChart: null,
}

const listData = ref<FrequencyInfo[]>([])
const detailList = ref<FrequencyInfo[]>([])
const detailTotal = ref(0)
const trendData = ref<FrequencyTrend>({ dates: [], series: [] })
const windowDays = ref(30)
const trendDays = ref(14)

const isLoading = ref(false)
const isTrendLoading = ref(false)
const isDetailLoading = ref(false)
const isRebuilding = ref(false)

const queryParams = ref({
  page: 1,
  pageSize: 10,
  word: '',
  orderBy: 'count' as 'count' | 'updateTime',
})

const hasData = computed(() => listData.value.length > 0)
const hasTrendData = computed(() => trendData.value.series.length > 0 && trendData.value.dates.length > 0)
const totalCount = computed(() => listData.value.reduce((sum, item) => sum + (item.countNum || 0), 0))
const maxCount = computed(() => listData.value.reduce((max, item) => Math.max(max, item.countNum || 0), 0))
const latestUpdate = computed(() => {
  const values = listData.value.map((item) => item.updateTime).filter(Boolean)
  if (!values.length) {
    return ''
  }
  return values.reduce((latest, current) =>
    parseTime(current) >= parseTime(latest) ? current : latest,
  )
})

const parseTime = (value: string) => {
  const timestamp = new Date(value.replace(/-/g, '/')).getTime()
  return Number.isNaN(timestamp) ? 0 : timestamp
}

const formatPercent = (countNum: number) => {
  if (!totalCount.value) {
    return '0.00%'
  }
  return `${((countNum / totalCount.value) * 100).toFixed(2)}%`
}

const initCharts = () => {
  if (wordCloudRef.value) {
    charts.wordCloud = echarts.init(wordCloudRef.value)
  }
  if (barChartRef.value) {
    charts.barChart = echarts.init(barChartRef.value)
  }
  if (pieChartRef.value) {
    charts.pieChart = echarts.init(pieChartRef.value)
  }
  if (lineChartRef.value) {
    charts.lineChart = echarts.init(lineChartRef.value)
  }
}

// 词云：字号与出现次数成正比，颜色按固定色板取值，保证刷新后配色稳定
const renderWordCloud = () => {
  const palette = ['#2f7cf6', '#1aa7a0', '#f0932b', '#8e6bf2', '#e0567a', '#3fa45b', '#4a6fa5']
  const data = listData.value.slice(0, WORD_CLOUD_LIMIT).map((item, index) => ({
    name: item.word,
    value: item.countNum,
    textStyle: { color: palette[index % palette.length] },
  }))

  const option: any = {
    tooltip: {
      formatter: (params: any) => `${params.name}：${params.value} 次`,
    },
    series: [
      {
        type: 'wordCloud',
        shape: 'circle',
        left: 'center',
        top: 'center',
        width: '92%',
        height: '92%',
        sizeRange: [12, 52],
        rotationRange: [-45, 45],
        rotationStep: 45,
        gridSize: 8,
        drawOutOfBound: false,
        textStyle: {
          fontFamily: 'sans-serif',
          fontWeight: 'bold',
        },
        emphasis: {
          focus: 'self',
          textStyle: { shadowBlur: 10, shadowColor: '#333' },
        },
        data,
      },
    ],
  }
  charts.wordCloud?.setOption(option, true)
}

// TOP10 横向条形图，标签直接显示真实次数
const renderTopBar = () => {
  const top10 = [...listData.value].sort((a, b) => b.countNum - a.countNum).slice(0, 10)

  const option: any = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any) => `${params[0].name}：${params[0].value} 次`,
    },
    grid: { left: 8, right: 48, top: 8, bottom: 8, containLabel: true },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: {
      type: 'category',
      inverse: true,
      data: top10.map((item) => item.word),
      axisLabel: { width: 90, overflow: 'truncate' },
    },
    series: [
      {
        name: '出现次数',
        type: 'bar',
        barMaxWidth: 18,
        data: top10.map((item) => item.countNum),
        label: { show: true, position: 'right', fontSize: 12, color: '#48576c' },
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#83bff6' },
            { offset: 1, color: '#188df0' },
          ]),
        },
      },
    ],
  }
  charts.barChart?.setOption(option, true)
}

// 词频分布按当前最高词频动态分档，不再使用固定的 10000 阈值
const renderDistribution = () => {
  const max = maxCount.value
  const highLine = Math.max(1, Math.ceil((max * 2) / 3))
  const midLine = Math.max(1, Math.ceil(max / 3))
  const tiers = [
    { name: `高频（≥${highLine} 次）`, test: (value: number) => value >= highLine },
    { name: `中频（${midLine}-${highLine - 1} 次）`, test: (value: number) => value >= midLine && value < highLine },
    { name: `低频（<${midLine} 次）`, test: (value: number) => value < midLine },
  ]

  const option: any = {
    tooltip: { trigger: 'item', formatter: '{b}：{c} 个（{d}%）' },
    legend: { orient: 'vertical', left: 8, top: 'middle', itemWidth: 10, itemHeight: 10 },
    series: [
      {
        name: '词频分布',
        type: 'pie',
        radius: ['38%', '62%'],
        center: ['58%', '52%'],
        avoidLabelOverlap: true,
        data: tiers.map((tier) => ({
          name: tier.name,
          value: listData.value.filter((item) => tier.test(item.countNum || 0)).length,
        })),
        label: { formatter: '{b}\n{c} 个' },
        emphasis: {
          itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.4)' },
        },
      },
    ],
  }
  charts.pieChart?.setOption(option, true)
}

// 趋势图完全使用后端按天统计的真实次数
const renderTrend = () => {
  const option: any = {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, type: 'scroll' },
    grid: { left: 8, right: 16, top: 40, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trendData.value.dates.map((date) => date.slice(5)),
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: trendData.value.series.map((item) => ({
      name: item.word,
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: item.data,
    })),
  }
  charts.lineChart?.setOption(option, true)
}

const loadListData = async () => {
  isLoading.value = true
  try {
    const response = await listFrequencyApi()
    if (response.code === 0 && Array.isArray(response.data)) {
      listData.value = response.data
      renderWordCloud()
      renderTopBar()
      renderDistribution()
    } else {
      ElMessage.error(response.message || '获取热词数据失败')
    }
  } catch (error) {
    console.error('获取热词数据失败:', error)
    ElMessage.error('获取热词数据失败')
  } finally {
    isLoading.value = false
  }
}

const loadTrendData = async () => {
  isTrendLoading.value = true
  try {
    const response = await getFrequencyTrendApi(trendDays.value, trendTopN)
    if (response.code === 0 && response.data) {
      trendData.value = {
        dates: response.data.dates || [],
        series: response.data.series || [],
      }
      renderTrend()
    } else {
      ElMessage.error(response.message || '获取趋势数据失败')
    }
  } catch (error) {
    console.error('获取趋势数据失败:', error)
    ElMessage.error('获取趋势数据失败')
  } finally {
    isTrendLoading.value = false
  }
}

const loadDetailData = async () => {
  isDetailLoading.value = true
  try {
    const response = await queryFrequencyApi({ ...queryParams.value })
    if (response.code === 0 && response.data) {
      detailList.value = response.data.records || []
      detailTotal.value = response.data.total || 0
    } else {
      ElMessage.error(response.message || '获取热词明细失败')
    }
  } catch (error) {
    console.error('获取热词明细失败:', error)
    ElMessage.error('获取热词明细失败')
  } finally {
    isDetailLoading.value = false
  }
}

const loadWindowDays = async () => {
  try {
    const response = await getFrequencyWindowApi()
    if (response.code === 0 && typeof response.data === 'number' && response.data > 0) {
      windowDays.value = response.data
    }
  } catch (error) {
    console.error('获取统计窗口失败:', error)
  }
}

const refreshAll = async () => {
  await Promise.all([loadListData(), loadTrendData(), loadDetailData()])
}

const handleSearch = () => {
  queryParams.value.page = 1
  loadDetailData()
}

const handleSizeChange = (size: number) => {
  queryParams.value.pageSize = size
  queryParams.value.page = 1
  loadDetailData()
}

const handleRebuild = () => {
  ElMessageBox.confirm(
    '将按最近的真实提问重新统计热词，结果会覆盖现有数据，是否继续？',
    '重新统计',
    { confirmButtonText: '开始统计', cancelButtonText: '取消', type: 'warning' },
  )
    .then(async () => {
      isRebuilding.value = true
      try {
        const response = await rebuildFrequencyApi()
        if (response.code === 0) {
          ElMessage.success(`统计完成，共生成 ${response.data ?? 0} 个热词`)
          await refreshAll()
        } else {
          ElMessage.error(response.message || '重新统计失败')
        }
      } catch (error) {
        console.error('重新统计失败:', error)
        ElMessage.error('重新统计失败')
      } finally {
        isRebuilding.value = false
      }
    })
    .catch(() => ElMessage.info('已取消重新统计'))
}

const handleClean = () => {
  ElMessageBox.confirm(
    '清空后热词数据会立即失效，需等待下次统计任务或手动点击「重新统计」才能恢复，确定清空吗？',
    '清空数据',
    { confirmButtonText: '确定清空', cancelButtonText: '取消', type: 'warning' },
  )
    .then(async () => {
      try {
        const response = await cleanFrequencyApi()
        if (response.code === 0) {
          ElMessage.success(response.data || '清空完成')
          queryParams.value.page = 1
          await refreshAll()
        } else {
          ElMessage.error(response.message || '清空数据失败')
        }
      } catch (error) {
        console.error('清空数据失败:', error)
        ElMessage.error('清空数据失败')
      }
    })
    .catch(() => ElMessage.info('已取消清空操作'))
}

const handleResize = () => {
  Object.values(charts).forEach((chart) => chart?.resize())
}

onMounted(async () => {
  await nextTick()
  initCharts()
  await loadWindowDays()
  await refreshAll()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  Object.values(charts).forEach((chart) => chart?.dispose())
})
</script>

<style scoped lang="less">
.word-frequency {
  height: var(--app-content-h);
  padding: 20px;
  box-sizing: border-box;
  overflow-y: auto;

  .box-card {
    :deep(.el-card__body) {
      padding: 20px;
    }
  }
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;

  &__title {
    font-size: 18px;
    font-weight: 600;
    color: #16233a;
  }

  &__desc {
    margin-top: 6px;
    font-size: 12px;
    line-height: 1.6;
    color: #8b9bb2;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }
}

.trend-days-select {
  width: 130px;
}

.summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;

  &__item {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 12px 16px;
    border: 1px solid #e8eef7;
    border-radius: 10px;
    background: #fbfcff;
  }

  &__label {
    font-size: 12px;
    color: #8b9bb2;
  }

  &__value {
    font-size: 20px;
    font-weight: 600;
    color: #2f7cf6;

    &--text {
      font-size: 13px;
      font-weight: 500;
      color: #48576c;
    }
  }
}

.charts-container {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;

  .chart-item {
    display: flex;
    flex-direction: column;
    padding: 12px 14px 8px;
    border: 1px solid #e8eef7;
    border-radius: 10px;
    background: #fff;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);

    &__header {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 8px;

      h3 {
        margin: 0;
        font-size: 14px;
        font-weight: 600;
        color: #24344d;
      }
    }

    &__hint {
      font-size: 12px;
      color: #9aa8bb;
    }

    &__body {
      position: relative;
      height: 240px;
      margin-top: 6px;
    }

    &__empty {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0 24px;
      text-align: center;
      font-size: 13px;
      color: #9aa8bb;
      background: rgba(255, 255, 255, 0.92);
    }

    .chart-box {
      width: 100%;
      height: 100%;
    }
  }
}

.detail-panel {
  margin-top: 20px;

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 12px;
    flex-wrap: wrap;
  }

  &__title {
    font-size: 14px;
    font-weight: 600;
    color: #24344d;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  &__search {
    width: 200px;
  }

  &__order {
    width: 150px;
  }
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 1200px) {
  .summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .charts-container {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>