package com.common.rag.pojo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.common.rag.enums.FusionStrategyEnum;
import com.common.rag.enums.RetrievalModeEnum;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @description: 检索配置提交参数，所有字段统一收敛，避免前端传入异常值影响检索
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RetrievalConfigDTO {

    private static final int MAX_CATEGORIES = 20;

    private static final int MAX_CATEGORY_LENGTH = 64;

    private static final int MAX_TOP_K = 50;

    /**
     * 生效的分类范围，空表示全局知识库
     */
    private List<String> categories;

    private String retrievalMode;

    private Integer vectorTopK;

    private Double similarityThreshold;

    private Integer keywordTopK;

    private Double keywordMinScore;

    private String fusionStrategy;

    private Double vectorWeight;

    private Double keywordWeight;

    private Boolean rerankEnabled;

    private Integer rerankTopN;

    /**
     * 补齐默认值并收敛非法取值，同时去重与裁剪分类
     */
    public void normalize() {
        categories = normalizeCategories(categories);
        retrievalMode = RetrievalModeEnum.fromCode(retrievalMode).getCode();
        vectorTopK = resolveTopK(vectorTopK);
        keywordTopK = resolveTopK(keywordTopK);
        similarityThreshold = resolveThreshold(similarityThreshold);
        keywordMinScore = resolveMinScore(keywordMinScore);
        fusionStrategy = FusionStrategyEnum.fromCode(fusionStrategy).getCode();
        resolveWeights();
        if (rerankEnabled == null) {
            rerankEnabled = Boolean.FALSE;
        }
        rerankTopN = resolveTopK(rerankTopN);
    }

    private List<String> normalizeCategories(List<String> source) {
        List<String> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String item : source) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            String value = item.trim();
            if (value.length() > MAX_CATEGORY_LENGTH) {
                value = value.substring(0, MAX_CATEGORY_LENGTH);
            }
            unique.add(value);
            if (unique.size() >= MAX_CATEGORIES) {
                break;
            }
        }
        result.addAll(unique);
        return result;
    }

    private Integer resolveTopK(Integer value) {
        int topK = value == null || value <= 0 ? 10 : value;
        return Math.min(topK, MAX_TOP_K);
    }

    private Double resolveThreshold(Double value) {
        if (value == null) {
            return 0.4D;
        }
        return Math.min(Math.max(value, 0D), 1D);
    }

    private Double resolveMinScore(Double value) {
        if (value == null) {
            return null;
        }
        return Math.max(value, 0D);
    }

    /**
     * 权重为空或都为 0 时回退默认 0.6/0.4，避免混合检索结果得分恒为 0
     */
    private void resolveWeights() {
        double vector = vectorWeight == null ? 0D : Math.min(Math.max(vectorWeight, 0D), 1D);
        double keyword = keywordWeight == null ? 0D : Math.min(Math.max(keywordWeight, 0D), 1D);
        if (vector + keyword <= 0) {
            vector = 0.6D;
            keyword = 0.4D;
        }
        vectorWeight = vector;
        keywordWeight = keyword;
    }
}
