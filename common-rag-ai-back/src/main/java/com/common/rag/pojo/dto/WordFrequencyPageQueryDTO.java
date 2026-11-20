package com.common.rag.pojo.dto;

import lombok.Data;

@Data
public class WordFrequencyPageQueryDTO {

    private int page;

    private int pageSize;

    private String word;

    private String businessType;

    /**
     * 次数下限，实际按大于该值过滤
     */
    private Integer countNumMin;

    /**
     * 排序字段：count 按次数降序（默认），updateTime 按最近出现时间降序
     */
    private String orderBy;
}