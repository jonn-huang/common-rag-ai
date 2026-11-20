package com.common.rag.mapper;

import com.common.rag.entity.SensitiveWord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @description 针对表【sensitive_word】的数据库操作Mapper
* @createDate 2025-03-03 21:29:10
* @Entity com.common.rag.entity.SensitiveWord
*/

@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {

}




