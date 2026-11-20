package com.common.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.common.rag.core.PageResult;
import com.common.rag.entity.WordFrequency;
import com.common.rag.pojo.dto.WordFrequencyPageQueryDTO;
import com.common.rag.service.WordFrequencyService;
import com.common.rag.mapper.WordFrequencyMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
* @description 针对表【word_frequency】的数据库操作Service实现
* @createDate 2025-03-06 15:56:07
*/
@Service
public class WordFrequencyServiceImpl extends ServiceImpl<WordFrequencyMapper, WordFrequency>
    implements WordFrequencyService{

    /**
     * 页码与每页条数兜底，避免 0 基页码导致首屏空白
     */
    private static final int DEFAULT_PAGE = 1;

    private static final int DEFAULT_PAGE_SIZE = 10;

    @Override
    public PageResult pageQuery(WordFrequencyPageQueryDTO queryDTO) {
        int current = queryDTO.getPage() > 0 ? queryDTO.getPage() : DEFAULT_PAGE;
        int size = queryDTO.getPageSize() > 0 ? queryDTO.getPageSize() : DEFAULT_PAGE_SIZE;
        Page<WordFrequency> page = new Page<>(current, size);

        LambdaQueryWrapper<WordFrequency> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotEmpty(queryDTO.getWord()),
                        WordFrequency::getWord, queryDTO.getWord())
                .eq(StringUtils.isNotEmpty(queryDTO.getBusinessType()),
                        WordFrequency::getBusinessType, queryDTO.getBusinessType())
                .gt(queryDTO.getCountNumMin() != null,
                        WordFrequency::getCountNum, queryDTO.getCountNumMin());
        if ("updateTime".equalsIgnoreCase(queryDTO.getOrderBy())) {
            wrapper.orderByDesc(WordFrequency::getUpdateTime);
        } else {
            wrapper.orderByDesc(WordFrequency::getCountNum);
        }
        // 次数相同的词按 ID 稳定排序，避免翻页时重复或漏数据
        wrapper.orderByAsc(WordFrequency::getId);

        this.page(page, wrapper);
        return new PageResult(page.getTotal(), page.getRecords());
    }
}