<template>
  <div class="composer" :class="{ 'is-focused': focused, 'is-loading': loading }">
    <el-input
      v-model="draft"
      class="composer__input"
      type="textarea"
      resize="none"
      :autosize="{ minRows: 2, maxRows: 8 }"
      :maxlength="2000"
      placeholder="请输入你的问题，我会优先依据知识库资料作答…"
      @keydown="handleKeydown"
      @compositionstart="composing = true"
      @compositionend="composing = false"
      @focus="focused = true"
      @blur="focused = false"
    />

    <div class="composer__footer">
      <div class="composer__meta">
        <span class="composer__hint">Enter 发送 · Shift + Enter 换行</span>
        <span class="composer__count">{{ draft.length }}/2000</span>
      </div>
      <div class="composer__actions">
        <el-button v-if="loading" class="stop-btn" @click="emit('stop')">
          <span class="stop-btn__icon"></span>
          停止生成
        </el-button>
        <el-button
          v-else
          class="send-btn"
          type="primary"
          :disabled="!draft.trim()"
          @click="emit('send')"
        >
          发送
          <el-icon><Promotion /></el-icon>
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { Promotion } from "@element-plus/icons-vue";

const props = defineProps<{
  modelValue: string;
  loading: boolean;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
  (e: "send"): void;
  (e: "stop"): void;
}>();

const focused = ref(false);
const composing = ref(false);

const draft = computed({
  get: () => props.modelValue,
  set: (value: string) => emit("update:modelValue", value),
});

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key !== "Enter" || event.shiftKey) return;
  // 中文输入法组合状态下回车用于选词，不能触发发送
  if (composing.value || (event as KeyboardEvent & { isComposing?: boolean }).isComposing) return;
  event.preventDefault();
  emit("send");
};
</script>

<style scoped lang="less">
.composer {
  margin-top: 12px;
  padding: 12px 14px 10px;
  border: 1px solid #e3ebf7;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 6px 18px rgba(26, 71, 138, 0.06);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &.is-focused {
    border-color: #b9d1f5;
    box-shadow: 0 8px 22px rgba(47, 124, 246, 0.12);
  }
}

.composer__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.composer__hint {
  font-size: 11px;
  color: #a6b3c6;
}

.composer__input {
  :deep(.el-textarea__inner) {
    padding: 4px 2px;
    border: none;
    box-shadow: none;
    background: transparent;
    font-size: 14px;
    line-height: 1.7;
    color: #1f2d3d;

    &::placeholder {
      color: #a6b3c6;
    }
  }
}

.composer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #f0f4fa;
}

.composer__count {
  font-size: 11px;
  color: #a6b3c6;
}

.composer__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.send-btn {
  height: 34px;
  padding: 0 18px;
  border: none;
  border-radius: 9px;
  font-size: 13px;
  background: linear-gradient(120deg, #1a478a 0%, #2f7cf6 100%);
  box-shadow: 0 6px 14px rgba(47, 124, 246, 0.22);

  &:hover,
  &:focus {
    background: linear-gradient(120deg, #1a478a 0%, #2f7cf6 100%);
    opacity: 0.92;
  }

  &.is-disabled,
  &.is-disabled:hover {
    opacity: 0.6;
    box-shadow: none;
  }
}

.stop-btn {
  height: 34px;
  padding: 0 14px;
  border-radius: 9px;
  border-color: #e3ebf7;
  color: #5c7597;
  font-size: 13px;

  &:hover {
    color: #d45b5b;
    border-color: #f3cfcf;
    background: #fef6f6;
  }
}

.stop-btn__icon {
  width: 9px;
  height: 9px;
  margin-right: 6px;
  border-radius: 2px;
  background: currentColor;
  display: inline-block;
}
</style>