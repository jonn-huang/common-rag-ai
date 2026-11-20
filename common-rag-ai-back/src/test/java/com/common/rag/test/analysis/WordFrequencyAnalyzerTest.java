package com.common.rag.test.analysis;

import com.common.rag.service.analysis.WordFrequencyAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分词过滤：寒暄、结构字符、数字与文件后缀都不应该进入热点词
 */
class WordFrequencyAnalyzerTest {

    private WordFrequencyAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new WordFrequencyAnalyzer();
        analyzer.loadStopWords();
    }

    @Test
    void noiseWordsAreFiltered() {
        assertFalse(analyzer.isMeaningful("你好"));
        assertFalse(analyzer.isMeaningful("请用"));
        assertFalse(analyzer.isMeaningful("一句话"));
        assertFalse(analyzer.isMeaningful("null"));
        assertFalse(analyzer.isMeaningful("12345"));
        assertFalse(analyzer.isMeaningful("你"));
        assertFalse(analyzer.isMeaningful("report.pdf"));
        assertFalse(analyzer.isMeaningful("http"));
        assertFalse(analyzer.isMeaningful("\\\\"));
    }

    @Test
    void businessWordsAreKept() {
        assertTrue(analyzer.isMeaningful("报销"));
        assertTrue(analyzer.isMeaningful("差旅"));
        assertTrue(analyzer.isMeaningful("审批"));
    }

    @Test
    void segmentDropsGreetings() {
        List<String> words = analyzer.segment("你好，请用一句话介绍一下你自己");
        // 整句基本都是寒暄，过滤后不应再出现这些词
        assertFalse(words.contains("你好"));
        assertFalse(words.contains("请用"));
        assertFalse(words.contains("一句话"));
        words.forEach(word -> assertFalse(word.contains("null"), "不应切出结构字符：" + word));
    }

    @Test
    void segmentKeepsBusinessTopics() {
        List<String> words = analyzer.segment("差旅报销需要在几天内提交？");
        assertNotNull(words);
        assertFalse(words.isEmpty(), "业务提问应至少切出一个有效词");
        // 停用词与单字已被过滤
        words.forEach(word -> assertTrue(word.length() >= 2, "不应保留单字：" + word));
    }
}