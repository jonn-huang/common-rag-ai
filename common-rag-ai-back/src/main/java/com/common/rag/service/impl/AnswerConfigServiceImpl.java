package com.common.rag.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.common.rag.config.RagAnswerProperties;
import com.common.rag.entity.AnswerConfig;
import com.common.rag.mapper.AnswerConfigMapper;
import com.common.rag.pojo.dto.AnswerConfigDTO;
import com.common.rag.pojo.vo.AnswerConfigVO;
import com.common.rag.service.AnswerConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * @description 针对表【answer_config】的数据库操作Service实现
 */
@Slf4j
@Service
public class AnswerConfigServiceImpl extends ServiceImpl<AnswerConfigMapper, AnswerConfig>
        implements AnswerConfigService {

    /**
     * 提示词配置只有一条全局记录
     */
    private static final Long CONFIG_ID = 1L;

    @Autowired
    private RagAnswerProperties answerProperties;

    @Override
    public AnswerConfigVO getEffectiveConfig() {
        AnswerConfig config = getById(CONFIG_ID);
        if (config == null) {
            return getDefaultConfig();
        }
        // 提示词与话术为空说明是历史脏数据，回退默认值避免大模型裸奔；其余字段以库中保存为准
        return AnswerConfigVO.builder()
                .systemPrompt(StringUtils.hasText(config.getSystemPrompt())
                        ? config.getSystemPrompt() : answerProperties.getSystemPrompt())
                .refusalMessage(StringUtils.hasText(config.getRefusalMessage())
                        ? config.getRefusalMessage() : answerProperties.getRefusalMessage())
                .refuseWhenNoContext(config.getRefuseWhenNoContext() == null
                        ? answerProperties.isRefuseWhenNoContext() : config.getRefuseWhenNoContext())
                .minAnswerScore(config.getMinAnswerScore())
                .updateTime(config.getUpdateTime())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnswerConfigVO saveConfig(AnswerConfigDTO request) {
        AnswerConfigDTO dto = request == null ? new AnswerConfigDTO() : request;
        dto.normalize();

        AnswerConfig config = getById(CONFIG_ID);
        Date now = new Date();
        if (config == null) {
            config = new AnswerConfig();
            config.setId(CONFIG_ID);
            config.setCreateTime(now);
        }
        // 提示词与话术留空时回退默认，保证每一轮问答都有可用的行业约束
        config.setSystemPrompt(StringUtils.hasText(dto.getSystemPrompt())
                ? dto.getSystemPrompt() : answerProperties.getSystemPrompt());
        config.setRefusalMessage(StringUtils.hasText(dto.getRefusalMessage())
                ? dto.getRefusalMessage() : answerProperties.getRefusalMessage());
        config.setRefuseWhenNoContext(dto.getRefuseWhenNoContext() == null
                ? answerProperties.isRefuseWhenNoContext() : dto.getRefuseWhenNoContext());
        // 允许留空，表示不按相似度拒答
        config.setMinAnswerScore(dto.getMinAnswerScore());
        config.setUpdateTime(now);
        saveOrUpdate(config);

        log.info("问答提示词配置保存：refuseWhenNoContext={}, minAnswerScore={}, promptLength={}",
                config.getRefuseWhenNoContext(), config.getMinAnswerScore(),
                config.getSystemPrompt() == null ? 0 : config.getSystemPrompt().length());
        return getEffectiveConfig();
    }

    @Override
    public AnswerConfigVO getDefaultConfig() {
        return AnswerConfigVO.builder()
                .systemPrompt(answerProperties.getSystemPrompt())
                .refusalMessage(answerProperties.getRefusalMessage())
                .refuseWhenNoContext(answerProperties.isRefuseWhenNoContext())
                .minAnswerScore(answerProperties.getMinAnswerScore())
                .build();
    }
}
