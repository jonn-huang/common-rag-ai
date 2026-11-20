package com.common.rag.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.common.rag.entity.RetrievalConfig;
import com.common.rag.pojo.dto.RetrievalConfigDTO;
import com.common.rag.pojo.vo.RetrievalConfigVO;

import java.util.List;

/**
 * @description 针对表【retrieval_config】的数据库操作Service
 */
public interface RetrievalConfigService extends IService<RetrievalConfig> {

    /**
     * 读取当前生效配置，未配置时返回默认值（混合检索 + 全局知识库）
     */
    RetrievalConfigVO getEffectiveConfig();

    /**
     * 保存配置，始终写入单条记录（id = 1）
     */
    RetrievalConfigVO saveConfig(RetrievalConfigDTO request);

    /**
     * 知识库中已有的分类列表，供前端多选绑定
     */
    List<String> listCategories();
}
