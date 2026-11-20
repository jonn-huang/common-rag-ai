package com.common.rag.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.common.rag.entity.AliOssFile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
* @description 针对表【ali_oss_file】的数据库操作Mapper
* @createDate 2025-02-08 20:51:33
* @Entity com.common.rag.entity.AliOssFile
*/

@Mapper
public interface AliOssFileMapper extends BaseMapper<AliOssFile> {

    IPage<AliOssFile> findByFileNameContaining(Page<AliOssFile> page, @Param("fileName") String fileName,
                                             @Param("category") String category);
}




