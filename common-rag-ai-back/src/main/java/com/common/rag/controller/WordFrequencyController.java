package com.common.rag.controller;

import com.common.rag.core.ApplicationConstant;
import com.common.rag.core.BaseResponse;
import com.common.rag.core.PageResult;
import com.common.rag.core.ResultUtils;
import com.common.rag.entity.WordFrequency;
import com.common.rag.pojo.dto.WordFrequencyPageQueryDTO;
import com.common.rag.pojo.vo.FrequencyTrendVO;
import com.common.rag.service.WordFrequencyService;
import com.common.rag.service.analysis.FrequencyAnalysisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @Title: WordFrequencyController
 * @Package com.common.rag.controller
 * @description: 热点词分析控制器，统计口径为真实问答提问
 */
@Tag(name = "WordFrequencyController", description = "热点词分析控制器")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/frequency")
public class WordFrequencyController {

    /**
     * 热词列表缓存有效期
     */
    private static final int CACHE_TTL_MINUTES = 5;

    private final WordFrequencyService wordFrequencyService;

    private final FrequencyAnalysisService frequencyAnalysisService;

    private final RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper;

    public WordFrequencyController(WordFrequencyService wordFrequencyService,
                                   FrequencyAnalysisService frequencyAnalysisService,
                                   RedisTemplate<String, Object> redisTemplate,
                                   ObjectMapper objectMapper) {
        this.wordFrequencyService = wordFrequencyService;
        this.frequencyAnalysisService = frequencyAnalysisService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页条件查询，total 取真实总数，不再用当前页条数覆盖
     */
    @PostMapping("/page")
    @Operation(summary = "page", description = "分页查询热词")
    public BaseResponse<PageResult> pageQuery(@RequestBody WordFrequencyPageQueryDTO queryDTO) {
        return ResultUtils.success(wordFrequencyService.pageQuery(queryDTO));
    }

    /**
     * 清空热词数据，缓存必须同步失效，否则页面还会读到旧结果
     */
    @DeleteMapping("/clean")
    @Operation(summary = "clean", description = "清空热词数据")
    public BaseResponse<String> clean() {
        wordFrequencyService.remove(null);
        redisTemplate.delete(ApplicationConstant.CACHE_WORD_FREQUENCY);
        return ResultUtils.success("已清空，下次统计任务或手动重新统计会重新生成");
    }

    /**
     * 立即全量重算，幂等
     */
    @PostMapping("/rebuild")
    @Operation(summary = "rebuild", description = "按真实提问重新统计热词")
    public BaseResponse<Integer> rebuild() {
        return ResultUtils.success(frequencyAnalysisService.rebuild());
    }

    /**
     * 热词趋势，按天统计真实提问中的词频，替代前端随机数
     */
    @GetMapping("/trend")
    @Operation(summary = "trend", description = "TOP 热词的每日出现次数")
    public BaseResponse<FrequencyTrendVO> trend(@RequestParam(value = "days", defaultValue = "14") int days,
                                                @RequestParam(value = "topN", defaultValue = "5") int topN) {
        return ResultUtils.success(frequencyAnalysisService.dailyTrend(days, topN));
    }

    /**
     * 当前统计窗口天数，页面用于展示统计口径
     */
    @GetMapping("/window")
    @Operation(summary = "window", description = "统计窗口天数")
    public BaseResponse<Integer> window() {
        return ResultUtils.success(frequencyAnalysisService.windowDays());
    }

    @GetMapping("/getList")
    @Operation(summary = "getList", description = "热词列表，可按次数降序取前 N 条")
    public BaseResponse<List<WordFrequency>> getList(
            @RequestParam(value = "limit", required = false) Integer limit) {
        List<WordFrequency> list = loadAll();
        if (limit != null && limit > 0 && list.size() > limit) {
            list = list.subList(0, limit);
        }
        return ResultUtils.success(list);
    }

    private List<WordFrequency> loadAll() {
        String cacheKey = ApplicationConstant.CACHE_WORD_FREQUENCY;
        String cached = (String) redisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cached)) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<WordFrequency>>() {
                });
            } catch (Exception e) {
                log.warn("热词缓存解析失败，回源查询", e);
                redisTemplate.delete(cacheKey);
            }
        }

        List<WordFrequency> list = wordFrequencyService.lambdaQuery()
                .orderByDesc(WordFrequency::getCountNum)
                .list();
        List<WordFrequency> sorted = list.stream()
                .sorted(Comparator.comparing(WordFrequency::getCountNum,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(sorted),
                    CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("热词写入缓存失败", e);
        }
        return sorted;
    }
}