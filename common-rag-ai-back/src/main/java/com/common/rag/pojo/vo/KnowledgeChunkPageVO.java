package com.common.rag.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @description: 分片管理分页结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunkPageVO {

    /**
     * 分片总数
     */
    private Long total;

    /**
     * 当前页分片
     */
    private List<KnowledgeChunkVO> records;

    /**
     * 分片与向量索引是否一致，false 时只允许查看，需重新切片后才能编辑或删除
     */
    private Boolean vectorSynced;
}
