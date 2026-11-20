package com.common.rag.pojo.vo;

import lombok.Builder;
import lombok.Data;

/**
 * @description: RAG 问答对客展示的参考文件，一份文件对应一条（同一文件的多个分片已合并），
 * 只下发文件名、业务分类与片段摘要，文件标识、路径与检索得分一律不出后端
 */
@Data
@Builder
public class RagSourceVO {

    /**
     * 参考文件名，用于对客展示与核对
     */
    private String fileName;

    /**
     * 业务分类，如「差旅报销」，为空时前端只展示文件名
     */
    private String category;

    /**
     * 该文件命中分片合并后的摘要，供用户点击核对
     */
    private String snippet;
}