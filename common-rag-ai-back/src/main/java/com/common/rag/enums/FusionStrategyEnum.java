package com.common.rag.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @description: 混合检索的融合方式
 */
@Getter
@AllArgsConstructor
public enum FusionStrategyEnum {

    /**
     * 权重加权：各通道归一化后按权重相加
     */
    WEIGHTED("WEIGHTED", "加权融合"),

    /**
     * RRF 排名融合：按通道排名倒数累加，无需归一化
     */
    RRF("RRF", "RRF 排名融合");

    private final String code;

    private final String desc;

    /**
     * 解析融合方式，空值或未知编码回退 RRF 排名融合
     */
    public static FusionStrategyEnum fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return RRF;
        }
        String target = code.trim();
        for (FusionStrategyEnum strategy : values()) {
            if (strategy.code.equalsIgnoreCase(target)) {
                return strategy;
            }
        }
        return RRF;
    }
}
