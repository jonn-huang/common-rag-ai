package com.common.rag.test.analysis;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.common.rag.entity.LogInfo;
import com.common.rag.entity.WordFrequency;
import com.common.rag.pojo.vo.FrequencyTrendVO;
import com.common.rag.service.LogInfoService;
import com.common.rag.service.WordFrequencyService;
import com.common.rag.service.analysis.WordFrequencyAnalyzer;
import com.common.rag.service.analysis.impl.FrequencyAnalysisServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 热词重算必须幂等：连续执行两次得到的计数完全一致，不能像旧逻辑那样每跑一次就翻一倍
 */
@ExtendWith(MockitoExtension.class)
class FrequencyAnalysisServiceTest {

    @Mock
    private LogInfoService logInfoService;

    @Mock
    private WordFrequencyService wordFrequencyService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private FrequencyAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        WordFrequencyAnalyzer analyzer = new WordFrequencyAnalyzer();
        analyzer.loadStopWords();
        service = new FrequencyAnalysisServiceImpl(logInfoService, wordFrequencyService, analyzer, redisTemplate, 30);
    }

    @Test
    void rebuildIsIdempotent() {
        when(logInfoService.list(ArgumentMatchers.<Wrapper<LogInfo>>any())).thenReturn(List.of(
                log("[差旅报销需要在几天内提交？, null]", LocalDate.now()),
                log("[差旅报销需要在几天内提交？, null]", LocalDate.now().minusDays(1)),
                log("[你是什么模型, null]", LocalDate.now())));

        service.rebuild();
        service.rebuild();

        ArgumentCaptor<List<WordFrequency>> captor = ArgumentCaptor.forClass(List.class);
        verify(wordFrequencyService, times(2)).saveBatch(captor.capture());
        Map<String, Integer> first = toCountMap(captor.getAllValues().get(0));
        Map<String, Integer> second = toCountMap(captor.getAllValues().get(1));

        assertEquals(first, second, "两次重算结果必须一致，说明统计是覆盖写而不是累加");
        assertTrue(first.containsKey("报销"), "业务提问应统计出「报销」");
        assertEquals(2, first.get("报销"), "同一提问出现两次，计数只能是 2");
        // 每次重算前都清空，保证幂等
        verify(wordFrequencyService, times(2)).remove(any());
    }

    @Test
    void rebuildSkipsUnparseableLogs() {
        when(logInfoService.list(ArgumentMatchers.<Wrapper<LogInfo>>any())).thenReturn(List.of(
                log("{}", LocalDate.now()),
                log("[null, null]", LocalDate.now())));

        assertEquals(0, service.rebuild());
        verify(wordFrequencyService, never()).saveBatch(anyList());
    }

    @Test
    void dailyTrendCoversEveryDayInWindow() {
        when(logInfoService.list(ArgumentMatchers.<Wrapper<LogInfo>>any())).thenReturn(List.of(
                log("[差旅报销需要在几天内提交？, null]", LocalDate.now()),
                log("[差旅报销需要在几天内提交？, null]", LocalDate.now().minusDays(2))));

        FrequencyTrendVO trend = service.dailyTrend(3, 5);

        assertEquals(3, trend.getDates().size());
        assertFalse(trend.getSeries().isEmpty());
        trend.getSeries().forEach(series -> assertEquals(3, series.getData().size(), "每个词的曲线必须与日期轴等长"));
    }

    private Map<String, Integer> toCountMap(List<WordFrequency> records) {
        Map<String, Integer> result = new LinkedHashMap<>();
        records.forEach(record -> result.put(record.getWord(), record.getCountNum()));
        return result;
    }

    private LogInfo log(String params, LocalDate date) {
        LogInfo logInfo = new LogInfo();
        logInfo.setClassName("com.common.rag.controller.AiRagController");
        logInfo.setMethodName("generatePost");
        logInfo.setRequestParams(params);
        logInfo.setRequestTime(java.sql.Date.valueOf(date));
        return logInfo;
    }
}