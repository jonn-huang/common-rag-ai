package com.common.rag.pojo.dto;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.common.rag.enums.ChunkStrategyEnum;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @description: 切片参数，四种策略的参数合并到同一个对象，按所选策略只取用其中一部分。
 * 前端只提交当前策略使用的参数，其余字段在 {@link #normalize} 中补默认值，保证上传不因参数缺失中断。
 */
@Slf4j
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChunkConfigDTO {

    /**
     * 默认分隔符：中文标点优先，最后用空串兜底按长度硬切
     */
    public static final List<String> DEFAULT_SEPARATORS =
            List.of("\n\n", "\n", "。", "！", "？", "；", "，", " ", "");

    /**
     * 长度类参数上限，防御前端传入异常大值
     */
    private static final int MAX_ALLOWED_LENGTH = 20000;

    /**
     * 定长切片：切片最大长度
     */
    private Integer maxLength;

    /**
     * 结构切片：单块最小长度
     */
    private Integer minLength;

    /**
     * 递归切片 / 语义切片：切片目标长度
     */
    private Integer targetLength;

    /**
     * 重叠字符数
     */
    private Integer overlap;

    /**
     * 递归切片：自定义分隔符，多个用 | 分隔
     */
    private String separators;

    /**
     * 递归切片：是否保留分隔符
     */
    private Boolean keepSeparator;

    /**
     * 语义切片：分句长度
     */
    private Integer sentenceLength;

    /**
     * 语义切片：语义相似度阈值
     */
    private Double similarityThreshold;

    /**
     * 结构切片：自动追加标题到切片
     */
    private Boolean appendTitle;

    /**
     * 结构切片：识别失败自动降级递归切片
     */
    private Boolean fallbackToRecursive;

    /**
     * 解析前端提交的 JSON 参数并补默认值，解析失败时使用全部默认值
     *
     * @param json     前端提交的切片参数 JSON，允许为空
     * @param strategy 当前切片策略
     */
    public static ChunkConfigDTO parse(String json, ChunkStrategyEnum strategy) {
        ChunkConfigDTO config = null;
        if (StringUtils.hasText(json)) {
            try {
                config = JSON.parseObject(json, ChunkConfigDTO.class);
            } catch (Exception e) {
                log.warn("切片参数解析失败，改用默认参数: {}", json, e);
            }
        }
        if (config == null) {
            config = new ChunkConfigDTO();
        }
        config.normalize(strategy);
        return config;
    }

    /**
     * 按策略补齐默认值并收敛非法取值：重叠必须小于切片长度，最小长度不能大于最大长度
     */
    public void normalize(ChunkStrategyEnum strategy) {
        switch (strategy) {
            case FIXED -> {
                maxLength = resolveLength(maxLength, 800, 50, MAX_ALLOWED_LENGTH);
                overlap = resolveOverlap(overlap, 120, maxLength);
            }
            case SEMANTIC -> {
                sentenceLength = resolveLength(sentenceLength, 120, 20, 1000);
                targetLength = resolveLength(targetLength, 800, 50, MAX_ALLOWED_LENGTH);
                overlap = resolveOverlap(overlap, 0, targetLength);
                similarityThreshold = resolveThreshold(similarityThreshold);
            }
            case STRUCTURED -> {
                maxLength = resolveLength(maxLength, 1200, 50, MAX_ALLOWED_LENGTH);
                minLength = resolveLength(minLength, 100, 20, maxLength);
                overlap = resolveOverlap(overlap, 120, maxLength);
                if (appendTitle == null) {
                    appendTitle = Boolean.TRUE;
                }
                if (fallbackToRecursive == null) {
                    fallbackToRecursive = Boolean.TRUE;
                }
            }
            default -> {
                targetLength = resolveLength(targetLength, 800, 50, MAX_ALLOWED_LENGTH);
                overlap = resolveOverlap(overlap, 120, targetLength);
                if (keepSeparator == null) {
                    keepSeparator = Boolean.TRUE;
                }
            }
        }
    }

    /**
     * 定长 / 结构切片的单块最大长度
     */
    public int maxLength() {
        return maxLength == null ? 1200 : maxLength;
    }

    /**
     * 结构切片的单块最小长度
     */
    public int minLength() {
        return minLength == null ? 100 : minLength;
    }

    /**
     * 递归 / 语义切片的切片目标长度，未设置时回退到单块最大长度，保证降级切片可用
     */
    public int targetLength() {
        if (targetLength != null) {
            return targetLength;
        }
        return maxLength == null ? 800 : maxLength;
    }

    /**
     * 重叠字符数
     */
    public int overlap() {
        return overlap == null ? 0 : overlap;
    }

    /**
     * 语义切片的分句长度
     */
    public int sentenceLength() {
        return sentenceLength == null ? 120 : sentenceLength;
    }

    /**
     * 语义切片的相似度阈值
     */
    public double similarityThreshold() {
        return similarityThreshold == null ? 0.65 : similarityThreshold;
    }

    public boolean keepSeparator() {
        return keepSeparator == null || keepSeparator;
    }

    public boolean appendTitle() {
        return appendTitle == null || appendTitle;
    }

    public boolean fallbackToRecursive() {
        return fallbackToRecursive == null || fallbackToRecursive;
    }

    /**
     * 解析实际生效的分隔符列表，自定义分隔符里允许写 \n、\t 等转义
     */
    public List<String> resolvedSeparators() {
        if (!StringUtils.hasText(separators)) {
            return DEFAULT_SEPARATORS;
        }
        List<String> resolved = new ArrayList<>();
        for (String item : separators.split("\\|")) {
            String separator = unescape(item);
            if (!separator.isEmpty()) {
                resolved.add(separator);
            }
        }
        if (resolved.isEmpty()) {
            return DEFAULT_SEPARATORS;
        }
        // 用户自定义分隔符时补一个空串，保证超长片段仍能按长度硬切
        if (!resolved.contains("")) {
            resolved.add("");
        }
        return resolved;
    }

    private String unescape(String text) {
        return text.replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\n");
    }

    private static int resolveLength(Integer value, int defaultValue, int min, int max) {
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return Math.min(Math.max(value, min), Math.max(min, max));
    }

    private static int resolveOverlap(Integer value, int defaultValue, int length) {
        int overlap = value == null ? defaultValue : Math.max(value, 0);
        // 重叠不小于切片长度会导致切分无法推进，直接归零
        return overlap >= length ? 0 : overlap;
    }

    private static double resolveThreshold(Double value) {
        if (value == null) {
            return 0.65;
        }
        return Math.min(Math.max(value, 0D), 1D);
    }
}
