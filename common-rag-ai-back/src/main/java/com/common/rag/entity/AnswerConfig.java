package com.common.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @TableName answer_config
 * @description 知识库问答的提示词配置，只保留一条全局记录（id = 1）
 */
@TableName(value = "answer_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerConfig {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 系统提示词模板，支持 {refusal} 与 {current_data} 占位符
     */
    private String systemPrompt;

    /**
     * 超出范围或资料不足时的固定回复
     */
    private String refusalMessage;

    /**
     * 检索未命中时是否直接拒答，不调用大模型
     */
    private Boolean refuseWhenNoContext;

    /**
     * 语义检索最高相似度低于该值即拒答，空表示不参与判断
     */
    private Double minAnswerScore;

    private Date createTime;

    private Date updateTime;
}
