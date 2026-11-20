<template>
  <div class="chat-page">
    <ChatSessionList
      v-if="!isNarrow"
      class="chat-page__aside"
      :groups="groupedSessions"
      :active-id="activeId"
      @create="handleCreateSession"
      @select="handleSwitchSession"
      @rename="(id, title) => renameSession(id, title)"
      @delete="handleDeleteSession"
    />

    <section class="chat-page__main">
      <ChatHeaderBar
        :session-title="activeSession?.title"
        :config-summary="configSummary"
        :has-messages="messages.length > 0"
        :show-session-toggle="isNarrow"
        @clear="handleClearCurrent"
        @toggle-sessions="sessionDrawer = true"
      />

      <div ref="listRef" class="chat-page__messages" @scroll="handleScroll">
        <ChatWelcome
          v-if="!messages.length"
          :hot-questions="hotQuestions"
          @ask="(question) => handleSend(question)"
        />

        <ChatMessageItem
          v-for="(message, index) in messages"
          :key="message.id"
          :message="message"
          :streaming="streamingId === message.id"
          :is-last="index === messages.length - 1"
          @copy="handleCopy"
          @regenerate="handleRegenerate(message)"
          @retry="handleRegenerate(message)"
          @feedback="(value) => handleFeedback(message, value)"
        />
      </div>

      <transition name="fade">
        <el-button
          v-if="showJumpButton"
          class="jump-btn"
          circle
          @click="scrollToBottom(true)"
        >
          <el-icon><ArrowDown /></el-icon>
        </el-button>
      </transition>

      <ChatComposer
        v-model="draft"
        :loading="loading"
        @send="handleSend()"
        @stop="handleStop"
      />
    </section>
  </div>

  <el-drawer
    v-model="sessionDrawer"
    class="chat-drawer"
    direction="ltr"
    size="280px"
    :with-header="false"
  >
    <ChatSessionList
      class="chat-drawer__panel"
      :groups="groupedSessions"
      :active-id="activeId"
      @create="handleCreateSession"
      @select="handleSwitchSession"
      @rename="(id, title) => renameSession(id, title)"
      @delete="handleDeleteSession"
    />
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { ArrowDown } from "@element-plus/icons-vue";
import ChatSessionList from "@/components/chat/ChatSessionList.vue";
import ChatHeaderBar from "@/components/chat/ChatHeaderBar.vue";
import ChatWelcome from "@/components/chat/ChatWelcome.vue";
import ChatMessageItem from "@/components/chat/ChatMessageItem.vue";
import ChatComposer from "@/components/chat/ChatComposer.vue";
import { ChatApi, getHotQuestionsApi, type ChatMessage, type HotQuestion } from "@/api/ChatApi";
import { streamChat } from "@/api/StreamApi";
import { getRetrievalConfigApi } from "@/api/KnowHubApi";
import { useChatSessions, type ChatSession } from "@/composables/useChatSessions";

const MODE_LABELS: Record<string, string> = {
  SEMANTIC: "语义检索",
  FULL_TEXT: "全文检索",
  HYBRID: "混合检索",
};

const {
  activeId,
  activeSession,
  groupedSessions,
  createSession,
  ensureSession,
  switchSession,
  renameSession,
  deleteSession,
  clearSession,
  appendMessage,
  touchSession,
} = useChatSessions();

const messages = computed<ChatMessage[]>(() => activeSession.value?.messages ?? []);

const draft = ref("");
const loading = ref(false);
const streamingId = ref("");
const configSummary = ref("全局检索配置：读取中");
const sessionDrawer = ref(false);
const isNarrow = ref(window.innerWidth < 1100);

const listRef = ref<HTMLElement | null>(null);
const showJumpButton = ref(false);
const atBottom = ref(true);

// 热门问题由后端按真实提问挖掘，拉取失败时组件内部回退内置引导
const hotQuestions = ref<HotQuestion[]>([]);

let activeController: AbortController | null = null;

const loadRetrievalSummary = () => {
  getRetrievalConfigApi()
    .then((res) => {
      const data = res?.code === 0 ? res.data : null;
      if (!data) {
        configSummary.value = "全局配置：默认检索";
        return;
      }
      const mode = MODE_LABELS[data.retrievalMode] ?? "混合检索";
      const scope = Array.isArray(data.categories) && data.categories.length
        ? `${data.categories.length} 个分类`
        : "全局知识库";
      configSummary.value = `全局配置：${mode} · ${scope}`;
    })
    .catch(() => {
      configSummary.value = "全局配置：默认检索";
    });
};

const loadHotQuestions = () => {
  getHotQuestionsApi(6)
    .then((res) => {
      const data = res?.code === 0 ? res.data : null;
      hotQuestions.value = Array.isArray(data) ? data : [];
    })
    .catch(() => {
      hotQuestions.value = [];
    });
};

const scrollToBottom = (force = false) => {
  const container = listRef.value;
  if (!container) return;
  if (!force && !atBottom.value) return;
  nextTick(() => {
    container.scrollTop = container.scrollHeight;
    atBottom.value = true;
    showJumpButton.value = false;
  });
};

const handleScroll = () => {
  const container = listRef.value;
  if (!container) return;
  const distance = container.scrollHeight - container.scrollTop - container.clientHeight;
  atBottom.value = distance < 80;
  showJumpButton.value = !atBottom.value;
};

const resolveErrorMessage = (error: unknown) => {
  if (error instanceof Error) {
    if (error.message.includes("401")) return "登录状态已过期，请重新登录后重试";
    if (error.message.startsWith("HTTP")) return `服务响应异常（${error.message}）`;
    // 浏览器网络层错误统一提示，其余保留后端下发的错误文案
    if (/failed to fetch|network|load failed|terminated|abort/i.test(error.message)) {
      return "网络中断，回答未能完成";
    }
    return error.message || "回答未能完成";
  }
  return "网络中断，回答未能完成";
};

const finalizeAssistant = (target: ChatMessage) => {
  if (!target.content.trim()) {
    target.status = "error";
    target.error = "未获取到回答内容，请稍后重试";
    return;
  }
  target.status = "done";
};

/** 发起一轮流式问答：先写入用户提问，再持续追加助手回答 */
const startStream = (session: ChatSession, target: ChatMessage, question: string) => {
  activeController?.abort();
  streamingId.value = target.id;

  activeController = streamChat({
    message: question,
    url: ChatApi.RagChat,
    sessionId: session.id,
    onSources: (payload) => {
      target.sources = payload.hits ?? [];
      if (payload.mode) {
        target.retrievalMode = payload.mode;
      }
      if (payload.refused) {
        target.refused = true;
      }
      touchSession(session.id);
    },
    onRefused: (refused) => {
      target.refused = refused;
      touchSession(session.id);
    },
    onDelta: (delta) => {
      target.content += delta;
      touchSession(session.id);
      scrollToBottom();
    },
    onError: (error) => {
      target.status = "error";
      target.error = resolveErrorMessage(error);
      loading.value = false;
      streamingId.value = "";
      activeController = null;
      touchSession(session.id);
    },
    onClose: () => {
      if (target.status === "streaming") {
        finalizeAssistant(target);
      }
      loading.value = false;
      streamingId.value = "";
      activeController = null;
      touchSession(session.id);
    },
  });
};

const sendQuestion = (rawText: string) => {
  const question = rawText.trim();
  if (!question || loading.value) return;

  const session = ensureSession();
  draft.value = "";
  appendMessage(session.id, {
    role: "user",
    content: question,
    createdAt: Date.now(),
    status: "done",
  });

  const assistant = appendMessage(session.id, {
    role: "assistant",
    content: "",
    createdAt: Date.now(),
    status: "streaming",
  });
  if (!assistant) return;

  loading.value = true;
  startStream(session, assistant, question);
  scrollToBottom(true);
};

const handleSend = (question?: string) => {
  sendQuestion(typeof question === "string" ? question : draft.value);
};

const handleStop = () => {
  activeController?.abort();
  activeController = null;
  const target = messages.value.find((message) => message.id === streamingId.value);
  if (target) {
    target.stopped = true;
    if (target.content.trim()) {
      target.status = "done";
    } else {
      target.status = "error";
      target.error = "已停止生成，可重新发送";
    }
  }
  streamingId.value = "";
  loading.value = false;
};

const handleRegenerate = (message: ChatMessage) => {
  if (loading.value) return;
  const session = activeSession.value;
  if (!session) return;
  const index = session.messages.findIndex((item) => item.id === message.id);
  if (index < 0) return;

  let question = "";
  for (let i = index - 1; i >= 0; i -= 1) {
    if (session.messages[i].role === "user") {
      question = session.messages[i].content;
      break;
    }
  }
  if (!question) return;

  session.messages.splice(index, 1);
  const assistant = appendMessage(session.id, {
    role: "assistant",
    content: "",
    createdAt: Date.now(),
    status: "streaming",
  });
  if (!assistant) return;

  loading.value = true;
  startStream(session, assistant, question);
  scrollToBottom(true);
};

const handleFeedback = (message: ChatMessage, value: "up" | "down") => {
  message.feedback = message.feedback === value ? undefined : value;
  ElMessage({
    type: "success",
    message: message.feedback ? "感谢你的反馈" : "已取消反馈",
    duration: 1500,
  });
};

const handleCopy = async (content: string) => {
  try {
    await navigator.clipboard.writeText(content);
    ElMessage({ type: "success", message: "已复制到剪贴板", duration: 1500 });
  } catch {
    ElMessage({ type: "error", message: "复制失败，请手动选择文本", duration: 2000 });
  }
};

const handleCreateSession = () => {
  handleStop();
  createSession();
  draft.value = "";
  sessionDrawer.value = false;
  scrollToBottom(true);
};

const handleSwitchSession = (id: string) => {
  if (id === activeId.value) {
    sessionDrawer.value = false;
    return;
  }
  handleStop();
  switchSession(id);
  sessionDrawer.value = false;
  nextTick(() => scrollToBottom(true));
};

const handleDeleteSession = (id: string) => {
  if (id === activeId.value) {
    handleStop();
  }
  deleteSession(id);
  if (!activeId.value) {
    createSession();
  }
};

const handleClearCurrent = () => {
  const session = activeSession.value;
  if (!session || !session.messages.length) return;
  ElMessageBox.confirm("将清空当前对话的全部消息，是否继续？", "清空对话", {
    confirmButtonText: "清空",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      handleStop();
      clearSession(session.id);
      ElMessage({ type: "success", message: "已清空当前对话", duration: 1500 });
    })
    .catch(() => {});
};

const handleResize = () => {
  isNarrow.value = window.innerWidth < 1100;
  if (!isNarrow.value) {
    sessionDrawer.value = false;
  }
};

onMounted(() => {
  ensureSession();
  loadRetrievalSummary();
  loadHotQuestions();
  window.addEventListener("resize", handleResize);
  scrollToBottom(true);
});

onBeforeUnmount(() => {
  activeController?.abort();
  activeController = null;
  window.removeEventListener("resize", handleResize);
});
</script>

<style scoped lang="less">
.chat-page {
  display: flex;
  gap: 12px;
  /* 外层 el-main 有 20px 内边距，这里严格对齐主框架（扣除 Header/Footer）后的可用高度，避免页面级滚动 */
  height: var(--app-content-h);
  min-height: 520px;
  box-sizing: border-box;
}

.chat-page__aside {
  height: 100%;
}

.chat-page__main {
  position: relative;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  height: 100%;
}

.chat-page__messages {
  flex: 1;
  min-height: 0;
  margin-top: 12px;
  padding: 18px 20px 8px;
  overflow-y: auto;
  background: #ffffff;
  border: 1px solid #e8eef7;
  border-radius: 14px;
  box-shadow: 0 2px 12px rgba(26, 71, 138, 0.06);

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background: #d7e2f2;
    border-radius: 3px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }
}

.jump-btn {
  position: absolute;
  right: 26px;
  bottom: 130px;
  width: 32px;
  height: 32px;
  border-color: #e3ebf7;
  color: #5c7597;
  background: #ffffff;
  box-shadow: 0 6px 16px rgba(26, 71, 138, 0.14);

  &:hover {
    color: #2f7cf6;
    border-color: #cfe0ff;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.chat-drawer {
  :deep(.el-drawer__body) {
    padding: 10px;
    background: #f4f6fa;
  }
}

.chat-drawer__panel {
  height: 100%;
}

@media (max-width: 1100px) {
  .chat-page {
    gap: 0;
  }
}
</style>