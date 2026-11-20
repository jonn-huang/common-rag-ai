package com.common.rag.etl.splitter;

import com.common.rag.enums.ChunkStrategyEnum;
import com.common.rag.pojo.dto.ChunkConfigDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @description: 语义切片，按相邻句子的向量相似度判断语义边界，在语义跳变处断句。
 * 向量模型不可用、调用异常或文本过大时自动降级为递归切片，避免上传失败。
 */
@Slf4j
@Component
public class SemanticTextSplitter implements TextChunkSplitter {

    /**
     * 单批向量化句数，主流向量模型对批量大小有限制
     */
    private static final int EMBED_BATCH_SIZE = 10;

    /**
     * 句子数超过该值时降级，避免同步上传接口耗时不可控
     */
    private static final int MAX_SENTENCE_COUNT = 2000;

    /**
     * 文本字符数超过该值时降级
     */
    private static final int MAX_TEXT_LENGTH = 1_000_000;

    /**
     * 句末标点
     */
    private static final String SENTENCE_END_CHARS = "。！？；!?;\n";

    /**
     * 次级断句标点，用于拆分超长句子
     */
    private static final String SUB_SENTENCE_CHARS = "，,、：:";

    private final RecursiveCharacterSplitter recursiveSplitter;

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    /**
     * 解析到的向量模型，懒加载避免启动期强依赖
     */
    private EmbeddingModel embeddingModel;

    private boolean embeddingModelResolved;

    public SemanticTextSplitter(RecursiveCharacterSplitter recursiveSplitter,
                                ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        this.recursiveSplitter = recursiveSplitter;
        this.embeddingModelProvider = embeddingModelProvider;
    }

    @Override
    public ChunkStrategyEnum strategy() {
        return ChunkStrategyEnum.SEMANTIC;
    }

    @Override
    public List<TextChunk> split(String text, ChunkConfigDTO config) {
        if (text.length() > MAX_TEXT_LENGTH) {
            log.warn("文本长度 {} 超过语义切片上限 {}，降级为递归切片", text.length(), MAX_TEXT_LENGTH);
            return fallback(text, config);
        }
        List<String> sentences = splitSentences(text, config.sentenceLength());
        if (sentences.size() < 2) {
            return fallback(text, config);
        }
        if (sentences.size() > MAX_SENTENCE_COUNT) {
            log.warn("句子数 {} 超过语义切片上限 {}，降级为递归切片", sentences.size(), MAX_SENTENCE_COUNT);
            return fallback(text, config);
        }
        EmbeddingModel model = resolveEmbeddingModel();
        if (model == null) {
            log.warn("未找到可用的向量模型，降级为递归切片");
            return fallback(text, config);
        }
        List<float[]> vectors;
        try {
            vectors = embed(model, sentences);
        } catch (Exception e) {
            log.warn("语义切片向量化失败，降级为递归切片: {}", e.getMessage());
            return fallback(text, config);
        }
        if (vectors.size() != sentences.size()) {
            log.warn("向量数量与句子数量不一致，降级为递归切片");
            return fallback(text, config);
        }
        return assemble(sentences, vectors, config);
    }

    /**
     * 按相似度与长度约束把句子组装成切片
     */
    private List<TextChunk> assemble(List<String> sentences, List<float[]> vectors, ChunkConfigDTO config) {
        int targetLength = config.targetLength();
        int minChunkLength = Math.max(config.sentenceLength(), targetLength / 4);
        double threshold = config.similarityThreshold();
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < sentences.size(); index++) {
            String sentence = sentences.get(index);
            if (current.length() > 0) {
                // 语义跳变且在语义边界处已有足够内容时才断句，避免产生过碎的切片
                boolean semanticBreak = current.length() >= minChunkLength
                        && cosine(vectors.get(index - 1), vectors.get(index)) < threshold;
                int nextLength = current.length() + sentence.length();
                boolean lengthBreak = nextLength > targetLength && current.length() >= minChunkLength;
                // 长度硬约束，避免单块远超目标长度
                boolean hardBreak = nextLength > targetLength * 2;
                if (semanticBreak || lengthBreak || hardBreak) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
            }
            current.append(sentence);
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return withOverlap(chunks, config.overlap());
    }

    /**
     * 分句：先按句末标点切，超长句再按逗号等次级标点切，仍超长按分句长度硬切
     */
    private List<String> splitSentences(String text, int sentenceLength) {
        List<String> sentences = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            current.append(value);
            if (SENTENCE_END_CHARS.indexOf(value) >= 0) {
                addSentence(sentences, current.toString(), sentenceLength);
                current.setLength(0);
            }
        }
        addSentence(sentences, current.toString(), sentenceLength);
        return sentences;
    }

    private void addSentence(List<String> sentences, String sentence, int sentenceLength) {
        String trimmed = sentence.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (trimmed.length() <= sentenceLength) {
            sentences.add(trimmed);
            return;
        }
        for (String piece : splitByChars(trimmed, SUB_SENTENCE_CHARS)) {
            for (String part : hardCut(piece, sentenceLength)) {
                String value = part.trim();
                if (!value.isEmpty()) {
                    sentences.add(value);
                }
            }
        }
    }

    private List<String> splitByChars(String text, String chars) {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            current.append(value);
            if (chars.indexOf(value) >= 0) {
                pieces.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            pieces.add(current.toString());
        }
        return pieces;
    }

    private List<String> hardCut(String text, int maxLength) {
        List<String> pieces = new ArrayList<>();
        for (int start = 0; start < text.length(); start += maxLength) {
            pieces.add(text.substring(start, Math.min(start + maxLength, text.length())));
        }
        return pieces;
    }

    /**
     * 批量取向量，减少接口调用次数
     */
    private List<float[]> embed(EmbeddingModel model, List<String> sentences) {
        List<float[]> vectors = new ArrayList<>(sentences.size());
        for (int from = 0; from < sentences.size(); from += EMBED_BATCH_SIZE) {
            int to = Math.min(from + EMBED_BATCH_SIZE, sentences.size());
            vectors.addAll(model.embed(sentences.subList(from, to)));
        }
        return vectors;
    }

    /**
     * 余弦相似度，向量已归一化时等价于点积，这里统一按余弦计算兼容未归一化输出
     */
    private double cosine(float[] left, float[] right) {
        int size = Math.min(left.length, right.length);
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < size; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    /**
     * 相邻切片补充重叠，与递归切片保持一致的处理方式
     */
    private List<TextChunk> withOverlap(List<String> chunks, int overlap) {
        List<TextChunk> result = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            String chunk = chunks.get(index);
            if (index > 0 && overlap > 0) {
                String previous = chunks.get(index - 1);
                String prefix = previous.substring(Math.max(0, previous.length() - overlap)).trim();
                if (!prefix.isEmpty()) {
                    chunk = prefix + "\n" + chunk;
                }
            }
            if (chunk.length() >= MIN_CHUNK_LENGTH) {
                result.add(new TextChunk(chunk));
            }
        }
        return result;
    }

    private EmbeddingModel resolveEmbeddingModel() {
        if (embeddingModelResolved) {
            return embeddingModel;
        }
        embeddingModelResolved = true;
        try {
            embeddingModel = embeddingModelProvider.getIfAvailable();
        } catch (Exception e) {
            log.warn("存在多个向量模型，无法确定语义切片使用的模型: {}", e.getMessage());
        }
        return embeddingModel;
    }

    private List<TextChunk> fallback(String text, ChunkConfigDTO config) {
        return recursiveSplitter.split(text, config);
    }
}
