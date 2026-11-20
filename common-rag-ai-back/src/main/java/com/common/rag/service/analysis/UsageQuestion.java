package com.common.rag.service.analysis;

import java.time.LocalDate;

/**
 * 一条真实用户提问，词频统计与热门问题挖掘共用
 *
 * @param question   提问原文，用于展示
 * @param normalized 归一化后的去重键
 * @param date       提问日期，log_info.request_time 为 date 类型，只到天
 */
public record UsageQuestion(String question, String normalized, LocalDate date) {
}