package com.common.rag.etl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.common.rag.entity.AliOssFile;
import com.common.rag.entity.KnowledgeChunk;
import com.common.rag.enums.ChunkStrategyEnum;
import com.common.rag.mapper.KnowledgeChunkMapper;
import com.common.rag.pojo.dto.ChunkConfigDTO;
import com.common.rag.retrieval.Bm25KeywordRetriever;
import com.common.rag.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @description: 关键词索引维护，把切片原文同步到 knowledge_chunk，供 BM25 全文检索使用
 */
@Slf4j
@Service
public class KnowledgeChunkIndexService extends ServiceImpl<KnowledgeChunkMapper, KnowledgeChunk> {

    /**
     * 批量写入条数，避免单批 SQL 过长
     */
    private static final int BATCH_SIZE = 200;

    @Autowired
    private AliOssUtil aliOssUtil;

    @Autowired
    private DocumentChunkService documentChunkService;

    @Autowired
    private Bm25KeywordRetriever bm25KeywordRetriever;

    /**
     * 覆盖写入某个文件的分片原文，ETL 成功后调用
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceChunks(AliOssFile file, List<Document> chunks) {
        if (file == null || file.getId() == null || chunks == null) {
            return;
        }
        removeByFileId(file.getId());
        List<KnowledgeChunk> rows = new ArrayList<>(chunks.size());
        Date now = new Date();
        for (int index = 0; index < chunks.size(); index++) {
            Document chunk = chunks.get(index);
            String content = chunk.getText();
            if (!StringUtils.hasText(content)) {
                continue;
            }
            rows.add(KnowledgeChunk.builder()
                    .fileId(file.getId())
                    .fileName(file.getFileName())
                    .category(file.getCategory())
                    .chunkIndex(index + 1)
                    .content(content)
                    // 记录向量ID，分片管理时才能精确定位并同步向量库
                    .vectorId(chunk.getId())
                    .createTime(now)
                    .build());
        }
        if (!rows.isEmpty()) {
            saveBatch(rows, BATCH_SIZE);
        }
        bm25KeywordRetriever.evict();
        log.info("关键词索引更新完成：file={}, chunks={}", file.getFileName(), rows.size());
    }

    /**
     * 文件删除时同步清理关键词索引
     */
    public void deleteByFileIds(Collection<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        remove(new QueryWrapper<KnowledgeChunk>().in("file_id", fileIds));
        bm25KeywordRetriever.evict();
    }

    /**
     * 历史文件补齐关键词索引：从 OSS 取回原始文件，按已保存策略重新切片后写入
     */
    public Map<String, Object> rebuild(List<AliOssFile> files) {
        int success = 0;
        int failed = 0;
        int chunkCount = 0;
        if (files != null) {
            for (AliOssFile file : files) {
                if (file == null || !StringUtils.hasText(file.getUrl())) {
                    continue;
                }
                try {
                    byte[] bytes = aliOssUtil.getObjectBytes(aliOssUtil.resolveObjectName(file.getUrl()));
                    ChunkStrategyEnum strategy = ChunkStrategyEnum.fromCode(file.getChunkStrategy());
                    ChunkConfigDTO config = ChunkConfigDTO.parse(file.getChunkConfig(), strategy);
                    List<Document> documents = new TikaDocumentReader(
                            new NamedByteArrayResource(bytes, file.getFileName())).read();
                    List<Document> chunks = documentChunkService.split(documents, strategy, config, file);
                    replaceChunks(file, chunks);
                    chunkCount += chunks.size();
                    success++;
                } catch (Exception e) {
                    failed++;
                    log.error("关键词索引重建失败: {}", file.getFileName(), e);
                }
            }
        }
        bm25KeywordRetriever.evict();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileCount", success);
        result.put("failedCount", failed);
        result.put("chunkCount", chunkCount);
        return result;
    }

    private void removeByFileId(Long fileId) {
        remove(new QueryWrapper<KnowledgeChunk>().eq("file_id", fileId));
    }

    /**
     * 带文件名的内存资源，Tika 需要文件名后缀辅助识别文件格式
     */
    private static class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
