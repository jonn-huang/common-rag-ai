package com.common.rag.etl.splitter;

import com.common.rag.enums.ChunkStrategyEnum;
import com.common.rag.pojo.dto.ChunkConfigDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @description: 结构切片，按 markdown 标题与中文章节编号识别目录层级，优先保证一节一块（标题与正文不被拆散）。
 * 未识别到任何标题且开启降级时，退回递归切片。
 */
@Slf4j
@Component
public class StructuredTextSplitter implements TextChunkSplitter {

    /**
     * markdown 标题，最多 6 级
     */
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");

    /**
     * 中文章节标题：第X章 / 第X节 / 第X篇 / 第X部
     */
    private static final Pattern CHAPTER_HEADING =
            Pattern.compile("^(第[零一二三四五六七八九十百千0-9]+[章节篇部])\\s*(.*)$");

    /**
     * 数字编号标题：1 / 1.1 / 2.3.1
     */
    private static final Pattern NUMBER_HEADING = Pattern.compile("^(\\d+(?:\\.\\d+)*)[\\s、.．:：]*\\S.*$");

    /**
     * 中文数字编号标题：一、二、
     */
    private static final Pattern CHINESE_NUMBER_HEADING = Pattern.compile("^([一二三四五六七八九十]+)、\\s*\\S.*$");

    /**
     * 标题行最大长度，超过该长度视为正文，避免把长句误判为标题
     */
    private static final int MAX_HEADING_LENGTH = 60;

    /**
     * 正文过短时的合并分隔符
     */
    private static final String JOIN_SEPARATOR = "\n\n";

    private final RecursiveCharacterSplitter recursiveSplitter;

    public StructuredTextSplitter(RecursiveCharacterSplitter recursiveSplitter) {
        this.recursiveSplitter = recursiveSplitter;
    }

    @Override
    public ChunkStrategyEnum strategy() {
        return ChunkStrategyEnum.STRUCTURED;
    }

    @Override
    public List<TextChunk> split(String text, ChunkConfigDTO config) {
        List<Section> sections = parseSections(text);
        boolean headingFound = sections.stream().anyMatch(section -> StringUtils.hasText(section.heading()));
        if (!headingFound) {
            if (config.fallbackToRecursive()) {
                log.info("未识别到目录结构，结构切片降级为递归切片");
                return recursiveSplitter.split(text, config);
            }
            return List.of(new TextChunk(text));
        }
        List<TextChunk> chunks = new ArrayList<>();
        for (Section section : sections) {
            chunks.addAll(splitSection(section, config));
        }
        return mergeShortChunks(chunks, config);
    }

    /**
     * 解析目录结构：按标题行切小节，并用标题栈拼出层级路径
     */
    private List<Section> parseSections(String text) {
        Deque<Heading> stack = new ArrayDeque<>();
        List<Section> sections = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        String currentHeading = null;
        for (String rawLine : text.split("\n", -1)) {
            Heading heading = detectHeading(rawLine.trim());
            if (heading == null) {
                body.append(rawLine).append('\n');
                continue;
            }
            // 遇到新标题先结束上一节，标题前的正文单独成节，避免内容丢失
            if (StringUtils.hasText(body)) {
                sections.add(new Section(currentHeading, body.toString().trim()));
                body.setLength(0);
            }
            while (!stack.isEmpty() && stack.peek().level() >= heading.level()) {
                stack.pop();
            }
            stack.push(heading);
            currentHeading = joinHeadingPath(stack);
        }
        if (StringUtils.hasText(body)) {
            sections.add(new Section(currentHeading, body.toString().trim()));
        }
        return sections;
    }

    /**
     * 标题栈自根到叶子拼接为 "第二章 > 2.1 概述" 形式
     */
    private String joinHeadingPath(Deque<Heading> stack) {
        List<String> titles = new ArrayList<>();
        stack.descendingIterator().forEachRemaining(heading -> titles.add(heading.title()));
        return String.join(" > ", titles);
    }

    /**
     * 识别标题行，识别不到返回 null
     */
    private Heading detectHeading(String line) {
        if (!StringUtils.hasText(line) || line.length() > MAX_HEADING_LENGTH) {
            return null;
        }
        Matcher markdown = MARKDOWN_HEADING.matcher(line);
        if (markdown.matches()) {
            return new Heading(markdown.group(1).length(), markdown.group(2).trim());
        }
        if (CHAPTER_HEADING.matcher(line).matches()) {
            return new Heading(1, line);
        }
        if (CHINESE_NUMBER_HEADING.matcher(line).matches()) {
            return new Heading(1, line);
        }
        Matcher number = NUMBER_HEADING.matcher(line);
        if (number.matches()) {
            // 1.1 这类编号按小数点个数确定层级，便于还原目录嵌套
            return new Heading(number.group(1).split("\\.").length, line);
        }
        return null;
    }

    /**
     * 切分单个小节：超长小节再做递归切分，并给每块补齐标题
     */
    private List<TextChunk> splitSection(Section section, ChunkConfigDTO config) {
        String heading = section.heading();
        String body = section.body();
        if (!StringUtils.hasText(body)) {
            return StringUtils.hasText(heading) ? List.of(new TextChunk(heading, heading)) : List.of();
        }
        boolean withTitle = config.appendTitle() && StringUtils.hasText(heading);
        String content = withTitle ? heading + JOIN_SEPARATOR + body : body;
        if (content.length() <= config.maxLength()) {
            return List.of(new TextChunk(content, heading));
        }
        // 超长小节按"去掉标题后的可用长度"递归切分，保证加了标题也不超长
        ChunkConfigDTO subConfig = new ChunkConfigDTO();
        int available = withTitle ? config.maxLength() - heading.length() - JOIN_SEPARATOR.length() : config.maxLength();
        subConfig.setTargetLength(Math.max(available, 100));
        subConfig.setOverlap(config.overlap());
        subConfig.setKeepSeparator(true);
        subConfig.normalize(ChunkStrategyEnum.RECURSIVE);
        List<TextChunk> pieces = recursiveSplitter.split(body, subConfig);
        List<TextChunk> result = new ArrayList<>(pieces.size());
        for (TextChunk piece : pieces) {
            String value = withTitle ? heading + JOIN_SEPARATOR + piece.text() : piece.text();
            result.add(new TextChunk(value, heading));
        }
        return result;
    }

    /**
     * 过短切片与相邻切片合并，合并后不超过单块最大长度
     */
    private List<TextChunk> mergeShortChunks(List<TextChunk> chunks, ChunkConfigDTO config) {
        int minLength = config.minLength();
        int maxLength = config.maxLength();
        List<TextChunk> merged = new ArrayList<>();
        TextChunk pending = null;
        for (TextChunk chunk : chunks) {
            if (pending == null) {
                pending = chunk;
                continue;
            }
            boolean tooShort = pending.text().length() < minLength;
            boolean mergeable = pending.text().length() + chunk.text().length() + JOIN_SEPARATOR.length() <= maxLength;
            if (tooShort && mergeable) {
                String heading = StringUtils.hasText(pending.heading()) ? pending.heading() : chunk.heading();
                pending = new TextChunk(pending.text() + JOIN_SEPARATOR + chunk.text(), heading);
            } else {
                merged.add(pending);
                pending = chunk;
            }
        }
        if (pending != null) {
            merged.add(pending);
        }
        return merged;
    }

    private record Heading(int level, String title) {
    }

    private record Section(String heading, String body) {
    }
}
