package com.common.rag.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 热词趋势图数据，日期轴与各词的每日出现次数严格对齐
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FrequencyTrendVO {

    /**
     * 横轴日期，格式 yyyy-MM-dd，按时间升序
     */
    private List<String> dates;

    /**
     * 各热词的每日次数
     */
    private List<Series> series;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Series {

        /**
         * 热词
         */
        private String word;

        /**
         * 与 dates 等长的每日次数，无数据补 0
         */
        private List<Integer> data;
    }
}