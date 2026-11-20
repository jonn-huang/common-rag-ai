package com.common.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.common.rag.entity.RetrievalConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * @description 针对表【retrieval_config】的数据库操作Mapper
 */
@Mapper
public interface RetrievalConfigMapper extends BaseMapper<RetrievalConfig> {
}
