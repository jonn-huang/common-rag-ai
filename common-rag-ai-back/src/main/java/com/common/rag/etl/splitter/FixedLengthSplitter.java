package com.common.rag.etl.splitter;

import com.common.rag.enums.ChunkStrategyEnum;
import com.common.rag.pojo.dto.ChunkConfigDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @description: 定长切片，按最大长度等步长推进，尽量在标点处断开
 */
@Component
public class FixedLengthSplitter implements TextChunkSplitter {

    /**
     * 断句标点，切分时优先在这些字符之后断开
     */
    private static final String BOUNDARY_CHARS = "。！？；!?;\n，,．. ";

    /**
     * 回溯窗口占最大长度的比例，窗口越大越容易落在标点处，但块长度波动也越大
     */
    private static final double BACKTRACK_RATIO = 0.25;

    @Override
    public ChunkStrategyEnum strategy() {
        return ChunkStrategyEnum.FIXED;
    }

    @Override
    public List<TextChunk> split(String text, ChunkConfigDTO config) {
        int maxLength = config.maxLength();
        int overlap = config.overlap();
        int step = Math.max(1, maxLength - overlap);
        int length = text.length();
        List<TextChunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < length) {
            int end = Math.min(start + maxLength, length);
            if (end < length) {
                int lowerBound = Math.max(start + step, end - (int) (maxLength * BACKTRACK_RATIO));
                int boundary = findBoundary(text, end, lowerBound);
                if (boundary > start) {
                    end = boundary;
                }
            }
            String chunk = text.substring(start, end).trim();
            if (chunk.length() >= MIN_CHUNK_LENGTH) {
                chunks.add(new TextChunk(chunk));
            }
            if (end >= length) {
                break;
            }
            // 下一块窗口回退 overlap 个字符形成重叠，至少前进一个字符避免死循环
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }

    /**
     * 从窗口尾部向前找最近的标点，返回标点之后的位置；找不到返回 -1
     */
    private int findBoundary(String text, int end, int lowerBound) {
        for (int index = end - 1; index >= lowerBound && index >= 0; index--) {
            if (BOUNDARY_CHARS.indexOf(text.charAt(index)) >= 0) {
                return index + 1;
            }
        }
        return -1;
    }
}
