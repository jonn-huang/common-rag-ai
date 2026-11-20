/** 禁止渲染的标签，避免模型输出或用户内容注入脚本 */
const BLOCKED_TAGS =
  "script,style,iframe,frame,frameset,object,embed,form,input,textarea,select,link,meta,base";

const DANGEROUS_PROTOCOL = /^(javascript|vbscript|data):/i;

/**
 * 轻量 HTML 清洗：模型返回的 Markdown 会转成 HTML 后直接渲染，
 * 这里拦掉脚本、事件属性与危险协议，不引入额外依赖
 */
export const sanitizeHtml = (html: string): string => {
  if (!html) return "";
  const holder = document.createElement("div");
  holder.innerHTML = html;

  holder.querySelectorAll(BLOCKED_TAGS).forEach((node) => node.remove());

  holder.querySelectorAll<HTMLElement>("*").forEach((element) => {
    Array.from(element.attributes).forEach((attribute) => {
      const name = attribute.name.toLowerCase();
      const value = (attribute.value || "").trim();
      if (name.startsWith("on")) {
        element.removeAttribute(attribute.name);
        return;
      }
      if (
        (name === "href" || name === "src" || name === "xlink:href") &&
        DANGEROUS_PROTOCOL.test(value)
      ) {
        element.removeAttribute(attribute.name);
      }
    });
    if (element.tagName === "A") {
      element.setAttribute("target", "_blank");
      element.setAttribute("rel", "noopener noreferrer");
    }
  });

  return holder.innerHTML;
};

/** 去掉标签，用于引用摘要等纯文本场景 */
export const stripHtml = (html: string): string => {
  const holder = document.createElement("div");
  holder.innerHTML = html;
  return (holder.textContent || "").replace(/\s+/g, " ").trim();
};