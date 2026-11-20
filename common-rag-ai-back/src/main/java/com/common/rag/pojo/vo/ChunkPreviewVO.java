package com.common.rag.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * @description: 切片预览中的单条切片
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkPreviewVO {

    /**
     * 切片序号，从 1 开始
     */
    private Integer index;

    /**
     * 切片字符数
     */
    private Integer length;

    /**
     * 标题路径，仅结构切片有值
     */
    private String heading;

    /**
     * 切片正文
     */
    private String text;

    /**
     * 切片元数据，包含文件名、分类、切片序号与总数等入库时写入的字段
     */
    private Map<String, Object> metadata;
}
