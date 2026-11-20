/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.common.rag.controller;

import com.common.rag.annotation.Loggable;
import com.common.rag.core.ApplicationConstant;
import com.common.rag.context.BaseContext;
import com.common.rag.enums.RetrievalModeEnum;
import com.common.rag.pojo.vo.AnswerConfigVO;
import com.common.rag.pojo.vo.RagSourceVO;
import com.common.rag.retrieval.KnowledgeRetrievalService;
import com.common.rag.retrieval.ScoredChunk;
import com.common.rag.service.AnswerConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Tag(name = "AiRagController", description = "Rag接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/ai")
public class AiRagController {

    /**
     * 拼进提示词的资料条数上限，避免上下文过长拖慢响应
     */
    private static final int MAX_CONTEXT_DOCS = 8;

    /**
     * 拼进提示词的资料字符上限
     */
    private static final int MAX_CONTEXT_CHARS = 4000;

    /**
     * 同一文件最多贡献的资料段数，避免一份大文档吃满上下文导致引用全是同一份资料
     */
    private static final int MAX_CHUNKS_PER_FILE = 2;

    /**
     * 对客展示的参考文件摘要长度，同一文件多个分片合并后截断
     */
    private static final int REFERENCE_SNIPPET_LENGTH = 400;

    /**
     * 最多保留几份参考文件，避免把不相关的文件也列给客户
     */
    private static final int MAX_REFERENCE_FILES = 3;

    /**
     * 相对最高分的保留比例，低于最高分与该比例的乘积视为不相关
     */
    private static final double RELATIVE_SCORE_RATIO = 0.6D;

    /**
     * 缺少文件名时的占位名，只用于内部日志，不会作为参考文件下发
     */
    private static final String UNNAMED_FILE = "未命名文档";

    /**
     * 输出格式约束，追加在用户配置的提示词之后，覆盖任何要求标注编号的历史配置
     */
    private static final String OUTPUT_FORMAT_RULE = """

            输出格式（最高优先级，覆盖上文任何冲突要求）：
            回答中不得出现【资料N】这类编号，不得提及文件名、路径或检索过程，直接用自然语言表述依据。
            """;

    // 对话代理
    private final ChatClient chatClient;

    // 知识库检索（语义 / 全文 / 混合）
    private final KnowledgeRetrievalService knowledgeRetrievalService;

    // 引用来源事件需要序列化成 JSON
    private final ObjectMapper objectMapper;

    // 行业边界：提示词与无依据拒答话术，页面保存后每轮实时读取，不再固定进 ChatClient
    private final AnswerConfigService answerConfigService;

    public AiRagController(ChatModel chatModel, ChatMemory chatMemory,
                           KnowledgeRetrievalService knowledgeRetrievalService,
                           ObjectMapper objectMapper,
                           AnswerConfigService answerConfigService) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        PromptChatMemoryAdvisor.builder(chatMemory).build(),
                        SimpleLoggerAdvisor.builder().build()
                )
                .build();
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.objectMapper = objectMapper;
        this.answerConfigService = answerConfigService;
    }

    @Operation(summary = "rag post", description = "Rag对话接口POST版本，先推送引用来源再流式返回答案")
    @PostMapping(value = "/rag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Loggable("message")
    public Flux<ServerSentEvent<String>> generatePost(
            @RequestParam(value = "message", defaultValue = "你好") String message,
            @RequestParam(value = "retrievalMode", required = false) String retrievalMode,
            @RequestParam(value = "sources", required = false) List<String> sources,
            @RequestParam(value = "sessionId", required = false) String sessionId)
            throws IOException {

        // 按检索配置（可被 retrievalMode 临时覆盖、可按文件范围收敛）召回分片，再与问题一起送进模型
        KnowledgeRetrievalService.RetrievalOutcome outcome =
                knowledgeRetrievalService.retrieveScored(message, retrievalMode, sources);
        List<ScoredChunk> hits = outcome.hits();
        log.info("RAG 检索完成：query={}, mode={}, sources={}, hits={}",
                message, outcome.mode(), sources, hits.size());

        // 提示词与拒答策略来自页面配置，保存后下一次问答立即生效
        AnswerConfigVO answerConfig = answerConfigService.getEffectiveConfig();

        // 无检索依据或得分过低时不调用模型，避免用通用知识编造回答
        if (shouldRefuse(hits, outcome.mode(), answerConfig)) {
            log.info("RAG 未命中知识库，直接拒答：query={}, mode={}, hits={}",
                    message, outcome.mode(), hits.size());
            ServerSentEvent<String> emptySources = ServerSentEvent.<String>builder(
                            objectMapper.writeValueAsString(
                                    Map.of("mode", outcome.mode(), "hits", List.of(), "refused", true)))
                    .event("sources")
                    .build();
            return Flux.just(
                    emptySources,
                    ServerSentEvent.<String>builder(answerConfig.getRefusalMessage()).event("delta").build(),
                    ServerSentEvent.<String>builder("true").event("refused").build(),
                    ServerSentEvent.<String>builder("done").event("done").build());
        }

        // 先按相对得分剔除无关文件，再剔掉无法关联文件名（文件已删或历史数据缺元数据）的分片，
        // 最后同源合并、单文件配额与字符预算裁剪，进入上下文的资料与对客展示的参考文件是同一批
        List<ScoredChunk> relevantHits = dropUnnamed(filterRelevant(hits));
        List<ContextBlock> contextBlocks = fitContext(selectContext(relevantHits));
        log.info("RAG 引用收敛：query={}, hits={}, relevantHits={}, files={}", message, hits.size(),
                relevantHits.size(), contextBlocks.stream().map(ContextBlock::sourceKey).distinct().count());
        log.info("RAG 引用来源（内部溯源）：query={}, context={}", message, describeContext(contextBlocks));

        String userMessage = buildUserMessage(message, contextBlocks);
        String conversationId = resolveConversationId(sessionId);

        // 首帧推送参考文件（含文件名与合并摘要）与拒答标记，文件标识、路径与得分不出后端
        ServerSentEvent<String> metaEvent = ServerSentEvent.<String>builder(
                        objectMapper.writeValueAsString(
                                Map.of("mode", outcome.mode(), "hits", toSources(contextBlocks), "refused", false)))
                .event("sources")
                .build();

        // 占位符在这里手工替换后整段作为系统提示词，避免模板引擎误解析文档内容中的花括号；
        // 末尾强制追输出格式约束，避免数据库里已保存的旧提示词继续要求模型标注资料编号
        String systemPrompt = answerConfig.getSystemPrompt()
                .replace("{refusal}", answerConfig.getRefusalMessage())
                .replace("{current_data}", LocalDate.now().toString())
                + OUTPUT_FORMAT_RULE;

        // 边推边累积完整回答，流结束后据此判断模型是否自行拒答
        StringBuilder answer = new StringBuilder();
        Flux<ServerSentEvent<String>> content = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()// 流式方式
                .content()
                .filter(StringUtils::hasLength)
                .map(delta -> {
                    answer.append(delta);
                    return ServerSentEvent.<String>builder(delta).event("delta").build();
                })
                // 模型调用异常时用 error 事件告知前端，避免连接无声中断
                .onErrorResume(error -> {
                    log.error("RAG 流式生成失败：query={}", message, error);
                    return Flux.just(ServerSentEvent.<String>builder("回答生成失败，请稍后重试")
                            .event("error")
                            .build());
                });

        // 流结束后再判定是否拒答，前端据此把回答换成系统提示条并隐藏参考文件
        Flux<ServerSentEvent<String>> tail = Flux.defer(() -> {
            boolean refused = isRefusalAnswer(answer.toString(), answerConfig.getRefusalMessage());
            if (refused) {
                log.info("RAG 模型自行拒答：query={}", message);
            }
            return Flux.just(
                    ServerSentEvent.<String>builder(refused ? "true" : "false").event("refused").build(),
                    ServerSentEvent.<String>builder("done").event("done").build());
        });

        return Flux.concat(
                Flux.just(metaEvent),
                content,
                tail);
    }

    /**
     * 剔除无法关联到文件名的分片：文件记录已删只剩孤儿向量，或历史数据确实缺文件名，
     * 这类分片既不进上下文也不列参考文件，避免对客出现「未命名文档」
     */
    private List<ScoredChunk> dropUnnamed(List<ScoredChunk> hits) {
        if (hits == null || hits.isEmpty()) {
            return hits;
        }
        List<ScoredChunk> named = hits.stream()
                .filter(hit -> StringUtils.hasText(metadataText(hit.document().getMetadata(), "fileName")))
                .collect(Collectors.toCollection(ArrayList::new));
        if (named.isEmpty()) {
            // 全部无名时仍保留上下文，只是参考文件会为空
            return hits;
        }
        if (named.size() < hits.size()) {
            log.warn("RAG 命中分片缺少文件名，已排除：query hits={}, excluded={}",
                    hits.size(), hits.size() - named.size());
        }
        return named;
    }

    /**
     * 只保留与最高分接近的命中，避免无关文件既浪费上下文又列成参考文件
     */
    private List<ScoredChunk> filterRelevant(List<ScoredChunk> hits) {
        if (hits == null || hits.isEmpty()) {
            return new ArrayList<>();
        }
        double bestScore = hits.stream().mapToDouble(ScoredChunk::score).max().orElse(0D);
        // 得分为零或负时尺度不可比，不做相对过滤
        if (bestScore <= 0D) {
            return hits;
        }
        double threshold = bestScore * RELATIVE_SCORE_RATIO;
        List<ScoredChunk> relevant = hits.stream()
                .filter(hit -> hit.score() >= threshold)
                .collect(Collectors.toCollection(ArrayList::new));
        return relevant.isEmpty() ? hits : relevant;
    }

    /**
     * 判断模型输出是否就是拒答话术：正常回答不会整段复述拒答话术，包含即视为拒答
     */
    private boolean isRefusalAnswer(String answer, String refusalMessage) {
        String normalizedAnswer = normalizeText(answer);
        String normalizedRefusal = normalizeText(refusalMessage);
        if (!StringUtils.hasText(normalizedAnswer) || !StringUtils.hasText(normalizedRefusal)) {
            return false;
        }
        return normalizedAnswer.contains(normalizedRefusal);
    }

    /**
     * 去空白与标点后比较，兼容模型改写标点、换行的情况
     */
    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", "").replaceAll("[\\p{Punct}。，、；：！？—“”‘’（）【】《》]", "");
    }

    /**
     * 判定本轮是否需要直接拒答：零召回必定拒答；语义检索额外看最高相似度，
     * 混合与全文的得分经归一化或为 BM25 原始分，尺度不同不参与绝对分判断
     */
    private boolean shouldRefuse(List<ScoredChunk> hits, String mode, AnswerConfigVO answerConfig) {
        if (!Boolean.TRUE.equals(answerConfig.getRefuseWhenNoContext())) {
            return false;
        }
        if (hits == null || hits.isEmpty()) {
            return true;
        }
        Double minScore = answerConfig.getMinAnswerScore();
        if (minScore != null && RetrievalModeEnum.SEMANTIC.getCode().equals(mode)) {
            return hits.get(0).score() < minScore;
        }
        return false;
    }

    /**
     * 同一用户的不同会话使用独立记忆，避免多会话上下文串拢
     */
    private String resolveConversationId(String sessionId) {
        Long userId = BaseContext.getCurrentId();
        if (!StringUtils.hasText(sessionId)) {
            return userId == null ? "anonymous" : String.valueOf(userId);
        }
        return userId == null ? sessionId : userId + ":" + sessionId;
    }

    /**
     * 上下文中的一段资料：同一文件的相邻分片会被合并成一段；
     * fileLabel 只用于内部日志溯源，不会随接口下发
     */
    private record ContextBlock(String sourceKey, String fileLabel, String category, int chunkIndex,
                                String text, double score) {
    }

    /**
     * 按文件聚合命中分片：组内相邻分片合并、单文件限量，组间轮询取段，
     * 避免一份大文档的多个分片占满上下文导致引用全是同一份资料
     */
    private List<ContextBlock> selectContext(List<ScoredChunk> hits) {
        if (hits == null || hits.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, List<ScoredChunk>> groups = new LinkedHashMap<>();
        for (ScoredChunk hit : hits) {
            String key = resolveSourceKey(hit.document());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(hit);
        }

        List<List<ContextBlock>> blocksByFile = new ArrayList<>(groups.size());
        for (List<ScoredChunk> chunks : groups.values()) {
            List<ContextBlock> blocks = mergeBlocks(chunks);
            if (blocks.size() > MAX_CHUNKS_PER_FILE) {
                // 单文件只保留得分最高的几段，其余让位给其他文件
                blocks.sort(Comparator.comparingDouble(ContextBlock::score).reversed());
                blocks = new ArrayList<>(blocks.subList(0, MAX_CHUNKS_PER_FILE));
                blocks.sort(Comparator.comparingInt(ContextBlock::chunkIndex));
            }
            if (!blocks.isEmpty()) {
                blocksByFile.add(blocks);
            }
        }
        // 相关度更高的文件先参与轮询
        blocksByFile.sort(Comparator.comparingDouble(
                (List<ContextBlock> blocks) -> blocks.stream().mapToDouble(ContextBlock::score).max().orElse(0D))
                .reversed());
        // 只保留最相关的几份文件，无关文件不进上下文也不列成参考文件
        if (blocksByFile.size() > MAX_REFERENCE_FILES) {
            blocksByFile = new ArrayList<>(blocksByFile.subList(0, MAX_REFERENCE_FILES));
        }

        List<ContextBlock> selected = new ArrayList<>(MAX_CONTEXT_DOCS);
        for (int round = 0; round < MAX_CHUNKS_PER_FILE && selected.size() < MAX_CONTEXT_DOCS; round++) {
            for (List<ContextBlock> blocks : blocksByFile) {
                if (round >= blocks.size()) {
                    continue;
                }
                selected.add(blocks.get(round));
                if (selected.size() >= MAX_CONTEXT_DOCS) {
                    break;
                }
            }
        }
        return selected;
    }

    /**
     * 按分片序号排序后把相邻分片合并成一段，没有序号的分片各自成段
     */
    private List<ContextBlock> mergeBlocks(List<ScoredChunk> chunks) {
        List<ScoredChunk> sorted = new ArrayList<>(chunks);
        sorted.sort(Comparator.comparing(
                (ScoredChunk chunk) -> Optional.ofNullable(chunkIndexOf(chunk.document())).orElse(Integer.MAX_VALUE)));

        List<ContextBlock> blocks = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        String sourceKey = null;
        String fileLabel = null;
        String category = null;
        Integer startIndex = null;
        Integer lastIndex = null;
        double score = 0D;

        for (ScoredChunk chunk : sorted) {
            String body = chunk.document().getText();
            if (!StringUtils.hasText(body)) {
                continue;
            }
            Integer index = chunkIndexOf(chunk.document());
            boolean adjacent = startIndex != null && index != null && lastIndex != null && index == lastIndex + 1;
            if (text.length() > 0 && !adjacent) {
                blocks.add(new ContextBlock(sourceKey, fileLabel, category, startIndex == null ? -1 : startIndex,
                        text.toString(), score));
                text.setLength(0);
                sourceKey = null;
                fileLabel = null;
                category = null;
                startIndex = null;
                score = 0D;
            }
            if (text.length() == 0) {
                sourceKey = resolveSourceKey(chunk.document());
                fileLabel = resolveFileLabel(chunk.document());
                category = metadataText(chunk.document().getMetadata(), "category");
                startIndex = index;
            }
            text.append(text.length() > 0 ? "\n" : "").append(body);
            score = Math.max(score, chunk.score());
            lastIndex = index;
        }
        if (text.length() > 0) {
            blocks.add(new ContextBlock(sourceKey, fileLabel, category, startIndex == null ? -1 : startIndex,
                    text.toString(), score));
        }
        return blocks;
    }

    /**
     * 按字符预算裁剪，尽量在句读边界截断，避免把一条制度条款从中间切断
     */
    private List<ContextBlock> fitContext(List<ContextBlock> blocks) {
        List<ContextBlock> fitted = new ArrayList<>(blocks.size());
        int used = 0;
        for (ContextBlock block : blocks) {
            if (used >= MAX_CONTEXT_CHARS) {
                break;
            }
            if (!StringUtils.hasText(block.text())) {
                continue;
            }
            String text = truncateAtBoundary(block.text(), MAX_CONTEXT_CHARS - used);
            if (!StringUtils.hasText(text)) {
                continue;
            }
            used += text.length();
            fitted.add(new ContextBlock(block.sourceKey(), block.fileLabel(), block.category(),
                    block.chunkIndex(), text, block.score()));
        }
        return fitted;
    }

    /**
     * 对客的参考文件：同一文件的多个分片合并为一条，按文件名排序而不是相关度，
     * 避免条目顺序把检索排名透给客户
     */
    private List<RagSourceVO> toSources(List<ContextBlock> blocks) {
        Map<String, List<ContextBlock>> groups = new LinkedHashMap<>();
        for (ContextBlock block : blocks) {
            groups.computeIfAbsent(block.sourceKey(), k -> new ArrayList<>()).add(block);
        }

        List<RagSourceVO> sources = new ArrayList<>(groups.size());
        for (List<ContextBlock> chunks : groups.values()) {
            List<ContextBlock> ordered = new ArrayList<>(chunks);
            ordered.sort(Comparator.comparingInt(ContextBlock::chunkIndex));
            StringBuilder text = new StringBuilder();
            String category = null;
            String fileName = null;
            for (ContextBlock chunk : ordered) {
                if (StringUtils.hasText(chunk.text())) {
                    text.append(text.length() > 0 ? "\n" : "").append(chunk.text());
                }
                if (category == null) {
                    category = chunk.category();
                }
                if (fileName == null) {
                    fileName = chunk.fileLabel();
                }
            }
            if (!StringUtils.hasText(fileName) || UNNAMED_FILE.equals(fileName)) {
                // 无法关联文件名的分片不列为参考文件，避免对客出现占位名
                log.warn("RAG 参考文件缺少文件名，已跳过展示：sourceKey={}", chunks.get(0).sourceKey());
                continue;
            }
            sources.add(RagSourceVO.builder()
                    .fileName(fileName)
                    .category(category)
                    .snippet(truncateAtBoundary(text.toString(), REFERENCE_SNIPPET_LENGTH))
                    .build());
        }
        Collator collator = Collator.getInstance(Locale.CHINA);
        sources.sort(Comparator.comparing(RagSourceVO::getFileName, Comparator.nullsLast(collator)));
        return sources;
    }

    /**
     * 内部溯源：记录本轮实际引用的文件与分片起点，接口响应里不下发
     */
    private String describeContext(List<ContextBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "无";
        }
        return blocks.stream()
                .map(block -> block.fileLabel() + "#" + block.chunkIndex())
                .collect(Collectors.joining(", "));
    }

    /**
     * 内部日志展示用，优先文件名，缺失时给占位名（占位名不会进对客载荷）
     */
    private String resolveFileLabel(Document document) {
        String fileName = metadataText(document.getMetadata(), "fileName");
        if (StringUtils.hasText(fileName)) {
            return fileName;
        }
        return UNNAMED_FILE;
    }

    private String resolveSourceKey(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        String fileId = metadataText(metadata, "fileId");
        if (StringUtils.hasText(fileId)) {
            return "文件ID#" + fileId;
        }
        String fileName = metadataText(metadata, "fileName");
        if (StringUtils.hasText(fileName)) {
            return fileName;
        }
        // 连文件名都拿不到时按内容区分，避免不同文档被当成同一份
        return "未知来源#" + Integer.toHexString(java.util.Objects.hashCode(document.getText()));
    }

    private Integer chunkIndexOf(Document document) {
        Object value = document.getMetadata() == null ? null : document.getMetadata().get("chunkIndex");
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 超长文本优先在句号、分号或换行处截断，断点太靠前则直接硬切
     */
    private String truncateAtBoundary(String text, int limit) {
        if (limit <= 0 || !StringUtils.hasText(text)) {
            return "";
        }
        int max = Math.min(limit, text.length());
        if (text.length() <= limit) {
            return text;
        }
        int boundary = -1;
        for (char mark : new char[]{'。', '；', '\n', ';'}) {
            boundary = Math.max(boundary, text.lastIndexOf(mark, max - 1));
        }
        int end = boundary > max / 3 ? boundary + 1 : max;
        return text.substring(0, Math.min(end, text.length())) + "…";
    }

    /**
     * 把召回资料拼到用户问题前，未检索到资料时保持原问题不变；
     * 这里只给编号不带文件名，模型无从复述文档来源
     */
    private String buildUserMessage(String message, List<ContextBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return message;
        }
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            context.append("【资料").append(i + 1).append("】\n")
                    .append(blocks.get(i).text())
                    .append("\n\n");
        }
        if (context.length() == 0) {
            return message;
        }
        return "请优先依据以下知识库资料回答用户问题，资料不足时明确说明，不要编造。\n\n"
                + context
                + "用户问题：" + message;
    }

    private String metadataText(Map<String, Object> metadata, String key) {
        Object value = metadata == null ? null : metadata.get(key);
        return value == null ? null : value.toString();
    }
}
