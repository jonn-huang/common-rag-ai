package com.common.rag.test.analysis;

import com.common.rag.service.analysis.QuestionLogParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日志参数解析：三种存量格式都要能取出真实提问，否则热词统计口径依然是脏的
 */
class QuestionLogParserTest {

    @Test
    void extractQuestionFromLegacyArrayFormat() {
        assertEquals("你好，请用一句话介绍一下你自己",
                QuestionLogParser.extractQuestion("[你好，请用一句话介绍一下你自己]"));
        assertEquals("我的知识库有多少内容呢",
                QuestionLogParser.extractQuestion("[我的知识库有多少内容呢, null]"));
        assertEquals("what is the acceptance test case",
                QuestionLogParser.extractQuestion("[what is the acceptance test case, null]"));
    }

    @Test
    void extractQuestionDropsTrailingSessionArgs() {
        // 线上真实的 4 参日志：message、retrievalMode、sources、sessionId
        assertEquals("差旅报销需要在几天内提交？",
                QuestionLogParser.extractQuestion(
                        "[差旅报销需要在几天内提交？, null, null, 3f2b1c8e-9d2f-4a71-8a1c-5f6d7e8a9b0c]"));
        assertEquals("差旅报销需要在几天内提交？",
                QuestionLogParser.extractQuestion(
                        "[差旅报销需要在几天内提交？, HYBRID, [], 3f2b1c8e-9d2f-4a71-8a1c-5f6d7e8a9b0c]"));
    }

    @Test
    void extractQuestionKeepsEqualsAndCommaInQuestion() {
        assertEquals("x=1, 怎么计算", QuestionLogParser.extractQuestion("{message=x=1, 怎么计算}"));
    }

    @Test
    void extractQuestionFromMapFormat() {
        assertEquals("报销需要准备哪些凭证？",
                QuestionLogParser.extractQuestion("{message=报销需要准备哪些凭证？}"));
    }

    @Test
    void extractQuestionDropsAdminAndEmptyParams() {
        assertNull(QuestionLogParser.extractQuestion("{}"));
        assertNull(QuestionLogParser.extractQuestion("[]"));
        assertNull(QuestionLogParser.extractQuestion("   "));
        assertNull(QuestionLogParser.extractQuestion(null));
    }

    @Test
    void extractQuestionUnescapesLineBreaks() {
        assertEquals("你是什么模型", QuestionLogParser.extractQuestion("[你是什么模型\\r\\n]"));
    }

    @Test
    void normalizeRemovesPunctuationAndWhitespace() {
        assertEquals("whatis报销", QuestionLogParser.normalize("What is 报销?"));
        assertEquals("差旅报销需要在几天内提交", QuestionLogParser.normalize("差旅报销需要在几天内提交？"));
    }

    @Test
    void trivialQuestionsAreFiltered() {
        assertTrue(QuestionLogParser.isTrivial("你好"));
        assertTrue(QuestionLogParser.isTrivial("你是什么模型"));
        assertTrue(QuestionLogParser.isTrivial("hello"));
        assertFalse(QuestionLogParser.isTrivial("差旅报销需要在几天内提交？"));
    }
}