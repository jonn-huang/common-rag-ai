package com.common.rag.service.analysis;

import com.common.rag.pojo.vo.FrequencyTrendVO;

import java.util.List;

/**
 * 基于真实问答日志的热词与热门问题分析。
 * <p>
 * 统计口径固定为 AiRagController#generatePost 的提问文本，全量重算覆盖写，
 * 不再对历史日志做累加，避免定时任务每跑一次就把频次翻一倍。
 */
public interface FrequencyAnalysisService {

    /**
     * 全量重算热词并覆盖写库，幂等：重复执行结果一致
     *
     * @return 重算后写入的词条数
     */
    int rebuild();

    /**
     * 最近 N 天 TOP 热词的每日出现次数，用于替换前端曾经的随机数趋势
     */
    FrequencyTrendVO dailyTrend(int days, int topN);

    /**
     * 读取统计窗口内的真实提问，供热词重算与热门问题挖掘共用
     */
    List<UsageQuestion> loadRecentQuestions();

    /**
     * 当前统计窗口天数
     */
    int windowDays();
}