import service from "@/http";
type Res = any;
export const FrequencyApi = {
  QueryPage: "/frequency/page",
  Clean: "/frequency/clean",
  List: "/frequency/getList",
  Trend: "/frequency/trend",
  Rebuild: "/frequency/rebuild",
  Window: "/frequency/window",
};

// 查询参数接口，page 从 1 开始
export interface FrequencyQueryParams {
  page: number;
  pageSize: number;
  word?: string;
  businessType?: string;
  countNumMin?: number;
  /** count 按次数降序（默认），updateTime 按最近出现时间降序 */
  orderBy?: "count" | "updateTime";
}

// 词频信息接口
export interface FrequencyInfo {
  id: number;
  word: string;
  countNum: number;
  businessType: string;
  createTime: string;
  updateTime: string;
}

// 趋势图中单个热词的每日次数，与 dates 等长
export interface FrequencyTrendSeries {
  word: string;
  data: number[];
}

// 热词趋势，日期按升序
export interface FrequencyTrend {
  dates: string[];
  series: FrequencyTrendSeries[];
}

// 分页查询词频
export const queryFrequencyApi = async (params: FrequencyQueryParams): Promise<Res> => {
  return service.post(FrequencyApi.QueryPage, params);
};

// 清空词频数据，后端为 DELETE 且会同步清理 Redis 缓存
export const cleanFrequencyApi = async (): Promise<Res> => {
  return service.delete(FrequencyApi.Clean);
};

// 按次数降序取热词，limit 为空表示全量
export const listFrequencyApi = async (limit?: number): Promise<Res> => {
  return service.get(FrequencyApi.List, { params: limit ? { limit } : undefined });
};

// 真实趋势数据，days 为区间天数，topN 为词条数
export const getFrequencyTrendApi = async (days = 14, topN = 5): Promise<Res> => {
  return service.get(FrequencyApi.Trend, { params: { days, topN } });
};

// 立即按真实提问重新统计，幂等
export const rebuildFrequencyApi = async (): Promise<Res> => {
  return service.post(FrequencyApi.Rebuild);
};

// 当前统计窗口天数，用于展示统计口径
export const getFrequencyWindowApi = async (): Promise<Res> => {
  return service.get(FrequencyApi.Window);
};