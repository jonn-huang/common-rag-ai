package com.common.rag.service.analysis.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.common.rag.core.ApplicationConstant;
import com.common.rag.entity.LogInfo;
import com.common.rag.entity.WordFrequency;
import com.common.rag.pojo.vo.FrequencyTrendVO;
import com.common.rag.service.LogInfoService;
import com.common.rag.service.WordFrequencyService;
import com.common.rag.service.analysis.FrequencyAnalysisService;
import com.common.rag.service.analysis.QuestionLogParser;
import com.common.rag.service.analysis.UsageQuestion;
import com.common.rag.service.analysis.WordFrequencyAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 热词统计实现：只统计真实提问，全量重算覆盖写，不再做累加
 */
@Slf4j
@Service
public class FrequencyAnalysisServiceImpl implements FrequencyAnalysisService {

    /**
     * 词频业务类型，保持与历史数据一致
     */
    private static final String BUSINESS_TYPE = "log";

    /**
     * 单批写库条数，避免一条 SQL 过大
     */
    private static final int BATCH_SIZE = 500;

    /**
     * 单次重算读取日志的上限，超出时只取最近的日志，防止内存被打满
     */
    private static final int MAX_LOG_ROWS = 20000;

    /**
     * 趋势图最多返回的热词条数
     */
    private static final int MAX_TREND_WORDS = 10;

    /**
     * 趋势图最长天数
     */
    private static final int MAX_TREND_DAYS = 90;

    private final LogInfoService logInfoService;

    private final WordFrequencyService wordFrequencyService;

    private final WordFrequencyAnalyzer analyzer;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 统计窗口天数，只统计最近 N 天的提问
     */
    private final int windowDays;

    public FrequencyAnalysisServiceImpl(LogInfoService logInfoService,
                                        WordFrequencyService wordFrequencyService,
                                        WordFrequencyAnalyzer analyzer,
                                        RedisTemplate<String, Object> redisTemplate,
                                        @Value("${rag.frequency.window-days:30}") int windowDays) {
        this.logInfoService = logInfoService;
        this.wordFrequencyService = wordFrequencyService;
        this.analyzer = analyzer;
        this.redisTemplate = redisTemplate;
        this.windowDays = Math.max(1, windowDays);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rebuild() {
        List<UsageQuestion> questions = loadRecentQuestions();
        Map<String, WordAccumulator> stats = new LinkedHashMap<>();
        for (UsageQuestion question : questions) {
            analyzer.analyze(question.question()).forEach((word, count) ->
                    stats.computeIfAbsent(word, key -> new WordAccumulator()).add(count, question.date()));
        }

        // 先清后写：重算结果就是全部数据，不做任何累加，保证幂等；
        // 只删本业务类型，且与批量写入同事务，避免中途失败把热词表清空或停在半表状态
        wordFrequencyService.remove(new LambdaQueryWrapper<WordFrequency>()
                .eq(WordFrequency::getBusinessType, BUSINESS_TYPE));
        if (stats.isEmpty()) {
            evictCache();
            log.info("热词重算完成：统计窗口 {} 天，提问 {} 条，无有效词", windowDays, questions.size());
            return 0;
        }

        Date createTime = new Date();
        List<WordFrequency> records = new ArrayList<>(stats.size());
        stats.forEach((word, accumulator) -> {
            WordFrequency record = new WordFrequency();
            record.setWord(word);
            record.setCountNum(accumulator.count);
            record.setBusinessType(BUSINESS_TYPE);
            record.setCreateTime(createTime);
            record.setUpdateTime(toDate(accumulator.lastSeen));
            records.add(record);
        });
        records.sort(Comparator.comparing(WordFrequency::getCountNum).reversed());

        for (int start = 0; start < records.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, records.size());
            wordFrequencyService.saveBatch(records.subList(start, end));
        }
        evictCache();
        log.info("热词重算完成：统计窗口 {} 天，提问 {} 条，写入热词 {} 个", windowDays, questions.size(), records.size());
        return records.size();
    }

    @Override
    public FrequencyTrendVO dailyTrend(int days, int topN) {
        // 趋势区间不能超出实际统计窗口，否则早期天数恒为 0，页面会误判成没有提问
        int window = Math.min(Math.max(days, 1), Math.min(MAX_TREND_DAYS, windowDays));
        int limit = Math.min(Math.max(topN, 1), MAX_TREND_WORDS);

        List<LocalDate> dateList = new ArrayList<>(window);
        LocalDate today = LocalDate.now();
        for (int offset = window - 1; offset >= 0; offset--) {
            dateList.add(today.minusDays(offset));
        }
        List<String> dates = dateList.stream().map(LocalDate::toString).toList();
        Set<LocalDate> dateSet = new HashSet<>(dateList);

        Map<String, Map<LocalDate, Integer>> counters = new HashMap<>();
        for (UsageQuestion question : loadRecentQuestions()) {
            if (question.date() == null || !dateSet.contains(question.date())) {
                continue;
            }
            for (String word : analyzer.segment(question.question())) {
                counters.computeIfAbsent(word, key -> new HashMap<>())
                        .merge(question.date(), 1, Integer::sum);
            }
        }

        List<FrequencyTrendVO.Series> series = counters.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Map<LocalDate, Integer>> entry) ->
                        sum(entry.getValue())).reversed())
                .limit(limit)
                .map(entry -> FrequencyTrendVO.Series.builder()
                        .word(entry.getKey())
                        .data(dateList.stream()
                                .map(date -> entry.getValue().getOrDefault(date, 0))
                                .toList())
                        .build())
                .toList();

        return FrequencyTrendVO.builder().dates(dates).series(series).build();
    }

    @Override
    public List<UsageQuestion> loadRecentQuestions() {
        LocalDate fromDate = LocalDate.now().minusDays(windowDays - 1L);
        LambdaQueryWrapper<LogInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(LogInfo::getClassName, QuestionLogParser.QUESTION_CLASS_KEYWORD)
                .eq(LogInfo::getMethodName, QuestionLogParser.QUESTION_METHOD)
                .isNotNull(LogInfo::getRequestParams)
                .ge(LogInfo::getRequestTime, toDate(fromDate))
                // 超出上限时保留最近的日志，早年的数据对热词没有参考价值
                .orderByDesc(LogInfo::getId)
                .last("LIMIT " + MAX_LOG_ROWS);

        List<LogInfo> logs = logInfoService.list(wrapper);
        if (logs == null || logs.isEmpty()) {
            return List.of();
        }

        List<UsageQuestion> questions = new ArrayList<>(logs.size());
        for (LogInfo logInfo : logs) {
            String question = QuestionLogParser.extractQuestion(logInfo.getRequestParams());
            if (!StringUtils.hasText(question)) {
                continue;
            }
            questions.add(new UsageQuestion(question, QuestionLogParser.normalize(question),
                    logInfo.getRequestTime() == null ? LocalDate.now() : toLocalDate(logInfo.getRequestTime())));
        }
        return questions;
    }

    @Override
    public int windowDays() {
        return windowDays;
    }

    private void evictCache() {
        redisTemplate.delete(ApplicationConstant.CACHE_WORD_FREQUENCY);
    }

    private static int sum(Map<LocalDate, Integer> values) {
        return values.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static LocalDate toLocalDate(Date date) {
        return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * word -> 累计次数 + 最近出现日期
     */
    private static final class WordAccumulator {

        private int count;

        private LocalDate lastSeen;

        private void add(int times, LocalDate date) {
            this.count += times;
            if (date != null && (this.lastSeen == null || date.isAfter(this.lastSeen))) {
                this.lastSeen = date;
            }
        }
    }
}