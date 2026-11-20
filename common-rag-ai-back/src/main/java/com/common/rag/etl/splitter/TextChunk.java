package com.common.rag.etl.splitter;

/**
 * @description: 切片结果，heading 只在结构切片时有值
 */
public record TextChunk(String text, String heading) {

    public TextChunk(String text) {
        this(text, null);
    }
}
