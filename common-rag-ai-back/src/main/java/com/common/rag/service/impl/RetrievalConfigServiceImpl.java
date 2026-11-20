package com.common.rag.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.common.rag.entity.AliOssFile;
import com.common.rag.entity.RetrievalConfig;
import com.common.rag.mapper.RetrievalConfigMapper;
import com.common.rag.pojo.dto.RetrievalConfigDTO;
import com.common.rag.pojo.vo.RetrievalConfigVO;
import com.common.rag.service.AliOssFileService;
import com.common.rag.service.RetrievalConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @description 针对表【retrieval_config】的数据库操作Service实现
 */
@Slf4j
@Service
public class RetrievalConfigServiceImpl extends ServiceImpl<RetrievalConfigMapper, RetrievalConfig>
        implements RetrievalConfigService {

    /**
     * 检索配置只有一条全局记录
     */
    private static final Long CONFIG_ID = 1L;

    /**
     * 未指定知识库分类的文件不会参与分类绑定检索
     */
    private static final String DEFAULT_CATEGORY = "未分类";

    @Autowired
    private AliOssFileService aliOssFileService;

    @Override
    public RetrievalConfigVO getEffectiveConfig() {
        RetrievalConfig config = getById(CONFIG_ID);
        if (config == null) {
            return RetrievalConfigVO.builder().build();
        }
        return toVO(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RetrievalConfigVO saveConfig(RetrievalConfigDTO request) {
        RetrievalConfigDTO dto = request == null ? new RetrievalConfigDTO() : request;
        dto.normalize();

        RetrievalConfig config = getById(CONFIG_ID);
        Date now = new Date();
        if (config == null) {
            config = new RetrievalConfig();
            config.setId(CONFIG_ID);
            config.setCreateTime(now);
        }
        config.setCategories(JSON.toJSONString(dto.getCategories()));
        config.setRetrievalMode(dto.getRetrievalMode());
        config.setVectorTopK(dto.getVectorTopK());
        config.setSimilarityThreshold(dto.getSimilarityThreshold());
        config.setKeywordTopK(dto.getKeywordTopK());
        config.setKeywordMinScore(dto.getKeywordMinScore());
        config.setFusionStrategy(dto.getFusionStrategy());
        config.setVectorWeight(dto.getVectorWeight());
        config.setKeywordWeight(dto.getKeywordWeight());
        config.setRerankEnabled(dto.getRerankEnabled());
        config.setRerankTopN(dto.getRerankTopN());
        config.setUpdateTime(now);
        saveOrUpdate(config);

        log.info("知识库检索配置保存：mode={}, categories={}, fusion={}",
                config.getRetrievalMode(), config.getCategories(), config.getFusionStrategy());
        return toVO(config);
    }

    @Override
    public List<String> listCategories() {
        List<AliOssFile> files = aliOssFileService.list();
        List<String> categories = new ArrayList<>();
        for (AliOssFile file : files) {
            String category = file.getCategory();
            if (!StringUtils.hasText(category) || DEFAULT_CATEGORY.equals(category)) {
                continue;
            }
            if (!categories.contains(category)) {
                categories.add(category);
            }
        }
        categories.sort(String::compareTo);
        return categories;
    }

    private RetrievalConfigVO toVO(RetrievalConfig config) {
        RetrievalConfigVO vo = RetrievalConfigVO.builder()
                .retrievalMode(config.getRetrievalMode())
                .vectorTopK(config.getVectorTopK())
                .similarityThreshold(config.getSimilarityThreshold())
                .keywordTopK(config.getKeywordTopK())
                .keywordMinScore(config.getKeywordMinScore())
                .fusionStrategy(config.getFusionStrategy())
                .vectorWeight(config.getVectorWeight())
                .keywordWeight(config.getKeywordWeight())
                .rerankEnabled(config.getRerankEnabled())
                .rerankTopN(config.getRerankTopN())
                .build();
        vo.setCategories(parseCategories(config.getCategories()));
        return vo;
    }

    private List<String> parseCategories(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new ArrayList<>();
        }
        try {
            List<String> categories = JSON.parseArray(raw, String.class);
            return categories == null ? new ArrayList<>() : categories;
        } catch (Exception e) {
            log.warn("检索配置分类解析失败，按全局知识库处理: {}", raw);
            return new ArrayList<>();
        }
    }
}
