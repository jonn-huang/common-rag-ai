package com.common.rag.pojo.dto;

import lombok.Data;

/**
 * @description: 知识库文件编辑请求，可修改知识库分类与切片策略；
 * 切片策略或参数发生变化时，后端会按 OSS 上的原始文件重新切片并向量化
 */
@Data
public class FileUpdateDTO {

    /**
     * 文件主键
     */
    private Long id;

    /**
     * 知识库分类
     */
    private String category;

    /**
     * 切片策略：FIXED / RECURSIVE / SEMANTIC / STRUCTURED
     */
    private String chunkStrategy;

    /**
     * 当前策略对应的切片参数 JSON
     */
    private String chunkConfig;
}
