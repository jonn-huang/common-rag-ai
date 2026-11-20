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
 * @TableName knowledge_chunk
 * @description 关键词检索索引，ETL 时与向量一起写入，供 BM25 全文检索使用
 */
@TableName(value = "knowledge_chunk")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件主键
     */
    private Long fileId;

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
     * 分片原文
     */
    private String content;

    /**
     * 分片对应的向量ID，用于单条分片编辑/删除时同步向量库
     */
    private String vectorId;

    private Date createTime;
}
