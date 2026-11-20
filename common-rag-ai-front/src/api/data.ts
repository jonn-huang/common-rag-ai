export interface StoreFile {
  id: number;
  url: string;
  fileName: string;
  /** 文件大小，单位：字节 */
  fileSize: number;
  /** 知识库分类 */
  category: string;
  /** 切片策略：FIXED / RECURSIVE / SEMANTIC / STRUCTURED */
  chunkStrategy?: string;
  /** 切片策略中文描述 */
  chunkStrategyDesc?: string;
  /** 切片参数 JSON（后端返回字符串） */
  chunkConfig?: string;
  /** 已入库的分片数量 */
  chunkCount?: number;
  /** ETL 状态：PENDING / PROCESSING / SUCCESS / FAILED */
  status: string;
  /** ETL 状态中文描述 */
  statusDesc: string;
  /** 该文件分割出的向量文本ID（后端返回 JSON 字符串） */
  vectorId: string;
  createTime: Date;
  updateTime: Date;
}
