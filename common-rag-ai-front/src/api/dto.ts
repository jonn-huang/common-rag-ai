
/**
 * 请求对象
 */
export interface Message {
  role: string;
  content: string;
}

export interface ChatOptions {
  model: string;
  maxHistoryLength: number;
  chatType: string;
  temperature: number;
}

export interface ChatDTO {
  messages: Message[];
  chatOptions: ChatOptions;
  prompt: string;
}

export interface UploadFileDTO {
  files: File[];
}

export interface SelectDto {
  page: number;
  pageSize: number;
}
export interface AddDto {
  baseUrl: string;
  apiKey: string;
  describe: string;
}

export interface QueryFileDto {
  page: number;
  pageSize: number;
  fileName: string | undefined;
  /** 知识库分类，不传表示不限制 */
  category?: string;
}

/** 切片策略：定长 / 递归 / 语义 / 结构 */
export type ChunkStrategy = "FIXED" | "RECURSIVE" | "SEMANTIC" | "STRUCTURED";

/** 定长切片参数 */
export interface FixedChunkConfig {
  /** 切片最大长度 */
  maxLength: number;
  /** 相邻切片重叠字符数 */
  overlap: number;
}

/** 递归切片参数 */
export interface RecursiveChunkConfig {
  /** 切片目标长度 */
  targetLength: number;
  /** 相邻切片重叠字符数 */
  overlap: number;
  /** 自定义分隔符，多个用 | 分隔，留空使用默认分隔符 */
  separators: string;
  /** 是否保留分隔符 */
  keepSeparator: boolean;
}

/** 语义切片参数 */
export interface SemanticChunkConfig {
  /** 分句长度 */
  sentenceLength: number;
  /** 语义相似度阈值 0~1 */
  similarityThreshold: number;
  /** 期望切片长度 */
  targetLength: number;
  /** 相邻切片重叠字符数 */
  overlap: number;
}

/** 结构切片参数 */
export interface StructuredChunkConfig {
  /** 单块最大长度 */
  maxLength: number;
  /** 单块最小长度 */
  minLength: number;
  /** 相邻切片重叠字符数 */
  overlap: number;
  /** 自动追加标题到切片 */
  appendTitle: boolean;
  /** 识别失败自动降级递归切片 */
  fallbackToRecursive: boolean;
}

/** 各切片策略的参数集合 */
export interface ChunkConfig {
  fixed: FixedChunkConfig;
  recursive: RecursiveChunkConfig;
  semantic: SemanticChunkConfig;
  structured: StructuredChunkConfig;
}

/** 切片预览中的单条切片 */
export interface ChunkPreviewItem {
  /** 切片序号，从 1 开始 */
  index: number;
  /** 切片字符数 */
  length: number;
  /** 标题路径，仅结构切片有值 */
  heading?: string | null;
  /** 切片正文 */
  text: string;
  /** 切片元数据：文件名、分类、切片序号与总数等 */
  metadata?: Record<string, unknown>;
}

/** 切片预览结果 */
export interface ChunkPreviewResult {
  /** 预览的文件名 */
  fileName?: string | null;
  /** 使用的切片策略 */
  chunkStrategy: ChunkStrategy;
  /** 切片策略中文描述 */
  chunkStrategyDesc: string;
  /** 切片总数 */
  total: number;
  /** 是否因为条数限制而截断 */
  truncated: boolean;
  /** 本次返回的切片 */
  chunks: ChunkPreviewItem[];
}

/** 后端 @RequestParam List<Long> ids，支持单个 id 或逗号拼接的 id 串 */
export interface DeleteFileDto {
  ids: string | number;
}

/** 同 DeleteFileDto，ids 允许逗号拼接 */
export interface DownloadFileDto {
  ids: string | number;
}

/** 编辑知识库文件：修改知识库分类与切片策略，策略变化时后端重新切片并向量化 */
export interface UpdateFileDto {
  /** 文件主键 */
  id: number;
  /** 知识库分类 */
  category?: string;
  /** 切片策略 */
  chunkStrategy?: ChunkStrategy;
  /** 当前策略对应的切片参数 JSON 字符串 */
  chunkConfig?: string;
}

/** 分片管理列表查询：按文件维度分页 */
export interface QueryChunkDto {
  /** 所属文件主键 */
  fileId: number;
  /** 分片正文关键词，可选 */
  keyword?: string;
  page: number;
  pageSize: number;
}

/** 修改分片正文：保存后后端同步更新向量与全文索引 */
export interface UpdateChunkDto {
  /** 分片主键 */
  id: number;
  /** 修改后的分片正文 */
  content: string;
}

/** 分片管理中的单条分片 */
export interface KnowledgeChunkItem {
  id: number;
  fileId: number;
  fileName?: string | null;
  category?: string | null;
  /** 分片序号，从 1 开始 */
  chunkIndex: number;
  /** 分片字符数 */
  length: number;
  /** 分片正文 */
  content: string;
}

/** 分片管理分页结果 */
export interface KnowledgeChunkPage {
  total: number;
  records: KnowledgeChunkItem[];
  /** 分片与向量索引是否一致，false 时只允许查看，需重新切片后才能编辑或删除 */
  vectorSynced: boolean;
}

/** 检索方式：语义检索 / 全文检索（BM25） / 混合检索 */
export type RetrievalMode = "SEMANTIC" | "FULL_TEXT" | "HYBRID";

/** 混合检索融合方式：权重加权 / RRF 排名融合 */
export type FusionStrategy = "WEIGHTED" | "RRF";

/** 知识库检索配置：categories 为空表示全局知识库 */
export interface RetrievalConfigDto {
  /** 生效的知识库分类，空数组代表全局 */
  categories: string[];
  /** 检索方式 */
  retrievalMode: RetrievalMode;
  /** 语义检索召回数量 */
  vectorTopK: number;
  /** 语义检索最低相似度阈值 0~1 */
  similarityThreshold: number;
  /** 全文检索召回数量 */
  keywordTopK: number;
  /** 全文检索最低 BM25 得分，为空表示不限制 */
  keywordMinScore?: number | null;
  /** 混合检索融合方式 */
  fusionStrategy: FusionStrategy;
  /** 向量权重 */
  vectorWeight: number;
  /** 关键词权重 */
  keywordWeight: number;
  /** 是否开启 Rerank 重排 */
  rerankEnabled: boolean;
  /** 重排后保留条数 */
  rerankTopN: number;
}

/** 检索调试命中的分片 */
export interface RetrievalHitItem {
  fileName?: string | null;
  category?: string | null;
  chunkIndex?: number | null;
  /** 当前检索方式下的得分：语义为相似度、全文为 BM25、混合为融合分 */
  score?: number;
  text?: string;
}

/** 问答提示词配置：单套全局模板，由「我的知识库 - 问答提示词」页面维护 */
export interface AnswerConfigDto {
  /** 系统提示词模板，支持 {refusal} 与 {current_data} 占位符，上限 8000 字符 */
  systemPrompt: string;
  /** 超出业务范围或资料不足时的固定回复，上限 512 字符 */
  refusalMessage: string;
  /** 检索未命中时是否直接拒答（不调用大模型） */
  refuseWhenNoContext: boolean;
  /** 语义检索最低应答相似度 0~1，为空表示不按得分拒答 */
  minAnswerScore?: number | null;
  /** 最近一次保存时间，未保存过为空 */
  updateTime?: string | null;
}

export interface DrawOptions {
  model: string;
  width: number;
  height: number;
  format: string;
}

export interface DrawImageDto {
  prompt: string;
  options: DrawOptions;
}