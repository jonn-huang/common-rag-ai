package com.common.rag.retrieval;

import com.common.rag.entity.KnowledgeChunk;
import com.common.rag.mapper.KnowledgeChunkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @description: 基于 knowledge_chunk 的 BM25 全文检索，索引按 TTL 缓存，ETL 写入后主动失效
 */
@Slf4j
@Service
public class Bm25KeywordRetriever {

    private static final double K1 = 1.2D;

    private static final double B = 0.75D;

    /**
     * 索引缓存时间，避免每次问答都全量加载分片
     */
    private static final long CACHE_TTL_MILLIS = 120_000L;

    @Autowired
    private KnowledgeChunkMapper knowledgeChunkMapper;

    private volatile Corpus corpus;

    private volatile long loadedAt;

    /**
     * 分片变更后立即失效，保证新的关键词索引马上生效
     */
    public void evict() {
        corpus = null;
    }

    /**
     * 按分片原文反查文件信息，向量召回拿不到元数据时用它补齐分类与文件名
     */
    public ChunkRef findByText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return loadCorpus().byText().get(text);
    }

    public List<ScoredChunk> search(String query, List<String> categories, int topK, Double minScore) {
        Set<String> terms = Bm25Tokenizer.termFrequency(query).keySet();
        if (terms.isEmpty()) {
            return new ArrayList<>();
        }
        Corpus current = loadCorpus();
        if (current.docs().isEmpty()) {
            return new ArrayList<>();
        }
        int total = current.docs().size();
        List<ScoredChunk> hits = new ArrayList<>();
        for (ChunkDoc doc : current.docs()) {
            if (!matchCategory(doc.category(), categories)) {
                continue;
            }
            double score = score(current, doc, terms, total);
            if (score <= 0) {
                continue;
            }
            if (minScore != null && score < minScore) {
                continue;
            }
            hits.add(new ScoredChunk(toDocument(doc), score));
        }
        hits.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return hits.size() > topK ? new ArrayList<>(hits.subList(0, topK)) : hits;
    }

    private double score(Corpus corpus, ChunkDoc doc, Set<String> terms, int total) {
        double score = 0D;
        for (String term : terms) {
            Integer tf = doc.termFrequency().get(term);
            if (tf == null || tf == 0) {
                continue;
            }
            int df = corpus.documentFrequency().getOrDefault(term, 0);
            double idf = Math.log(1D + (total - df + 0.5D) / (df + 0.5D));
            double denominator = tf + K1 * (1 - B + B * doc.length() / corpus.averageLength());
            score += idf * (tf * (K1 + 1)) / denominator;
        }
        return score;
    }

    /**
     * 分类为空表示全局知识库，不作过滤
     */
    private boolean matchCategory(String category, List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return true;
        }
        return category != null && categories.contains(category);
    }

    private Document toDocument(ChunkDoc doc) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileId", doc.fileId());
        if (StringUtils.hasText(doc.fileName())) {
            metadata.put("fileName", doc.fileName());
        }
        if (StringUtils.hasText(doc.category())) {
            metadata.put("category", doc.category());
        }
        metadata.put("chunkIndex", doc.chunkIndex());
        return Document.builder().text(doc.content()).metadata(metadata).build();
    }

    private Corpus loadCorpus() {
        Corpus current = corpus;
        if (current != null && System.currentTimeMillis() - loadedAt < CACHE_TTL_MILLIS) {
            return current;
        }
        synchronized (this) {
            if (corpus != null && System.currentTimeMillis() - loadedAt < CACHE_TTL_MILLIS) {
                return corpus;
            }
            corpus = buildCorpus();
            loadedAt = System.currentTimeMillis();
            return corpus;
        }
    }

    private Corpus buildCorpus() {
        long start = System.currentTimeMillis();
        List<KnowledgeChunk> rows = knowledgeChunkMapper.selectList(null);
        List<ChunkDoc> docs = new ArrayList<>(rows.size());
        Map<String, Integer> documentFrequency = new HashMap<>();
        Map<String, ChunkRef> byText = new HashMap<>();
        long totalLength = 0L;
        for (KnowledgeChunk row : rows) {
            Map<String, Integer> tf = Bm25Tokenizer.termFrequency(row.getContent());
            int length = tf.values().stream().mapToInt(Integer::intValue).sum();
            if (length == 0) {
                continue;
            }
            tf.keySet().forEach(term -> documentFrequency.merge(term, 1, Integer::sum));
            ChunkDoc doc = new ChunkDoc(row.getFileId(),
                    row.getFileName(),
                    row.getCategory(),
                    row.getChunkIndex() == null ? 0 : row.getChunkIndex(),
                    tf,
                    length,
                    row.getContent());
            docs.add(doc);
            byText.putIfAbsent(doc.content(), new ChunkRef(doc.fileId(), doc.fileName(),
                    doc.category(), doc.chunkIndex()));
            totalLength += length;
        }
        double averageLength = docs.isEmpty() ? 1D : (double) totalLength / docs.size();
        log.info("关键词索引加载完成：分片 {} 个，词项 {} 个，耗时 {} ms",
                docs.size(), documentFrequency.size(), System.currentTimeMillis() - start);
        return new Corpus(docs, documentFrequency, averageLength, byText);
    }

    /**
     * 分片对应的文件信息
     */
    public record ChunkRef(Long fileId, String fileName, String category, int chunkIndex) {
    }

    private record Corpus(List<ChunkDoc> docs, Map<String, Integer> documentFrequency,
                          double averageLength, Map<String, ChunkRef> byText) {
    }

    private record ChunkDoc(Long fileId, String fileName, String category, int chunkIndex,
                            Map<String, Integer> termFrequency, int length, String content) {
    }
}
