package com.common.rag.retrieval;

import com.alibaba.fastjson2.JSON;
import com.common.rag.entity.AliOssFile;
import com.common.rag.service.AliOssFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @description: 向量ID到文件信息的反查，Milvus 中旧分片可能只带 source 元数据，靠它补齐分类与文件名
 */
@Slf4j
@Service
public class VectorMetadataResolver {

    /**
     * 映射缓存时间，新文件上传后最多 60 秒自动生效
     */
    private static final long CACHE_TTL_MILLIS = 60_000L;

    @Autowired
    private AliOssFileService aliOssFileService;

    private volatile Map<String, FileRef> byVectorId = Map.of();

    private volatile Map<Long, FileRef> byFileId = Map.of();

    private volatile long loadedAt;

    public void evict() {
        byVectorId = Map.of();
        byFileId = Map.of();
        loadedAt = 0L;
    }

    /**
     * 按向量ID反查文件信息，未收录返回 null
     */
    public FileRef resolve(String vectorId) {
        if (!StringUtils.hasText(vectorId)) {
            return null;
        }
        ensureLoaded();
        return byVectorId.get(vectorId);
    }

    /**
     * 按文件ID反查文件信息，分片带着 fileId 却丢了文件名时用它补齐
     */
    public FileRef resolveByFileId(Long fileId) {
        if (fileId == null) {
            return null;
        }
        ensureLoaded();
        return byFileId.get(fileId);
    }

    private void ensureLoaded() {
        if (System.currentTimeMillis() - loadedAt < CACHE_TTL_MILLIS) {
            return;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - loadedAt < CACHE_TTL_MILLIS) {
                return;
            }
            byVectorId = load();
            loadedAt = System.currentTimeMillis();
        }
    }

    /**
     * 向量ID列表按切分顺序落库，下标即分片序号
     */
    private Map<String, FileRef> load() {
        List<AliOssFile> files = aliOssFileService.list();
        Map<String, FileRef> map = new HashMap<>();
        Map<Long, FileRef> fileIdMap = new HashMap<>();
        for (AliOssFile file : files) {
            // 文件级映射先落一份，向量ID解析失败的旧文件也能按 fileId 补齐文件名
            if (file.getId() != null) {
                fileIdMap.putIfAbsent(file.getId(),
                        new FileRef(file.getId(), file.getFileName(), file.getCategory(), 0));
            }
            List<String> vectorIds = parseVectorIds(file.getVectorId());
            for (int index = 0; index < vectorIds.size(); index++) {
                map.putIfAbsent(vectorIds.get(index),
                        new FileRef(file.getId(), file.getFileName(), file.getCategory(), index + 1));
            }
        }
        byFileId = fileIdMap;
        log.info("向量元数据映射加载完成：文件 {} 个，分片 {} 个", files.size(), map.size());
        return map;
    }

    private List<String> parseVectorIds(String vectorId) {
        if (!StringUtils.hasText(vectorId)) {
            return List.of();
        }
        try {
            List<String> ids = JSON.parseArray(vectorId, String.class);
            return ids == null ? List.of() : ids;
        } catch (Exception e) {
            log.warn("向量ID解析失败，跳过分片元数据映射: {}", vectorId);
            return List.of();
        }
    }

    /**
     * 分片对应的文件信息
     */
    public record FileRef(Long fileId, String fileName, String category, int chunkIndex) {
    }
}