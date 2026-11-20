package com.common.rag.retrieval;

import org.springframework.ai.document.Document;

/**
 * @description: 带打分的检索结果，混合融合阶段需要同时保留各通道分数
 */
public record ScoredChunk(Document document, double score) {
}
