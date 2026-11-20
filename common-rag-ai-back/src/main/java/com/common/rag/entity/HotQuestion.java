package com.common.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 问答页热门问题推荐
 *
 * @TableName hot_question
 */
@TableName(value = "hot_question")
@Data
public class HotQuestion {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 展示给用户的问题文案
     */
    private String question;

    /**
     * 归一化问题，去重键
     */
    private String normalized;

    /**
     * 关联热词，逗号分隔
     */
    private String keywords;

    /**
     * 来源：AUTO 自动挖掘，MANUAL 人工新增或人工编辑
     */
    private String source;

    /**
     * 累计提问次数
     */
    private Integer hitCount;

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

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}