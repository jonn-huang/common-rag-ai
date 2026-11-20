import service from "@/http";
import { getStreamChat, type RagSource } from "./StreamApi";

export const ChatApi = {
  Chat: "/chat/stream",
  RagChat: "/ai/rag",
  HotQuestions: "/ai/hot-questions",
};

/** 问答页推荐问题，由后端按真实提问挖掘，keywords 为关联热词 */
export interface HotQuestion {
  id?: number;
  question: string;
  keywords: string[];
  hitCount: number;
}

/** 拉取热门问题，后端按置顶、排序值与提问次数返回，失败时由调用方回退默认引导 */
export const getHotQuestionsApi = async (limit = 6): Promise<any> => {
  return service.get(ChatApi.HotQuestions, { params: { limit } });
};

export type { RagSource };

export type ChatRole = "user" | "assistant";

/** pending：已入列未开始；streaming：流式输出中；done：完成；error：异常 */
export type ChatMessageStatus = "pending" | "streaming" | "done" | "error";

/** 聊天消息，含引用来源与反馈状态，便于会话持久化后完整还原 */
export interface ChatMessage {
  id: string;
  role: ChatRole;
  content: string;
  createdAt: number;
  status: ChatMessageStatus;
  /** 本轮命中的知识库分片 */
  sources?: RagSource[];
  /** 本轮实际使用的检索方式 */
  retrievalMode?: string;
  /** 本轮是否拒答：拒答时展示系统提示条，不展示参考文件 */
  refused?: boolean;
  feedback?: "up" | "down";
  /** 错误提示文案，status 为 error 时展示 */
  error?: string;
  /** 是否被用户手动停止 */
  stopped?: boolean;
  /** 兼容旧字段 */
  isTyping?: boolean;
}

// 发送消息接口 (传统fetch方式)
export const sendChatMessageApi = async (message: string): Promise<Response> => {
  return fetch(`${service.defaults.baseURL}${ChatApi.Chat}?message=${encodeURIComponent(message)}`, {
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('token')}`
    }
  }).then(response => {
    if (response.status === 401) {
      // 处理401未授权错误
      import('@/api/authUtils').then(module => {
        module.default();
      });
    }
    return response;
  });
};

// 发送RAG消息接口
export const sendRagChatMessageApi = async (message: string, sources: string[] = []): Promise<Response> => {
  const formData = new FormData();
  formData.append('message', message);
  
  if (sources && sources.length > 0) {
    sources.forEach(source => {
      formData.append('sources', source);
    });
  }
  
  return fetch(`${service.defaults.baseURL}${ChatApi.RagChat}`, {
    method: 'POST',
    body: formData,
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
    }
  }).then(response => {
    if (response.status === 401) {
      // 处理401未授权错误
      import('@/api/authUtils').then(module => {
        module.default();
      });
    }
    return response;
  });
};

// 发送消息接口 (SSE方式)
export const sendChatMessageWithSSE = (
  message: string,
  onMessage: (event: any) => void,
  onError: (error: any) => void,
  onClose: () => void
) => {
  return getStreamChat(
    message,
    ChatApi.Chat,
    onMessage,
    onError,
    onClose
  );
};