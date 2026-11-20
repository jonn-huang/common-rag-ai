package com.common.rag.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @description: 知识库文件切片策略
 */
@Getter
@AllArgsConstructor
public enum ChunkStrategyEnum {

    /**
     * 定长切片：按最大长度等步长切分
     */
    FIXED("FIXED", "定长切片"),

    /**
     * 递归切片：按分隔符优先级递归切分后合并
     */
    RECURSIVE("RECURSIVE", "递归切片"),

    /**
     * 语义切片：按相邻句子语义相似度断句
     */
    SEMANTIC("SEMANTIC", "语义切片"),

    /**
     * 结构切片：按标题/目录层级切分
     */
    STRUCTURED("STRUCTURED", "结构切片");

    private final String code;

    private final String desc;

    /**
     * 根据策略编码获取描述，空值或未知编码按递归切片返回
     */
    public static String descOf(String code) {
        return fromCode(code).desc;
    }

    /**
     * 解析策略编码，空值或未知编码统一回退递归切片，保证上传不中断
     */
    public static ChunkStrategyEnum fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return RECURSIVE;
        }
        String target = code.trim();
        for (ChunkStrategyEnum strategy : values()) {
            if (strategy.code.equalsIgnoreCase(target)) {
                return strategy;
            }
        }
        return RECURSIVE;
    }
}
