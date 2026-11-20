<template>
  <el-form
    style="display: flex; justify-content: space-between; align-items: center; margin: 20px 0"
    :model="queryFileDto"
  >
    <div style="display: flex; gap: 10px; align-items: center">
      <el-form-item label="文件名:" style="margin-bottom: 0">
        <el-input placeholder="请输入文件名称" v-model="queryFileDto.fileName" />
      </el-form-item>
      <el-form-item style="margin-bottom: 0">
        <el-button type="primary" @click="loadStoreFileData" :disabled="isLoading"
          >搜索</el-button
        >
        <el-button
            type="danger"
            @click="batchDelete"
            :disabled="selectedFiles.length === 0"
        >
          批量删除
        </el-button>
        <el-button
            type="primary"
            @click="batchDownload"
            :disabled="selectedFiles.length === 0"
        >
          批量下载
        </el-button>
        <el-button type="success" @click="openUploadDialog">
          <el-icon><upload-filled /></el-icon>
          上传文件
        </el-button>
        <el-button plain @click="openRetrievalDialog">
          <el-icon><setting /></el-icon>
          知识库检索配置
        </el-button>
      </el-form-item>
    </div>
  </el-form>

  <el-table 
    ref="tableRef"
    :data="storeFileData" 
    border 
    v-loading="isLoading"
    row-key="id"
    height="calc(100vh - 400px)"
    @selection-change="handleSelectionChange"
  >
    <el-table-column type="selection" width="55" />
    <el-table-column label="序号" width="70">
      <template #default="scope">
        {{ (queryFileDto.page - 1) * queryFileDto.pageSize + scope.$index + 1 }}
      </template>
    </el-table-column>
    <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
    <el-table-column label="OSS 路径" min-width="240" show-overflow-tooltip>
      <template #default="scope">
        <a
          v-if="scope.row.url"
          :href="scope.row.url"
          target="_blank"
          rel="noopener noreferrer"
          class="oss-link"
          >{{ scope.row.url }}</a
        >
        <span v-else>-</span>
      </template>
    </el-table-column>
    <el-table-column label="大小" width="100">
      <template #default="scope">
        {{ formatFileSize(scope.row.fileSize) }}
      </template>
    </el-table-column>
    <el-table-column label="知识库分类" width="120">
      <template #default="scope">
        <el-tag effect="plain" type="info">{{ scope.row.category || "未分类" }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="切片策略" width="220">
      <template #default="scope">
        <el-tag effect="plain" type="success">{{
          chunkStrategyLabel(scope.row.chunkStrategy)
        }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="分片数量" width="100" align="center">
      <template #default="scope">{{ scope.row.chunkCount ?? 0 }}</template>
    </el-table-column>
    <el-table-column label="状态" width="110">
      <template #default="scope">
        <el-tag :type="statusTagType(scope.row.status)">{{
          scope.row.statusDesc || statusLabel(scope.row.status)
        }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="上传时间" width="160">
      <template #default="scope">
        {{ formatTime(scope.row.createTime) }}
      </template>
    </el-table-column>
    <el-table-column label="更新时间" width="160">
      <template #default="scope">
        {{ formatTime(scope.row.updateTime) }}
      </template>
    </el-table-column>
    <el-table-column label="操作" width="300" fixed="right">
      <template #default="scope">
        <el-button
          @click="previewStoreFileChunks(scope.row)"
          type="primary"
          link
          size="small"
          >分片预览</el-button
        >
        <el-button
          @click="openViewFile(scope.row)"
          type="primary"
          link
          size="small"
          >查看</el-button
        >
        <el-button
          @click="openEditFile(scope.row)"
          type="warning"
          link
          size="small"
          >编辑</el-button
        >
        <el-button
          @click="downloadStoreFile(scope.row)"
          type="primary"
          link
          size="small"
          >下载</el-button
        >
        <el-button
          @click="deleteStoreFile(scope.row)"
          type="danger"
          link
          size="small"
          >删除</el-button
        >
      </template>
    </el-table-column>
  </el-table>

  <div style="margin-top: 20px; display: flex; justify-content: flex-end;">
    <el-pagination
      v-model:current-page="queryFileDto.page"
      v-model:page-size="queryFileDto.pageSize"
      :page-sizes="[10, 20, 50, 100]"
      :total="storeFileTotal"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
      layout="total, sizes, prev, pager, next, jumper"
    />
  </div>

  <!-- 上传弹窗：拖拽/点击选择文件，选中文件后展示切片配置 -->
  <el-dialog
    v-model="uploadDialogVisible"
    title="上传文件"
    width="680px"
    top="6vh"
    class="upload-dialog"
    :close-on-click-modal="false"
    :close-on-press-escape="!isUploading"
    :show-close="!isUploading"
    @closed="resetUploadForm"
  >
    <el-upload
      class="upload-demo"
      drag
      multiple
      v-model:file-list="fileList"
      :auto-upload="false"
      :disabled="isUploading"
      v-loading="isUploading"
    >
      <el-icon class="el-icon--upload"><upload-filled /></el-icon>
      <div class="el-upload__text">
        拖拽文件至此或<em>点击选择文件</em>进行上传
      </div>
      <template #tip>
        <div style="text-align: center">
          <el-text
            >文件支持 <i>pdf、doc、md、excel、text</i>等，最大可上传<em
              style="color: blue"
              >100MB</em
            ></el-text
          >
        </div>
      </template>
    </el-upload>

    <!-- 未选择文件时只给轻提示，避免空白配置项干扰操作 -->
    <div v-if="!hasSelectedFiles" class="upload-hint">
      <el-icon><info-filled /></el-icon>
      选择文件后即可配置知识库分类与切片策略
    </div>

    <!-- 上传配置区：文件分类与切片配置同属一个区域 -->
    <div v-else class="upload-panel">
      <div class="upload-panel__row">
        <span class="upload-panel__label">知识库分类</span>
        <el-select
          v-model="uploadCategory"
          placeholder="选择知识库分类（可自定义），留空则为未分类"
          class="upload-panel__field"
          filterable
          allow-create
          default-first-option
          clearable
        >
          <el-option
            v-for="item in fileCategoryOptions"
            :key="item"
            :label="item"
            :value="item"
          />
        </el-select>
      </div>

      <div class="upload-panel__divider"></div>

      <!-- 切片配置：选中策略后，参数面板展开在当前 radio 下方 -->
      <ChunkConfigPanel v-model:strategy="chunkStrategy" v-model:config="chunkConfig" />
    </div>

    <template #footer>
      <div class="upload-footer">
        <span class="upload-footer__summary">
          <template v-if="hasSelectedFiles"
            >已选 {{ fileList.length }} 个文件，共
            {{ formatFileSize(selectedFileSize) }}</template
          >
          <template v-else>尚未选择文件</template>
        </span>
        <div class="upload-footer__actions">
          <el-button @click="closeUploadDialog" :disabled="isUploading"
            >关闭</el-button
          >
          <el-button
            plain
            @click="previewChunk"
            :loading="isPreviewing"
            :disabled="!hasSelectedFiles || isUploading"
          >
            {{ isPreviewing ? "预览中..." : "预览切片" }}
          </el-button>
          <el-button
            type="primary"
            @click="uploadFile"
            :loading="isUploading"
            :disabled="!hasSelectedFiles"
          >
            {{ isUploading ? "上传中..." : "上传" }}
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>

  <!-- 切片预览抽屉：上传前确认切片效果，预览不落库、不写向量库；已入库文件展示实际分片 -->
  <el-drawer
    v-model="previewVisible"
    :title="previewTitle"
    size="640px"
    class="preview-drawer"
  >
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
            共 {{ previewResult.total }} 块，展示前
            {{ previewResult.chunks.length }} 块
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
          v-if="previewMode === 'upload'"
          type="warning"
          :closable="false"
          show-icon
          title="预览不会保存文件与向量，确认效果后再点击上传"
        />
        <el-alert
          v-else
          type="info"
          :closable="false"
          show-icon
          title="按该文件已保存的切片策略实时计算，不会修改已有分片"
        />
      </div>

      <div class="preview__list">
        <div
          v-for="item in previewResult.chunks"
          :key="item.index"
          class="preview__item"
        >
          <div class="preview__item-head">
            <span class="preview__item-index">#{{ item.index }}</span>
            <el-tag v-if="item.heading" size="small" effect="plain">{{
              item.heading
            }}</el-tag>
            <el-button
              link
              type="primary"
              size="small"
              @click="togglePreviewMetadata(item.index)"
            >
              {{ isMetadataExpanded(item.index) ? "收起元数据" : "查看元数据" }}
            </el-button>
            <span class="preview__item-length">{{ item.length }} 字符</span>
          </div>
          <!-- 元数据：按需展开，避免挤压正文展示 -->
          <div v-if="isMetadataExpanded(item.index)" class="preview__metadata">
            <div
              v-for="(value, key) in item.metadata"
              :key="key"
              class="preview__metadata-row"
            >
              <span class="preview__metadata-key">{{ key }}</span>
              <span class="preview__metadata-value">{{ formatMetadataValue(value) }}</span>
            </div>
            <span
              v-if="!item.metadata || Object.keys(item.metadata).length === 0"
              class="preview__metadata-empty"
              >无元数据</span
            >
          </div>
          <div class="preview__item-text">{{ item.text }}</div>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="previewVisible = false">关闭</el-button>
    </template>
  </el-drawer>

  <!-- 查看文件：展示文件基础信息与切片参数，不提供修改 -->
  <el-dialog v-model="viewDialogVisible" title="文件详情" width="680px" top="8vh">
    <el-descriptions v-if="viewFile" :column="2" border>
      <el-descriptions-item label="文件名" :span="2">
        {{ viewFile.fileName }}
      </el-descriptions-item>
      <el-descriptions-item label="知识库分类">
        {{ viewFile.category || "未分类" }}
      </el-descriptions-item>
      <el-descriptions-item label="文件大小">
        {{ formatFileSize(viewFile.fileSize) }}
      </el-descriptions-item>
      <el-descriptions-item label="切片策略">
        {{ chunkStrategyLabel(viewFile.chunkStrategy) }}
      </el-descriptions-item>
      <el-descriptions-item label="分片数量">
        {{ viewFile.chunkCount ?? 0 }}
      </el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag :type="statusTagType(viewFile.status)">{{
          viewFile.statusDesc || statusLabel(viewFile.status)
        }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="上传时间">
        {{ formatTime(viewFile.createTime) }}
      </el-descriptions-item>
      <el-descriptions-item label="更新时间" :span="2">
        {{ formatTime(viewFile.updateTime) }}
      </el-descriptions-item>
      <el-descriptions-item label="OSS 路径" :span="2">
        <a
          v-if="viewFile.url"
          :href="viewFile.url"
          target="_blank"
          rel="noopener noreferrer"
          class="oss-link"
          >{{ viewFile.url }}</a
        >
        <span v-else>-</span>
      </el-descriptions-item>
      <el-descriptions-item label="切片参数" :span="2">
        <div class="config-summary">
          <div
            v-for="item in viewChunkParams"
            :key="item.label"
            class="config-summary__row"
          >
            <span class="config-summary__label">{{ item.label }}</span>
            <span class="config-summary__value">{{ item.value }}</span>
          </div>
          <span v-if="viewChunkParams.length === 0">-</span>
        </div>
      </el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button @click="viewDialogVisible = false">关闭</el-button>
    </template>
  </el-dialog>

  <!-- 编辑文件：修改分类与切片策略，策略变化时后端按原文件重新切片 -->
  <el-dialog
    v-model="editDialogVisible"
    title="编辑文件"
    width="680px"
    top="6vh"
    class="upload-dialog"
    :close-on-click-modal="false"
    :close-on-press-escape="!isSavingEdit"
    :show-close="!isSavingEdit"
  >
    <div v-if="editFile" class="upload-panel">
      <div class="upload-panel__row">
        <span class="upload-panel__label">文件名</span>
        <span class="upload-panel__field">{{ editFile.fileName }}</span>
      </div>
      <div class="upload-panel__row" style="margin-top: 12px">
        <span class="upload-panel__label">知识库分类</span>
        <el-select
          v-model="editCategory"
          placeholder="选择知识库分类（可自定义），留空则为未分类"
          class="upload-panel__field"
          filterable
          allow-create
          default-first-option
          clearable
        >
          <el-option
            v-for="item in fileCategoryOptions"
            :key="item"
            :label="item"
            :value="item"
          />
        </el-select>
      </div>

      <div class="upload-panel__divider"></div>

      <ChunkConfigPanel
        v-model:strategy="editChunkStrategy"
        v-model:config="editChunkConfig"
      />

      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="修改切片策略或参数后会重新切片并向量化，原有分片将被替换"
      />
    </div>

    <template #footer>
      <div class="upload-footer">
        <span class="upload-footer__summary">
          当前共 {{ editFile?.chunkCount ?? 0 }} 个分片
        </span>
        <div class="upload-footer__actions">
          <el-button @click="editDialogVisible = false" :disabled="isSavingEdit"
            >关闭</el-button
          >
          <el-button
            type="primary"
            :loading="isSavingEdit"
            @click="submitEdit"
            >保存</el-button
          >
        </div>
      </div>
    </template>
  </el-dialog>

  <!-- 知识库检索配置：多分类绑定 + 三种检索方式参数 + 可选 Rerank 重排 -->
  <el-dialog
    v-model="retrievalDialogVisible"
    title="知识库检索配置"
    width="700px"
    top="8vh"
    class="retrieval-dialog"
    :close-on-click-modal="false"
  >
    <div class="retrieval-config" v-loading="isRetrievalLoading">
      <div class="retrieval-config__scope">
        <span class="retrieval-config__scope-label">生效知识库分类</span>
        <el-select
          v-model="retrievalForm.categories"
          multiple
          filterable
          clearable
          collapse-tags
          collapse-tags-tooltip
          placeholder="不选择则默认全局知识库"
          class="retrieval-config__scope-select"
        >
          <el-option
            v-for="item in retrievalCategoryOptions"
            :key="item"
            :label="item"
            :value="item"
          />
        </el-select>
      </div>
      <div class="retrieval-config__tip">
        可多选知识库分类绑定；不选择知识库分类时对整个知识库生效
      </div>

      <div class="retrieval-config__list">
        <el-radio
          v-for="item in retrievalOptions"
          :key="item.value"
          v-model="retrievalForm.retrievalMode"
          :value="item.value"
          border
          class="retrieval-config__item"
        >
          <span class="retrieval-config__label">{{ item.label }}</span>
          <span class="retrieval-config__desc">{{ item.desc }}</span>
        </el-radio>
      </div>

      <!-- 语义检索：向量召回数量 + 最低相似度阈值 -->
      <div v-if="retrievalForm.retrievalMode === 'SEMANTIC'" class="retrieval-config__panel">
        <div class="retrieval-config__row">
          <span class="retrieval-config__row-label">召回数量</span>
          <el-input-number
            v-model="retrievalForm.vectorTopK"
            :min="1"
            :max="50"
            :step="1"
            size="small"
          />
        </div>
        <div class="retrieval-config__row">
          <span class="retrieval-config__row-label">最低相似度阈值</span>
          <el-slider
            v-model="retrievalForm.similarityThreshold"
            :min="0"
            :max="1"
            :step="0.05"
            show-input
            :show-input-controls="false"
            class="retrieval-config__slider"
          />
        </div>
      </div>

      <!-- 全文检索：BM25 关键词召回数量 + 最低匹配得分 -->
      <div
        v-else-if="retrievalForm.retrievalMode === 'FULL_TEXT'"
        class="retrieval-config__panel"
      >
        <div class="retrieval-config__row">
          <span class="retrieval-config__row-label">召回数量</span>
          <el-input-number
            v-model="retrievalForm.keywordTopK"
            :min="1"
            :max="50"
            :step="1"
            size="small"
          />
        </div>
        <div class="retrieval-config__row">
          <span class="retrieval-config__row-label">最低匹配得分</span>
          <el-input-number
            v-model="keywordMinScoreInput"
            :min="0"
            :step="0.1"
            :precision="2"
            size="small"
            controls-position="right"
          />
          <span class="retrieval-config__hint">留空表示不限制</span>
        </div>
      </div>

      <!-- 混合检索：双通道召回数量 + 加权 / RRF 融合 -->
      <div v-else class="retrieval-config__panel">
        <div class="retrieval-config__row">
          <span class="retrieval-config__row-label">向量召回数量</span>
          <el-input-number
            v-model="retrievalForm.vectorTopK"
            :min="1"
            :max="50"
            :step="1"
            size="small"
          />
        </div>
        <div class="retrieval-config__row">
          <span class="retrieval-config__row-label">关键词召回数量</span>
          <el-input-number
            v-model="retrievalForm.keywordTopK"
            :min="1"
            :max="50"
            :step="1"
            size="small"
          />
        </div>
        <div class="retrieval-config__row">
          <span class="retrieval-config__row-label">融合方式</span>
          <el-radio-group v-model="retrievalForm.fusionStrategy" size="small">
            <el-radio-button value="RRF">RRF 排名融合（推荐）</el-radio-button>
            <el-radio-button value="WEIGHTED">权重加权</el-radio-button>
          </el-radio-group>
        </div>
        <div
          v-if="retrievalForm.fusionStrategy === 'WEIGHTED'"
          class="retrieval-config__weight"
        >
          <div class="retrieval-config__weight-head">
            <span>向量权重 {{ vectorWeightPercent }}%</span>
            <span>关键词权重 {{ keywordWeightPercent }}%</span>
          </div>
          <el-slider v-model="vectorWeight" :min="0" :max="1" :step="0.05" />
          <div class="retrieval-config__hint">向量权重变化时，关键词权重自动取剩余比例</div>
        </div>
        <div v-else class="retrieval-config__hint retrieval-config__fusion-tip">
          RRF 按通道排名倒数累加融合，无需归一化，权重配置不参与计算
        </div>
      </div>

      <!-- Rerank 重排：高级能力，默认不启用 -->
      <div class="retrieval-config__rerank">
        <div class="retrieval-config__rerank-head">
          <el-checkbox v-model="retrievalForm.rerankEnabled">开启 Rerank 重排</el-checkbox>
          <span class="retrieval-config__hint">高级能力，会增加检索耗时，可提升精度</span>
        </div>
        <div v-if="retrievalForm.rerankEnabled" class="retrieval-config__row">
          <span class="retrieval-config__row-label">重排后保留条数</span>
          <el-input-number
            v-model="retrievalForm.rerankTopN"
            :min="1"
            :max="50"
            :step="1"
            size="small"
          />
        </div>
      </div>
    </div>
    <template #footer>
      <div class="retrieval-config__footer">
        <el-button link type="warning" @click="rebuildRetrievalIndex">
          重建关键词索引
        </el-button>
        <div>
          <el-button @click="retrievalDialogVisible = false" :disabled="isRetrievalLoading"
            >取消</el-button
          >
          <el-button type="primary" :loading="isRetrievalLoading" @click="saveRetrievalConfig"
            >保存</el-button
          >
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { type UploadUserFile, ElMessage, ElMessageBox } from "element-plus";
import {
  uploadFileApi,
  previewChunkApi,
  queryFileApi,
  deleteFileApi,
  downloadFileApi,
  fileDetailApi,
  previewStoredChunksApi,
  updateFileApi,
  getRetrievalConfigApi,
  saveRetrievalConfigApi,
  listRetrievalCategoriesApi,
  rebuildRetrievalIndexApi,
} from "@/api/KnowHubApi";
import ChunkConfigPanel from "@/components/ChunkConfigPanel.vue";
import { chunkStrategyLabel } from "@/utils/chunkStrategy";
import { KNOWLEDGE_CATEGORY_OPTIONS, mergeCategoryOptions } from "@/utils/category";
import { StoreFile } from "@/api/data";
import {
  QueryFileDto,
  type ChunkConfig,
  type ChunkStrategy,
  type ChunkPreviewResult,
  type RetrievalConfigDto,
  type RetrievalMode,
} from "@/api/dto";
import { format } from "date-fns";

const storeFileData = ref<StoreFile[]>([]);
const tableRef = ref<any>();
// 列表请求序号，用于丢弃过期的列表响应
let listRequestId = 0;
const queryFileDto = ref<QueryFileDto>({
  page: 1,
  pageSize: 10,
  fileName: "",
});
const isUploading = ref(false);
const isLoading = ref(false);
const storeFileTotal = ref(0);
const selectedFiles = ref<any[]>([]);
// 上传时选择的分类，留空则由后端置为“未分类”
const uploadCategory = ref("");
// 上传与编辑弹窗共用的分类候选：预置分类 + 库中已有自定义分类
const fileCategoryOptions = ref<string[]>([...KNOWLEDGE_CATEGORY_OPTIONS]);

const loadFileCategoryOptions = () => {
  listRetrievalCategoriesApi()
    .then((res: any) => {
      if (res.code === 0 && Array.isArray(res.data)) {
        fileCategoryOptions.value = mergeCategoryOptions(res.data);
      }
    })
    .catch(() => {});
};

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

// 切片策略名称统一由 utils/chunkStrategy 维护，与上传弹窗选项保持一致
// 字节转可读大小，避免 10485760 这种数字直接暴露给用户
const formatFileSize = (size?: number) => {
  if (!size || size <= 0) return "-";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = size;
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index++;
  }
  return `${value.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
};

const formatTime = (time?: Date | string) => {
  if (!time) return "-";
  const date = new Date(time);
  if (Number.isNaN(date.getTime())) return "-";
  return format(date, "yyyy-MM-dd HH:mm");
};

// 把后端返回的文件流转成浏览器另存为
const saveBlob = (blob: Blob, fileName: string) => {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
};

// 从 Content-Disposition 解析文件名，优先使用可带中文的 filename*
const parseFileName = (headers: any, fallback: string) => {
  const disposition: string = headers?.["content-disposition"] ?? "";
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (encoded?.[1]) {
    try {
      return decodeURIComponent(encoded[1]);
    } catch {
      return fallback;
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition);
  return plain?.[1] ? plain[1] : fallback;
};

// 下载并弹出保存：单个文件直接返回文件流，多个文件后端打包成 zip
const downloadFilesToLocal = (ids: string | number, fallbackName: string) => {
  downloadFileApi({ ids })
    .then(async (res: any) => {
      const contentType: string = res.headers?.["content-type"] ?? "";
      // 下载失败时后端返回 JSON，不会带 Content-Disposition
      if (contentType.includes("application/json")) {
        const text = await (res.data as Blob).text();
        let message = "下载失败";
        try {
          message = JSON.parse(text).message || message;
        } catch {
          // 非标准 JSON 时保留默认提示
        }
        ElMessage({ type: "error", message });
        return;
      }
      saveBlob(res.data as Blob, parseFileName(res.headers, fallbackName));
      ElMessage({ type: "success", message: "下载成功" });
    })
    .catch((err) => {
      ElMessage({ type: "error", message: err?.message ?? "下载失败" });
    });
};

// 列表数据整体替换后，el-table 会复用行，需同步清空勾选并重算固定列布局
const syncTableState = () => {
  nextTick(() => {
    tableRef.value?.clearSelection();
    selectedFiles.value = [];
    tableRef.value?.doLayout?.();
  });
};

// 删除后刷新：当前页被删空时回退一页，避免停在空白页
const refreshAfterDelete = (deletedCount: number) => {
  if (storeFileData.value.length - deletedCount <= 0 && queryFileDto.value.page > 1) {
    queryFileDto.value.page -= 1;
  }
  loadStoreFileData();
};

const loadStoreFileData = () => {
  // 请求序号：并发刷新时只保留最后一次响应，避免旧列表盖掉新列表
  const requestId = ++listRequestId;
  isLoading.value = true;
  queryFileApi(queryFileDto.value)
    .then((res) => {
      if (requestId !== listRequestId) return;
      if (res.code == 0) {
        const data = res.data;
        // MyBatis-Plus 分页返回 total，兼容旧字段 totalElements
        storeFileTotal.value = data.total ?? data.totalElements ?? 0;
        storeFileData.value = data.records ?? [];
        syncTableState();
      } else {
        ElMessage({
          type: "error",
          message: res.message,
        });
      }
    })
    .catch((err) => {
      if (requestId !== listRequestId) return;
      ElMessage({
        type: "error",
        message: err,
      });
    })
    .finally(() => {
      if (requestId === listRequestId) {
        isLoading.value = false;
      }
    });
};

const fileList = ref<UploadUserFile[]>([]);
const uploadDialogVisible = ref(false);
// 切片策略：选中文件后才展示配置项
const chunkStrategy = ref<ChunkStrategy>("RECURSIVE");

// 各策略参数默认值，切换策略时各自保留用户已修改的值
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
const chunkConfig = ref<ChunkConfig>(createDefaultChunkConfig());

// 重叠长度必须小于切片长度，否则切分无法推进
const validateChunkConfig = (
  strategy: ChunkStrategy = chunkStrategy.value,
  config: ChunkConfig = chunkConfig.value
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

// 只提交当前策略的参数，后端根据 chunkStrategy 取用
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

const getActiveChunkParams = () =>
  pickChunkParams(chunkStrategy.value, chunkConfig.value);

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
  // 语义切片的阈值来自用户输入，需保证是数字才能正常展示
  if (strategy === "SEMANTIC" && typeof config.semantic.similarityThreshold !== "number") {
    config.semantic.similarityThreshold = Number(config.semantic.similarityThreshold) || 0.65;
  }
  return config;
};

// 未选中文件时不展示切片配置
const hasSelectedFiles = computed(() => fileList.value.length > 0);

// 已选文件总大小，用于弹窗底部提示
const selectedFileSize = computed(() =>
  fileList.value.reduce((total, item) => total + (item.size ?? 0), 0)
);

// 切片预览：只预览首个文件，每次最多展示 PREVIEW_LIMIT 条
const isPreviewing = ref(false);
const previewVisible = ref(false);
const previewResult = ref<ChunkPreviewResult | null>(null);
const PREVIEW_LIMIT = 10;
// 预览来源：上传前切片预览 / 已入库文件的分片预览
const previewMode = ref<"upload" | "stored">("upload");
const previewTitle = computed(() =>
  previewMode.value === "stored" ? "分片预览" : "切片预览"
);

// 切片元数据展开状态，按切片序号记录，默认收起
const expandedPreviewMetadata = ref<Set<number>>(new Set());

const isMetadataExpanded = (index: number) =>
  expandedPreviewMetadata.value.has(index);

const togglePreviewMetadata = (index: number) => {
  if (expandedPreviewMetadata.value.has(index)) {
    expandedPreviewMetadata.value.delete(index);
    return;
  }
  expandedPreviewMetadata.value.add(index);
};

// 元数据值可能是对象或数组，统一转成可读文本
const formatMetadataValue = (value: unknown) => {
  if (value === null || value === undefined || value === "") return "-";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
};

// 预览切片：不落库、不写向量库，便于上传前确认切片效果
const previewChunk = () => {
  const raw = fileList.value[0]?.raw as File | undefined;
  if (!raw) {
    ElMessage({ type: "warning", message: "请先选择要预览的文件" });
    return;
  }
  if (!validateChunkConfig()) {
    return;
  }
  // 预览需要后端完整解析文件，超过 20MB 直接引导用户上传后查看
  const maxPreviewSize = 20 * 1024 * 1024;
  if (raw.size > maxPreviewSize) {
    ElMessage({
      type: "warning",
      message: `预览文件不能超过 20MB，请直接上传「${raw.name}」后查看切片结果`,
    });
    return;
  }
  if (fileList.value.length > 1) {
    ElMessage({
      type: "info",
      message: `预览仅针对首个文件「${raw.name}」，其余文件可上传后查看`,
    });
  }
  isPreviewing.value = true;
  previewChunkApi(raw, chunkStrategy.value, getActiveChunkParams(), PREVIEW_LIMIT)
    .then((res) => {
      if (res.data.code === 0) {
        previewMode.value = "upload";
        expandedPreviewMetadata.value.clear();
        previewResult.value = res.data.data as ChunkPreviewResult;
        previewVisible.value = true;
        return;
      }
      ElMessage({ type: "error", message: res.data.message });
    })
    .catch((err) => {
      console.log(err);
      ElMessage({
        type: "error",
        message: err?.response?.data?.message ?? "切片预览失败，请稍后重试",
      });
    })
    .finally(() => {
      isPreviewing.value = false;
    });
};

// 分片预览：按已入库文件保存的策略重新计算，不修改已有分片
const previewStoreFileChunks = (row: StoreFile) => {
  if (!row?.id) {
    ElMessage({ type: "warning", message: "文件信息缺失，请刷新列表后重试" });
    return;
  }
  isPreviewing.value = true;
  isLoading.value = true;
  previewStoredChunksApi(row.id, PREVIEW_LIMIT)
    .then((res) => {
      if (res.code === 0) {
        previewMode.value = "stored";
        expandedPreviewMetadata.value.clear();
        previewResult.value = res.data as ChunkPreviewResult;
        previewVisible.value = true;
        return;
      }
      ElMessage({ type: "error", message: res.message });
    })
    .catch((err) => {
      ElMessage({
        type: "error",
        message: err?.response?.data?.message ?? "分片预览失败，请稍后重试",
      });
    })
    .finally(() => {
      isPreviewing.value = false;
      isLoading.value = false;
    });
};

const openUploadDialog = () => {
  uploadDialogVisible.value = true;
};

const closeUploadDialog = () => {
  if (isUploading.value) return;
  uploadDialogVisible.value = false;
};

// 关闭动画结束后再清空，避免关闭过程中内容闪变
const resetUploadForm = () => {
  fileList.value = [];
  uploadCategory.value = "";
  chunkStrategy.value = "RECURSIVE";
  chunkConfig.value = createDefaultChunkConfig();
  previewVisible.value = false;
  previewResult.value = null;
};

const uploadFile = () => {
  // 只提交有真实文件的条目，避免历史残留的无 raw 项被当成空文件提交
  const files: File[] = fileList.value
    .map((item) => item.raw)
    .filter((raw): raw is NonNullable<typeof raw> => !!raw);

  if (files.length === 0) {
    ElMessage({
      type: "warning",
      message: "请先选择要上传的文件",
    });
    return;
  }

  // 修改文件大小限制为100MB
  const maxSize = 100 * 1024 * 1024; // 100MB
  for (const file of files) {
    if (file.size > maxSize) {
      ElMessage({
        type: "error",
        message: `文件 ${file.name} 超过了最大上传大小限制 (100MB)`,
      });
      return;
    }
  }

  // 切片参数校验：重叠长度需小于切片长度，否则切分无法推进
  if (!validateChunkConfig()) {
    return;
  }

  isUploading.value = true;
  uploadFileApi(
    files,
    uploadCategory.value,
    chunkStrategy.value,
    getActiveChunkParams()
  )
    .then((res) => {
      let code = res.data.code;
      if (code == 0) {
        ElMessage({
          type: "success",
          message: res.data.data,
        });
      } else {
        // 部分文件失败时后端返回错误码，但仍需刷新列表查看失败记录
        ElMessage({
          type: "error",
          message: res.data.message,
        });
      }
      // 先清空弹窗文件，避免关闭动画期间再次打开时旧文件残留被重复提交
      resetUploadForm();
      uploadDialogVisible.value = false;
      // 新文件按 id 倒序排在第一页，回到首页刷新才能立即看到
      queryFileDto.value.page = 1;
      loadStoreFileData();
      // 本次上传可能新增了自定义分类，同步刷新分类候选
      loadFileCategoryOptions();
    })
    .catch((err) => {
      console.log(err);
      ElMessage({
        type: "error",
        message: err,
      });
    })
    .finally(() => {
      isUploading.value = false;
    });
};
const deleteStoreFile = (e: any) => {
  ElMessageBox.confirm("确定要删除这个知识库吗？", "警告", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      deleteFileApi({
        ids: e.id,
      })
        .then((res) => {
          let code = res.code;
          if (code == 0) {
            ElMessage({
              type: "success",
              message: res.data,
            });
            refreshAfterDelete(1);
          } else {
            ElMessage({
              type: "error",
              message: res.message,
            });
          }
        })
        .catch((err) => {
          ElMessage({
            type: "error",
            message: err,
          });
        });
    })
    .catch(() => {});
};

const downloadStoreFile = (e: any) => {
  downloadFilesToLocal(e.id, e.fileName || "download");
};

// 切片参数中文名与各策略使用的参数键，用于详情弹窗只说当前策略关心的参数
const CHUNK_PARAM_LABELS: Record<string, string> = {
  maxLength: "单块最大长度",
  minLength: "单块最小长度",
  targetLength: "切片目标长度",
  overlap: "重叠字符数",
  sentenceLength: "分句长度",
  similarityThreshold: "语义相似度阈值",
  separators: "自定义分隔符",
  keepSeparator: "保留分隔符",
  appendTitle: "自动追加标题",
  fallbackToRecursive: "识别失败降级递归切片",
};

const STRATEGY_PARAM_KEYS: Record<string, string[]> = {
  FIXED: ["maxLength", "overlap"],
  RECURSIVE: ["targetLength", "overlap", "separators", "keepSeparator"],
  SEMANTIC: ["sentenceLength", "similarityThreshold", "targetLength", "overlap"],
  STRUCTURED: ["maxLength", "minLength", "overlap", "appendTitle", "fallbackToRecursive"],
};

const formatChunkParamValue = (value: unknown) => {
  if (typeof value === "boolean") return value ? "是" : "否";
  if (value === "" || value === undefined || value === null) return "默认";
  return String(value);
};

// 查看弹窗
const viewDialogVisible = ref(false);
const viewFile = ref<StoreFile | null>(null);

const viewChunkParams = computed(() => {
  if (!viewFile.value?.chunkConfig) return [];
  let parsed: Record<string, unknown> = {};
  try {
    parsed = JSON.parse(viewFile.value.chunkConfig);
  } catch {
    return [];
  }
  const strategy = viewFile.value.chunkStrategy ?? "RECURSIVE";
  const keys = STRATEGY_PARAM_KEYS[strategy] ?? [];
  return keys
    .filter((key) => parsed[key] !== undefined && parsed[key] !== null)
    .map((key) => ({
      label: CHUNK_PARAM_LABELS[key] ?? key,
      value: formatChunkParamValue(parsed[key]),
    }));
});

const openViewFile = (row: StoreFile) => {
  viewFile.value = row;
  viewDialogVisible.value = true;
  // 列表可能为旧数据，这里拉取最新详情刷新切片策略与分片数量
  fileDetailApi(row.id)
    .then((res) => {
      if (res.code === 0) {
        viewFile.value = res.data as StoreFile;
      }
    })
    .catch(() => {});
};

// 编辑弹窗：分类与切片策略可改，策略变化时由后端重新切片
const editDialogVisible = ref(false);
const editFile = ref<StoreFile | null>(null);
const editCategory = ref("");
const editChunkStrategy = ref<ChunkStrategy>("RECURSIVE");
const editChunkConfig = ref<ChunkConfig>(createDefaultChunkConfig());
const isSavingEdit = ref(false);

const applyEditFile = (file: StoreFile) => {
  editFile.value = file;
  editCategory.value = file.category === "未分类" ? "" : file.category ?? "";
  editChunkStrategy.value = (file.chunkStrategy as ChunkStrategy) || "RECURSIVE";
  editChunkConfig.value = buildChunkConfigFromStored(
    editChunkStrategy.value,
    file.chunkConfig
  );
};

const openEditFile = (row: StoreFile) => {
  applyEditFile(row);
  editDialogVisible.value = true;
  // 列表未携带切片参数时再拉取详情补全
  if (!row.chunkConfig) {
    fileDetailApi(row.id)
      .then((res) => {
        if (res.code === 0 && editDialogVisible.value) {
          applyEditFile(res.data as StoreFile);
        }
      })
      .catch(() => {});
  }
};

const submitEdit = () => {
  if (!editFile.value) return;
  if (!validateChunkConfig(editChunkStrategy.value, editChunkConfig.value)) {
    return;
  }
  isSavingEdit.value = true;
  updateFileApi({
    id: editFile.value.id,
    category: editCategory.value,
    chunkStrategy: editChunkStrategy.value,
    chunkConfig: JSON.stringify(
      pickChunkParams(editChunkStrategy.value, editChunkConfig.value)
    ),
  })
    .then((res) => {
      if (res.code === 0) {
        ElMessage({ type: "success", message: res.data || "保存成功" });
        editDialogVisible.value = false;
        loadStoreFileData();
        // 编辑时可能修改为新的自定义分类，同步刷新分类候选
        loadFileCategoryOptions();
        return;
      }
      ElMessage({ type: "error", message: res.message });
    })
    .catch((err) => {
      ElMessage({
        type: "error",
        message: err?.response?.data?.message ?? "保存失败，请稍后重试",
      });
    })
    .finally(() => {
      isSavingEdit.value = false;
    });
};

const handleSizeChange = (val: number) => {
  queryFileDto.value.pageSize = val
  // 改变每页条数后回到第一页，避免页码越界导致列表空白
  queryFileDto.value.page = 1
  loadStoreFileData()
}

const handleCurrentChange = (val: number) => {
  queryFileDto.value.page = val
  loadStoreFileData()
}

const handleSelectionChange = (selection: any[]) => {
  selectedFiles.value = selection
}

// 知识库检索配置：多分类绑定 + 语义/全文/混合三种检索方式，默认混合检索
const retrievalOptions: { value: RetrievalMode; label: string; desc: string }[] = [
  {
    value: "SEMANTIC",
    label: "语义检索",
    desc: "使用向量进行文本相关性查询",
  },
  {
    value: "FULL_TEXT",
    label: "全文检索",
    desc: "使用传统的全文检索，适合查找一些关键词和主谓语特殊的数据",
  },
  {
    value: "HYBRID",
    label: "混合检索（推荐）",
    desc: "使用向量检索与全文检索的综合结果返回，使用 RRF 算法进行排序。",
  },
];

// 与后端 RetrievalConfigVO 的默认值保持一致，接口未配置时直接复用
const createDefaultRetrievalConfig = (): RetrievalConfigDto => ({
  categories: [],
  retrievalMode: "HYBRID",
  vectorTopK: 10,
  similarityThreshold: 0.4,
  keywordTopK: 10,
  keywordMinScore: null,
  fusionStrategy: "RRF",
  vectorWeight: 0.6,
  keywordWeight: 0.4,
  rerankEnabled: false,
  rerankTopN: 8,
});

const retrievalDialogVisible = ref(false);
const isRetrievalLoading = ref(false);
const retrievalForm = ref<RetrievalConfigDto>(createDefaultRetrievalConfig());
const retrievalCategoryOptions = ref<string[]>([]);

// 最低匹配得分允许留空，留空表示不限制
const keywordMinScoreInput = computed<number | undefined>({
  get: () => retrievalForm.value.keywordMinScore ?? undefined,
  set: (value) => {
    retrievalForm.value.keywordMinScore = value ?? null;
  },
});

// 权重滑块只控制向量权重，关键词权重自动补足，保证两者之和为 1
const vectorWeight = computed<number>({
  get: () => retrievalForm.value.vectorWeight,
  set: (value) => {
    const ratio = Math.min(Math.max(value ?? 0, 0), 1);
    retrievalForm.value.vectorWeight = Number(ratio.toFixed(2));
    retrievalForm.value.keywordWeight = Number((1 - ratio).toFixed(2));
  },
});

const vectorWeightPercent = computed(() => Math.round(retrievalForm.value.vectorWeight * 100));
const keywordWeightPercent = computed(() => Math.round(retrievalForm.value.keywordWeight * 100));

// 同时拉取配置与分类，分类用于多选绑定，配置为空时用默认值回显
const loadRetrievalConfig = () => {
  isRetrievalLoading.value = true;
  Promise.all([getRetrievalConfigApi(), listRetrievalCategoriesApi()])
    .then(([configRes, categoryRes]) => {
      if (categoryRes.code === 0 && Array.isArray(categoryRes.data)) {
        retrievalCategoryOptions.value = categoryRes.data;
      }
      if (configRes.code === 0 && configRes.data) {
        const data = configRes.data;
        retrievalForm.value = {
          ...createDefaultRetrievalConfig(),
          ...data,
          categories: Array.isArray(data.categories) ? data.categories : [],
        };
      }
    })
    .catch((err) => {
      ElMessage({
        type: "error",
        message: err?.response?.data?.message ?? "检索配置加载失败",
      });
    })
    .finally(() => {
      isRetrievalLoading.value = false;
    });
};

const openRetrievalDialog = () => {
  retrievalDialogVisible.value = true;
  loadRetrievalConfig();
};

const saveRetrievalConfig = () => {
  isRetrievalLoading.value = true;
  saveRetrievalConfigApi({ ...retrievalForm.value })
    .then((res) => {
      if (res.code === 0) {
        ElMessage({ type: "success", message: res.message || "知识库检索配置已保存" });
        retrievalDialogVisible.value = false;
        return;
      }
      ElMessage({ type: "error", message: res.message });
    })
    .catch((err) => {
      ElMessage({
        type: "error",
        message: err?.response?.data?.message ?? "保存失败，请稍后重试",
      });
    })
    .finally(() => {
      isRetrievalLoading.value = false;
    });
};

// 历史文件没有关键词语料，重建后全文检索才能命中
const rebuildRetrievalIndex = () => {
  ElMessageBox.confirm(
    "将重新读取 OSS 上的全部文件并按已保存策略重建关键词索引，文件较多时耗时较长，是否继续？",
    "重建关键词索引",
    {
      confirmButtonText: "开始重建",
      cancelButtonText: "取消",
      type: "warning",
    }
  )
    .then(() => {
      isRetrievalLoading.value = true;
      rebuildRetrievalIndexApi()
        .then((res) => {
          if (res.code === 0) {
            const data = res.data ?? {};
            ElMessage({
              type: "success",
              message: `重建完成：成功 ${
                data.fileCount ?? 0
              } 个文件，失败 ${data.failedCount ?? 0} 个，共 ${
                data.chunkCount ?? 0
              } 个分片`,
            });
            return;
          }
          ElMessage({ type: "error", message: res.message });
        })
        .catch((err) => {
          ElMessage({
            type: "error",
            message: err?.response?.data?.message ?? "重建失败，请稍后重试",
          });
        })
        .finally(() => {
          isRetrievalLoading.value = false;
        });
    })
    .catch(() => {});
};

const batchDelete = () => {
  if (selectedFiles.value.length === 0) return
  
  ElMessageBox.confirm("确定要删除选中的文件吗？", "警告", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      const deletedCount = selectedFiles.value.length
      const ids = selectedFiles.value.map(file => file.id).join(',')
      deleteFileApi({
        ids: ids,
      })
        .then((res) => {
          if (res.code == 0) {
            ElMessage({
              type: "success",
              message: res.data,
            });
            selectedFiles.value = [];
            refreshAfterDelete(deletedCount);
          } else {
            ElMessage({
              type: "error",
              message: res.message,
            });
          }
        })
        .catch((err) => {
          ElMessage({
            type: "error",
            message: err,
          });
        });
    })
    .catch(() => {});
}
const batchDownload = () => {
  if (selectedFiles.value.length === 0) return

  const ids = selectedFiles.value.map(file => file.id).join(',')
  const fallbackName =
    selectedFiles.value.length > 1
      ? "知识库文件.zip"
      : selectedFiles.value[0].fileName || "download"
  downloadFilesToLocal(ids, fallbackName)
}

onMounted(() => {
  loadStoreFileData();
  loadFileCategoryOptions();
});
</script>

<style scoped>
.el-table {
  ::-webkit-scrollbar {
    width: 6px;
    height: 6px;
  }
  ::-webkit-scrollbar-thumb {
    background: #ddd;
    border-radius: 3px;
  }
  ::-webkit-scrollbar-track {
    background: #f5f5f5;
  }
}

.upload-demo {
  margin-bottom: 16px;
}

.upload-demo :deep(.el-upload-dragger) {
  padding: 24px 10px;
}

.oss-link {
  color: #409eff;
  text-decoration: none;
}

.oss-link:hover {
  text-decoration: underline;
}

/* 上传配置区：文件分类与切片配置共用同一个区域容器 */
.upload-panel {
  margin-top: 16px;
  padding: 12px 14px 4px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.upload-panel__divider {
  height: 1px;
  margin: 4px 0 12px;
  background-color: var(--el-border-color-lighter);
}

.upload-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  color: #909399;
  font-size: 12px;
}

.upload-panel__row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.upload-panel__label {
  flex: 0 0 auto;
  width: 96px;
  color: #606266;
  font-size: 14px;
}

.upload-panel__field {
  flex: 1;
}

/* 查看弹窗中的切片参数列表 */
.config-summary {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.config-summary__row {
  display: flex;
  gap: 10px;
  font-size: 13px;
}

.config-summary__label {
  flex: 0 0 auto;
  width: 140px;
  color: #909399;
}

.config-summary__value {
  flex: 1;
  color: #303133;
  word-break: break-all;
}

.upload-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.upload-footer__summary {
  color: #909399;
  font-size: 12px;
}

.upload-footer__actions {
  display: flex;
  gap: 8px;
}

.upload-dialog :deep(.el-dialog__body) {
  max-height: 70vh;
  overflow-y: auto;
}

/* ---------------- 切片预览抽屉 ---------------- */
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
  color: #606266;
  font-size: 12px;
  font-weight: 600;
}

.preview__item-length {
  margin-left: auto;
  color: #909399;
  font-size: 12px;
}

/* 切片元数据：默认收起，展开后限定高度可滚动 */
.preview__metadata {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 180px;
  overflow-y: auto;
  margin-bottom: 8px;
  padding: 8px 10px;
  background-color: var(--el-fill-color-lighter);
  border-radius: 4px;
  font-size: 12px;
}

.preview__metadata-row {
  display: flex;
  gap: 8px;
}

.preview__metadata-key {
  flex: 0 0 auto;
  width: 120px;
  color: #909399;
}

.preview__metadata-value {
  flex: 1;
  color: #303133;
  word-break: break-all;
}

.preview__metadata-empty {
  color: #909399;
}

.preview__item-text {
  max-height: 220px;
  overflow-y: auto;
  color: #303133;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

/* ---------------- 知识库检索配置弹窗 ---------------- */
.retrieval-config__scope {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.retrieval-config__scope-label {
  flex: 0 0 auto;
  width: 72px;
  color: #606266;
  font-size: 13px;
}

.retrieval-config__scope-select {
  flex: 1;
}

.retrieval-config__tip {
  margin-bottom: 12px;
  color: #909399;
  font-size: 12px;
}

.retrieval-config__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 检索方式卡片的描述可能较长，需整行排列并自动换行 */
.retrieval-config :deep(.el-radio) {
  width: 100%;
  height: auto;
  margin-right: 0;
  padding: 10px 12px;
  align-items: flex-start;
}

.retrieval-config :deep(.el-radio__label) {
  display: block;
  white-space: normal;
  line-height: 1.5;
}

.retrieval-config__label {
  display: block;
  font-weight: 600;
}

.retrieval-config__desc {
  display: block;
  color: #909399;
  font-size: 12px;
}

/* 各检索方式的参数面板 */
.retrieval-config__panel {
  margin-top: 12px;
  padding: 12px 14px;
  background-color: var(--el-fill-color-lighter);
  border-radius: 4px;
}

.retrieval-config__row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 32px;
}

.retrieval-config__row + .retrieval-config__row {
  margin-top: 8px;
}

.retrieval-config__row-label {
  flex: 0 0 auto;
  width: 110px;
  color: #606266;
  font-size: 13px;
}

.retrieval-config__slider {
  flex: 1;
  margin-right: 8px;
}

.retrieval-config__hint {
  color: #909399;
  font-size: 12px;
}

.retrieval-config__weight {
  margin-top: 10px;
}

.retrieval-config__fusion-tip {
  margin-top: 8px;
}

.retrieval-config__weight-head {
  display: flex;
  justify-content: space-between;
  color: #606266;
  font-size: 12px;
}

.retrieval-config__rerank {
  margin-top: 12px;
  padding: 10px 14px;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
}

.retrieval-config__rerank-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.retrieval-config__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.el-form {
  background-color: #fff;
  padding: 15px;
  border-radius: 4px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}
</style>