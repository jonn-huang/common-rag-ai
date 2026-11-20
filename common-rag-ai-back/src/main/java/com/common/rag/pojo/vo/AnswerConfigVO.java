package com.common.rag.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * @description: 知识库问答提示词配置返回值，数据库无记录时由 RagAnswerProperties 提供默认值
 */
@Data
@Builder
public class AnswerConfigVO {

    /**
     * 系统提示词模板，{refusal} 为拒答话术占位符，{current_data} 为当天日期占位符
     */
    private String systemPrompt;

    /**
     * 超出业务范围或资料不足时的固定回复
     */
    private String refusalMessage;

    /**
     * 检索未命中时是否直接拒答
     */
    private Boolean refuseWhenNoContext;

    /**
     * 语义检索最低应答相似度，为空表示不判断
     */
    private Double minAnswerScore;

    /**
     * 最近一次保存时间，未保存过为空
     */
    private Date updateTime;
}
