<template>
  <div class="chunk-panel">
    <div class="chunk-panel__title">切片配置</div>
    <div class="chunk-strategy">
      <div
        v-for="item in chunkStrategyOptions"
        :key="item.value"
        class="chunk-strategy__item"
      >
        <el-radio v-model="strategy" :value="item.value" border>
          <span class="chunk-strategy__label">{{ item.label }}</span>
          <span class="chunk-strategy__desc">{{ item.desc }}</span>
        </el-radio>

        <!-- 参数面板：只展开在当前选中的 radio 下方 -->
        <div v-if="strategy === item.value" class="chunk-strategy__panel">
          <el-form label-width="140px" class="chunk-config">
            <!-- 定长切片 -->
            <template v-if="item.value === 'FIXED'">
              <el-form-item label="切片最大长度">
                <el-input-number
                  v-model="config.fixed.maxLength"
                  :min="50"
                  :max="10000"
                  :step="50"
                  controls-position="right"
                  class="chunk-config__number"
                />
                <span class="chunk-config__unit">字符</span>
              </el-form-item>
              <el-form-item label="重叠字符数">
                <el-input-number
                  v-model="config.fixed.overlap"
                  :min="0"
                  :max="5000"
                  :step="10"
                  controls-position="right"
                  class="chunk-config__number"
                />
                <span class="chunk-config__unit">字符</span>
              </el-form-item>
            </template>

            <!-- 递归切片 -->
            <template v-else-if="item.value === 'RECURSIVE'">
              <el-form-item label="切片目标长度">
                <el-input-number
                  v-model="config.recursive.targetLength"
                  :min="50"
                  :max="10000"
                  :step="50"
                  controls-position="right"
                  class="chunk-config__number"
                />
                <span class="chunk-config__unit">字符</span>
              </el-form-item>
              <el-form-item label="重叠字符数">
                <el-input-number
                  v-model="config.recursive.overlap"
                  :min="0"
                  :max="5000"
                  :step="10"
                  controls-position="right"
                  class="chunk-config__number"
                />
                <span class="chunk-config__unit">字符</span>
              </el-form-item>
              <el-collapse class="chunk-config__advanced">
                <el-collapse-item title="高级设置" name="advanced">
                  <el-form-item label="自定义分隔符">
                    <el-input
                      v-model="config.recursive.separators"
                      type="textarea"
                      :rows="2"
                      placeholder="多个分隔符用 | 分隔，例如 \n\n|\n|。|；，留空使用默认分隔符"
                    />
                  </el-form-item>
                  <el-form-item label="保留分隔符">
                    <div class="chunk-config__inline">
                      <el-switch v-model="config.recursive.keepSeparator" />
                      <span class="chunk-config__hint"
                        >保留分隔符有助于维持段落边界</span
                      >
                    </div>
                  </el-form-item>
                </el-collapse-item>
              </el-collapse>
            </template>

            <!-- 语义切片 -->
            <template v-else-if="item.value === 'SEMANTIC'">
              <el-form-item label="分句长度">
                <el-input-number
                  v-model="config.semantic.sentenceLength"
                  :min="20"
                  :max="1000"
                  :step="10"
                  controls-position="right"
                  class="chunk-config__number"
                />
                <span class="chunk-config__unit">字符</span>
              </el-form-item>
              <el-form-item label="语义相似度阈值">
                <div class="chunk-config__slider">
                  <el-slider
                    v-model="config.semantic.similarityThreshold"
                    :min="0"
                    :max="1"
                    :step="0.01"
                  />
                  <span class="chunk-config__slider-value">{{
                    config.semantic.similarityThreshold.toFixed(2)
                  }}</span>
                </div>
              </el-form-item>
              <el-form-item label="期望切片长度">
                <el-input-number
                  v-model="config.semantic.targetLength"
                  :min="50"
                  :max="10000"
                  :step="50"
                  controls-position="right"
                  class="chunk-config__number"
                />
                <span class="chunk-config__unit">字符</span>
              </el-form-item>
              <el-form-item label="重叠字符">
                <el-input-number
                  v-model="config.semantic.overlap"
                  :min="0"
                  :max="5000"
                  :step="10"
                  controls-position="right"
                  class="chunk-config__number"
                />
                <span class="chunk-config__unit">字符</span>
              </el-form-item>
              <div class="chunk-config__tip">
                语义切片需逐段计算向量相似度，文件较大时处理耗时会明显增加
              </div>
            </template>

            <!-- 结构切片 -->
            <template v-else>
              <el-form-item label="单块最大长度">
                <el-input-number
                  v-model="config.structured.maxLength"
                  :min="100"
                  :max="10000"
                  :step="50"
                  controls-position="right"
                  class="chunk-config__number"
                />
                <span class="chunk-config__unit">字符</span>
              </el-form-item>
              <el-form-item label="单块最小长度">
                <el-input-number
                  v-model="config.structured.minLength"
                  :min="0"
                  :max="5000"
                  :step="10"
                  controls-position="right"
                  class="chunk-config__number"
                />
                <span class="chunk-config__unit">字符</span>
              </el-form-item>
              <el-form-item label="重叠字符">
                <el-input-number
                  v-model="config.structured.overlap"
                  :min="0"
                  :max="5000"
                  :step="10"
                  controls-position="right"
                  class="chunk-config__number"
                />
                <span class="chunk-config__unit">字符</span>
              </el-form-item>
              <el-form-item label="切片增强">
                <div class="chunk-config__checks">
                  <el-checkbox v-model="config.structured.appendTitle"
                    >自动追加标题到切片</el-checkbox
                  >
                  <el-checkbox v-model="config.structured.fallbackToRecursive"
                    >识别失败自动降级递归切片</el-checkbox
                  >
                </div>
              </el-form-item>
            </template>
          </el-form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ChunkConfig, ChunkStrategy } from "@/api/dto";
import { CHUNK_STRATEGY_OPTIONS } from "@/utils/chunkStrategy";

// 切片策略与参数由父组件双向绑定，上传与编辑弹窗共用同一份配置面板
const strategy = defineModel<ChunkStrategy>("strategy", { required: true });
const config = defineModel<ChunkConfig>("config", { required: true });

// 选项文案统一在 utils/chunkStrategy 维护，列表回显也使用同一份名称
const chunkStrategyOptions = CHUNK_STRATEGY_OPTIONS;
</script>

<style scoped>
.chunk-panel__title {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
}

.chunk-strategy {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

/* 单个策略：radio 与其参数面板作为一组 */
.chunk-strategy__item {
  display: flex;
  flex-direction: column;
  width: 100%;
}

/* 参数面板紧跟在选中的 radio 下方 */
.chunk-strategy__panel {
  margin-top: 8px;
  padding: 8px 12px 4px;
  background-color: var(--el-fill-color-lighter);
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
}

.chunk-strategy :deep(.el-radio) {
  width: 100%;
  height: auto;
  margin-right: 0;
  padding: 8px 12px;
  align-items: flex-start;
}

.chunk-strategy :deep(.el-radio__label) {
  display: block;
  line-height: 1.5;
}

.chunk-strategy__label {
  display: block;
  font-weight: 600;
}

.chunk-strategy__desc {
  display: block;
  color: #909399;
  font-size: 12px;
}

.chunk-config {
  margin-top: 0;
  padding: 0;
  background-color: transparent;
  box-shadow: none;
}

.chunk-config__number {
  width: 180px;
}

.chunk-config__unit {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

.chunk-config__inline {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chunk-config__hint {
  color: #909399;
  font-size: 12px;
}

.chunk-config__slider {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
}

.chunk-config__slider-value {
  min-width: 36px;
  color: #606266;
  font-size: 13px;
  text-align: right;
}

.chunk-config__checks {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.chunk-config__tip {
  margin: 0 0 12px 140px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.chunk-config__advanced {
  margin-top: 4px;
  border-top: none;
}
</style>
