package com.common.rag.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.common.rag.core.BaseResponse;
import com.common.rag.core.ErrorCode;
import com.common.rag.core.ResultUtils;
import com.common.rag.entity.AliOssFile;
import com.common.rag.enums.ChunkStrategyEnum;
import com.common.rag.enums.EtlStatusEnum;
import com.common.rag.etl.KnowledgeChunkIndexService;
import com.common.rag.pojo.dto.QueryFileDTO;
import com.common.rag.service.AliOssFileService;
import com.common.rag.mapper.AliOssFileMapper;
import com.common.rag.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
* @description 针对表【ali_oss_file】的数据库操作Service实现
* @createDate 2025-02-08 20:51:33
*/
@Slf4j
@Service
public class AliOssFileServiceImpl extends ServiceImpl<AliOssFileMapper, AliOssFile>
    implements AliOssFileService{

    @Autowired
    private AliOssFileMapper aliOssFileMapper;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private AliOssUtil aliOssUtil;

    @Autowired
    private KnowledgeChunkIndexService knowledgeChunkIndexService;


    /**
     * 查询文件
     * @param request
     * @return
     */
    @Override
    public BaseResponse queryPage(QueryFileDTO request) {
        Page<AliOssFile> page = new Page<>(request.getPage(), request.getPageSize());
        IPage<AliOssFile> fileList = aliOssFileMapper.findByFileNameContaining(page, request.getFileName(), request.getCategory());
        // 补充状态、切片策略、分片数量等展示字段，前端直接展示
        fileList.getRecords().forEach(this::fillDisplayFields);
        return ResultUtils.success(fileList);
    }

    @Override
    public AliOssFile getFileDetail(Long id) {
        if (id == null) {
            return null;
        }
        AliOssFile file = aliOssFileMapper.selectById(id);
        if (file != null) {
            fillDisplayFields(file);
        }
        return file;
    }

    /**
     * 填充不落库的展示字段：状态描述、切片策略描述、分片数量
     */
    private void fillDisplayFields(AliOssFile file) {
        file.setStatusDesc(EtlStatusEnum.descOf(file.getStatus()));
        file.setChunkStrategyDesc(ChunkStrategyEnum.descOf(file.getChunkStrategy()));
        file.setChunkCount(countChunk(file.getVectorId()));
    }

    /**
     * 分片数量由向量ID数量推导，避免额外落库一列
     */
    private int countChunk(String vectorId) {
        if (!StringUtils.hasText(vectorId)) {
            return 0;
        }
        try {
            List<String> vectorIds = JSON.parseArray(vectorId, String.class);
            return vectorIds == null ? 0 : vectorIds.size();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResponse deleteFiles(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请选择文件");
        }
        List<AliOssFile> aliOssFiles = aliOssFileMapper.selectByIds(ids);
        int count = aliOssFileMapper.deleteBatchIds(ids);
        if (count == 0) {
            return ResultUtils.error(ErrorCode.OPERATION_ERROR, "删除失败");
        }
        for (AliOssFile aliOssFile : aliOssFiles) {
            // 未完成切片的文件 vector_id 为空，需跳过，否则 MilvusVectorStore 会因 id 列表为 null 抛断言异常
            deleteVectorsQuietly(parseVectorIds(aliOssFile.getVectorId()), aliOssFile.getId());
            aliOssUtil.deleteOss(aliOssFile.getUrl());
        }
        // 同步清理关键词索引，避免已删除文件仍被全文检索命中
        knowledgeChunkIndexService.deleteByFileIds(ids);

        return ResultUtils.success("成功删除"+ count + "个文件");
    }

    /**
     * 解析已入库的向量ID列表，空值或解析失败返回空集合
     */
    private List<String> parseVectorIds(String vectorId) {
        if (!StringUtils.hasText(vectorId)) {
            return Collections.emptyList();
        }
        try {
            List<String> ids = JSON.parseArray(vectorId, String.class);
            if (ids == null) {
                return Collections.emptyList();
            }
            // 过滤 JSON 数组中的 null / 空串，避免把无效 id 传给向量库
            return ids.stream().filter(StringUtils::hasText).toList();
        } catch (Exception e) {
            log.warn("向量ID解析失败，跳过分片清理: {}", vectorId);
            return Collections.emptyList();
        }
    }

    /**
     * 清理向量分片，失败只记录日志，避免向量库异常导致已删除的文件记录回滚
     */
    private void deleteVectorsQuietly(List<String> vectorIds, Long fileId) {
        if (vectorIds.isEmpty()) {
            return;
        }
        try {
            vectorStore.delete(vectorIds);
        } catch (Exception e) {
            log.warn("文件分片清理失败，需人工确认向量库残留 fileId={} vectorIds={}", fileId, vectorIds, e);
        }
    }

    @Override
    public List<AliOssFile> listForDownload(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return aliOssFileMapper.selectByIds(ids);
    }

}




