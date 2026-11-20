import { computed, ref, watch } from "vue";
import type { ChatMessage } from "@/api/ChatApi";

/** 一条会话：消息列表 + 标题 + 时间戳，持久化在浏览器本地 */
export interface ChatSession {
  id: string;
  title: string;
  createdAt: number;
  updatedAt: number;
  messages: ChatMessage[];
}

export interface SessionGroup {
  label: string;
  items: ChatSession[];
}

const STORAGE_KEY = "rag-chat:sessions:v1";
const ACTIVE_KEY = "rag-chat:active:v1";
const MAX_SESSIONS = 50;
const DEFAULT_TITLE = "新对话";
const DAY = 24 * 60 * 60 * 1000;

export const createId = () =>
  typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;

const readSessions = (): ChatSession[] => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((item: any) => item && typeof item.id === "string" && Array.isArray(item.messages))
      .map((item: any) => ({
        id: item.id,
        title: typeof item.title === "string" && item.title ? item.title : DEFAULT_TITLE,
        createdAt: Number(item.createdAt) || Date.now(),
        updatedAt: Number(item.updatedAt) || Number(item.createdAt) || Date.now(),
        messages: item.messages as ChatMessage[],
      }));
  } catch {
    return [];
  }
};

const sessions = ref<ChatSession[]>(readSessions());
const activeId = ref<string>(localStorage.getItem(ACTIVE_KEY) || sessions.value[0]?.id || "");

let persistTimer: number | undefined;
const persist = () => {
  if (persistTimer) {
    window.clearTimeout(persistTimer);
  }
  // 流式输出时每个增量都会触发，延迟写入避免频繁序列化大对象
  persistTimer = window.setTimeout(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions.value));
      localStorage.setItem(ACTIVE_KEY, activeId.value);
    } catch {
      // 本地存储满时忽略，保证聊天不中断
    }
  }, 300);
};

watch(sessions, persist, { deep: true });
watch(activeId, persist);

const trimSessions = () => {
  if (sessions.value.length <= MAX_SESSIONS) return;
  sessions.value = [...sessions.value]
    .sort((left, right) => right.updatedAt - left.updatedAt)
    .slice(0, MAX_SESSIONS);
};

export const useChatSessions = () => {
  const activeSession = computed(
    () => sessions.value.find((item) => item.id === activeId.value) ?? null
  );

  const groupedSessions = computed<SessionGroup[]>(() => {
    const now = new Date();
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    const buckets: SessionGroup[] = [
      { label: "今天", items: [] },
      { label: "昨天", items: [] },
      { label: "近 7 天", items: [] },
      { label: "更早", items: [] },
    ];
    const ordered = [...sessions.value].sort((left, right) => right.updatedAt - left.updatedAt);
    ordered.forEach((session) => {
      if (session.updatedAt >= startOfToday) {
        buckets[0].items.push(session);
      } else if (session.updatedAt >= startOfToday - DAY) {
        buckets[1].items.push(session);
      } else if (session.updatedAt >= startOfToday - 6 * DAY) {
        buckets[2].items.push(session);
      } else {
        buckets[3].items.push(session);
      }
    });
    return buckets.filter((bucket) => bucket.items.length > 0);
  });

  const createSession = (title = DEFAULT_TITLE): ChatSession => {
    const now = Date.now();
    const id = createId();
    sessions.value.unshift({ id, title, createdAt: now, updatedAt: now, messages: [] });
    trimSessions();
    activeId.value = id;
    return sessions.value.find((item) => item.id === id)!;
  };

  const ensureSession = (): ChatSession => {
    if (activeSession.value) return activeSession.value;
    return createSession();
  };

  const switchSession = (id: string) => {
    if (sessions.value.some((item) => item.id === id)) {
      activeId.value = id;
    }
  };

  const renameSession = (id: string, title: string) => {
    const session = sessions.value.find((item) => item.id === id);
    if (!session) return;
    const next = title.trim();
    session.title = next ? next.slice(0, 30) : DEFAULT_TITLE;
  };

  const deleteSession = (id: string) => {
    sessions.value = sessions.value.filter((item) => item.id !== id);
    if (activeId.value === id) {
      activeId.value = sessions.value[0]?.id ?? "";
    }
  };

  const clearSession = (id: string) => {
    const session = sessions.value.find((item) => item.id === id);
    if (!session) return;
    session.messages = [];
    session.updatedAt = Date.now();
  };

  /**
   * 追加消息并返回数组中的响应式对象，流式输出时直接改它的 content
   */
  const appendMessage = (
    sessionId: string,
    message: Omit<ChatMessage, "id"> & { id?: string }
  ): ChatMessage | null => {
    const session = sessions.value.find((item) => item.id === sessionId);
    if (!session) return null;
    const created: ChatMessage = { ...message, id: message.id ?? createId() };
    session.messages.push(created);
    session.updatedAt = Date.now();
    // 首次提问自动用问题作为会话标题
    if (message.role === "user" && session.title === DEFAULT_TITLE) {
      const title = message.content.trim().replace(/\s+/g, " ");
      if (title) {
        session.title = title.slice(0, 20);
      }
    }
    return session.messages[session.messages.length - 1];
  };

  const touchSession = (sessionId: string) => {
    const session = sessions.value.find((item) => item.id === sessionId);
    if (session) session.updatedAt = Date.now();
  };

  return {
    sessions,
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
  };
};