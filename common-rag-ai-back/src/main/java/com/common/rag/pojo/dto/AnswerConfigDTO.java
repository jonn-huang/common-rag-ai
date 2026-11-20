package com.common.rag.pojo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * @description: 提示词配置提交参数，统一收敛长度与取值范围，避免异常文本影响每一轮问答
 */
@Slf4j
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnswerConfigDTO {

    /**
     * 提示词长度上限，提示词每轮都会拼进请求，过长会拖慢响应
     */
    private static final int MAX_PROMPT_LENGTH = 8000;

    /**
     * 拒答话术长度上限，同时会被注入提示词
     */
    private static final int MAX_REFUSAL_LENGTH = 512;

    /**
     * 系统提示词模板，空白时由 Service 回退为默认模板
     */
    private String systemPrompt;

    /**
     * 拒答话术，空白时由 Service 回退为默认话术
     */
    private String refusalMessage;

    /**
     * 检索未命中时是否直接拒答，为空按默认开启处理
     */
    private Boolean refuseWhenNoContext;

    /**
     * 语义检索最低应答相似度，为空表示不判断
     */
    private Double minAnswerScore;

    /**
     * 去除首尾空白并收敛长度与取值范围
     */
    public void normalize() {
        systemPrompt = trim(systemPrompt);
        if (systemPrompt != null && systemPrompt.length() > MAX_PROMPT_LENGTH) {
            log.warn("提示词超过 {} 字符，已截断保存", MAX_PROMPT_LENGTH);
            systemPrompt = systemPrompt.substring(0, MAX_PROMPT_LENGTH);
        }
        refusalMessage = trim(refusalMessage);
        if (refusalMessage != null && refusalMessage.length() > MAX_REFUSAL_LENGTH) {
            log.warn("拒答话术超过 {} 字符，已截断保存", MAX_REFUSAL_LENGTH);
            refusalMessage = refusalMessage.substring(0, MAX_REFUSAL_LENGTH);
        }
        if (minAnswerScore != null) {
            minAnswerScore = Math.min(Math.max(minAnswerScore, 0D), 1D);
        }
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
