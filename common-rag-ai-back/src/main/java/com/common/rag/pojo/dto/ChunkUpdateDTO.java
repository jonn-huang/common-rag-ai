package com.common.rag.pojo.dto;

import lombok.Data;

/**
 * @description: 分片内容修改请求，保存后同步更新向量库与全文索引
 */
@Data
public class ChunkUpdateDTO {

    /**
     * 分片主键
     */
    private Long id;

    /**
     * 修改后的分片正文
     */
    private String content;
}
