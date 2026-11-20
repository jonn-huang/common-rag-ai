package com.common.rag.test.ELT;

import com.common.rag.entity.AliOssFile;
import com.common.rag.enums.ChunkStrategyEnum;
import com.common.rag.etl.DocumentChunkService;
import com.common.rag.etl.splitter.FixedLengthSplitter;
import com.common.rag.etl.splitter.RecursiveCharacterSplitter;
import com.common.rag.etl.splitter.SemanticTextSplitter;
import com.common.rag.etl.splitter.StructuredTextSplitter;
import com.common.rag.etl.splitter.TextChunk;
import com.common.rag.pojo.dto.ChunkConfigDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 切片策略单元测试，不依赖 MySQL / Milvus / 向量模型接口
 */
class ChunkSplitterTest {

    private final RecursiveCharacterSplitter recursiveSplitter = new RecursiveCharacterSplitter();

    private final FixedLengthSplitter fixedSplitter = new FixedLengthSplitter();

    private final StructuredTextSplitter structuredSplitter = new StructuredTextSplitter(recursiveSplitter);

    private final SemanticTextSplitter semanticSplitter =
            new SemanticTextSplitter(recursiveSplitter, provider(new KeywordEmbeddingModel()));

    @Test
    void fixedSplitterShouldRespectMaxLength() {
        ChunkConfigDTO config = new ChunkConfigDTO();
        config.setMaxLength(100);
        config.setOverlap(20);
        config.normalize(ChunkStrategyEnum.FIXED);

        List<TextChunk> chunks = fixedSplitter.split(paragraph("固定长度切片测试内容。", 60), config);

        assertTrue(chunks.size() > 1, "长文本应被切成多块");
        for (TextChunk chunk : chunks) {
            assertTrue(chunk.text().length() <= 100, "单块长度不能超过最大长度");
            assertTrue(StringUtils.hasText(chunk.text()), "切片内容不能为空");
        }
    }

    @Test
    void recursiveSplitterShouldMergePiecesNearTargetLength() {
        ChunkConfigDTO config = new ChunkConfigDTO();
        config.setTargetLength(80);
        config.setOverlap(10);
        config.normalize(ChunkStrategyEnum.RECURSIVE);

        List<TextChunk> chunks = recursiveSplitter.split(paragraph("递归字符切片测试。", 40), config);

        assertTrue(chunks.size() > 1, "长文本应被切成多块");
        for (TextChunk chunk : chunks) {
            // 允许一块目标长度 + 重叠前缀的余量
            assertTrue(chunk.text().length() <= 80 + 10 + 1, "单块长度不能明显超过目标长度");
        }
    }

    @Test
    void recursiveSplitterShouldKeepCustomSeparator() {
        ChunkConfigDTO config = new ChunkConfigDTO();
        config.setTargetLength(30);
        config.setOverlap(0);
        config.setSeparators("|");
        config.setKeepSeparator(true);
        config.normalize(ChunkStrategyEnum.RECURSIVE);

        String text = "第一段内容|第二段内容|第三段内容|第四段内容|第五段内容";
        List<TextChunk> chunks = recursiveSplitter.split(text, config);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).text().contains("|"), "保留分隔符时切片应包含分隔符");
    }

    @Test
    void structuredSplitterShouldAppendHeadingPath() {
        ChunkConfigDTO config = new ChunkConfigDTO();
        config.setMaxLength(1200);
        config.setMinLength(20);
        config.setOverlap(0);
        config.normalize(ChunkStrategyEnum.STRUCTURED);

        String text = """
                # 第一章 总则
                为规范公司知识库管理，制定本制度。
                ## 1.1 适用范围
                本制度适用于公司全体员工。
                # 第二章 附则
                本制度自发布之日起执行。
                """;
        List<TextChunk> chunks = structuredSplitter.split(text, config);

        assertEquals(3, chunks.size(), "每级标题应各自成块");
        assertEquals("第一章 总则", chunks.get(0).heading());
        assertEquals("第一章 总则 > 1.1 适用范围", chunks.get(1).heading());
        assertTrue(chunks.get(1).text().startsWith("第一章 总则 > 1.1 适用范围"), "切片正文应带上标题路径");
    }

    @Test
    void structuredSplitterShouldFallbackWhenNoHeading() {
        ChunkConfigDTO config = new ChunkConfigDTO();
        config.setMaxLength(60);
        config.setMinLength(20);
        config.setOverlap(0);
        config.setFallbackToRecursive(true);
        config.normalize(ChunkStrategyEnum.STRUCTURED);

        List<TextChunk> chunks = structuredSplitter.split(paragraph("没有标题的正文内容。", 30), config);

        assertTrue(chunks.size() > 1, "未识别到标题时应降级为递归切片");
        for (TextChunk chunk : chunks) {
            assertTrue(chunk.text().length() <= 60 + 1, "降级后单块长度应接近最大长度");
        }
    }

    @Test
    void semanticSplitterShouldBreakOnSemanticJump() {
        ChunkConfigDTO config = new ChunkConfigDTO();
        config.setSentenceLength(20);
        config.setTargetLength(50);
        config.setOverlap(0);
        config.setSimilarityThreshold(0.65);
        config.normalize(ChunkStrategyEnum.SEMANTIC);

        String text = "旅行前需要准备证件。旅行中注意人身安全。旅行后及时整理照片。"
                + "财务报表需要签字。财务数据必须审核。财务档案按期归档。";
        List<TextChunk> chunks = semanticSplitter.split(text, config);

        assertEquals(2, chunks.size(), "话题切换处应断句");
        assertTrue(chunks.get(0).text().contains("旅行"));
        assertTrue(chunks.get(1).text().contains("财务"));
    }

    @Test
    void semanticSplitterShouldFallbackWhenEmbeddingFails() {
        SemanticTextSplitter splitter = new SemanticTextSplitter(recursiveSplitter, provider(new BrokenEmbeddingModel()));
        ChunkConfigDTO config = new ChunkConfigDTO();
        config.setSentenceLength(20);
        config.setTargetLength(50);
        config.normalize(ChunkStrategyEnum.SEMANTIC);

        List<TextChunk> chunks = splitter.split(paragraph("向量模型不可用时降级。", 20), config);

        assertFalse(chunks.isEmpty(), "向量模型异常时应降级为递归切片而不是抛错");
    }

    @Test
    void documentChunkServiceShouldFillMetadata() {
        DocumentChunkService service = new DocumentChunkService(
                List.of(fixedSplitter, recursiveSplitter, semanticSplitter, structuredSplitter));
        ChunkConfigDTO config = new ChunkConfigDTO();
        config.setMaxLength(100);
        config.setOverlap(0);
        config.normalize(ChunkStrategyEnum.FIXED);

        AliOssFile file = AliOssFile.builder()
                .id(1L)
                .fileName("测试.txt")
                .category("制度")
                .build();
        List<Document> documents = service.split(
                List.of(new Document(paragraph("知识库切片元数据测试。", 20))),
                ChunkStrategyEnum.FIXED, config, file);

        assertFalse(documents.isEmpty());
        Map<String, Object> metadata = documents.get(0).getMetadata();
        assertEquals(1L, metadata.get("fileId"));
        assertEquals("测试.txt", metadata.get("fileName"));
        assertEquals("制度", metadata.get("category"));
        assertEquals("FIXED", metadata.get("chunkStrategy"));
        assertEquals(0, metadata.get("chunkIndex"));
        assertEquals(documents.size(), metadata.get("chunkTotal"));
        assertNotNull(documents.get(0).getId());
    }

    private static String paragraph(String sentence, int repeat) {
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < repeat; index++) {
            lines.add(sentence);
        }
        return String.join("\n", lines);
    }

    private static ObjectProvider<EmbeddingModel> provider(EmbeddingModel model) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("embeddingModel", model);
        return factory.getBeanProvider(EmbeddingModel.class);
    }

    /**
     * 以关键词出现次数构造向量：同主题句子相似度高，跨主题相似度低
     */
    private static class KeywordEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            throw new UnsupportedOperationException("测试用向量模型不支持 call");
        }

        @Override
        public float[] embed(Document document) {
            return vectorOf(document.getText());
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            List<float[]> vectors = new ArrayList<>(texts.size());
            for (String text : texts) {
                vectors.add(vectorOf(text));
            }
            return vectors;
        }

        private float[] vectorOf(String text) {
            float travel = text.contains("旅行") ? 1F : 0F;
            float finance = text.contains("财务") ? 1F : 0F;
            return new float[]{travel, finance, text.length() / 100F};
        }
    }

    /**
     * 模拟向量接口异常，用于验证降级逻辑
     */
    private static class BrokenEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            throw new UnsupportedOperationException("测试用向量模型不支持 call");
        }

        @Override
        public float[] embed(Document document) {
            throw new IllegalStateException("模拟向量化失败");
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            throw new IllegalStateException("模拟向量化失败");
        }
    }
}
