package com.common.rag.pojo.dto;

import lombok.Data;

import java.util.List;

/**
 * 热门问题新增/编辑入参
 */
@Data
public class HotQuestionDTO {

    /**
     * 为空表示新增，非空表示编辑
     */
    private Long id;

    /**
     * 问题文案，必填
     */
    private String question;

    /**
     * 关联热词，可为空
     */
    private List<String> keywords;

    /**
     * 是否置顶：1 置顶，0 普通
     */
    private Integer pinned;

    /**
     * 排序值，越小越靠前
     */
    private Integer sortNum;

    /**
     * 状态：1 启用，0 屏蔽
     */
    private Integer status;
}