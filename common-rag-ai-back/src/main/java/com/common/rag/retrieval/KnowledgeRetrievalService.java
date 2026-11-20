package com.common.rag.retrieval;

import com.common.rag.enums.FusionStrategyEnum;
import com.common.rag.enums.RetrievalModeEnum;
import com.common.rag.pojo.vo.RetrievalConfigVO;
import com.common.rag.pojo.vo.RetrievalHitVO;
import com.common.rag.service.RetrievalConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @description: 知识库统一检索入口，支持语义（向量）、全文（BM25）与混合（加权/RRF）三种方式
 */
@Slf4j
@Service
public class KnowledgeRetrievalService {

    /**
     * 分类过滤在 Java 侧完成，语义检索先按倍率多召回再截断
     */
    private static final int FETCH_MULTIPLIER = 5;

    private static final int MAX_FETCH_SIZE = 200;

    /**
     * RRF 平滑系数，取值参考经典实现
     */
    private static final int RRF_K = 60;

    private static final int PREVIEW_TEXT_LENGTH = 600;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private Bm25KeywordRetriever bm25KeywordRetriever;

    @Autowired
    private RerankService rerankService;

    @Autowired
    private VectorMetadataResolver vectorMetadataResolver;

    @Autowired
    private RetrievalConfigService retrievalConfigService;

    /**
     * 按当前保存的配置检索
     */
    public List<Document> retrieve(String query) {
        return retrieve(query, (String) null);
    }

    /**
     * 按当前配置检索，modeOverride 用于临时指定检索方式
     */
    public List<Document> retrieve(String query, String modeOverride) {
        return toDocuments(retrieveScored(query, modeOverride, null).hits());
    }

    public List<Document> retrieve(String query, RetrievalConfigVO config) {
        return toDocuments(search(query, config, null));
    }

    /**
     * 检索并保留得分：RAG 对话需要把命中分片作为引用来源回给前端
     */
    public RetrievalOutcome retrieveScored(String query, String modeOverride, List<String> fileNames) {
        RetrievalConfigVO config = retrievalConfigService.getEffectiveConfig();
        if (StringUtils.hasText(modeOverride)) {
            config.setRetrievalMode(RetrievalModeEnum.fromCode(modeOverride).getCode());
        }
        RetrievalModeEnum mode = RetrievalModeEnum.fromCode(config.getRetrievalMode());
        List<ScoredChunk> hits = search(query, config, resolveFileFilter(fileNames));
        return new RetrievalOutcome(mode.getCode(), hits);
    }

    /**
     * 一次检索的结果：实际生效的检索方式与排序后的命中分片
     */
    public record RetrievalOutcome(String mode, List<ScoredChunk> hits) {
    }

    /**
     * 把命中分片转成引用来源，供前端展示文件名、分类、序号与相关度
     */
    public static RetrievalHitVO toHit(ScoredChunk chunk) {
        Document document = chunk.document();
        Map<String, Object> metadata = document.getMetadata();
        String text = document.getText();
        return RetrievalHitVO.builder()
                .fileName(metadataText(metadata, "fileName"))
                .category(metadataText(metadata, "category"))
                .chunkIndex(metadataValue(metadata, "chunkIndex"))
                .score(chunk.score())
                .text(text != null && text.length() > PREVIEW_TEXT_LENGTH
                        ? text.substring(0, PREVIEW_TEXT_LENGTH) + "..."
                        : text)
                .build();
    }

    private List<Document> toDocuments(List<ScoredChunk> chunks) {
        List<Document> documents = new ArrayList<>(chunks.size());
        for (ScoredChunk chunk : chunks) {
            documents.add(chunk.document());
        }
        return documents;
    }

    /**
     * 前端传来的文件名白名单，为空表示不限定范围
     */
    private Set<String> resolveFileFilter(List<String> fileNames) {
        if (fileNames == null || fileNames.isEmpty()) {
            return null;
        }
        Set<String> filter = new LinkedHashSet<>();
        for (String fileName : fileNames) {
            if (StringUtils.hasText(fileName)) {
                filter.add(fileName.trim());
            }
        }
        return filter.isEmpty() ? null : filter;
    }

    /**
     * 检索调试：返回当前配置下的命中分片与得分
     */
    public List<RetrievalHitVO> preview(String query) {
        RetrievalConfigVO config = retrievalConfigService.getEffectiveConfig();
        List<RetrievalHitVO> hits = new ArrayList<>();
        for (ScoredChunk chunk : search(query, config, null)) {
            Document document = chunk.document();
            Map<String, Object> metadata = document.getMetadata();
            String text = document.getText();
            hits.add(RetrievalHitVO.builder()
                    .fileName(metadataText(metadata, "fileName"))
                    .category(metadataText(metadata, "category"))
                    .chunkIndex(metadataValue(metadata, "chunkIndex"))
                    .score(chunk.score())
                    .text(text != null && text.length() > PREVIEW_TEXT_LENGTH
                            ? text.substring(0, PREVIEW_TEXT_LENGTH) + "..."
                            : text)
                    .build());
        }
        return hits;
    }

    private List<ScoredChunk> search(String query, RetrievalConfigVO config, Set<String> fileNames) {
        if (!StringUtils.hasText(query) || config == null) {
            return new ArrayList<>();
        }
        RetrievalModeEnum mode = RetrievalModeEnum.fromCode(config.getRetrievalMode());
        List<String> categories = config.getCategories() == null ? new ArrayList<>() : config.getCategories();
        List<ScoredChunk> hits;
        try {
            hits = switch (mode) {
                case SEMANTIC -> semantic(query, config, categories, fileNames);
                case FULL_TEXT -> keyword(query, config, categories, fileNames);
                default -> hybrid(query, config, categories, fileNames);
            };
        } catch (Exception e) {
            log.error("知识库检索失败：mode={}, query={}", mode.getCode(), query, e);
            return new ArrayList<>();
        }
        if (hits.isEmpty()) {
            return hits;
        }
        // 三种模式的命中统一补齐元数据：关键词路径也要补文件名，混合融合的去重键才能对齐
        hits = enrichAll(hits);
        if (Boolean.TRUE.equals(config.getRerankEnabled())) {
            hits = applyRerank(query, hits, config.getRerankTopN());
        }
        return hits;
    }

    /**
     * 向量召回，分类与文件范围过滤在 Java 侧完成，因此先按倍率多召回
     */
    private List<ScoredChunk> semantic(String query, RetrievalConfigVO config, List<String> categories,
                                       Set<String> fileNames) {
        int topK = resolveTopK(config.getVectorTopK());
        boolean needFilter = (categories != null && !categories.isEmpty()) || fileNames != null;
        int fetchSize = needFilter
                ? Math.min(Math.max(topK * FETCH_MULTIPLIER, 20), MAX_FETCH_SIZE)
                : topK;
        double threshold = config.getSimilarityThreshold() == null ? 0.4D : config.getSimilarityThreshold();

        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(fetchSize)
                .similarityThreshold(threshold)
                .build());
        List<ScoredChunk> hits = new ArrayList<>();
        if (documents == null) {
            return hits;
        }
        for (Document document : documents) {
            // 向量库回来的分片可能丢失元数据，先按原文反查补齐分类与文件名
            Document enriched = enrich(document);
            if (!matchFilters(enriched, categories, fileNames)) {
                continue;
            }
            double score = document.getScore() == null ? 0D : document.getScore();
            hits.add(new ScoredChunk(enriched, score));
            if (hits.size() >= topK) {
                break;
            }
        }
        return hits;
    }

    /**
     * 向量召回只带回正文时，逐级反查落库的文件信息，否则分类过滤、混合融合去重
     * 与对客展示的文件都会因为缺少元数据而失真
     */
    private Document enrich(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        String fileName = metadataText(metadata, "fileName");
        Long fileId = parseId(metadata == null ? null : metadata.get("fileId"));
        if (fileId != null && StringUtils.hasText(fileName)) {
            return document;
        }
        Map<String, Object> merged = new HashMap<>();
        if (metadata != null) {
            merged.putAll(metadata);
        }
        // 1. 已有文件ID：直接按文件反查文件名与分类，这是缺文件名时最可靠的一级
        if (fileId != null) {
            VectorMetadataResolver.FileRef byFileId = vectorMetadataResolver.resolveByFileId(fileId);
            if (fillMissing(merged, byFileId)) {
                return rebuild(document, merged);
            }
        }
        // 2. 按向量ID反查，能定位到文件就返回
        VectorMetadataResolver.FileRef vectorRef = vectorMetadataResolver.resolve(document.getId());
        if (vectorRef != null) {
            fillMissing(merged, vectorRef);
            if (vectorRef.fileId() != null) {
                merged.putIfAbsent("fileId", vectorRef.fileId());
            }
            if (vectorRef.chunkIndex() > 0) {
                merged.putIfAbsent("chunkIndex", vectorRef.chunkIndex());
            }
            return rebuild(document, merged);
        }
        // 3. 最后按原文反查落库的分片
        Bm25KeywordRetriever.ChunkRef chunkRef = bm25KeywordRetriever.findByText(document.getText());
        if (chunkRef == null) {
            return rebuild(document, merged);
        }
        if (chunkRef.fileId() != null) {
            merged.putIfAbsent("fileId", chunkRef.fileId());
        }
        if (StringUtils.hasText(chunkRef.fileName()) && !StringUtils.hasText(metadataText(merged, "fileName"))) {
            merged.put("fileName", chunkRef.fileName());
        }
        if (StringUtils.hasText(chunkRef.category()) && !StringUtils.hasText(metadataText(merged, "category"))) {
            merged.put("category", chunkRef.category());
        }
        if (merged.get("chunkIndex") == null) {
            merged.put("chunkIndex", chunkRef.chunkIndex());
        }
        return rebuild(document, merged);
    }

    /**
     * 对一批命中补齐元数据，已在 semantic 内补过的分片会原样返回
     */
    private List<ScoredChunk> enrichAll(List<ScoredChunk> hits) {
        List<ScoredChunk> enriched = new ArrayList<>(hits.size());
        for (ScoredChunk hit : hits) {
            Document document = hit.document();
            Document filled = enrich(document);
            enriched.add(filled == document ? hit : new ScoredChunk(filled, hit.score()));
        }
        return enriched;
    }

    /**
     * 只补缺失的文件名与分类，已存在的元数据不覆盖
     */
    private boolean fillMissing(Map<String, Object> merged, VectorMetadataResolver.FileRef ref) {
        if (ref == null) {
            return false;
        }
        if (!StringUtils.hasText(metadataText(merged, "fileName")) && StringUtils.hasText(ref.fileName())) {
            merged.put("fileName", ref.fileName());
        }
        if (!StringUtils.hasText(metadataText(merged, "category")) && StringUtils.hasText(ref.category())) {
            merged.put("category", ref.category());
        }
        return StringUtils.hasText(metadataText(merged, "fileName"));
    }

    private Document rebuild(Document document, Map<String, Object> metadata) {
        return Document.builder().text(document.getText()).metadata(metadata).build();
    }

    /**
     * 文件ID在元数据里可能是 Long、Integer 或字符串，统一放宽解析
     */
    private static Long parseId(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * BM25 关键词召回，分类过滤在索引内完成，文件范围过滤在此补全
     */
    private List<ScoredChunk> keyword(String query, RetrievalConfigVO config, List<String> categories,
                                      Set<String> fileNames) {
        int topK = resolveTopK(config.getKeywordTopK());
        int fetchSize = fileNames == null
                ? topK
                : Math.min(topK * FETCH_MULTIPLIER, MAX_FETCH_SIZE);
        List<ScoredChunk> hits = bm25KeywordRetriever.search(query, categories, fetchSize,
                config.getKeywordMinScore());
        if (fileNames == null) {
            return hits;
        }
        List<ScoredChunk> filtered = new ArrayList<>();
        for (ScoredChunk hit : hits) {
            if (!matchFilters(hit.document(), categories, fileNames)) {
                continue;
            }
            filtered.add(hit);
            if (filtered.size() >= topK) {
                break;
            }
        }
        return filtered;
    }

    private List<ScoredChunk> hybrid(String query, RetrievalConfigVO config, List<String> categories,
                                     Set<String> fileNames) {
        List<ScoredChunk> vectorHits = semantic(query, config, categories, fileNames);
        List<ScoredChunk> keywordHits = keyword(query, config, categories, fileNames);
        int limit = Math.max(resolveTopK(config.getVectorTopK()), resolveTopK(config.getKeywordTopK()));
        FusionStrategyEnum fusion = FusionStrategyEnum.fromCode(config.getFusionStrategy());
        if (fusion == FusionStrategyEnum.RRF) {
            return fuseByRrf(vectorHits, keywordHits, limit);
        }
        return fuseByWeight(vectorHits, keywordHits, limit,
                config.getVectorWeight(), config.getKeywordWeight());
    }

    /**
     * 加权融合：两路得分先 min-max 归一化，再按权重相加
     */
    private List<ScoredChunk> fuseByWeight(List<ScoredChunk> vectorHits, List<ScoredChunk> keywordHits,
                                           int limit, Double vectorWeight, Double keywordWeight) {
        double vectorW = vectorWeight == null ? 0.6D : vectorWeight;
        double keywordW = keywordWeight == null ? 0.4D : keywordWeight;
        Map<String, Document> documents = new LinkedHashMap<>();
        Map<String, Double> scores = new LinkedHashMap<>();
        accumulate(documents, scores, normalize(vectorHits), vectorW);
        accumulate(documents, scores, normalize(keywordHits), keywordW);
        return toSortedHits(documents, scores, limit);
    }

    /**
     * RRF 排名融合：按通道内排名的倒数累加，无需归一化
     */
    private List<ScoredChunk> fuseByRrf(List<ScoredChunk> vectorHits, List<ScoredChunk> keywordHits, int limit) {
        Map<String, Document> documents = new LinkedHashMap<>();
        Map<String, Double> scores = new LinkedHashMap<>();
        accumulateRrf(documents, scores, vectorHits);
        accumulateRrf(documents, scores, keywordHits);
        return toSortedHits(documents, scores, limit);
    }

    private void accumulateRrf(Map<String, Document> documents, Map<String, Double> scores, List<ScoredChunk> hits) {
        for (int rank = 0; rank < hits.size(); rank++) {
            ScoredChunk hit = hits.get(rank);
            String key = keyOf(hit.document());
            documents.putIfAbsent(key, hit.document());
            scores.merge(key, 1D / (RRF_K + rank + 1), Double::sum);
        }
    }

    private void accumulate(Map<String, Document> documents, Map<String, Double> scores,
                            List<ScoredChunk> hits, double weight) {
        for (ScoredChunk hit : hits) {
            String key = keyOf(hit.document());
            documents.putIfAbsent(key, hit.document());
            scores.merge(key, hit.score() * weight, Double::sum);
        }
    }

    /**
     * min-max 归一化，通道内得分全部相同时统一记为 1，避免被整体归零
     */
    private List<ScoredChunk> normalize(List<ScoredChunk> hits) {
        if (hits == null || hits.isEmpty()) {
            return new ArrayList<>();
        }
        double max = Double.NEGATIVE_INFINITY;
        double min = Double.POSITIVE_INFINITY;
        for (ScoredChunk hit : hits) {
            max = Math.max(max, hit.score());
            min = Math.min(min, hit.score());
        }
        List<ScoredChunk> normalized = new ArrayList<>(hits.size());
        double range = max - min;
        for (ScoredChunk hit : hits) {
            double score = range <= 1e-9 ? 1D : (hit.score() - min) / range;
            normalized.add(new ScoredChunk(hit.document(), score));
        }
        return normalized;
    }

    private List<ScoredChunk> toSortedHits(Map<String, Document> documents, Map<String, Double> scores, int limit) {
        List<ScoredChunk> hits = new ArrayList<>(scores.size());
        scores.forEach((key, score) -> hits.add(new ScoredChunk(documents.get(key), score)));
        hits.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return hits.size() > limit ? new ArrayList<>(hits.subList(0, limit)) : hits;
    }

    /**
     * 重排后按原文回填融合得分，保证预览仍能看到排序依据
     */
    private List<ScoredChunk> applyRerank(String query, List<ScoredChunk> hits, Integer topN) {
        Map<String, Double> scoreByText = new LinkedHashMap<>();
        List<Document> documents = new ArrayList<>(hits.size());
        for (ScoredChunk hit : hits) {
            documents.add(hit.document());
            scoreByText.merge(hit.document().getText(), hit.score(), Math::max);
        }
        int limit = topN == null || topN <= 0 ? documents.size() : topN;
        List<Document> reranked = rerankService.rerank(query, documents, limit);
        List<ScoredChunk> result = new ArrayList<>(reranked.size());
        for (Document document : reranked) {
            result.add(new ScoredChunk(document, scoreByText.getOrDefault(document.getText(), 0D)));
        }
        return result;
    }

    /**
     * 同一分片的去重键，向量分片用向量ID，关键词分片用文件+序号+文本
     */
    private String keyOf(Document document) {
        String fileId = metadataText(document.getMetadata(), "fileId");
        String chunkIndex = metadataText(document.getMetadata(), "chunkIndex");
        String text = document.getText();
        if (StringUtils.hasText(fileId) && StringUtils.hasText(chunkIndex)) {
            return fileId + "#" + chunkIndex;
        }
        if (StringUtils.hasText(text)) {
            return "text#" + Integer.toHexString(text.hashCode());
        }
        return "id#" + document.getId();
    }

    private int resolveTopK(Integer value) {
        return value == null || value <= 0 ? 10 : value;
    }

    /**
     * 分类与文件范围的双重过滤，fileNames 为空时不限制文件
     */
    private static boolean matchFilters(Document document, List<String> categories, Set<String> fileNames) {
        Map<String, Object> metadata = document.getMetadata();
        if (categories != null && !categories.isEmpty()) {
            String category = metadataText(metadata, "category");
            if (category == null || !categories.contains(category)) {
                return false;
            }
        }
        if (fileNames != null) {
            String fileName = metadataText(metadata, "fileName");
            return fileName != null && fileNames.contains(fileName);
        }
        return true;
    }

    private static String metadataText(Map<String, Object> metadata, String key) {
        Object value = metadata == null ? null : metadata.get(key);
        return value == null ? null : value.toString();
    }

    private static Integer metadataValue(Map<String, Object> metadata, String key) {
        Object value = metadata == null ? null : metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
