package com.common.rag.etl;

import com.common.rag.entity.AliOssFile;
import com.common.rag.enums.ChunkStrategyEnum;
import com.common.rag.etl.splitter.TextChunk;
import com.common.rag.etl.splitter.TextChunkSplitter;
import com.common.rag.pojo.dto.ChunkConfigDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @description: 文档切片服务，按用户选择的策略切分文档并补齐向量库需要的元数据
 */
@Slf4j
@Service
public class DocumentChunkService {

    /**
     * 单文件最大切片数，超出后截断，避免异常文件把向量库刷爆
     */
    private static final int MAX_CHUNKS_PER_DOCUMENT = 5000;

    /**
     * 连续空行压缩后的最大保留数
     */
    private static final int MAX_BLANK_LINES = 2;

    private final Map<ChunkStrategyEnum, TextChunkSplitter> splitters;

    public DocumentChunkService(List<TextChunkSplitter> splitterList) {
        this.splitters = splitterList.stream()
                .collect(Collectors.toMap(TextChunkSplitter::strategy, Function.identity()));
    }

    /**
     * 切分文档
     *
     * @param documents 阅读器解析出的原始文档
     * @param strategy  切片策略
     * @param config    切片参数（已补齐默认值）
     * @param file      当前文件记录，用于写入元数据
     */
    public List<Document> split(List<Document> documents, ChunkStrategyEnum strategy,
                                ChunkConfigDTO config, AliOssFile file) {
        List<Document> result = new ArrayList<>();
        for (Document document : documents) {
            String text = normalizeText(document.getText());
            if (!StringUtils.hasText(text)) {
                continue;
            }
            List<TextChunk> chunks = splitText(text, strategy, config, file.getFileName());
            if (chunks.isEmpty()) {
                continue;
            }
            if (chunks.size() > MAX_CHUNKS_PER_DOCUMENT) {
                log.warn("文件 {} 切片数 {} 超过上限 {}，已截断", file.getFileName(), chunks.size(), MAX_CHUNKS_PER_DOCUMENT);
                chunks = chunks.subList(0, MAX_CHUNKS_PER_DOCUMENT);
            }
            int total = chunks.size();
            for (int index = 0; index < total; index++) {
                result.add(buildDocument(chunks.get(index), index, total, strategy, document, file));
            }
        }
        return result;
    }

    /**
     * 执行切分，任何异常都降级为递归切片，最后兜底整篇作为单块
     */
    private List<TextChunk> splitText(String text, ChunkStrategyEnum strategy, ChunkConfigDTO config, String fileName) {
        TextChunkSplitter splitter = splitters.getOrDefault(strategy, splitters.get(ChunkStrategyEnum.RECURSIVE));
        try {
            List<TextChunk> chunks = splitter.split(text, config);
            if (chunks != null && !chunks.isEmpty()) {
                return chunks;
            }
            log.warn("策略 {} 未切出内容，按整篇作为单块: {}", strategy.getCode(), fileName);
        } catch (Exception e) {
            log.error("策略 {} 切片失败，降级为递归切片: {}", strategy.getCode(), fileName, e);
            TextChunkSplitter fallback = splitters.get(ChunkStrategyEnum.RECURSIVE);
            try {
                List<TextChunk> chunks = fallback.split(text, config);
                if (chunks != null && !chunks.isEmpty()) {
                    return chunks;
                }
            } catch (Exception ex) {
                log.error("递归切片同样失败，按整篇作为单块: {}", fileName, ex);
            }
        }
        return List.of(new TextChunk(text));
    }

    private Document buildDocument(TextChunk chunk, int index, int total, ChunkStrategyEnum strategy,
                                   Document source, AliOssFile file) {
        Map<String, Object> metadata = new HashMap<>();
        // 保留解析器的原始元数据，只放标量值，避免向量库 JSON 字段序列化异常
        source.getMetadata().forEach((key, value) -> {
            if (isScalar(value)) {
                metadata.put(key, value);
            }
        });
        metadata.put("chunkStrategy", strategy.getCode());
        metadata.put("chunkIndex", index);
        metadata.put("chunkTotal", total);
        // 预览时文件记录尚未落库，id 等字段可能为空，空值不写入元数据
        putIfNotNull(metadata, "fileId", file.getId());
        putIfNotNull(metadata, "fileName", file.getFileName());
        putIfNotNull(metadata, "category", file.getCategory());
        putIfNotNull(metadata, "heading", chunk.heading());
        return Document.builder().text(chunk.text()).metadata(metadata).build();
    }

    /**
     * 统一换行符并压缩多余空行，减少无效字符对切片的干扰
     */
    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{" + (MAX_BLANK_LINES + 1) + ",}", "\n\n");
        return normalized.trim();
    }

    private boolean isScalar(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    private void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }
}
