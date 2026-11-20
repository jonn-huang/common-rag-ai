package com.common.rag.etl.splitter;

import com.common.rag.enums.ChunkStrategyEnum;
import com.common.rag.pojo.dto.ChunkConfigDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @description: 递归切片，按分隔符优先级逐级下沉切分，再自底向上合并到目标长度。
 * 该实现同时作为其他策略的兜底切分方案。
 */
@Component
public class RecursiveCharacterSplitter implements TextChunkSplitter {

    @Override
    public ChunkStrategyEnum strategy() {
        return ChunkStrategyEnum.RECURSIVE;
    }

    @Override
    public List<TextChunk> split(String text, ChunkConfigDTO config) {
        int targetLength = config.targetLength();
        List<String> pieces = splitRecursive(text, config.resolvedSeparators(), 0, targetLength, config.keepSeparator());
        List<String> merged = mergePieces(pieces, targetLength);
        List<TextChunk> chunks = new ArrayList<>();
        for (String chunk : withOverlap(merged, config.overlap())) {
            if (chunk.length() >= MIN_CHUNK_LENGTH) {
                chunks.add(new TextChunk(chunk));
            }
        }
        return chunks;
    }

    /**
     * 递归切分：当前分隔符切出的片段仍超长时，换下一级分隔符继续切
     */
    private List<String> splitRecursive(String text, List<String> separators, int index,
                                       int maxLength, boolean keepSeparator) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return result;
        }
        if (text.length() <= maxLength) {
            result.add(text);
            return result;
        }
        if (index >= separators.size()) {
            return hardCut(text, maxLength);
        }
        String separator = separators.get(index);
        // 空串表示已到最末级，按长度硬切
        if (separator.isEmpty()) {
            return hardCut(text, maxLength);
        }
        List<String> pieces = splitKeepingSeparator(text, separator, keepSeparator);
        if (pieces.size() <= 1) {
            return splitRecursive(text, separators, index + 1, maxLength, keepSeparator);
        }
        for (String piece : pieces) {
            if (piece.length() > maxLength) {
                result.addAll(splitRecursive(piece, separators, index + 1, maxLength, keepSeparator));
            } else if (!piece.isEmpty()) {
                result.add(piece);
            }
        }
        return result;
    }

    /**
     * 合并片段：贪心拼接到不超过目标长度
     */
    private List<String> mergePieces(List<String> pieces, int maxLength) {
        List<String> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String piece : pieces) {
            if (current.length() == 0) {
                current.append(piece);
                continue;
            }
            if (current.length() + piece.length() <= maxLength) {
                current.append(piece);
            } else {
                addChunk(merged, current);
                current.setLength(0);
                current.append(piece);
            }
        }
        addChunk(merged, current);
        return merged;
    }

    /**
     * 给相邻切片补充重叠：把上一块尾部 overlap 个字符前置到当前块，提升上下文连贯性
     */
    private List<String> withOverlap(List<String> chunks, int overlap) {
        if (overlap <= 0 || chunks.size() < 2) {
            return chunks;
        }
        List<String> result = new ArrayList<>(chunks.size());
        result.add(chunks.get(0));
        for (int index = 1; index < chunks.size(); index++) {
            String previous = chunks.get(index - 1);
            String prefix = previous.substring(Math.max(0, previous.length() - overlap)).trim();
            result.add(prefix.isEmpty() ? chunks.get(index) : prefix + "\n" + chunks.get(index));
        }
        return result;
    }

    /**
     * 按分隔符切分，keepSeparator 决定分隔符是否留在前一片段末尾
     */
    private List<String> splitKeepingSeparator(String text, String separator, boolean keepSeparator) {
        List<String> pieces = new ArrayList<>();
        int from = 0;
        int position;
        while ((position = text.indexOf(separator, from)) >= 0) {
            int end = keepSeparator ? position + separator.length() : position;
            pieces.add(text.substring(from, end));
            from = position + separator.length();
        }
        if (from < text.length()) {
            pieces.add(text.substring(from));
        }
        return pieces;
    }

    /**
     * 按固定长度硬切
     */
    private List<String> hardCut(String text, int maxLength) {
        List<String> pieces = new ArrayList<>();
        for (int start = 0; start < text.length(); start += maxLength) {
            pieces.add(text.substring(start, Math.min(start + maxLength, text.length())));
        }
        return pieces;
    }

    private void addChunk(List<String> chunks, StringBuilder builder) {
        String chunk = builder.toString().trim();
        if (!chunk.isEmpty()) {
            chunks.add(chunk);
        }
    }
}
