package com.common.rag.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.common.rag.entity.AnswerConfig;
import com.common.rag.pojo.dto.AnswerConfigDTO;
import com.common.rag.pojo.vo.AnswerConfigVO;

/**
 * @description 针对表【answer_config】的数据库操作Service
 */
public interface AnswerConfigService extends IService<AnswerConfig> {

    /**
     * 读取当前生效配置，未配置时返回 RagAnswerProperties 的默认值
     */
    AnswerConfigVO getEffectiveConfig();

    /**
     * 保存配置，始终写入单条记录（id = 1），保存后下一次问答立即生效
     */
    AnswerConfigVO saveConfig(AnswerConfigDTO request);

    /**
     * 系统默认配置，供页面「恢复默认」填充表单，不落库
     */
    AnswerConfigVO getDefaultConfig();
}
