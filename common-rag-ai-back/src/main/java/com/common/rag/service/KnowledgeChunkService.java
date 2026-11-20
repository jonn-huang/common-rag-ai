package com.common.rag.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.common.rag.entity.KnowledgeChunk;
import com.common.rag.pojo.vo.KnowledgeChunkPageVO;

import java.util.List;

/**
 * @description: 知识库分片管理，支持按文件分页查看、修改正文与删除单个分片，并同步向量库
 */
public interface KnowledgeChunkService extends IService<KnowledgeChunk> {

    /**
     * 按文件分页查询分片
     *
     * @param fileId   文件主键
     * @param keyword  分片正文关键词，可为空
     * @param page     页码
     * @param pageSize 每页条数
     */
    KnowledgeChunkPageVO pageByFile(Long fileId, String keyword, Integer page, Integer pageSize);

    /**
     * 修改分片正文，同时更新向量库与全文索引
     *
     * @return 操作提示
     */
    String updateContent(Long id, String content);

    /**
     * 删除分片，同时清理向量库与文件上的向量ID记录
     *
     * @return 操作提示
     */
    String deleteChunks(List<Long> ids);
}
