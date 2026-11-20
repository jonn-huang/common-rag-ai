package com.common.rag.service;

import com.common.rag.core.BaseResponse;
import com.common.rag.entity.AliOssFile;
import com.baomidou.mybatisplus.extension.service.IService;
import com.common.rag.pojo.dto.QueryFileDTO;

import java.util.List;

/**
* @description 针对表【ali_oss_file】的数据库操作Service
* @createDate 2025-02-08 20:51:33
*/
public interface AliOssFileService extends IService<AliOssFile> {

    BaseResponse queryPage(QueryFileDTO request);

    /**
     * 查询文件详情，补齐状态、切片策略、分片数量等展示字段
     *
     * @param id 文件主键
     * @return 文件记录，不存在时返回 null
     */
    AliOssFile getFileDetail(Long id);

    BaseResponse deleteFiles(List<Long> ids);

    /**
     * 查询待下载的文件（只取元数据，文件内容由控制层直接从 OSS 读取）
     */
    List<AliOssFile> listForDownload(List<Long> ids);
}
