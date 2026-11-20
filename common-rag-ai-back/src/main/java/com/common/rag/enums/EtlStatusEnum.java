package com.common.rag.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @description: 知识库文件 ETL（向量化）状态
 */
@Getter
@AllArgsConstructor
public enum EtlStatusEnum {

    /**
     * 已上传成功，等待向量化
     */
    PENDING("PENDING", "待 ETL"),

    /**
     * 正在向量化
     */
    PROCESSING("PROCESSING", "ETL 中"),

    /**
     * 向量化成功
     */
    SUCCESS("SUCCESS", "成功"),

    /**
     * 向量化失败
     */
    FAILED("FAILED", "失败");

    private final String code;

    private final String desc;

    /**
     * 根据状态码获取描述，未知状态原样返回
     */
    public static String descOf(String code) {
        for (EtlStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status.desc;
            }
        }
        return code;
    }
}
