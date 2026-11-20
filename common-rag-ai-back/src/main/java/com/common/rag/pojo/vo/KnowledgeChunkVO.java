package com.common.rag.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description: 分片管理中的单条分片
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunkVO {

    /**
     * 分片主键
     */
    private Long id;

    /**
     * 所属文件主键
     */
    private Long fileId;

    /**
     * 所属文件名
     */
    private String fileName;

    /**
     * 知识库分类
     */
    private String category;

    /**
     * 分片序号，从 1 开始
     */
    private Integer chunkIndex;

    /**
     * 分片字符数
     */
    private Integer length;

    /**
     * 分片正文
     */
    private String content;
}
