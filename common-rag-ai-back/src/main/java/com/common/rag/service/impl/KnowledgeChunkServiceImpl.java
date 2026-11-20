package com.common.rag.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.common.rag.core.ErrorCode;
import com.common.rag.entity.AliOssFile;
import com.common.rag.entity.KnowledgeChunk;
import com.common.rag.enums.EtlStatusEnum;
import com.common.rag.exception.BusinessException;
import com.common.rag.exception.ThrowUtils;
import com.common.rag.mapper.KnowledgeChunkMapper;
import com.common.rag.pojo.vo.KnowledgeChunkPageVO;
import com.common.rag.pojo.vo.KnowledgeChunkVO;
import com.common.rag.retrieval.Bm25KeywordRetriever;
import com.common.rag.service.AliOssFileService;
import com.common.rag.service.KnowledgeChunkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @description 针对表【knowledge_chunk】的分片管理实现
 */
@Slf4j
@Service
public class KnowledgeChunkServiceImpl extends ServiceImpl<KnowledgeChunkMapper, KnowledgeChunk>
        implements KnowledgeChunkService {

    /**
     * 单条分片允许的最大字符数，避免误贴超长文本撑大索引
     */
    private static final int MAX_CONTENT_LENGTH = 20000;

    private static final int DEFAULT_PAGE_SIZE = 10;

    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private AliOssFileService aliOssFileService;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private Bm25KeywordRetriever bm25KeywordRetriever;

    @Override
    public KnowledgeChunkPageVO pageByFile(Long fileId, String keyword, Integer page, Integer pageSize) {
        ThrowUtils.throwIf(fileId == null, ErrorCode.PARAMS_ERROR, "文件 id 不能为空");
        AliOssFile file = aliOssFileService.getById(fileId);
        ThrowUtils.throwIf(file == null, ErrorCode.PARAMS_ERROR, "文件不存在");

        Page<KnowledgeChunk> pageParam = new Page<>(normalizePage(page), normalizePageSize(pageSize));
        QueryWrapper<KnowledgeChunk> wrapper = new QueryWrapper<KnowledgeChunk>()
                .eq("file_id", fileId)
                .like(StringUtils.hasText(keyword), "content", keyword == null ? null : keyword.trim())
                .orderByAsc("chunk_index");
        IPage<KnowledgeChunk> rows = page(pageParam, wrapper);

        // 历史分片没有记录向量ID，文件分片数与向量数一致时按序号自动回填
        long fileChunkCount = count(new QueryWrapper<KnowledgeChunk>().eq("file_id", fileId));
        boolean vectorSynced = syncVectorIds(file, rows.getRecords(), fileChunkCount);

        List<KnowledgeChunkVO> records = new ArrayList<>(rows.getRecords().size());
        for (KnowledgeChunk row : rows.getRecords()) {
            records.add(toVO(row));
        }
        return KnowledgeChunkPageVO.builder()
                .total(rows.getTotal())
                .records(records)
                .vectorSynced(vectorSynced)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String updateContent(Long id, String content) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "分片 id 不能为空");
        String newContent = content == null ? "" : content.trim();
        ThrowUtils.throwIf(!StringUtils.hasText(newContent), ErrorCode.PARAMS_ERROR, "分片内容不能为空");
        ThrowUtils.throwIf(newContent.length() > MAX_CONTENT_LENGTH, ErrorCode.PARAMS_ERROR,
                "分片内容不能超过 " + MAX_CONTENT_LENGTH + " 字符");

        KnowledgeChunk chunk = getById(id);
        ThrowUtils.throwIf(chunk == null, ErrorCode.PARAMS_ERROR, "分片不存在");
        AliOssFile file = aliOssFileService.getById(chunk.getFileId());
        ThrowUtils.throwIf(file == null, ErrorCode.PARAMS_ERROR, "分片所属文件不存在");
        ThrowUtils.throwIf(!EtlStatusEnum.SUCCESS.getCode().equals(file.getStatus()),
                ErrorCode.OPERATION_ERROR, "文件尚未完成 ETL，暂不能修改分片");

        List<String> vectorIds = resolveFileVectorIds(file);
        long fileChunkCount = count(new QueryWrapper<KnowledgeChunk>().eq("file_id", file.getId()));
        ThrowUtils.throwIf(!isVectorAligned(fileChunkCount, vectorIds), ErrorCode.OPERATION_ERROR,
                "分片与向量索引不一致，请先重新切片后再修改");
        String vectorId = resolveVectorId(chunk, vectorIds);
        ThrowUtils.throwIf(vectorId == null, ErrorCode.OPERATION_ERROR,
                "分片与向量索引不一致，请先重新切片后再修改");

        String oldContent = chunk.getContent();
        int chunkTotal = vectorIds.size();
        // 先删旧向量再写新向量，避免主键重复导致召回出两份内容
        deleteVector(vectorId);
        try {
            vectorStore.add(List.of(buildVectorDocument(chunk, file, vectorId, newContent, chunkTotal)));
        } catch (Exception e) {
            log.error("分片向量更新失败，尝试回写原内容: id={}", id, e);
            restoreVector(chunk, file, vectorId, oldContent, chunkTotal);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "向量更新失败，分片内容未修改，请稍后重试");
        }

        chunk.setContent(newContent);
        chunk.setVectorId(vectorId);
        updateById(chunk);
        bm25KeywordRetriever.evict();
        log.info("分片内容更新完成：id={}, file={}, index={}", id, file.getFileName(), chunk.getChunkIndex());
        return "分片内容已更新";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deleteChunks(List<Long> ids) {
        ThrowUtils.throwIf(ids == null || ids.isEmpty(), ErrorCode.PARAMS_ERROR, "请选择要删除的分片");
        List<KnowledgeChunk> chunks = listByIds(ids);
        ThrowUtils.throwIf(chunks.isEmpty(), ErrorCode.PARAMS_ERROR, "分片不存在或已删除");

        // 先清理向量，失败则整体回滚，避免出现只删索引不删向量的脏数据
        List<String> vectorIdsToDelete = new ArrayList<>();
        Map<Long, String> resolvedVectorIds = new HashMap<>();
        Map<Long, List<String>> fileVectorIds = new HashMap<>();
        Map<Long, Long> fileChunkCounts = new HashMap<>();
        Map<Long, AliOssFile> files = new LinkedHashMap<>();
        for (KnowledgeChunk chunk : chunks) {
            AliOssFile file = files.computeIfAbsent(chunk.getFileId(), aliOssFileService::getById);
            if (file == null) {
                continue;
            }
            // 删除前先把历史分片的向量ID补齐，避免序号错位后删错向量
            List<String> vectorIds = fileVectorIds.computeIfAbsent(file.getId(), key -> resolveFileVectorIds(file));
            long fileChunkCount = fileChunkCounts.computeIfAbsent(file.getId(),
                    key -> count(new QueryWrapper<KnowledgeChunk>().eq("file_id", key)));
            ThrowUtils.throwIf(!isVectorAligned(fileChunkCount, vectorIds), ErrorCode.OPERATION_ERROR,
                    "文件「" + file.getFileName() + "」的分片与向量索引不一致，请先重新切片后再删除");
            String vectorId = resolveVectorId(chunk, vectorIds);
            if (StringUtils.hasText(vectorId)) {
                resolvedVectorIds.put(chunk.getId(), vectorId);
                if (!vectorIdsToDelete.contains(vectorId)) {
                    vectorIdsToDelete.add(vectorId);
                }
            }
        }
        if (!vectorIdsToDelete.isEmpty()) {
            try {
                vectorStore.delete(vectorIdsToDelete);
            } catch (Exception e) {
                log.error("删除分片时清理向量失败: {}", vectorIdsToDelete, e);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "向量删除失败，分片未删除，请稍后重试");
            }
        }

        removeByIds(ids);
        // 同步从文件上移除向量ID，保证分片数量与向量数量一致
        for (AliOssFile file : files.values()) {
            if (file == null) {
                continue;
            }
            List<String> removed = new ArrayList<>();
            for (KnowledgeChunk chunk : chunks) {
                if (chunk.getFileId().equals(file.getId())) {
                    String vectorId = resolvedVectorIds.get(chunk.getId());
                    if (StringUtils.hasText(vectorId)) {
                        removed.add(vectorId);
                    }
                }
            }
            if (removed.isEmpty()) {
                continue;
            }
            List<String> remaining = parseVectorIds(file.getVectorId());
            if (!remaining.removeAll(removed)) {
                continue;
            }
            file.setVectorId(JSON.toJSONString(remaining));
            file.setUpdateTime(new Date());
            aliOssFileService.updateById(file);
        }
        bm25KeywordRetriever.evict();
        log.info("分片删除完成：count={}, vectorIds={}", chunks.size(), vectorIdsToDelete.size());
        return "成功删除 " + chunks.size() + " 个分片";
    }

    /**
     * 分页行的向量ID懒补齐，只有文件分片总数与向量数一致时才认为是同一批数据
     */
    private boolean syncVectorIds(AliOssFile file, List<KnowledgeChunk> records, long fileChunkCount) {
        List<String> vectorIds = parseVectorIds(file.getVectorId());
        if (vectorIds.isEmpty() || fileChunkCount != vectorIds.size()) {
            return false;
        }
        List<KnowledgeChunk> needUpdate = new ArrayList<>();
        for (KnowledgeChunk row : records) {
            if (StringUtils.hasText(row.getVectorId())) {
                continue;
            }
            Integer index = row.getChunkIndex();
            if (index == null || index < 1 || index > vectorIds.size()) {
                return false;
            }
            row.setVectorId(vectorIds.get(index - 1));
            needUpdate.add(row);
        }
        if (!needUpdate.isEmpty()) {
            updateBatchById(needUpdate);
        }
        return true;
    }

    /**
     * 优先使用分片自身记录的向量ID，缺失时按分片序号从文件向量列表中取值
     */
    private String resolveVectorId(KnowledgeChunk chunk, List<String> fileVectorIds) {
        if (StringUtils.hasText(chunk.getVectorId())) {
            return chunk.getVectorId();
        }
        Integer index = chunk.getChunkIndex();
        if (index == null || index < 1 || index > fileVectorIds.size()) {
            return null;
        }
        return fileVectorIds.get(index - 1);
    }

    /**
     * 文件分片数与其向量数一致时，才允许按序号回退定位向量
     */
    private boolean isVectorAligned(long fileChunkCount, List<String> vectorIds) {
        return !vectorIds.isEmpty() && fileChunkCount == vectorIds.size();
    }

    /**
     * 编辑或删除前读取文件的全部向量ID，并把历史分片缺失的向量ID回填到库中
     */
    private List<String> resolveFileVectorIds(AliOssFile file) {
        List<String> vectorIds = parseVectorIds(file.getVectorId());
        if (vectorIds.isEmpty()) {
            return vectorIds;
        }
        List<KnowledgeChunk> rows = list(new QueryWrapper<KnowledgeChunk>()
                .eq("file_id", file.getId())
                .orderByAsc("chunk_index"));
        if (rows.size() != vectorIds.size()) {
            return vectorIds;
        }
        List<KnowledgeChunk> needUpdate = new ArrayList<>();
        for (KnowledgeChunk row : rows) {
            if (StringUtils.hasText(row.getVectorId())) {
                continue;
            }
            Integer index = row.getChunkIndex();
            if (index == null || index < 1 || index > vectorIds.size()) {
                continue;
            }
            row.setVectorId(vectorIds.get(index - 1));
            needUpdate.add(row);
        }
        if (!needUpdate.isEmpty()) {
            updateBatchById(needUpdate);
        }
        return vectorIds;
    }

    /**
     * 重建分片对应的向量文档，元数据与 ETL 写入时保持一致
     */
    private Document buildVectorDocument(KnowledgeChunk chunk, AliOssFile file, String vectorId,
                                         String content, int chunkTotal) {
        Map<String, Object> metadata = new HashMap<>();
        if (StringUtils.hasText(file.getChunkStrategy())) {
            metadata.put("chunkStrategy", file.getChunkStrategy());
        }
        int chunkIndex = chunk.getChunkIndex() == null ? 0 : chunk.getChunkIndex() - 1;
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("chunkTotal", chunkTotal);
        if (file.getId() != null) {
            metadata.put("fileId", file.getId());
        }
        if (StringUtils.hasText(file.getFileName())) {
            metadata.put("fileName", file.getFileName());
        }
        if (StringUtils.hasText(file.getCategory())) {
            metadata.put("category", file.getCategory());
        }
        return Document.builder().id(vectorId).text(content).metadata(metadata).build();
    }

    private void deleteVector(String vectorId) {
        try {
            vectorStore.delete(List.of(vectorId));
        } catch (Exception e) {
            log.error("分片旧向量删除失败: {}", vectorId, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "向量删除失败，请稍后重试");
        }
    }

    /**
     * 向量更新失败时把原内容写回，尽量保证向量库与索引保持一致
     */
    private void restoreVector(KnowledgeChunk chunk, AliOssFile file, String vectorId,
                               String oldContent, int chunkTotal) {
        if (!StringUtils.hasText(oldContent)) {
            return;
        }
        try {
            vectorStore.add(List.of(buildVectorDocument(chunk, file, vectorId, oldContent, chunkTotal)));
        } catch (Exception e) {
            log.error("分片原向量回写失败，需重新切片修复: id={}, vectorId={}", chunk.getId(), vectorId, e);
        }
    }

    private KnowledgeChunkVO toVO(KnowledgeChunk chunk) {
        String content = chunk.getContent();
        return KnowledgeChunkVO.builder()
                .id(chunk.getId())
                .fileId(chunk.getFileId())
                .fileName(chunk.getFileName())
                .category(chunk.getCategory())
                .chunkIndex(chunk.getChunkIndex())
                .length(content == null ? 0 : content.length())
                .content(content)
                .build();
    }

    private List<String> parseVectorIds(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new ArrayList<>();
        }
        try {
            List<String> ids = JSON.parseArray(raw, String.class);
            return ids == null ? new ArrayList<>() : ids;
        } catch (Exception e) {
            log.warn("向量ID解析失败，按无向量处理: {}", raw);
            return new ArrayList<>();
        }
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
