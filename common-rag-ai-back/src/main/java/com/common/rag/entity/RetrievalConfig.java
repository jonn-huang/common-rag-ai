package com.common.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @TableName retrieval_config
 * @description 知识库检索配置，只保留一条全局记录（id = 1）
 */
@TableName(value = "retrieval_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalConfig {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 绑定的文件分类，JSON 数组，空表示全局知识库
     */
    private String categories;

    /**
     * 检索方式：SEMANTIC / FULL_TEXT / HYBRID
     */
    private String retrievalMode;

    /**
     * 语义检索召回数量
     */
    private Integer vectorTopK;

    /**
     * 语义检索最低相似度阈值
     */
    private Double similarityThreshold;

    /**
     * 全文检索召回数量
     */
    private Integer keywordTopK;

    /**
     * 全文检索最低 BM25 得分，空表示不限制
     */
    private Double keywordMinScore;

    /**
     * 混合检索融合方式：WEIGHTED / RRF，未配置时默认 RRF
     */
    private String fusionStrategy;

    /**
     * 混合检索向量权重
     */
    private Double vectorWeight;

    /**
     * 混合检索关键词权重
     */
    private Double keywordWeight;

    /**
     * 是否开启 Rerank 重排
     */
    private Boolean rerankEnabled;

    /**
     * 重排后保留条数
     */
    private Integer rerankTopN;

    private Date createTime;

    private Date updateTime;
}
