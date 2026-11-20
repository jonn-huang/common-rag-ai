<template>
  <div class="message" :class="isUser ? 'message--user' : 'message--assistant'">
    <img class="message__avatar" :src="isUser ? userUrl : robotUrl" :alt="displayName" />

    <div class="message__body">
      <div class="message__meta">
        <span class="message__name">{{ displayName }}</span>
        <span class="message__time">{{ timeText }}</span>
        <el-tag v-if="message.stopped" size="small" type="info" effect="plain" round>已停止</el-tag>
      </div>

      <el-alert
        v-if="sensitiveWord"
        class="message__sensitive"
        type="warning"
        :closable="false"
        show-icon
        :title="`提问命中敏感词「${sensitiveWord}」，已拦截并停止回答`"
      />

      <el-alert
        v-else-if="showRefusal"
        class="message__refusal"
        type="info"
        :closable="false"
        show-icon
      >
        <template #title>
          <span class="message__refusal-label">系统提示</span>
          <span class="message__refusal-text">{{ displayContent }}</span>
        </template>
      </el-alert>

      <div v-else class="message__bubble" :class="{ 'is-plain': isUser }">
        <span v-if="isUser" class="message__text">{{ message.content }}</span>
        <template v-else>
          <MarkdownContent v-if="message.content" :content="displayContent" />
          <div v-if="streaming && !message.content" class="thinking">
            <span class="thinking__dots"><i></i><i></i><i></i></span>
            <span class="thinking__text">正在为你查询…</span>
          </div>
          <div v-else-if="streaming" class="typing" aria-label="正在输出">
            <i></i><i></i><i></i>
          </div>
        </template>
      </div>

      <div v-if="message.status === 'error'" class="message__error">
        <el-icon><WarningFilled /></el-icon>
        <span>{{ message.error || "回答生成失败，请稍后重试" }}</span>
        <el-button link type="primary" @click="emit('retry')">重试</el-button>
      </div>

      <div v-if="!isUser && !message.refused && referenceFiles.length" class="message__sources">
        <span class="message__sources-label">参考文件 {{ referenceFiles.length }} 篇</span>
        <el-popover
          v-for="file in visibleReferences"
          :key="file.fileName"
          placement="top"
          :width="380"
          trigger="click"
          popper-class="source-popper"
        >
          <template #reference>
            <button type="button" class="source-chip">{{ file.fileName }}</button>
          </template>
          <div class="source-popper__title">{{ file.fileName }}</div>
          <div v-if="file.category" class="source-popper__meta">{{ file.category }}</div>
          <div class="source-popper__body">{{ file.snippet || "该文件未提供片段摘要" }}</div>
        </el-popover>
        <button
          v-if="referenceFiles.length > COLLAPSED_SOURCE_LIMIT"
          type="button"
          class="source-more"
          @click="showAllSources = !showAllSources"
        >
          {{ showAllSources ? "收起" : `+${referenceFiles.length - COLLAPSED_SOURCE_LIMIT}` }}
        </button>
      </div>

      <div class="message__actions">
        <el-tooltip content="复制" placement="top">
          <el-button text circle size="small" @click="emit('copy', displayContent)">
            <el-icon><CopyDocument /></el-icon>
          </el-button>
        </el-tooltip>
        <template v-if="!isUser">
          <el-tooltip v-if="isLast" content="重新生成" placement="top">
            <el-button text circle size="small" :disabled="streaming" @click="emit('regenerate')">
              <el-icon><RefreshRight /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="有帮助" placement="top">
            <el-button
              text
              circle
              size="small"
              :class="{ 'is-active': message.feedback === 'up' }"
              @click="emit('feedback', 'up')"
            >
              <el-icon><CaretTop /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="没帮助" placement="top">
            <el-button
              text
              circle
              size="small"
              :class="{ 'is-active': message.feedback === 'down' }"
              @click="emit('feedback', 'down')"
            >
              <el-icon><CaretBottom /></el-icon>
            </el-button>
          </el-tooltip>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import {
  CaretBottom,
  CaretTop,
  CopyDocument,
  RefreshRight,
  WarningFilled,
} from "@element-plus/icons-vue";
import MarkdownContent from "@/components/chat/MarkdownContent.vue";
import type { ChatMessage } from "@/api/ChatApi";
import robotUrl from "@/assets/robot.svg";
import userUrl from "@/assets/user.svg";

/** 对客展示的一份参考文件：同一文件的多条分片在这里合并成一条 */
interface ReferenceFile {
  fileName: string;
  category: string;
  snippet: string;
}

/** 依据超过该条数时折叠，避免引用区占满整个回答 */
const COLLAPSED_SOURCE_LIMIT = 6;

const props = defineProps<{
  message: ChatMessage;
  streaming?: boolean;
  isLast?: boolean;
}>();

const emit = defineEmits<{
  (e: "copy", content: string): void;
  (e: "regenerate"): void;
  (e: "retry"): void;
  (e: "feedback", value: "up" | "down"): void;
}>();

const isUser = computed(() => props.message.role === "user");
const displayName = computed(() => (isUser.value ? "我" : "智能助手"));
const sources = computed(() => props.message.sources ?? []);
const showAllSources = ref(false);

/** 后端标记拒答时改用系统提示条展示，不再按普通回答渲染，也不再列参考文件 */
const showRefusal = computed(
  () => !isUser.value && props.message.refused === true && !!props.message.content.trim()
);

/** 后端已不再下发的占位名，历史缓存里可能还存着 */
const PLACEHOLDER_FILE_NAME = "未命名文档";

/**
 * 按文件名聚合参考文件：后端已合并，这里再兜一次兼容本地缓存里的分片级旧会话；
 * 排序按文件名而不是相关度，避免条目顺序把检索排名透给用户
 */
const referenceFiles = computed<ReferenceFile[]>(() => {
  const groups = new Map<string, ReferenceFile>();
  sources.value.forEach((source) => {
    const fileName = (source.fileName || "").trim();
    // 无法关联文件名的条目直接跳过，不再显示占位名
    if (!fileName || fileName === PLACEHOLDER_FILE_NAME) return;
    const snippet = (source.snippet ?? source.text ?? "").trim();
    const category = (source.category || "").trim();
    const current = groups.get(fileName);
    if (!current) {
      groups.set(fileName, { fileName, category, snippet });
      return;
    }
    if (!current.category) current.category = category;
    if (snippet && !current.snippet.includes(snippet)) {
      current.snippet = current.snippet ? `${current.snippet}\n${snippet}` : snippet;
    }
  });
  return [...groups.values()].sort((left, right) =>
    left.fileName.localeCompare(right.fileName, "zh-CN")
  );
});

const visibleReferences = computed(() =>
  showAllSources.value ? referenceFiles.value : referenceFiles.value.slice(0, COLLAPSED_SOURCE_LIMIT)
);

/**
 * 模型仍可能在回答里带出【资料N】，渲染前统一剔除，作为后端约束失效时的兜底
 */
const displayContent = computed(() => {
  const raw = props.message.content || "";
  if (props.message.role !== "assistant") return raw;
  return raw
    .replace(/[【\[]\s*资料\s*\d+\s*[】\]]/g, "")
    .replace(/资料\s*\d+\s*[：:]/g, "")
    .replace(/\n{3,}/g, "\n\n");
});

const timeText = computed(() => {
  const date = new Date(props.message.createdAt);
  if (Number.isNaN(date.getTime())) return "";
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
});

/** 后端命中敏感词时直接返回一段提示文本，这里单独用告警样式展示 */
const sensitiveWord = computed(() => {
  if (props.message.role !== "assistant") return "";
  const match = /包含敏感词[:：]\s*(.+?)\s*$/.exec(props.message.content || "");
  return match ? match[1] : "";
});
</script>

<style scoped lang="less">
.message {
  display: flex;
  gap: 10px;
  max-width: 100%;
  margin-bottom: 22px;

  &.message--user {
    flex-direction: row-reverse;

    .message__body {
      align-items: flex-end;
    }

    .message__meta {
      flex-direction: row-reverse;
    }

    .message__bubble {
      background: linear-gradient(135deg, #1a478a 0%, #2f7cf6 100%);
      color: #ffffff;
      border-color: transparent;
      border-top-right-radius: 4px;
    }

    .message__actions {
      justify-content: flex-end;
    }
  }

  &:hover .message__actions,
  &:focus-within .message__actions {
    opacity: 1;
  }
}

.message__avatar {
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  padding: 6px;
  border-radius: 10px;
  background: #f1f6ff;
  box-sizing: border-box;
  border: 1px solid #e3ebf7;
}

.message--user .message__avatar {
  background: #eef2f8;
}

.message__body {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: 78%;
  min-width: 0;
}

.message__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 12px;
  color: #93a1b5;
}

.message__name {
  color: #48576c;
  font-weight: 500;
}

.message__bubble {
  position: relative;
  padding: 12px 15px;
  max-width: 100%;
  border: 1px solid #e8eef7;
  border-radius: 12px;
  border-top-left-radius: 4px;
  background: #ffffff;
  box-shadow: 0 2px 10px rgba(26, 71, 138, 0.05);
  font-size: 14px;
  line-height: 1.75;
  word-break: break-word;

  &.is-plain {
    white-space: pre-wrap;
    box-shadow: 0 4px 12px rgba(47, 124, 246, 0.18);
  }
}

.message__text {
  white-space: pre-wrap;
}

.thinking {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #8b9bb2;
  font-size: 13px;
}

.thinking__dots {
  display: inline-flex;
  gap: 4px;

  i {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #b9cce8;
    animation: bounce 1.2s infinite ease-in-out;

    &:nth-child(2) {
      animation-delay: 0.15s;
    }

    &:nth-child(3) {
      animation-delay: 0.3s;
    }
  }
}

.typing {
  display: inline-flex;
  gap: 4px;
  margin-top: 8px;

  i {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #9fbbe4;
    animation: bounce 1.2s infinite ease-in-out;

    &:nth-child(2) {
      animation-delay: 0.15s;
    }

    &:nth-child(3) {
      animation-delay: 0.3s;
    }
  }
}

@keyframes bounce {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.5;
  }

  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

.message__sensitive {
  max-width: 520px;
}

.message__refusal {
  max-width: 520px;

  :deep(.el-alert__title) {
    font-size: 13px;
    line-height: 1.7;
  }

  :deep(.el-alert__icon) {
    align-self: flex-start;
    margin-top: 2px;
  }
}

.message__refusal-label {
  display: inline-block;
  margin-right: 6px;
  font-weight: 600;
}

.message__refusal-text {
  color: #48576c;
  white-space: pre-wrap;
}

.message__error {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #fef1f1;
  color: #d45b5b;
  font-size: 13px;
}

.message__sources {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.message__sources-label {
  font-size: 12px;
  color: #93a1b5;
}

.source-chip {
  max-width: 180px;
  padding: 3px 10px;
  border: 1px solid #e8eef7;
  border-radius: 999px;
  background: #f7f9fd;
  color: #5c7597;
  font-family: inherit;
  font-size: 12px;
  line-height: 1.6;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: border-color 0.2s ease, background 0.2s ease, color 0.2s ease;

  &:hover {
    border-color: #cfe0ff;
    background: #f2f7ff;
    color: #2f7cf6;
  }
}

.source-popper__title {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #24344d;
}

.source-popper__meta {
  display: inline-block;
  margin-bottom: 8px;
  padding: 1px 8px;
  border-radius: 999px;
  background: #f2f7ff;
  font-size: 11px;
  color: #5c7597;
}

.source-popper__body {
  max-height: 220px;
  overflow-y: auto;
  font-size: 13px;
  line-height: 1.7;
  color: #48576c;
  white-space: pre-wrap;
  word-break: break-word;
}

.source-more {
  padding: 3px 8px;
  border: none;
  background: transparent;
  font-family: inherit;
  font-size: 12px;
  color: #2f7cf6;
  cursor: pointer;

  &:hover {
    color: #1a478a;
  }
}

.message__actions {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: 6px;
  /* 常驻显示，不依赖鼠标悬停，保证触屏设备也能反馈 */
  opacity: 0.8;
  transition: opacity 0.2s ease;

  :deep(.el-button) {
    color: #9aabc2;

    &:hover {
      color: #2f7cf6;
      background: #f2f7ff;
    }

    &.is-active {
      color: #2f7cf6;
    }
  }
}

@media (max-width: 900px) {
  .message__body {
    max-width: 86%;
  }
}
</style>