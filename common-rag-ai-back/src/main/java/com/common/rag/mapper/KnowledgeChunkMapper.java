package com.common.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.common.rag.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;

/**
 * @description 针对表【knowledge_chunk】的数据库操作Mapper
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {
}
