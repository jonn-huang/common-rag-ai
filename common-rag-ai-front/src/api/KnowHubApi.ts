import { KnowApi } from "./common";
import { BASE_URL } from "@/http/config";
import axios from "axios";
import { DownloadFileDto,DeleteFileDto, QueryFileDto, QueryChunkDto, UpdateFileDto, UpdateChunkDto, RetrievalConfigDto, AnswerConfigDto } from "./dto";
import service from "@/http";
import handleAuthError from "@/api/authUtils";

type Res = any;

const fileService = axios.create({
  baseURL: BASE_URL,
  headers: {
    "Content-Type": "multipart/form-data",
  },
});


// 创建请求拦截器
fileService.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token"); 
    if (token !== null) {
      config.headers.Authorization = "Bearer " + token;
    }
    return config;
  },
  (error) => {
    console.log(error);
    return Promise.reject(error);
  }
);

// 创建响应拦截器
fileService.interceptors.response.use(
  (res: any) => { 
    
    return res;
  },
  (error) => { 
    // 检查是否是401错误
    if (error.response && error.response.status === 401) {
      handleAuthError();
    }
    
    console.log(error);
    return Promise.reject(error);
  }
);

// 上传知识库接口
// chunkStrategy：切片策略，FIXED 定长 / RECURSIVE 递归 / SEMANTIC 语义 / STRUCTURED 结构
// chunkConfig：当前策略对应的切片参数，以 JSON 字符串随表单提交
export const uploadFileApi = async (
  filesList: File[],
  category?: string,
  chunkStrategy?: string,
  chunkConfig?: unknown
): Promise<Res> => {
  let formData = new FormData();
  filesList.map((e) => {
    formData.append("file", e);
  });
  if (category) {
    formData.append("category", category);
  }
  if (chunkStrategy) {
    formData.append("chunkStrategy", chunkStrategy);
  }
  if (chunkConfig) {
    formData.append("chunkConfig", JSON.stringify(chunkConfig));
  }

  return fileService.post(KnowApi.UploadFile, formData);
};

// 切片预览接口：只解析与切片，不落库、不写向量库，返回前 limit 条切片
// limit：返回的切片条数，后端默认 10，上限 50
export const previewChunkApi = async (
  file: File,
  chunkStrategy?: string,
  chunkConfig?: unknown,
  limit = 10
): Promise<Res> => {
  const formData = new FormData();
  formData.append("file", file);
  if (chunkStrategy) {
    formData.append("chunkStrategy", chunkStrategy);
  }
  if (chunkConfig) {
    formData.append("chunkConfig", JSON.stringify(chunkConfig));
  }
  formData.append("limit", String(limit));

  return fileService.post(KnowApi.PreviewChunk, formData);
};

// 查询单个文件的详情：含切片策略、切片参数与分片数量
export const fileDetailApi = async (id: number): Promise<Res> => {
  return service.get(KnowApi.FileDetail, {
    params: { id },
  });
};

// 已入库文件的分片预览：按文件已保存的切片策略重新计算，返回前 limit 条
// 传入 chunkStrategy/chunkConfig 时按新参数试算，用于重新切片前预览效果
export const previewStoredChunksApi = async (
  id: number,
  limit = 10,
  chunkStrategy?: string,
  chunkConfig?: unknown
): Promise<Res> => {
  return service.get(KnowApi.FileChunks, {
    params: {
      id,
      limit,
      chunkStrategy,
      chunkConfig: chunkConfig ? JSON.stringify(chunkConfig) : undefined,
    },
  });
};

// 分片管理：按文件分页查询已入库分片，keyword 用于过滤分片正文
export const listChunksApi = async (params: QueryChunkDto): Promise<Res> => {
  return service.get(KnowApi.ChunkList, { params });
};

// 修改单条分片正文：后端同步更新向量库与全文索引
export const updateChunkApi = async (data: UpdateChunkDto): Promise<Res> => {
  return service.post(KnowApi.ChunkUpdate, data);
};

// 删除分片：后端同步清理向量库与文件上的向量记录
export const deleteChunkApi = async (params: DeleteFileDto): Promise<Res> => {
  return service.delete(KnowApi.ChunkDelete, { params });
};

// 编辑文件：修改知识库分类与切片策略，策略或参数变化时后端会重新切片并向量化
export const updateFileApi = async (data: UpdateFileDto): Promise<Res> => {
  return service.post(KnowApi.UpdateFile, data);
};

// 查询所有知识库接口
export const queryFileApi = async (params: QueryFileDto): Promise<Res> => {
  console.log("请求参数：", params);

  return service.get(KnowApi.QueryFile, {
    params,
  });
};

// 删除指定ID列表的知识库
export const deleteFileApi = async (params: DeleteFileDto): Promise<Res> => {
  return service.delete(KnowApi.DeleteFile, {
    params,
  });
};

// 下载指定ID列表的知识库：单个直接返回文件流，多个返回 zip 流
export const downloadFileApi = async (params: DownloadFileDto): Promise<Res> => {
  return fileService.get(KnowApi.DownloadFile, {
    params,
    responseType: "blob",
  });
};

// 读取知识库检索配置：未配置时后端返回默认值（混合检索 + 全局知识库）
export const getRetrievalConfigApi = async (): Promise<Res> => {
  return service.get(KnowApi.RetrievalConfig);
};

// 保存知识库检索配置：知识库分类绑定、检索方式与各模式参数
// 注意：保存后全局生效，RAG 问答按此配置决定召回方式
export const saveRetrievalConfigApi = async (
  data: RetrievalConfigDto
): Promise<Res> => {
  return service.post(KnowApi.RetrievalConfig, data);
};

// 知识库已有分类：供检索配置多选绑定
export const listRetrievalCategoriesApi = async (): Promise<Res> => {
  return service.get(KnowApi.RetrievalCategories);
};

// 重建关键词检索索引：从 OSS 取回历史文件重新切片，补齐全文检索（BM25）语料
export const rebuildRetrievalIndexApi = async (): Promise<Res> => {
  return service.post(KnowApi.RetrievalRebuildIndex);
};

// 按当前配置试检索，用于确认三种检索方式的召回效果
export const previewRetrievalApi = async (query: string): Promise<Res> => {
  return service.get(KnowApi.RetrievalPreview, {
    params: { query },
  });
};

// 读取问答提示词配置：未保存过时后端返回默认提示词与拒答策略
export const getAnswerConfigApi = async (): Promise<Res> => {
  return service.get(KnowApi.AnswerConfig);
};

// 保存问答提示词配置：保存后下一次问答立即生效，无需重启
export const saveAnswerConfigApi = async (
  data: AnswerConfigDto
): Promise<Res> => {
  return service.post(KnowApi.AnswerConfig, data);
};

// 系统默认提示词：供页面「恢复默认」填充表单，不会直接落库
export const getAnswerDefaultApi = async (): Promise<Res> => {
  return service.get(KnowApi.AnswerDefault);
};