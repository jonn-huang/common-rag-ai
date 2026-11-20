package com.common.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.common.rag.entity.AnswerConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * @description 针对表【answer_config】的数据库操作Mapper
 */
@Mapper
public interface AnswerConfigMapper extends BaseMapper<AnswerConfig> {
}
