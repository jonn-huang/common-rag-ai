package com.common.rag.pojo.dto;

import lombok.Data;

/**
 * @Title: QueryFileDTO
 * @Package com.common.rag.pojo.dto
 * @Date 2025/2/8 21:31
 * @description: 查找文件dto
 */
@Data
public class QueryFileDTO {
    private Integer page;
    private Integer pageSize;
    private String fileName;
    /**
     * 知识库分类，空表示不限制
     */
    private String category;
}
