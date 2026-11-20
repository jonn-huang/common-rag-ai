package com.common.rag.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 热门问题出参，问答页与管理页共用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotQuestionVO {

    private Long id;

    /**
     * 推荐问题文案
     */
    private String question;

    /**
     * 关联热词，已拆分为数组
     */
    private List<String> keywords;

    /**
     * 累计提问次数，人工新增的为 0
     */
    private Integer hitCount;

    private Integer pinned;

    private Integer sortNum;

    /**
     * 1 启用，0 屏蔽
     */
    private Integer status;

    /**
     * AUTO 自动挖掘，MANUAL 人工维护
     */
    private String source;

    private Date updateTime;
}