package com.common.rag.test.analysis;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.common.rag.entity.HotQuestion;
import com.common.rag.entity.WordFrequency;
import com.common.rag.service.WordFrequencyService;
import com.common.rag.service.analysis.FrequencyAnalysisService;
import com.common.rag.service.analysis.QuestionLogParser;
import com.common.rag.service.analysis.UsageQuestion;
import com.common.rag.service.impl.HotQuestionServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 热门问题挖掘：只更新自动行，人工维护的行（含被编辑过的自动行）不被覆盖
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HotQuestionMiningTest {

    private static final String QUESTION = "差旅报销需要在几天内提交";

    @Mock
    private FrequencyAnalysisService frequencyAnalysisService;

    @Mock
    private WordFrequencyService wordFrequencyService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private HotQuestionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new HotQuestionServiceImpl(frequencyAnalysisService, wordFrequencyService,
                redisTemplate, new ObjectMapper()));
        doReturn(List.of()).when(service).list();
        doReturn(true).when(service).saveBatch(anyList());
        doReturn(true).when(service).updateBatchById(anyList());
        doReturn(true).when(service).removeByIds(anyList());
        doReturn(Set.of()).when(redisTemplate).keys(anyString());
    }

    @Test
    void repeatedQuestionsBecomeOneAutoQuestion() {
        when(frequencyAnalysisService.loadRecentQuestions()).thenReturn(List.of(
                question(QUESTION + "？", LocalDate.now()),
                question(QUESTION, LocalDate.now().minusDays(1)),
                question("你好", LocalDate.now())));
        when(wordFrequencyService.list(ArgumentMatchers.<Wrapper<WordFrequency>>any()))
                .thenReturn(List.of(word("报销", 5), word("差旅", 3)));

        service.refreshFromLogs();

        ArgumentCaptor<Collection<HotQuestion>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(service).saveBatch(captor.capture());
        List<HotQuestion> saved = List.copyOf(captor.getValue());

        assertEquals(1, saved.size(), "同一问题的两种标点写法应合并为一条");
        HotQuestion row = saved.get(0);
        assertEquals(2, row.getHitCount());
        assertEquals("AUTO", row.getSource());
        assertEquals("报销,差旅", row.getKeywords(), "应按热词池顺序关联热词");
        assertEquals(0, row.getPinned());
        assertEquals(1, row.getStatus());
    }

    @Test
    void manualQuestionIsNotOverwritten() {
        HotQuestion manual = row(1L, QUESTION, "MANUAL", 0);
        doReturn(List.of(manual)).when(service).list();
        when(frequencyAnalysisService.loadRecentQuestions()).thenReturn(List.of(
                question(QUESTION + "？", LocalDate.now())));
        when(wordFrequencyService.list(ArgumentMatchers.<Wrapper<WordFrequency>>any()))
                .thenReturn(List.of(word("报销", 5)));

        service.refreshFromLogs();

        verify(service, never()).saveBatch(anyList());
        verify(service, never()).updateBatchById(anyList());
    }

    @Test
    void autoQuestionUpdatesHitCountOnly() {
        HotQuestion auto = row(2L, QUESTION, "AUTO", 0);
        doReturn(List.of(auto)).when(service).list();
        when(frequencyAnalysisService.loadRecentQuestions()).thenReturn(List.of(
                question(QUESTION + "？", LocalDate.now()),
                question(QUESTION, LocalDate.now())));
        when(wordFrequencyService.list(ArgumentMatchers.<Wrapper<WordFrequency>>any()))
                .thenReturn(List.of(word("报销", 5)));

        service.refreshFromLogs();

        ArgumentCaptor<Collection<HotQuestion>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(service).updateBatchById(captor.capture());
        HotQuestion updated = List.copyOf(captor.getValue()).get(0);
        assertEquals(2, updated.getHitCount());
        assertEquals("报销", updated.getKeywords());
    }

    @Test
    void staleAutoQuestionIsRemovedButPinnedIsKept() {
        HotQuestion stale = row(3L, "过期的自动问题", "AUTO", 0);
        HotQuestion pinned = row(4L, "置顶的自动问题", "AUTO", 1);
        doReturn(List.of(stale, pinned)).when(service).list();
        when(frequencyAnalysisService.loadRecentQuestions()).thenReturn(List.of(
                question(QUESTION + "？", LocalDate.now())));
        when(wordFrequencyService.list(ArgumentMatchers.<Wrapper<WordFrequency>>any())).thenReturn(List.of());

        service.refreshFromLogs();

        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(service).removeByIds(captor.capture());
        List<Long> removed = List.copyOf(captor.getValue());
        assertTrue(removed.contains(3L), "掉出榜单的自动问题应清理");
        assertEquals(1, removed.size(), "置顶的自动问题不清理");
    }

    @Test
    void blockedAutoQuestionIsNotRemoved() {
        HotQuestion blocked = row(5L, "被屏蔽的自动问题", "AUTO", 0);
        blocked.setStatus(0);
        doReturn(List.of(blocked)).when(service).list();
        when(frequencyAnalysisService.loadRecentQuestions()).thenReturn(List.of(
                question(QUESTION + "？", LocalDate.now())));
        when(wordFrequencyService.list(ArgumentMatchers.<Wrapper<WordFrequency>>any())).thenReturn(List.of());

        service.refreshFromLogs();

        // 清理后会在下次挖掘时以启用状态复活，所以被屏蔽的行必须保留
        verify(service, never()).removeByIds(anyList());
    }

    private UsageQuestion question(String text, LocalDate date) {
        return new UsageQuestion(text, QuestionLogParser.normalize(text), date);
    }

    private WordFrequency word(String value, int count) {
        WordFrequency wordFrequency = new WordFrequency();
        wordFrequency.setWord(value);
        wordFrequency.setCountNum(count);
        return wordFrequency;
    }

    private HotQuestion row(Long id, String question, String source, int pinned) {
        HotQuestion row = new HotQuestion();
        row.setId(id);
        row.setQuestion(question);
        row.setNormalized(QuestionLogParser.normalize(question));
        row.setSource(source);
        row.setHitCount(1);
        row.setPinned(pinned);
        row.setSortNum(0);
        row.setStatus(1);
        return row;
    }
}