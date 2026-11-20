package com.common.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @TableName ali_oss_file
 */
@TableName(value ="ali_oss_file")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AliOssFile {
    /**
     * 主键id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 链接地址（OSS 路径）
     */
    private String url;

    /**
     * 文件大小，单位：字节
     */
    private Long fileSize;

    /**
     * 知识库分类
     */
    private String category;

    /**
     * 切片策略：FIXED / RECURSIVE / SEMANTIC / STRUCTURED
     */
    private String chunkStrategy;

    /**
     * 切片参数 JSON（只记录当前策略使用的参数）
     */
    private String chunkConfig;

    /**
     * ETL 状态：PENDING / PROCESSING / SUCCESS / FAILED
     */
    private String status;

    /**
     * 该文件分割出的多段向量文本ID
     */
    private String vectorId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * ETL 状态中文描述，仅用于接口返回，不落库
     */
    @TableField(exist = false)
    private String statusDesc;

    /**
     * 切片策略中文描述，仅用于接口返回，不落库
     */
    @TableField(exist = false)
    private String chunkStrategyDesc;

    /**
     * 分片数量，由 vectorId 的数量推导，仅用于接口返回，不落库
     */
    @TableField(exist = false)
    private Integer chunkCount;
}
