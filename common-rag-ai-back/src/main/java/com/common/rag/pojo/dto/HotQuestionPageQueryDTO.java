package com.common.rag.pojo.dto;

import lombok.Data;

/**
 * 热门问题分页查询入参
 */
@Data
public class HotQuestionPageQueryDTO {

    /**
     * 页码，从 1 开始
     */
    private int page;

    private int pageSize;

    /**
     * 问题文案模糊匹配
     */
    private String keyword;

    /**
     * 状态：1 启用，0 屏蔽，为空表示全部
     */
    private Integer status;

    /**
     * 来源：AUTO/MANUAL，为空表示全部
     */
    private String source;
}