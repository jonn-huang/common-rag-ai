package com.common.rag.pojo.vo;

import lombok.Builder;
import lombok.Data;

/**
 * @description: 检索调试结果，用于确认各检索方式的召回效果
 */
@Data
@Builder
public class RetrievalHitVO {

    private String fileName;

    private String category;

    private Integer chunkIndex;

    /**
     * 当前检索方式下的得分：语义为相似度、全文为 BM25、混合为融合分
     */
    private Double score;

    private String text;
}
