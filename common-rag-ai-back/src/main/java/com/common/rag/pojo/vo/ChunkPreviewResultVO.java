package com.common.rag.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @description: 切片预览结果，包含总数与本次返回的前若干条切片
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkPreviewResultVO {

    /**
     * 预览的文件名
     */
    private String fileName;

    /**
     * 使用的切片策略
     */
    private String chunkStrategy;

    /**
     * 切片策略中文描述
     */
    private String chunkStrategyDesc;

    /**
     * 切片总数
     */
    private Integer total;

    /**
     * 是否因为条数限制而截断
     */
    private Boolean truncated;

    /**
     * 本次返回的切片
     */
    private List<ChunkPreviewVO> chunks;
}
