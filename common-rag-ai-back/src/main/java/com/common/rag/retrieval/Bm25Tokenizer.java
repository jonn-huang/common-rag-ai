package com.common.rag.retrieval;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @description: BM25 轻量分词，中文按二元组（bigram）、英文数字按连续串切分，无需额外分词依赖
 */
public final class Bm25Tokenizer {

    private static final int MAX_LATIN_TOKEN_LENGTH = 64;

    private Bm25Tokenizer() {
    }

    /**
     * 统计词频，索引构建与查询共用同一套切分规则
     */
    public static Map<String, Integer> termFrequency(String text) {
        Map<String, Integer> tf = new HashMap<>();
        if (text == null || text.isBlank()) {
            return tf;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder latin = new StringBuilder();
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (isCjk(c)) {
                flushLatin(latin, tf);
                cjk.append(c);
            } else if (Character.isLetterOrDigit(c)) {
                flushCjk(cjk, tf);
                if (latin.length() < MAX_LATIN_TOKEN_LENGTH) {
                    latin.append(c);
                }
            } else {
                flushLatin(latin, tf);
                flushCjk(cjk, tf);
            }
        }
        flushLatin(latin, tf);
        flushCjk(cjk, tf);
        return tf;
    }

    private static void flushLatin(StringBuilder buffer, Map<String, Integer> tf) {
        if (buffer.length() == 0) {
            return;
        }
        tf.merge(buffer.toString(), 1, Integer::sum);
        buffer.setLength(0);
    }

    /**
     * 中文按相邻两字成词，单字句保留单字，兼顾召回与准确率
     */
    private static void flushCjk(StringBuilder buffer, Map<String, Integer> tf) {
        if (buffer.length() == 0) {
            return;
        }
        String text = buffer.toString();
        buffer.setLength(0);
        if (text.length() == 1) {
            tf.merge(text, 1, Integer::sum);
            return;
        }
        for (int i = 0; i + 1 < text.length(); i++) {
            tf.merge(text.substring(i, i + 2), 1, Integer::sum);
        }
    }

    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF);
    }
}
