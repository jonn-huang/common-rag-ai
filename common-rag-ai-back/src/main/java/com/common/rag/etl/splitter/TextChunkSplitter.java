package com.common.rag.etl.splitter;

import com.common.rag.enums.ChunkStrategyEnum;
import com.common.rag.pojo.dto.ChunkConfigDTO;

import java.util.List;

/**
 * @description: 文本切片器，一种切片策略对应一个实现
 */
public interface TextChunkSplitter {

    /**
     * 短于该长度的切片会被丢弃，避免产生无意义的向量
     */
    int MIN_CHUNK_LENGTH = 5;

    /**
     * 对应的切片策略
     */
    ChunkStrategyEnum strategy();

    /**
     * 切分文本
     *
     * @param text   待切分的纯文本
     * @param config 已补齐默认值的切片参数
     */
    List<TextChunk> split(String text, ChunkConfigDTO config);
}
