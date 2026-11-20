package com.common.rag.service.analysis;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.wltea.analyzer.core.IKSegmenter;
import org.wltea.analyzer.core.Lexeme;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 提问文本分词与过滤。
 * <p>
 * 只做"对业务有意义的词"，把寒暄、单字、纯数字、纯符号、URL、文件名后缀过滤掉，
 * 避免热点词页面出现「你好」「一句话」「null」这类噪音。
 */
@Slf4j
@Component
public class WordFrequencyAnalyzer {

    private static final String STOPWORD_PATH = "stopwords/zh-stopwords.txt";

    /**
     * 最短词长，单字没有统计价值
     */
    private static final int MIN_WORD_LENGTH = 2;

    /**
     * 最长词长，超过的多是整句未切开的片段
     */
    private static final int MAX_WORD_LENGTH = 10;

    private static final Pattern DIGITS = Pattern.compile("^\\d+$");

    private static final Pattern URL_PART = Pattern.compile("(https?|www|com|cn|net|org|html?)", Pattern.CASE_INSENSITIVE);

    private static final Pattern FILE_SUFFIX = Pattern.compile(".*\\.(pdf|docx?|xlsx?|pptx?|txt|md|csv|png|jpe?g)$",
            Pattern.CASE_INSENSITIVE);

    /**
     * 日志格式残留，命中说明切到了 request_params 的结构字符
     */
    private static final Pattern LOG_NOISE = Pattern.compile("[\\[\\]{}=\"\\\\]|null", Pattern.CASE_INSENSITIVE);

    private Set<String> stopWords = Collections.emptySet();

    @PostConstruct
    public void loadStopWords() {
        ClassPathResource resource = new ClassPathResource(STOPWORD_PATH);
        if (!resource.exists()) {
            log.warn("停用词表不存在，词频统计将只按长度与字符类型过滤：{}", STOPWORD_PATH);
            return;
        }
        Set<String> words = new HashSet<>();
        try (InputStream input = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (StringUtils.hasText(word) && !word.startsWith("#")) {
                    words.add(word.toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException e) {
            log.error("停用词表读取失败：{}", STOPWORD_PATH, e);
        }
        this.stopWords = words;
        log.info("停用词表加载完成，共 {} 个词", words.size());
    }

    /**
     * 统计一段文本中有效词的出现次数
     */
    public Map<String, Integer> analyze(String text) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String word : segment(text)) {
            result.merge(word, 1, Integer::sum);
        }
        return result;
    }

    /**
     * 切词并过滤，返回按原顺序排列的有效词
     */
    public List<String> segment(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        List<String> words = new ArrayList<>();
        try (StringReader reader = new StringReader(text)) {
            IKSegmenter segment = new IKSegmenter(reader, true);
            Lexeme lexeme;
            while ((lexeme = segment.next()) != null) {
                String word = lexeme.getLexemeText();
                if (!isMeaningful(word)) {
                    continue;
                }
                words.add(word);
            }
        } catch (IOException e) {
            log.warn("分词失败，跳过该段文本：{}", text, e);
        }
        return words;
    }

    /**
     * 单个词是否有统计价值
     */
    public boolean isMeaningful(String word) {
        if (!StringUtils.hasText(word)) {
            return false;
        }
        String value = word.trim();
        if (value.length() < MIN_WORD_LENGTH || value.length() > MAX_WORD_LENGTH) {
            return false;
        }
        if (DIGITS.matcher(value).matches()) {
            return false;
        }
        // 纯符号（标点、空格）直接丢弃
        if (value.chars().noneMatch(Character::isLetterOrDigit)) {
            return false;
        }
        if (URL_PART.matcher(value).matches() || FILE_SUFFIX.matcher(value).matches()) {
            return false;
        }
        if (LOG_NOISE.matcher(value).find()) {
            return false;
        }
        return !stopWords.contains(value.toLowerCase(Locale.ROOT));
    }
}