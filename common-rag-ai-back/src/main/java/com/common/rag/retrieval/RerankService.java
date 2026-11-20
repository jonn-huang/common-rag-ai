package com.common.rag.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @description: DashScope 文本重排，未配置 key 或调用失败时降级为原顺序，保证问答不被阻断
 */
@Slf4j
@Service
public class RerankService {

    private static final String RERANK_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

    private final ObjectMapper objectMapper;

    private final RestClient restClient;

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @Value("${common.rag.rerank.model:gte-rerank}")
    private String model;

    public RerankService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 重排并保留前 topN 条，任何异常都保留原排序结果
     */
    public List<Document> rerank(String query, List<Document> documents, int topN) {
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }
        int limit = topN <= 0 ? documents.size() : Math.min(topN, documents.size());
        if (documents.size() <= 1 || !StringUtils.hasText(apiKey)) {
            return new ArrayList<>(documents.subList(0, limit));
        }
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("query", query);
            input.put("documents", documents.stream().map(Document::getText).toList());
            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("return_documents", false);
            parameters.put("top_n", limit);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("input", input);
            body.put("parameters", parameters);

            String response = restClient.post()
                    .uri(RERANK_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);

            JsonNode results = objectMapper.readTree(response).path("output").path("results");
            List<Document> reranked = new ArrayList<>(limit);
            for (JsonNode item : results) {
                int index = item.path("index").asInt(-1);
                if (index >= 0 && index < documents.size() && reranked.size() < limit) {
                    reranked.add(documents.get(index));
                }
            }
            return reranked.isEmpty() ? new ArrayList<>(documents.subList(0, limit)) : reranked;
        } catch (Exception e) {
            log.warn("Rerank 调用失败，降级为融合排序结果: {}", e.getMessage());
            return new ArrayList<>(documents.subList(0, limit));
        }
    }
}
