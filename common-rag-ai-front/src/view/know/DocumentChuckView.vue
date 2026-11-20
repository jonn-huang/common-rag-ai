<template>
  <div class="chunk-manage">
    <el-card shadow="never" class="chunk-manage__card">
      <!-- 顶部筛选：按文件名与知识库分类定位待管理的文档 -->
      <div class="chunk-manage__toolbar">
        <div class="chunk-manage__toolbar-left">
          <el-input
            v-model="fileQuery.fileName"
            placeholder="请输入文件名"
            clearable
            class="chunk-manage__keyword"
            @keyup.enter="searchFiles"
            @clear="searchFiles"
          />
          <el-select
            v-model="fileQuery.category"
            placeholder="全部知识库分类"
            clearable
            filterable
            class="chunk-manage__category"
            @change="searchFiles"
          >
            <el-option
              v-for="item in categoryOptions"
              :key="item"
              :label="item"
              :value="item"
            />
          </el-select>
          <el-button type="primary" @click="searchFiles" :disabled="isFileLoading">
            搜索
          </el-button>
        </div>
        <el-button plain :loading="isFileLoading || isChunkLoading" @click="refreshAll">
          <el-icon><refresh /></el-icon>
          刷新
        </el-button>
      </div>

      <div class="chunk-manage__body">
        <!-- 左栏：文件列表，选中后查看该文件的分片 -->
        <aside class="file-panel">
          <div class="file-panel__title">文档列表</div>
          <div class="file-panel__list" v-loading="isFileLoading">
            <div
              v-for="item in files"
              :key="item.id"
              class="file-item"
              :class="{ 'file-item--active': item.id === selectedFile?.id }"
              @click="selectFile(item)"
            >
              <div class="file-item__name" :title="item.fileName">
                {{ item.fileName }}
              </div>
              <div class="file-item__meta">
                <el-tag size="small" effect="plain" type="info">{{
                  item.category || "未分类"
                }}</el-tag>
                <el-tag size="small" effect="plain" type="success">{{
                  chunkStrategyLabel(item.chunkStrategy)
                }}</el-tag>
                <span class="file-item__count">{{ item.chunkCount ?? 0 }} 分片</span>
              </div>
            </div>
            <el-empty
              v-if="!isFileLoading && files.length === 0"
              description="暂无文档，请先在文档管理中上传"
              :image-size="80"
            />
          </div>
          <el-pagination
            v-model:current-page="fileQuery.page"
            v-model:page-size="fileQuery.pageSize"
            :page-sizes="[10, 20, 50]"
            :total="fileTotal"
            small
            layout="prev, pager, next"
            class="file-panel__pagination"
            @current-change="handleFilePageChange"
            @size-change="handleFileSizeChange"
          />
        </aside>

        <!-- 右栏：分片列表与操作 -->
        <section class="chunk-panel">
          <template v-if="selectedFile">
            <header class="chunk-panel__header">
              <div class="chunk-panel__info">
                <span class="chunk-panel__name" :title="selectedFile.fileName">
                  {{ selectedFile.fileName }}
                </span>
                <div class="chunk-panel__meta">
                  <el-tag size="small" effect="plain" type="info">{{
                    selectedFile.category || "未分类"
                  }}</el-tag>
                  <el-tag size="small" effect="plain" type="success">{{
                    chunkStrategyLabel(selectedFile.chunkStrategy)
                  }}</el-tag>
                  <el-tag size="small" :type="statusTagType(selectedFile.status)">{{
                    selectedFile.statusDesc || statusLabel(selectedFile.status)
                  }}</el-tag>
                  <span class="chunk-panel__count">
                    共 {{ selectedFile.chunkCount ?? 0 }} 个分片
                  </span>
                </div>
              </div>
              <div class="chunk-panel__actions">
                <el-button
                  type="warning"
                  plain
                  :disabled="selectedFile.status === 'PROCESSING'"
                  @click="openRechunk"
                >
                  {{ selectedFile.status === "FAILED" ? "重试切片" : "重新切片" }}
                </el-button>
              </div>
            </header>

            <div class="chunk-panel__search">
              <el-input
                v-model="chunkQuery.keyword"
                placeholder="搜索分片内容"
                clearable
                class="chunk-panel__keyword"
                @keyup.enter="searchChunks"
                @clear="searchChunks"
              />
              <el-button type="primary" plain @click="searchChunks">查询分片</el-button>
            </div>

            <el-alert
              v-if="!vectorSynced"
              type="warning"
              :closable="false"
              show-icon
              class="chunk-panel__alert"
              title="分片与向量索引不一致，当前仅支持查看，请先重新切片后再编辑或删除"
            />

            <el-table
              :data="chunks"
              border
              v-loading="isChunkLoading"
              height="calc(100vh - 430px)"
              row-key="id"
            >
              <el-table-column label="序号" width="70" align="center">
                <template #default="scope">{{ scope.row.chunkIndex }}</template>
              </el-table-column>
              <el-table-column label="字符数" width="90" align="center">
                <template #default="scope">{{ scope.row.length }}</template>
              </el-table-column>
              <el-table-column label="分片内容" min-width="320">
                <template #default="scope">
                  <span class="chunk-text">{{ scope.row.content }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180" fixed="right">
                <template #default="scope">
                  <el-button link type="primary" size="small" @click="openViewChunk(scope.row)">
                    查看
                  </el-button>
                  <el-button
                    link
                    type="warning"
                    size="small"
                    :disabled="!vectorSynced"
                    @click="openEditChunk(scope.row)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    link
                    type="danger"
                    size="small"
                    :disabled="!vectorSynced"
                    @click="removeChunk(scope.row)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty
                  :description="
                    selectedFile.status === 'SUCCESS'
                      ? '暂无分片，可点击重新切片生成'
                      : '文件尚未完成 ETL，暂无分片'
                  "
                  :image-size="80"
                />
              </template>
            </el-table>

            <div class="chunk-panel__pagination">
              <el-pagination
                v-model:current-page="chunkQuery.page"
                v-model:page-size="chunkQuery.pageSize"
                :page-sizes="[10, 20, 50, 100]"
                :total="chunkTotal"
                layout="total, sizes, prev, pager, next, jumper"
                @current-change="handleChunkPageChange"
                @size-change="handleChunkSizeChange"
              />
            </div>
          </template>
          <el-empty v-else description="请选择左侧文档查看分片" />
        </section>
      </div>
    </el-card>

    <!-- 查看分片：展示正文全文，不提供修改 -->
    <el-drawer v-model="viewVisible" title="分片详情" size="640px">
      <div v-if="viewChunk" class="chunk-detail">
        <div class="chunk-detail__summary">
          <div class="chunk-detail__row">
            <span class="chunk-detail__label">文件</span>
            <span class="chunk-detail__value">{{ viewChunk.fileName || "-" }}</span>
          </div>
          <div class="chunk-detail__row">
            <span class="chunk-detail__label">知识库分类</span>
            <span class="chunk-detail__value">{{ viewChunk.category || "未分类" }}</span>
          </div>
          <div class="chunk-detail__row">
            <span class="chunk-detail__label">分片序号</span>
            <span class="chunk-detail__value">#{{ viewChunk.chunkIndex }}</span>
          </div>
          <div class="chunk-detail__row">
            <span class="chunk-detail__label">字符数</span>
            <span class="chunk-detail__value">{{ viewChunk.length }} 字符</span>
          </div>
        </div>
        <div class="chunk-detail__content">{{ viewChunk.content }}</div>
      </div>
      <template #footer>
        <el-button @click="viewVisible = false">关闭</el-button>
      </template>
    </el-drawer>

    <!-- 编辑分片：保存后同步更新向量与全文索引 -->
    <el-dialog
      v-model="editVisible"
      title="编辑分片"
      width="720px"
      top="8vh"
      :close-on-click-modal="false"
      :close-on-press-escape="!isSavingChunk"
      :show-close="!isSavingChunk"
    >
      <div v-if="editChunk" class="chunk-edit">
        <div class="chunk-edit__meta">
          <span>{{ editChunk.fileName || "-" }}</span>
          <el-tag size="small" effect="plain">#{{ editChunk.chunkIndex }}</el-tag>
          <span class="chunk-edit__count">{{ editContent.length }} 字符</span>
        </div>
        <el-input
          v-model="editContent"
          type="textarea"
          :rows="14"
          maxlength="20000"
          show-word-limit
          placeholder="请输入分片正文"
        />
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="保存后将同步更新向量库与全文索引，原分片内容不可恢复"
        />
      </div>
      <template #footer>
        <el-button @click="editVisible = false" :disabled="isSavingChunk">关闭</el-button>
        <el-button type="primary" :loading="isSavingChunk" @click="submitChunkEdit">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 重新切片：调整策略与参数，可先预览再保存 -->
    <el-dialog
      v-model="rechunkVisible"
      title="重新切片"
      width="720px"
      top="6vh"
      :close-on-click-modal="false"
      :close-on-press-escape="!isRechunking"
      :show-close="!isRechunking"
    >
      <div class="rechunk">
        <div class="rechunk__file">
          <span class="rechunk__label">文件</span>
          <span class="rechunk__value">{{ selectedFile?.fileName || "-" }}</span>
        </div>
        <ChunkConfigPanel v-model:strategy="rechunkStrategy" v-model:config="rechunkConfig" />
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="保存后会按新参数重新切片并向量化，原有分片将被替换"
        />
      </div>
      <template #footer>
        <div class="rechunk__footer">
          <span class="rechunk__summary">
            当前共 {{ selectedFile?.chunkCount ?? 0 }} 个分片
          </span>
          <div class="rechunk__actions">
            <el-button @click="rechunkVisible = false" :disabled="isRechunking">关闭</el-button>
            <el-button
              plain
              :loading="isPreviewing"
              :disabled="isRechunking"
              @click="previewRechunk"
            >
              {{ isPreviewing ? "预览中..." : "预览切片" }}
            </el-button>
            <el-button type="primary" :loading="isRechunking" @click="submitRechunk">
              {{ isRechunking ? "切片中..." : "保存并重新切片" }}
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 重新切片预览：按弹窗中尚未保存的参数试算，不修改已有分片 -->
    <el-drawer v-model="previewVisible" title="切片预览" size="640px">
      <div v-if="previewResult" class="preview">
        <div class="preview__summary">
          <div class="preview__summary-row">
            <span class="preview__summary-label">文件</span>
            <span class="preview__summary-value">{{ previewResult.fileName || "-" }}</span>
          </div>
          <div class="preview__summary-row">
            <span class="preview__summary-label">切片策略</span>
            <span class="preview__summary-value">{{ chunkStrategyLabel(previewResult.chunkStrategy) }}</span>
          </div>
          <div class="preview__summary-row">
            <span class="preview__summary-label">切片数量</span>
            <span class="preview__summary-value">
              共 {{ previewResult.total }} 块，展示前 {{ previewResult.chunks.length }} 块
            </span>
          </div>
          <el-alert
            v-if="previewResult.truncated"
            type="info"
            :closable="false"
            show-icon
            title="切片较多，仅展示前若干条，调整参数后可重新预览"
          />
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            title="预览不会修改已有分片，确认效果后再保存"
          />
        </div>
        <div class="preview__list">
          <div v-for="item in previewResult.chunks" :key="item.index" class="preview__item">
            <div class="preview__item-head">
              <span class="preview__item-index">#{{ item.index }}</span>
              <el-tag v-if="item.heading" size="small" effect="plain">{{ item.heading }}</el-tag>
              <span class="preview__item-length">{{ item.length }} 字符</span>
            </div>
            <div class="preview__item-text">{{ item.text }}</div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import {
  queryFileApi,
  listChunksApi,
  updateChunkApi,
  deleteChunkApi,
  previewStoredChunksApi,
  updateFileApi,
  fileDetailApi,
  listRetrievalCategoriesApi,
} from "@/api/KnowHubApi";
import ChunkConfigPanel from "@/components/ChunkConfigPanel.vue";
import { chunkStrategyLabel } from "@/utils/chunkStrategy";
import {
  mergeCategoryOptions,
  UNCATEGORIZED_CATEGORY,
} from "@/utils/category";
import type { StoreFile } from "@/api/data";
import type {
  ChunkConfig,
  ChunkPreviewResult,
  ChunkStrategy,
  KnowledgeChunkItem,
  KnowledgeChunkPage,
} from "@/api/dto";

type TagType = "primary" | "success" | "info" | "warning" | "danger";

const STATUS_TAG_TYPE: Record<string, TagType> = {
  PENDING: "info",
  PROCESSING: "warning",
  SUCCESS: "success",
  FAILED: "danger",
};

const STATUS_LABEL: Record<string, string> = {
  PENDING: "待 ETL",
  PROCESSING: "ETL 中",
  SUCCESS: "成功",
  FAILED: "失败",
};

const statusTagType = (status?: string): TagType =>
  STATUS_TAG_TYPE[status ?? ""] ?? "info";

const statusLabel = (status?: string) =>
  STATUS_LABEL[status ?? ""] ?? (status || "-");

/* ---------------- 文件列表 ---------------- */
const PREVIEW_LIMIT = 10;

const files = ref<StoreFile[]>([]);
const fileTotal = ref(0);
const isFileLoading = ref(false);
const selectedFile = ref<StoreFile | null>(null);
const customCategoryOptions = ref<string[]>([]);
// 分类候选：预置分类 + 库中已有自定义分类 + 未分类（对应上传时留空的文件）
const categoryOptions = computed(() => [
  ...mergeCategoryOptions(customCategoryOptions.value),
  UNCATEGORIZED_CATEGORY,
]);

const fileQuery = ref({
  page: 1,
  pageSize: 10,
  fileName: "",
  category: "",
});

const loadCategoryOptions = () => {
  listRetrievalCategoriesApi()
    .then((res: any) => {
      if (res.code === 0 && Array.isArray(res.data)) {
        customCategoryOptions.value = res.data;
      }
    })
    .catch(() => {});
};

const loadFiles = (keepSelection = true) => {
  isFileLoading.value = true;
  queryFileApi({
    page: fileQuery.value.page,
    pageSize: fileQuery.value.pageSize,
    fileName: fileQuery.value.fileName.trim() || undefined,
    category: fileQuery.value.category || undefined,
  })
    .then((res: any) => {
      if (res.code !== 0) {
        ElMessage({ type: "error", message: res.message });
        return;
      }
      const data = res.data ?? {};
      fileTotal.value = data.total ?? 0;
      files.value = data.records ?? [];
      if (!keepSelection || !selectedFile.value) {
        selectFile(files.value[0] ?? null);
        return;
      }
      const current = files.value.find((item) => item.id === selectedFile.value?.id);
      if (current) {
        selectedFile.value = current;
      } else if (files.value.length > 0) {
        selectFile(files.value[0]);
      } else {
        selectFile(null);
      }
    })
    .catch((err: any) => {
      ElMessage({
        type: "error",
        message: err?.response?.data?.message ?? "文档列表加载失败",
      });
    })
    .finally(() => {
      isFileLoading.value = false;
    });
};

const selectFile = (file: StoreFile | null) => {
  selectedFile.value = file;
  chunkQuery.value.page = 1;
  chunkQuery.value.keyword = "";
  chunks.value = [];
  chunkTotal.value = 0;
  vectorSynced.value = true;
  if (file) {
    loadChunks();
  }
};

const searchFiles = () => {
  fileQuery.value.page = 1;
  loadFiles(false);
};

const handleFilePageChange = (val: number) => {
  fileQuery.value.page = val;
  loadFiles();
};

const handleFileSizeChange = (val: number) => {
  fileQuery.value.pageSize = val;
  fileQuery.value.page = 1;
  loadFiles();
};

/* ---------------- 分片列表 ---------------- */
const chunks = ref<KnowledgeChunkItem[]>([]);
const chunkTotal = ref(0);
const vectorSynced = ref(true);
const isChunkLoading = ref(false);

const chunkQuery = ref({
  page: 1,
  pageSize: 10,
  keyword: "",
});

const loadChunks = () => {
  const file = selectedFile.value;
  if (!file) return;
  isChunkLoading.value = true;
  listChunksApi({
    fileId: file.id,
    keyword: chunkQuery.value.keyword.trim() || undefined,
    page: chunkQuery.value.page,
    pageSize: chunkQuery.value.pageSize,
  })
    .then((res: any) => {
      if (res.code !== 0) {
        ElMessage({ type: "error", message: res.message });
        return;
      }
      const data = res.data as KnowledgeChunkPage;
      chunks.value = data?.records ?? [];
      chunkTotal.value = data?.total ?? 0;
      vectorSynced.value = data?.vectorSynced !== false;
    })
    .catch((err: any) => {
      ElMessage({
        type: "error",
        message: err?.response?.data?.message ?? "分片加载失败",
      });
    })
    .finally(() => {
      isChunkLoading.value = false;
    });
};

const searchChunks = () => {
  chunkQuery.value.page = 1;
  loadChunks();
};

const handleChunkPageChange = (val: number) => {
  chunkQuery.value.page = val;
  loadChunks();
};

const handleChunkSizeChange = (val: number) => {
  chunkQuery.value.pageSize = val;
  chunkQuery.value.page = 1;
  loadChunks();
};

const refreshAll = () => {
  // 分类候选与文件列表一起刷新，新分类文件上传后无需重进页面
  loadCategoryOptions();
  loadFiles();
  if (selectedFile.value) {
    loadChunks();
  }
};

// ≥ 分片变更后同时刷新左侧分片数，当前页被删空时回退一页
const reloadAfterChunkChange = () => {
  if (chunks.value.length === 1 && chunkQuery.value.page > 1) {
    chunkQuery.value.page -= 1;
  }
  loadChunks();
  loadFiles();
};

/* ---------------- 查看分片 ---------------- */
const viewVisible = ref(false);
const viewChunk = ref<KnowledgeChunkItem | null>(null);

const openViewChunk = (row: KnowledgeChunkItem) => {
  viewChunk.value = row;
  viewVisible.value = true;
};

/* ---------------- 编辑分片 ---------------- */
const editVisible = ref(false);
const editChunk = ref<KnowledgeChunkItem | null>(null);
const editContent = ref("");
const isSavingChunk = ref(false);

const openEditChunk = (row: KnowledgeChunkItem) => {
  editChunk.value = row;
  editContent.value = row.content;
  editVisible.value = true;
};

const submitChunkEdit = () => {
  if (!editChunk.value) return;
  const content = editContent.value.trim();
  if (!content) {
    ElMessage({ type: "warning", message: "分片内容不能为空" });
    return;
  }
  isSavingChunk.value = true;
  updateChunkApi({ id: editChunk.value.id, content })
    .then((res: any) => {
      if (res.code === 0) {
        ElMessage({ type: "success", message: res.message || "分片内容已更新" });
        editVisible.value = false;
        loadChunks();
        return;
      }
      ElMessage({ type: "error", message: res.message });
    })
    .catch((err: any) => {
      ElMessage({
        type: "error",
        message: err?.response?.data?.message ?? "保存失败，请稍后重试",
      });
    })
    .finally(() => {
      isSavingChunk.value = false;
    });
};

/* ---------------- 删除分片 ---------------- */
const removeChunk = (row: KnowledgeChunkItem) => {
  ElMessageBox.confirm(
    `确定删除第 ${row.chunkIndex} 个分片吗？删除后不可恢复，且不会改动原文件。`,
    "删除分片",
    {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    }
  )
    .then(() => {
      deleteChunkApi({ ids: row.id })
        .then((res: any) => {
          if (res.code === 0) {
            ElMessage({ type: "success", message: res.data || "删除成功" });
            reloadAfterChunkChange();
            return;
          }
          ElMessage({ type: "error", message: res.message });
        })
        .catch((err: any) => {
          ElMessage({
            type: "error",
            message: err?.response?.data?.message ?? "删除失败，请稍后重试",
          });
        });
    })
    .catch(() => {});
};

/* ---------------- 重新切片 ---------------- */
const createDefaultChunkConfig = (): ChunkConfig => ({
  fixed: {
    maxLength: 800,
    overlap: 120,
  },
  recursive: {
    targetLength: 800,
    overlap: 120,
    separators: "",
    keepSeparator: true,
  },
  semantic: {
    sentenceLength: 120,
    similarityThreshold: 0.65,
    targetLength: 800,
    overlap: 0,
  },
  structured: {
    maxLength: 1200,
    minLength: 100,
    overlap: 120,
    appendTitle: true,
    fallbackToRecursive: true,
  },
});

const rechunkVisible = ref(false);
const rechunkStrategy = ref<ChunkStrategy>("RECURSIVE");
const rechunkConfig = ref<ChunkConfig>(createDefaultChunkConfig());
const isRechunking = ref(false);
const isPreviewing = ref(false);
const previewVisible = ref(false);
const previewResult = ref<ChunkPreviewResult | null>(null);

const pickChunkParams = (strategy: ChunkStrategy, config: ChunkConfig) => {
  switch (strategy) {
    case "FIXED":
      return config.fixed;
    case "SEMANTIC":
      return config.semantic;
    case "STRUCTURED":
      return config.structured;
    default:
      return config.recursive;
  }
};

// 后端只保存当前策略的扁平参数，编辑时按策略回填到对应的参数分组
const buildChunkConfigFromStored = (
  strategy: ChunkStrategy,
  raw?: string
): ChunkConfig => {
  const config = createDefaultChunkConfig();
  if (!raw) return config;
  let parsed: Record<string, unknown> = {};
  try {
    parsed = JSON.parse(raw);
  } catch {
    return config;
  }
  const group = pickChunkParams(strategy, config) as unknown as Record<string, unknown>;
  Object.keys(group).forEach((key) => {
    const value = parsed[key];
    if (value !== undefined && value !== null) {
      group[key] = value;
    }
  });
  if (strategy === "SEMANTIC" && typeof config.semantic.similarityThreshold !== "number") {
    config.semantic.similarityThreshold = Number(config.semantic.similarityThreshold) || 0.65;
  }
  return config;
};

// 重叠长度必须小于切片长度，否则切分无法推进
const validateChunkConfig = (
  strategy: ChunkStrategy = rechunkStrategy.value,
  config: ChunkConfig = rechunkConfig.value
) => {
  const { fixed, recursive, semantic, structured } = config;
  let message = "";
  if (strategy === "FIXED" && fixed.overlap >= fixed.maxLength) {
    message = "定长切片：重叠字符数需小于切片最大长度";
  } else if (strategy === "RECURSIVE" && recursive.overlap >= recursive.targetLength) {
    message = "递归切片：重叠字符数需小于切片目标长度";
  } else if (strategy === "SEMANTIC" && semantic.overlap >= semantic.targetLength) {
    message = "语义切片：重叠字符需小于期望切片长度";
  } else if (strategy === "STRUCTURED") {
    if (structured.minLength > structured.maxLength) {
      message = "结构切片：单块最小长度不能大于单块最大长度";
    } else if (structured.overlap >= structured.maxLength) {
      message = "结构切片：重叠字符需小于单块最大长度";
    }
  }
  if (message) {
    ElMessage({ type: "warning", message });
    return false;
  }
  return true;
};

const openRechunk = () => {
  const file = selectedFile.value;
  if (!file) return;
  // 只有 ETL 进行中的文件不能重切；失败文件必须允许重试，否则永远无法恢复
  if (file.status === "PROCESSING") {
    ElMessage({ type: "warning", message: "文件正在 ETL 处理中，请稍后再试" });
    return;
  }
  if (file.status === "FAILED") {
    ElMessage({
      type: "warning",
      message: "该文件上次 ETL 未成功，保存后将按所选策略重新解析并向量化",
    });
  }
  // 列表未携带切片参数时先拉取详情再回填
  if (file.chunkConfig) {
    applyRechunkForm(file);
  } else {
    fileDetailApi(file.id)
      .then((res: any) => {
        if (res.code === 0 && res.data) {
          selectedFile.value = res.data as StoreFile;
          applyRechunkForm(res.data as StoreFile);
        } else {
          applyRechunkForm(file);
        }
      })
      .catch(() => applyRechunkForm(file));
  }
  rechunkVisible.value = true;
};

const applyRechunkForm = (file: StoreFile) => {
  rechunkStrategy.value = (file.chunkStrategy as ChunkStrategy) || "RECURSIVE";
  rechunkConfig.value = buildChunkConfigFromStored(rechunkStrategy.value, file.chunkConfig);
};

const previewRechunk = () => {
  const file = selectedFile.value;
  if (!file) return;
  if (!validateChunkConfig()) {
    return;
  }
  isPreviewing.value = true;
  previewStoredChunksApi(
    file.id,
    PREVIEW_LIMIT,
    rechunkStrategy.value,
    pickChunkParams(rechunkStrategy.value, rechunkConfig.value)
  )
    .then((res: any) => {
      if (res.code === 0) {
        previewResult.value = res.data as ChunkPreviewResult;
        previewVisible.value = true;
        return;
      }
      ElMessage({ type: "error", message: res.message });
    })
    .catch((err: any) => {
      ElMessage({
        type: "error",
        message: err?.response?.data?.message ?? "切片预览失败，请稍后重试",
      });
    })
    .finally(() => {
      isPreviewing.value = false;
    });
};

const submitRechunk = () => {
  const file = selectedFile.value;
  if (!file) return;
  if (!validateChunkConfig()) {
    return;
  }
  isRechunking.value = true;
  updateFileApi({
    id: file.id,
    // 未分类在库中统一存为“未分类”，提交空串让后端按默认值处理
    category: file.category === "未分类" ? "" : file.category,
    chunkStrategy: rechunkStrategy.value,
    chunkConfig: JSON.stringify(
      pickChunkParams(rechunkStrategy.value, rechunkConfig.value)
    ),
  })
    .then((res: any) => {
      if (res.code === 0) {
        ElMessage({ type: "success", message: res.data || "重新切片完成" });
        rechunkVisible.value = false;
        // 重切后分片总数可能变化，回到第一页避免当前页越界显示为空
        chunkQuery.value.page = 1;
        loadChunks();
        loadFiles();
        return;
      }
      ElMessage({ type: "error", message: res.message });
    })
    .catch((err: any) => {
      ElMessage({
        type: "error",
        message: err?.response?.data?.message ?? "重新切片失败，请稍后重试",
      });
    })
    .finally(() => {
      isRechunking.value = false;
    });
};

onMounted(() => {
  loadCategoryOptions();
  loadFiles(false);
});
</script>

<style scoped lang="less">
.chunk-manage {
  height: 100%;
}

.chunk-manage__card :deep(.el-card__body) {
  padding: 16px;
}

.chunk-manage__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.chunk-manage__toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chunk-manage__keyword {
  width: 240px;
}

.chunk-manage__category {
  width: 200px;
}

.chunk-manage__body {
  display: flex;
  gap: 16px;
  align-items: stretch;
}

/* ---------------- 左侧文件列表 ---------------- */
.file-panel {
  display: flex;
  flex-direction: column;
  flex: 0 0 300px;
  width: 300px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
  height: calc(100vh - 260px);
}

.file-panel__title {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.file-panel__list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.file-item {
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.file-item:hover {
  border-color: var(--el-color-primary-light-5);
  background-color: var(--el-fill-color-lighter);
}

.file-item--active {
  border-color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9);
}

.file-item__name {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-item__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.file-item__count {
  color: #909399;
  font-size: 12px;
}

.file-panel__pagination {
  margin-top: 10px;
  justify-content: flex-end;
}

/* ---------------- 右侧分片面板 ---------------- */
.chunk-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.chunk-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.chunk-panel__info {
  min-width: 0;
}

.chunk-panel__name {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chunk-panel__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.chunk-panel__count {
  color: #909399;
  font-size: 12px;
}

.chunk-panel__search {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.chunk-panel__keyword {
  width: 260px;
}

.chunk-panel__alert {
  margin-bottom: 10px;
}

.chunk-panel__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.chunk-text {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  color: #606266;
  line-height: 1.6;
}

/* ---------------- 查看分片 ---------------- */
.chunk-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chunk-detail__summary {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  background-color: var(--el-fill-color-lighter);
  border-radius: 4px;
}

.chunk-detail__row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 13px;
}

.chunk-detail__label {
  flex: 0 0 auto;
  width: 84px;
  color: #909399;
}

.chunk-detail__value {
  flex: 1;
  color: #303133;
  word-break: break-all;
}

.chunk-detail__content {
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  color: #303133;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

/* ---------------- 编辑分片 ---------------- */
.chunk-edit {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chunk-edit__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #303133;
}

.chunk-edit__count {
  color: #909399;
  font-size: 12px;
}

/* ---------------- 重新切片 ---------------- */
.rechunk {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.rechunk__file {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 13px;
}

.rechunk__label {
  flex: 0 0 auto;
  width: 40px;
  color: #909399;
}

.rechunk__value {
  flex: 1;
  color: #303133;
  word-break: break-all;
}

.rechunk__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.rechunk__summary {
  color: #909399;
  font-size: 12px;
}

.rechunk__actions {
  display: flex;
  gap: 8px;
}

/* ---------------- 切片预览 ---------------- */
.preview {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.preview__summary {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  background-color: var(--el-fill-color-lighter);
  border-radius: 4px;
}

.preview__summary-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 13px;
}

.preview__summary-label {
  flex: 0 0 auto;
  width: 72px;
  color: #909399;
}

.preview__summary-value {
  flex: 1;
  color: #303133;
  word-break: break-all;
}

.preview__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.preview__item {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.preview__item-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.preview__item-index {
  font-weight: 600;
  color: var(--el-color-primary);
}

.preview__item-length {
  margin-left: auto;
  color: #909399;
  font-size: 12px;
}

.preview__item-text {
  max-height: 180px;
  overflow-y: auto;
  color: #303133;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
