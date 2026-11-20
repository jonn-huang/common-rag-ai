import { fetchEventSource, type EventSourceMessage } from "@microsoft/fetch-event-source";
import { BASE_URL } from "@/http/config.ts";
import service from "@/http";

type ResultCallBack = (e: any | null) => void;

/** 后端在首帧推送的参考文件：一份文件一条，同一文件的多个分片已由后端合并 */
export interface RagSource {
  /** 参考文件名，供对客展示与核对 */
  fileName?: string | null;
  /** 业务分类，如差旅报销 */
  category?: string | null;
  /** 命中分片合并后的摘要 */
  snippet?: string | null;
  /** 以下为旧会话本地缓存里的历史字段，新接口不再下发 */
  index?: number | null;
  chunkIndex?: number | null;
  score?: number | null;
  text?: string | null;
}

/** event=sources 的载荷：本轮实际生效的检索方式、参考文件与拒答标记 */
export interface SourcesEventPayload {
  mode?: string | null;
  hits: RagSource[];
  /** 本轮是否拒答：拒答时前端展示系统提示条并隐藏参考文件 */
  refused?: boolean;
}

export interface StreamChatOptions {
  message: string;
  /** 默认走 RAG 接口 */
  url?: string;
  /** 知识库文件名白名单，空数组表示不限定范围 */
  sources?: string[];
  /** 临时覆盖检索方式：SEMANTIC / FULL_TEXT / HYBRID */
  retrievalMode?: string;
  /** 会话标识，后端用它隔离多轮记忆 */
  sessionId?: string;
  onSources?: (payload: SourcesEventPayload) => void;
  /** 流结束时下发：模型是否以拒答话术作答 */
  onRefused?: (refused: boolean) => void;
  onDelta: (text: string) => void;
  onError?: (error: unknown) => void;
  onClose?: () => void;
}

const BaseUrl = BASE_URL;

/** 处理 401 未授权：清理登录态并跳转登录页 */
const redirectToLogin = () => {
  import("@/api/authUtils").then((module) => module.default());
};

/**
 * 统一的流式问答：解析 sources / delta / refused / done / error 五类事件，
 * 返回 AbortController 供页面中断生成
 */
export const streamChat = (options: StreamChatOptions): AbortController => {
  const { message, url = "/ai/rag", sources = [], retrievalMode, sessionId } = options;
  const ctrl = new AbortController();
  let finished = false;

  const finish = () => {
    if (finished) return;
    finished = true;
    options.onClose?.();
  };

  const formData = new FormData();
  formData.append("message", message);
  if (retrievalMode) {
    formData.append("retrievalMode", retrievalMode);
  }
  if (sessionId) {
    formData.append("sessionId", sessionId);
  }
  sources.forEach((source) => formData.append("sources", source));

  const handleEvent = (msg: EventSourceMessage) => {
    // 未命名事件按增量文本处理，兼容 /chat/stream 返回的纯文本流
    const event = msg.event && msg.event !== "message" ? msg.event : "delta";
    if (!msg.data) return;
    if (event === "sources") {
      try {
        options.onSources?.(JSON.parse(msg.data) as SourcesEventPayload);
      } catch {
        options.onSources?.({ hits: [] });
      }
      return;
    }
    if (event === "refused") {
      options.onRefused?.(msg.data === "true");
      return;
    }
    if (event === "done") {
      finish();
      ctrl.abort();
      return;
    }
    if (event === "error") {
      options.onError?.(new Error(msg.data || "流式响应异常"));
      finish();
      ctrl.abort();
      return;
    }
    options.onDelta(msg.data);
  };

  void fetchEventSource(service.defaults.baseURL + url, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${localStorage.getItem("token") || ""}`,
    },
    body: formData,
    signal: ctrl.signal,
    openWhenHidden: true,
    onmessage: handleEvent,
    onclose: finish,
    onerror: (err: any) => {
      options.onError?.(err);
      finish();
      // 抛出后由外层 catch 吞掉，避免框架自动重试造成内容重复
      throw err;
    },
    onopen: async (response: any) => {
      if (response.ok) {
        return;
      }
      if (response.status === 401) {
        redirectToLogin();
        finish();
        return new Promise<void>(() => {});
      }
      options.onError?.(new Error(`HTTP ${response.status}: ${response.statusText}`));
      finish();
      return new Promise<void>(() => {});
    },
  }).catch(() => {
    // 错误已通过 onError 上报，这里只阻止自动重试
  });

  return ctrl;
};

/**
 * 旧接口保留：按 { data } 形式回调增量文本
 */
export const getStreamChat = (
  message: string,
  url: string = "/chat/stream",
  onMessage: ResultCallBack,
  onError: ResultCallBack,
  onClose: ResultCallBack,
  sources?: string[]
) => {
  return streamChat({
    message,
    url,
    sources,
    onDelta: (text) => onMessage({ data: text }),
    onError,
    onClose: () => onClose(null),
  });
};

export const postStreamChat = (
  author: string,
  onMessage: ResultCallBack,
  onError: ResultCallBack,
  onClose: ResultCallBack
) => {
  const ctrl = new AbortController();
  fetchEventSource(BaseUrl + "/post-chat", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      author: author,
    }),
    signal: ctrl.signal,
    onmessage: onMessage,
    onerror: (err: any) => {
      onError(err);
    },
    onclose: () => {
      onClose(null);
    },
    onopen: async (response: any) => {
      if (response.ok) {
        return;
      } else if (
        response.status >= 400 &&
        response.status < 500 &&
        response.status !== 429
      ) {
        // 直接调用onError回调，并终止连接防止自动重试
        onError(new Error(`HTTP ${response.status}: ${response.statusText}`));
        return new Promise<void>(() => {});
      } else {
        onError(new Error(`HTTP ${response.status}: ${response.statusText}`));
        return new Promise<void>(() => {});
      }
    },
  });
  return ctrl;
};

// 专门用于POST请求的流式聊天函数
export const postStreamChatWithSources = (
  message: string,
  sources: string[],
  url: string = "/ai/rag",
  onMessage: ResultCallBack,
  onError: ResultCallBack,
  onClose: ResultCallBack
) => {
  const ctrl = new AbortController();

  const formData = new FormData();
  formData.append("message", message);
  sources.forEach((source) => {
    formData.append("sources", source);
  });

  fetchEventSource(service.defaults.baseURL + url, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${localStorage.getItem("token") || ""}`,
    },
    body: formData,
    signal: ctrl.signal,
    onmessage: onMessage,
    onerror: (err: any) => {
      onError(err);
    },
    onclose: () => {
      onClose(null);
    },
    onopen: async (response: any) => {
      if (response.ok) {
        return;
      } else if (response.status === 401) {
        redirectToLogin();
        return new Promise<void>(() => {});
      } else {
        onError(new Error(`HTTP ${response.status}: ${response.statusText}`));
        return new Promise<void>(() => {});
      }
    },
  });
  return ctrl;
};