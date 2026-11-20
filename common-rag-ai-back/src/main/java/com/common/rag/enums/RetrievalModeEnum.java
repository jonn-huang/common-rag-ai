package com.common.rag.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @description: 知识库检索方式
 */
@Getter
@AllArgsConstructor
public enum RetrievalModeEnum {

    /**
     * 语义检索：向量相关性查询
     */
    SEMANTIC("SEMANTIC", "语义检索"),

    /**
     * 全文检索：BM25 关键词检索
     */
    FULL_TEXT("FULL_TEXT", "全文检索"),

    /**
     * 混合检索：向量与关键词结果融合
     */
    HYBRID("HYBRID", "混合检索");

    private final String code;

    private final String desc;

    /**
     * 根据检索方式获取描述，未知编码按混合检索返回
     */
    public static String descOf(String code) {
        return fromCode(code).desc;
    }

    /**
     * 解析检索方式，空值或未知编码统一回退混合检索，保证配置异常时仍可问答
     */
    public static RetrievalModeEnum fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return HYBRID;
        }
        String target = code.trim();
        for (RetrievalModeEnum mode : values()) {
            if (mode.code.equalsIgnoreCase(target)) {
                return mode;
            }
        }
        return HYBRID;
    }
}
