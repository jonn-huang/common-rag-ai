import service from "@/http";
type Res = any;

export const HotQuestionApi = {
  Page: "/hot-question/page",
  Save: "/hot-question/save",
  Status: "/hot-question/status",
  Delete: "/hot-question/delete",
  Rebuild: "/hot-question/rebuild",
};

/** 热门问题记录，管理页使用，问答页取子集见 ChatApi.HotQuestion */
export interface HotQuestionInfo {
  id: number;
  question: string;
  keywords: string[];
  hitCount: number;
  /** 1 置顶，0 普通 */
  pinned: number;
  /** 越小越靠前 */
  sortNum: number;
  /** 1 启用，0 屏蔽 */
  status: number;
  /** AUTO 自动挖掘，MANUAL 人工维护 */
  source: "AUTO" | "MANUAL";
  updateTime: string;
}

// 分页查询参数，page 从 1 开始
export interface HotQuestionQueryParams {
  page: number;
  pageSize: number;
  keyword?: string;
  status?: number;
  source?: string;
}

// 新增/编辑入参，id 为空表示新增
export interface HotQuestionSaveDTO {
  id?: number;
  question: string;
  keywords?: string[];
  pinned?: number;
  sortNum?: number;
  status?: number;
}

export const queryHotQuestionApi = async (params: HotQuestionQueryParams): Promise<Res> => {
  return service.post(HotQuestionApi.Page, params);
};

export const saveHotQuestionApi = async (dto: HotQuestionSaveDTO): Promise<Res> => {
  return service.post(HotQuestionApi.Save, dto);
};

// 单独调整置顶、排序或启用状态，只传需要变更的字段
export const changeHotQuestionStatusApi = async (
  id: number,
  payload: { status?: number; pinned?: number; sortNum?: number }
): Promise<Res> => {
  return service.post(HotQuestionApi.Status, null, { params: { id, ...payload } });
};

export const deleteHotQuestionApi = async (id: number): Promise<Res> => {
  return service.delete(HotQuestionApi.Delete, { params: { id } });
};

// 立即按真实提问重新挖掘，返回变动的记录数
export const rebuildHotQuestionApi = async (): Promise<Res> => {
  return service.post(HotQuestionApi.Rebuild);
};