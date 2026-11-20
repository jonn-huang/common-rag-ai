package com.common.rag.scheduled;

import com.common.rag.service.HotQuestionService;
import com.common.rag.service.analysis.FrequencyAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @Title: TaskJobScheduled
 * @Package com.common.rag.scheduled
 * @description: 热点词与热门问题统计任务
 * <p>
 * 统计口径为 AiRagController#generatePost 的真实提问，每次执行全量重算覆盖写，
 * 不再像过去那样对历史日志累加，否则每小时跑一次就会把频次翻一倍。
 */
@Slf4j
@Component("taskJob")
public class TaskJobScheduled {

    private final FrequencyAnalysisService frequencyAnalysisService;

    private final HotQuestionService hotQuestionService;

    public TaskJobScheduled(FrequencyAnalysisService frequencyAnalysisService,
                            HotQuestionService hotQuestionService) {
        this.frequencyAnalysisService = frequencyAnalysisService;
        this.hotQuestionService = hotQuestionService;
    }

    /**
     * 每小时第 0 分执行：先重算热词，再基于同一批提问同步热门问题
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void taskJob() {
        try {
            frequencyAnalysisService.rebuild();
        } catch (Exception e) {
            log.error("热词重算任务执行失败", e);
        }
        try {
            hotQuestionService.refreshFromLogs();
        } catch (Exception e) {
            log.error("热门问题挖掘任务执行失败", e);
        }
    }
}