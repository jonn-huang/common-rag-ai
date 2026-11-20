package com.common.rag.pojo.vo;

import com.common.rag.enums.FusionStrategyEnum;
import com.common.rag.enums.RetrievalModeEnum;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @description: 知识库检索配置返回值，默认值与前端弹窗保持一致
 */
@Data
@Builder
public class RetrievalConfigVO {

    /**
     * 生效的分类范围，空表示全局知识库
     */
    @Builder.Default
    private List<String> categories = new ArrayList<>();

    @Builder.Default
    private String retrievalMode = RetrievalModeEnum.HYBRID.getCode();

    @Builder.Default
    private Integer vectorTopK = 10;

    @Builder.Default
    private Double similarityThreshold = 0.4D;

    @Builder.Default
    private Integer keywordTopK = 10;

    /**
     * 为空表示不限制最低匹配得分
     */
    private Double keywordMinScore;

    @Builder.Default
    private String fusionStrategy = FusionStrategyEnum.RRF.getCode();

    @Builder.Default
    private Double vectorWeight = 0.6D;

    @Builder.Default
    private Double keywordWeight = 0.4D;

    @Builder.Default
    private Boolean rerankEnabled = Boolean.FALSE;

    @Builder.Default
    private Integer rerankTopN = 8;
}
