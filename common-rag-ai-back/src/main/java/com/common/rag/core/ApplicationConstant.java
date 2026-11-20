package com.common.rag.core;

public class ApplicationConstant {
    public final static String API_VERSION = "/api/v1";
    public final static String APPLICATION_NAME = "xs-rag-ai";

    public final static String DEFAULT_BASE_URL = "https://api.openai.com";

    /**
     * 热词列表缓存 key，词频重算与清空数据时必须同步失效
     */
    public final static String CACHE_WORD_FREQUENCY = "wordFrequencyList";

    /**
     * 热门问题缓存 key 前缀，实际 key 为 前缀 + limit
     */
    public final static String CACHE_HOT_QUESTION_PREFIX = "hotQuestionList:";
    public final static String DEFAULT_DESCRIBE = "分享自公众号:程序员徐庶";
    public final static String SYSTEM_PROMPT = """
        Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
        If unsure, simply state that you don't know.
        Another thing you need to note is that your reply must be in Chinese!
        DOCUMENTS:
            {documents}    
        """;
}
