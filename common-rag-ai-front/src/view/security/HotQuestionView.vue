<template>
  <div class="hot-question-manage">
    <el-card class="box-card" shadow="never">
      <!-- 工具栏 -->
      <div class="table-header">
        <div class="table-header__filters">
          <el-input
            v-model="queryParams.keyword"
            class="filter-input"
            placeholder="搜索问题文案"
            clearable
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
          <el-select
            v-model="queryParams.source"
            class="filter-select"
            placeholder="全部来源"
            clearable
            @change="handleSearch"
          >
            <el-option value="AUTO" label="自动挖掘" />
            <el-option value="MANUAL" label="人工维护" />
          </el-select>
          <el-select
            v-model="queryParams.status"
            class="filter-select"
            placeholder="全部状态"
            clearable
            @change="handleSearch"
          >
            <el-option :value="1" label="启用" />
            <el-option :value="0" label="屏蔽" />
          </el-select>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
        </div>
        <div class="table-header__actions">
          <el-button type="primary" @click="handleAdd">新增问题</el-button>
          <el-button :loading="isRebuilding" @click="handleRebuild">立即挖掘</el-button>
        </div>
      </div>

      <div class="table-tip">
        问答页按「置顶 → 排序值 → 提问次数」展示前 6 条；自动挖掘只会更新自动行的提问次数与热词，不会覆盖人工修改的文案、置顶、排序与状态。
      </div>

      <!-- 表格 -->
      <div class="table-container">
        <el-table
          v-loading="isLoading"
          element-loading-text="加载中..."
          element-loading-background="rgba(255, 255, 255, 0.8)"
          :data="questionList"
          style="width: 100%"
          border
          height="calc(var(--app-content-h) - 260px)"
        >
          <el-table-column prop="question" label="问题文案" min-width="260" show-overflow-tooltip />
          <el-table-column label="关联热词" min-width="180">
            <template #default="scope">
              <template v-if="scope.row.keywords?.length">
                <el-tag
                  v-for="keyword in scope.row.keywords"
                  :key="keyword"
                  class="keyword-tag"
                  size="small"
                  type="info"
                >
                  {{ keyword }}
                </el-tag>
              </template>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="source" label="来源" width="110" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.source === 'MANUAL' ? 'success' : 'info'" size="small">
                {{ scope.row.source === 'MANUAL' ? '人工维护' : '自动挖掘' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="hitCount" label="提问次数" width="100" align="right" />
          <el-table-column label="置顶" width="90" align="center">
            <template #default="scope">
              <el-switch
                :model-value="scope.row.pinned === 1"
                size="small"
                @change="(value: string | number | boolean) => handleToggle(scope.row, 'pinned', Boolean(value))"
              />
            </template>
          </el-table-column>
          <el-table-column label="排序值" width="120" align="center">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.sortNum"
                :min="0"
                :max="9999"
                :controls="false"
                size="small"
                class="sort-input"
                @change="handleSortChange(scope.row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="scope">
              <el-switch
                :model-value="scope.row.status === 1"
                size="small"
                @change="(value: string | number | boolean) => handleToggle(scope.row, 'status', Boolean(value))"
              />
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" width="180" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="handleEdit(scope.row)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="queryParams.page"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="loadQuestionList"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑热门问题' : '新增热门问题'" width="560px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="问题文案" prop="question">
          <el-input
            v-model="form.question"
            type="textarea"
            :rows="3"
            maxlength="120"
            show-word-limit
            placeholder="请输入希望在问答页展示的问题，例如：差旅报销需要在几天内提交？"
          />
        </el-form-item>
        <el-form-item label="关联热词">
          <el-select
            v-model="form.keywords"
            class="keyword-select"
            multiple
            filterable
            allow-create
            default-first-option
            :reserve-keyword="false"
            placeholder="可直接输入热词后回车新增"
          >
            <el-option v-for="keyword in form.keywords" :key="keyword" :value="keyword" :label="keyword" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序值">
          <el-input-number v-model="form.sortNum" :min="0" :max="9999" controls-position="right" />
          <span class="form-hint">越小越靠前</span>
        </el-form-item>
        <el-form-item label="置顶">
          <el-switch v-model="form.pinned" :active-value="1" :inactive-value="0" />
          <span class="form-hint">置顶问题始终排在最前</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
          <span class="form-hint">屏蔽后问答页不再展示</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="isSaving" @click="submitForm">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  changeHotQuestionStatusApi,
  deleteHotQuestionApi,
  queryHotQuestionApi,
  rebuildHotQuestionApi,
  saveHotQuestionApi,
  type HotQuestionInfo,
  type HotQuestionSaveDTO,
} from '@/api/HotQuestionApi'

const questionList = ref<HotQuestionInfo[]>([])
const total = ref(0)
const isLoading = ref(false)
const isRebuilding = ref(false)
const isSaving = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()

const queryParams = ref<{
  page: number
  pageSize: number
  keyword: string
  source: string
  status: number | undefined
}>({
  page: 1,
  pageSize: 10,
  keyword: '',
  source: '',
  status: undefined,
})

const form = ref<HotQuestionSaveDTO & { keywords: string[] }>({
  id: undefined,
  question: '',
  keywords: [],
  pinned: 0,
  sortNum: 0,
  status: 1,
})

const formRules: FormRules = {
  question: [{ required: true, message: '请输入问题文案', trigger: 'blur' }],
}

const buildParams = () => ({
  page: queryParams.value.page,
  pageSize: queryParams.value.pageSize,
  keyword: queryParams.value.keyword || undefined,
  source: queryParams.value.source || undefined,
  status: typeof queryParams.value.status === 'number' ? queryParams.value.status : undefined,
})

const loadQuestionList = async () => {
  isLoading.value = true
  try {
    const response = await queryHotQuestionApi(buildParams())
    if (response.code === 0 && response.data) {
      questionList.value = response.data.records || []
      total.value = response.data.total || 0
    } else {
      ElMessage.error(response.message || '获取热门问题失败')
    }
  } catch (error) {
    console.error('获取热门问题失败:', error)
    ElMessage.error('获取热门问题失败')
  } finally {
    isLoading.value = false
  }
}

const handleSearch = () => {
  queryParams.value.page = 1
  loadQuestionList()
}

const handleSizeChange = (size: number) => {
  queryParams.value.pageSize = size
  queryParams.value.page = 1
  loadQuestionList()
}

const handleAdd = () => {
  form.value = { id: undefined, question: '', keywords: [], pinned: 0, sortNum: 0, status: 1 }
  dialogVisible.value = true
}

const handleEdit = (row: HotQuestionInfo) => {
  form.value = {
    id: row.id,
    question: row.question,
    keywords: [...(row.keywords || [])],
    pinned: row.pinned ?? 0,
    sortNum: row.sortNum ?? 0,
    status: row.status ?? 1,
  }
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    isSaving.value = true
    try {
      const response = await saveHotQuestionApi({
        id: form.value.id,
        question: form.value.question.trim(),
        keywords: form.value.keywords,
        pinned: form.value.pinned,
        sortNum: form.value.sortNum,
        status: form.value.status,
      })
      if (response.code === 0) {
        ElMessage.success(form.value.id ? '保存成功' : '新增成功')
        dialogVisible.value = false
        await loadQuestionList()
      } else {
        ElMessage.error(response.message || '保存失败')
      }
    } catch (error) {
      console.error('保存热门问题失败:', error)
      ElMessage.error('保存失败')
    } finally {
      isSaving.value = false
    }
  })
}

// 置顶与启用状态直接落库，失败时刷新列表回到服务端真实值
const handleToggle = async (row: HotQuestionInfo, field: 'pinned' | 'status', value: number | boolean) => {
  const target = value ? 1 : 0
  try {
    const response = await changeHotQuestionStatusApi(row.id, { [field]: target })
    if (response.code === 0) {
      ElMessage.success('已更新')
      await loadQuestionList()
    } else {
      ElMessage.error(response.message || '更新失败')
      await loadQuestionList()
    }
  } catch (error) {
    console.error('更新热门问题失败:', error)
    ElMessage.error('更新失败')
    await loadQuestionList()
  }
}

const handleSortChange = async (row: HotQuestionInfo) => {
  try {
    const response = await changeHotQuestionStatusApi(row.id, { sortNum: row.sortNum })
    if (response.code === 0) {
      ElMessage.success('排序已更新')
      await loadQuestionList()
    } else {
      ElMessage.error(response.message || '排序更新失败')
    }
  } catch (error) {
    console.error('更新排序失败:', error)
    ElMessage.error('排序更新失败')
  }
}

const handleDelete = (row: HotQuestionInfo) => {
  ElMessageBox.confirm(`确定删除「${row.question}」吗？`, '删除热门问题', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      try {
        const response = await deleteHotQuestionApi(row.id)
        if (response.code === 0) {
          ElMessage.success('删除成功')
          if (questionList.value.length === 1 && queryParams.value.page > 1) {
            queryParams.value.page -= 1
          }
          await loadQuestionList()
        } else {
          ElMessage.error(response.message || '删除失败')
        }
      } catch (error) {
        console.error('删除热门问题失败:', error)
        ElMessage.error('删除失败')
      }
    })
    .catch(() => ElMessage.info('已取消删除'))
}

const handleRebuild = () => {
  ElMessageBox.confirm(
    '将按最近的真实提问重新挖掘热门问题，自动行会被更新，人工维护的行不受影响，是否继续？',
    '立即挖掘',
    { confirmButtonText: '开始挖掘', cancelButtonText: '取消', type: 'warning' },
  )
    .then(async () => {
      isRebuilding.value = true
      try {
        const response = await rebuildHotQuestionApi()
        if (response.code === 0) {
          ElMessage.success(`挖掘完成，共更新 ${response.data ?? 0} 条问题`)
          await loadQuestionList()
        } else {
          ElMessage.error(response.message || '挖掘失败')
        }
      } catch (error) {
        console.error('挖掘热门问题失败:', error)
        ElMessage.error('挖掘失败')
      } finally {
        isRebuilding.value = false
      }
    })
    .catch(() => ElMessage.info('已取消挖掘'))
}

onMounted(() => {
  loadQuestionList()
})
</script>

<style scoped lang="less">
.hot-question-manage {
  height: var(--app-content-h);
  padding: 20px;
  box-sizing: border-box;

  .box-card {
    height: 100%;
    display: flex;
    flex-direction: column;

    :deep(.el-card__body) {
      flex: 1;
      display: flex;
      flex-direction: column;
      padding: 20px;
    }
  }

  .table-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 12px;
    flex-wrap: wrap;

    &__filters {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }

    &__actions {
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }

  .filter-input {
    width: 220px;
  }

  .filter-select {
    width: 140px;
  }

  .table-tip {
    margin-bottom: 12px;
    padding: 8px 12px;
    border-radius: 8px;
    background: #f4f8ff;
    color: #6b7a90;
    font-size: 12px;
    line-height: 1.6;
  }

  .table-container {
    flex: 1;
    overflow: hidden;
  }

  .keyword-tag {
    margin-right: 6px;
    margin-bottom: 2px;
  }

  .sort-input {
    width: 84px;

    :deep(.el-input__inner) {
      text-align: center;
    }
  }

  .pagination {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }
}

.muted {
  color: #b6c1d0;
}

.keyword-select {
  width: 100%;
}

.form-hint {
  margin-left: 10px;
  font-size: 12px;
  color: #9aa8bb;
}
</style>