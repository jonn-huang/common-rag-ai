import type { ChunkStrategy } from "@/api/dto";

/**
 * 切片策略选项：上传/编辑弹窗与列表回显共用同一份名称，
 * 避免各处单独维护导致展示名称不一致
 */
export const CHUNK_STRATEGY_OPTIONS: {
  value: ChunkStrategy;
  label: string;
  desc: string;
}[] = [
  {
    value: "FIXED",
    label: "定长切片",
    desc: "按固定字数切分，切片长度均匀，实现简单",
  },
  {
    value: "RECURSIVE",
    label: "递归切片",
    desc: "按段落、句子等分隔符递归切分，尽量不破坏语义边界",
  },
  {
    value: "SEMANTIC",
    label: "语义切片",
    desc: "按语义相似度切分，单个切片主题更完整，大文件耗时较长",
  },
  {
    value: "STRUCTURED",
    label: "结构切片",
    desc: "识别标题、列表、表格等文档结构后进行切分",
  },
];

const CHUNK_STRATEGY_LABELS: Record<string, string> = Object.fromEntries(
  CHUNK_STRATEGY_OPTIONS.map((item) => [item.value, item.label])
);

/**
 * 策略编码转名称，与上传弹窗选项保持一致；未知编码原样返回，空值显示 -
 */
export const chunkStrategyLabel = (strategy?: string) =>
  CHUNK_STRATEGY_LABELS[strategy ?? ""] ?? (strategy || "-");