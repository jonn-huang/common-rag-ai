<template>
  <div ref="rootRef" class="markdown-body" v-html="html"></div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from "vue";
import { marked } from "marked";
import hljs from "highlight.js";
import { sanitizeHtml } from "@/utils/sanitize";

const props = defineProps<{ content: string }>();

const html = ref("");
const rootRef = ref<HTMLElement | null>(null);
let frameId = 0;

/** 给代码块补上语言标签与复制按钮 */
const decorate = () => {
  const root = rootRef.value;
  if (!root) return;
  root.querySelectorAll<HTMLElement>("pre").forEach((pre) => {
    if (pre.closest(".code-block")) return;
    const code = pre.querySelector("code");
    if (code) {
      try {
        hljs.highlightElement(code);
      } catch {
        // 未知语言时保持纯文本
      }
    }
    const language = code?.className.match(/language-([\w-]+)/)?.[1] ?? "代码";
    const wrapper = document.createElement("div");
    wrapper.className = "code-block";
    const bar = document.createElement("div");
    bar.className = "code-bar";
    const label = document.createElement("span");
    label.className = "code-lang";
    label.textContent = language;
    const button = document.createElement("button");
    button.type = "button";
    button.className = "code-copy";
    button.textContent = "复制";
    button.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(code?.textContent ?? "");
        button.textContent = "已复制";
      } catch {
        button.textContent = "复制失败";
      }
      window.setTimeout(() => {
        button.textContent = "复制";
      }, 1500);
    });
    bar.append(label, button);
    pre.parentElement?.insertBefore(wrapper, pre);
    wrapper.append(bar, pre);
  });
};

const render = () => {
  try {
    const parsed = marked.parse(props.content ?? "", { breaks: true, gfm: true });
    html.value = sanitizeHtml(typeof parsed === "string" ? parsed : props.content ?? "");
  } catch {
    html.value = props.content ?? "";
  }
  nextTick(decorate);
};

// 流式输出时每个增量都会触发，用 rAF 把一帧内的多次更新合并成一次渲染
const scheduleRender = () => {
  if (frameId) return;
  frameId = window.requestAnimationFrame(() => {
    frameId = 0;
    render();
  });
};

watch(() => props.content, scheduleRender, { immediate: true });

onBeforeUnmount(() => {
  if (frameId) window.cancelAnimationFrame(frameId);
});
</script>

<style scoped lang="less">
.markdown-body {
  font-size: 14px;
  line-height: 1.75;
  color: #1f2d3d;
  word-break: break-word;

  :deep(> :first-child) {
    margin-top: 0;
  }

  :deep(> :last-child) {
    margin-bottom: 0;
  }

  :deep(p) {
    margin: 0 0 10px;
  }

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4) {
    margin: 18px 0 10px;
    font-weight: 600;
    line-height: 1.4;
    color: #16233a;
  }

  :deep(h1) {
    font-size: 19px;
  }

  :deep(h2) {
    font-size: 17px;
  }

  :deep(h3) {
    font-size: 15px;
  }

  :deep(ul),
  :deep(ol) {
    margin: 8px 0;
    padding-left: 20px;
  }

  :deep(li) {
    margin: 4px 0;
  }

  :deep(a) {
    color: #2f7cf6;
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }

  :deep(blockquote) {
    margin: 10px 0;
    padding: 8px 12px;
    border-left: 3px solid #b9d0f2;
    border-radius: 0 6px 6px 0;
    background: #f5f8fe;
    color: #4a5b74;
  }

  :deep(code) {
    font-family: "JetBrains Mono", Consolas, Monaco, "Andale Mono", monospace;
    font-size: 12.5px;
    padding: 2px 5px;
    border-radius: 4px;
    background: #eef3fb;
    color: #1f4b8f;
  }

  :deep(.code-block) {
    margin: 10px 0;
    border: 1px solid #e3ebf7;
    border-radius: 10px;
    overflow: hidden;
    background: #f8fafd;
  }

  :deep(.code-bar) {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 6px 12px;
    background: #eef3fb;
    border-bottom: 1px solid #e3ebf7;
  }

  :deep(.code-lang) {
    font-size: 12px;
    color: #6b7a90;
    text-transform: lowercase;
  }

  :deep(.code-copy) {
    border: none;
    background: transparent;
    color: #2f7cf6;
    font-size: 12px;
    cursor: pointer;
    padding: 0;

    &:hover {
      text-decoration: underline;
    }
  }

  :deep(pre) {
    margin: 0;
    padding: 12px 14px;
    overflow-x: auto;
    background: transparent;
  }

  :deep(pre code) {
    padding: 0;
    background: transparent;
    color: #1f2d3d;
    white-space: pre;
  }

  :deep(table) {
    width: 100%;
    margin: 10px 0;
    border-collapse: collapse;
    font-size: 13px;
  }

  :deep(th),
  :deep(td) {
    border: 1px solid #e3ebf7;
    padding: 6px 10px;
    text-align: left;
  }

  :deep(th) {
    background: #f5f8fe;
    font-weight: 600;
  }

  :deep(hr) {
    margin: 14px 0;
    border: none;
    border-top: 1px solid #e3ebf7;
  }

  :deep(img) {
    max-width: 100%;
    border-radius: 8px;
  }
}
</style>