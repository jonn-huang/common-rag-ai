package com.common.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.common.rag.core.ApplicationConstant;
import com.common.rag.core.ErrorCode;
import com.common.rag.core.PageResult;
import com.common.rag.entity.HotQuestion;
import com.common.rag.entity.WordFrequency;
import com.common.rag.exception.BusinessException;
import com.common.rag.mapper.HotQuestionMapper;
import com.common.rag.pojo.dto.HotQuestionDTO;
import com.common.rag.pojo.dto.HotQuestionPageQueryDTO;
import com.common.rag.pojo.vo.HotQuestionVO;
import com.common.rag.service.HotQuestionService;
import com.common.rag.service.WordFrequencyService;
import com.common.rag.service.analysis.FrequencyAnalysisService;
import com.common.rag.service.analysis.QuestionLogParser;
import com.common.rag.service.analysis.UsageQuestion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 热门问题实现：从真实提问里挖掘高频问句，允许运营置顶、排序、屏蔽与人工新增
 */
@Slf4j
@Service
public class HotQuestionServiceImpl extends ServiceImpl<HotQuestionMapper, HotQuestion> implements HotQuestionService {

    private static final String SOURCE_AUTO = "AUTO";

    private static final String SOURCE_MANUAL = "MANUAL";

    /**
     * 单条问题最多关联的热词个数，用于页面打标
     */
    private static final int MAX_KEYWORDS = 3;

    /**
     * 关联热词时参考的热词池大小
     */
    private static final int HOT_WORD_POOL = 50;

    /**
     * 问题文案长度上限，与表结构保持一致
     */
    private static final int MAX_QUESTION_LENGTH = 255;

    /**
     * 问答页缓存有效期
     */
    private static final int CACHE_TTL_MINUTES = 5;

    /**
     * 问答页一次最多返回的推荐问题数
     */
    private static final int MAX_CHAT_LIMIT = 20;

    private final FrequencyAnalysisService frequencyAnalysisService;

    private final WordFrequencyService wordFrequencyService;

    private final RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper;

    public HotQuestionServiceImpl(FrequencyAnalysisService frequencyAnalysisService,
                                  WordFrequencyService wordFrequencyService,
                                  RedisTemplate<String, Object> redisTemplate,
                                  ObjectMapper objectMapper) {
        this.frequencyAnalysisService = frequencyAnalysisService;
        this.wordFrequencyService = wordFrequencyService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public int refreshFromLogs() {
        List<UsageQuestion> questions = frequencyAnalysisService.loadRecentQuestions();
        Map<String, MineStat> stats = new LinkedHashMap<>();
        for (UsageQuestion question : questions) {
            if (!StringUtils.hasText(question.normalized()) || QuestionLogParser.isTrivial(question.question())) {
                continue;
            }
            MineStat stat = stats.computeIfAbsent(question.normalized(), key -> new MineStat(question.question()));
            stat.hitCount++;
            // 用最近一次的问法作为展示文案
            if (question.date() != null && (stat.lastSeen == null || question.date().isAfter(stat.lastSeen))) {
                stat.lastSeen = question.date();
                stat.question = question.question();
            }
        }

        List<String> hotWords = topHotWords();
        Map<String, HotQuestion> existing = list().stream()
                .filter(row -> StringUtils.hasText(row.getNormalized()))
                .collect(Collectors.toMap(HotQuestion::getNormalized, row -> row, (left, right) -> left));

        Date now = new Date();
        List<HotQuestion> inserts = new ArrayList<>();
        List<HotQuestion> updates = new ArrayList<>();
        stats.forEach((normalized, stat) -> {
            String keywords = resolveKeywords(stat.question, hotWords);
            HotQuestion row = existing.get(normalized);
            if (row == null) {
                HotQuestion insert = new HotQuestion();
                insert.setQuestion(stat.question);
                insert.setNormalized(normalized);
                insert.setKeywords(keywords);
                insert.setSource(SOURCE_AUTO);
                insert.setHitCount(stat.hitCount);
                insert.setPinned(0);
                insert.setSortNum(0);
                insert.setStatus(1);
                insert.setCreateTime(now);
                insert.setUpdateTime(now);
                inserts.add(insert);
                return;
            }
            // 人工维护的行（含被编辑过的自动行）不被挖掘结果覆盖
            if (!SOURCE_AUTO.equals(row.getSource())) {
                return;
            }
            row.setKeywords(keywords);
            row.setHitCount(stat.hitCount);
            row.setUpdateTime(now);
            updates.add(row);
        });

        // 掉出榜单且未被置顶的自动行直接清理，避免推荐里长期挂着过时问题；
        // 运营编辑过文案的自动行已转为 MANUAL，被屏蔽的行也算人工干预，清理后会在下次挖掘时以启用状态复活
        List<Long> staleIds = existing.values().stream()
                .filter(row -> SOURCE_AUTO.equals(row.getSource()))
                .filter(row -> row.getPinned() == null || row.getPinned() == 0)
                .filter(row -> row.getStatus() == null || row.getStatus() != 0)
                .filter(row -> !stats.containsKey(row.getNormalized()))
                .map(HotQuestion::getId)
                .toList();

        if (!inserts.isEmpty()) {
            saveBatch(inserts);
        }
        if (!updates.isEmpty()) {
            updateBatchById(updates);
        }
        if (!staleIds.isEmpty()) {
            removeByIds(staleIds);
        }
        evictCache();
        log.info("热门问题挖掘完成：提问 {} 条，命中有效问句 {} 条，新增 {}、更新 {}、清理 {}",
                questions.size(), stats.size(), inserts.size(), updates.size(), staleIds.size());
        return inserts.size() + updates.size();
    }

    @Override
    public List<HotQuestionVO> listForChat(int limit) {
        int size = Math.min(Math.max(limit, 1), MAX_CHAT_LIMIT);
        String cacheKey = ApplicationConstant.CACHE_HOT_QUESTION_PREFIX + size;
        String cached = (String) redisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cached)) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<HotQuestionVO>>() {
                });
            } catch (Exception e) {
                log.warn("热门问题缓存解析失败，回源查询：{}", cacheKey, e);
                redisTemplate.delete(cacheKey);
            }
        }

        LambdaQueryWrapper<HotQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotQuestion::getStatus, 1)
                .orderByDesc(HotQuestion::getPinned)
                .orderByAsc(HotQuestion::getSortNum)
                .orderByDesc(HotQuestion::getHitCount);
        List<HotQuestion> records = page(new Page<>(1, size), wrapper).getRecords();
        List<HotQuestionVO> result = records.stream().map(this::toVO).toList();

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result),
                    CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("热门问题写入缓存失败：{}", cacheKey, e);
        }
        return result;
    }

    @Override
    public PageResult pageQuery(HotQuestionPageQueryDTO queryDTO) {
        int current = queryDTO.getPage() > 0 ? queryDTO.getPage() : 1;
        int size = queryDTO.getPageSize() > 0 ? queryDTO.getPageSize() : 10;

        LambdaQueryWrapper<HotQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(queryDTO.getKeyword()), HotQuestion::getQuestion, queryDTO.getKeyword())
                .eq(queryDTO.getStatus() != null, HotQuestion::getStatus, queryDTO.getStatus())
                .eq(StringUtils.hasText(queryDTO.getSource()), HotQuestion::getSource, queryDTO.getSource())
                .orderByDesc(HotQuestion::getPinned)
                .orderByAsc(HotQuestion::getSortNum)
                .orderByDesc(HotQuestion::getHitCount);

        Page<HotQuestion> page = page(new Page<>(current, size), wrapper);
        List<HotQuestionVO> records = page.getRecords().stream().map(this::toVO).toList();
        return new PageResult(page.getTotal(), records);
    }

    @Override
    public boolean saveQuestion(HotQuestionDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getQuestion())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "问题文案不能为空");
        }
        String question = dto.getQuestion().trim();
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "问题文案不能超过 " + MAX_QUESTION_LENGTH + " 个字符");
        }
        String normalized = QuestionLogParser.normalize(question);
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "问题文案不能只包含符号");
        }

        Date now = new Date();
        boolean duplicate = lambdaQuery()
                .eq(HotQuestion::getNormalized, normalized)
                .ne(dto.getId() != null, HotQuestion::getId, dto.getId())
                .count() > 0;
        if (duplicate) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该问题已存在，请直接编辑已有条目");
        }

        if (dto.getId() == null) {
            HotQuestion entity = new HotQuestion();
            entity.setQuestion(question);
            entity.setNormalized(normalized);
            entity.setKeywords(joinKeywords(dto.getKeywords()));
            entity.setSource(SOURCE_MANUAL);
            entity.setHitCount(0);
            entity.setPinned(dto.getPinned() == null ? 0 : dto.getPinned());
            entity.setSortNum(dto.getSortNum() == null ? 0 : dto.getSortNum());
            entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            boolean saved = save(entity);
            evictCache();
            return saved;
        }

        HotQuestion entity = getById(dto.getId());
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "热门问题不存在或已被删除");
        }
        entity.setQuestion(question);
        entity.setNormalized(normalized);
        if (dto.getKeywords() != null) {
            entity.setKeywords(joinKeywords(dto.getKeywords()));
        }
        if (dto.getPinned() != null) {
            entity.setPinned(dto.getPinned());
        }
        if (dto.getSortNum() != null) {
            entity.setSortNum(dto.getSortNum());
        }
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        // 人工改过文案后不再参与自动挖掘覆盖，保留运营的措辞
        entity.setSource(SOURCE_MANUAL);
        entity.setUpdateTime(now);
        boolean updated = updateById(entity);
        evictCache();
        return updated;
    }

    @Override
    public boolean changeStatus(Long id, Integer status, Integer pinned, Integer sortNum) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "缺少热门问题 ID");
        }
        HotQuestion entity = getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "热门问题不存在或已被删除");
        }
        if (status != null) {
            entity.setStatus(status);
        }
        if (pinned != null) {
            entity.setPinned(pinned);
        }
        if (sortNum != null) {
            entity.setSortNum(sortNum);
        }
        entity.setUpdateTime(new Date());
        boolean updated = updateById(entity);
        evictCache();
        return updated;
    }

    @Override
    public boolean deleteQuestion(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "缺少热门问题 ID");
        }
        boolean removed = removeById(id);
        evictCache();
        return removed;
    }

    @Override
    public void evictCache() {
        Set<String> keys = redisTemplate.keys(ApplicationConstant.CACHE_HOT_QUESTION_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 热词池，用于给推荐问题打标，体现"热点词 → 热门问题"的关联
     */
    private List<String> topHotWords() {
        LambdaQueryWrapper<WordFrequency> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(WordFrequency::getWord)
                .orderByDesc(WordFrequency::getCountNum)
                .last("LIMIT " + HOT_WORD_POOL);
        List<WordFrequency> words = wordFrequencyService.list(wrapper);
        if (words == null || words.isEmpty()) {
            return Collections.emptyList();
        }
        return words.stream().map(WordFrequency::getWord).filter(StringUtils::hasText).toList();
    }

    private String resolveKeywords(String question, List<String> hotWords) {
        if (!StringUtils.hasText(question) || hotWords.isEmpty()) {
            return null;
        }
        List<String> matched = hotWords.stream()
                .filter(word -> question.contains(word))
                .limit(MAX_KEYWORDS)
                .toList();
        return matched.isEmpty() ? null : String.join(",", matched);
    }

    private String joinKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }
        List<String> cleaned = keywords.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(MAX_KEYWORDS)
                .toList();
        return cleaned.isEmpty() ? null : String.join(",", cleaned);
    }

    private HotQuestionVO toVO(HotQuestion entity) {
        return HotQuestionVO.builder()
                .id(entity.getId())
                .question(entity.getQuestion())
                .keywords(splitKeywords(entity.getKeywords()))
                .hitCount(entity.getHitCount() == null ? 0 : entity.getHitCount())
                .pinned(entity.getPinned())
                .sortNum(entity.getSortNum())
                .status(entity.getStatus())
                .source(entity.getSource())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    private List<String> splitKeywords(String keywords) {
        if (!StringUtils.hasText(keywords)) {
            return Collections.emptyList();
        }
        return List.of(keywords.split(","));
    }

    /**
     * 挖掘过程中的累计状态
     */
    private static final class MineStat {

        private String question;

        private int hitCount;

        private LocalDate lastSeen;

        private MineStat(String question) {
            this.question = question;
        }
    }
}