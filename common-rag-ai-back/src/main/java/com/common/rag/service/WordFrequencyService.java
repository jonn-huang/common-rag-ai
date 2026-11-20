package com.common.rag.service;

import com.common.rag.core.PageResult;
import com.common.rag.entity.WordFrequency;
import com.baomidou.mybatisplus.extension.service.IService;
import com.common.rag.pojo.dto.WordFrequencyPageQueryDTO;

/**
* @description 针对表【word_frequency】的数据库操作Service
* @createDate 2025-03-06 15:56:07
*/
public interface WordFrequencyService extends IService<WordFrequency> {

    PageResult pageQuery(WordFrequencyPageQueryDTO queryDTO);
}
