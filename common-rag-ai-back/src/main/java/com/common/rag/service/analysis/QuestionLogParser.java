package com.common.rag.service.analysis;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 从 log_info.request_params 中提取真实提问文本。
 * <p>
 * 历史日志由 LoggingAspect 用 {@code Arrays.toString(args)} 落库，形如 {@code [问题, null]}；
 * {@code @Loggable("message")} 之后改为 {@code Map.toString()}，形如 {@code {message=问题}}。
 * 两种格式都要兼容，否则重算历史数据时会漏统计或把 null、括号算成词。
 */
public final class QuestionLogParser {

    /**
     * 只看问答接口的日志，避免把管理类接口的参数混进词频
     */
    public static final String QUESTION_METHOD = "generatePost";

    /**
     * 问答接口所属类名关键字，用于日志过滤
     */
    public static final String QUESTION_CLASS_KEYWORD = "AiRagController";

    private static final String MESSAGE_PREFIX = "message=";

    private static final String NULL_ARG = "null";

    /**
     * 数组日志尾部的非提问参数：null、空集合、检索模式枚举、UUID 会话 ID。
     * 线上日志是 4 参形态 {@code [问题, null, null, 会话ID]}，只剥离 null 会把 UUID 当成提问文本
     */
    private static final Pattern TAIL_ARGS = Pattern.compile(
            "(?:,\\s*(?:" + NULL_ARG + "|\\[[^\\]]*\\]|SEMANTIC|FULL_TEXT|HYBRID|"
                    + "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}))+$");

    /**
     * 热门问题的最小长度（归一化后），过短的多是寒暄
     */
    private static final int MIN_QUESTION_LENGTH = 4;

    /**
     * 热门问题的最大长度，过长的一般是整段粘贴的文档内容
     */
    private static final int MAX_QUESTION_LENGTH = 60;

    /**
     * 与知识库业务无关的寒暄、测试、模型自述类提问，不作为热门问题推荐
     */
    private static final List<String> TRIVIAL_KEYWORDS = List.of(
            "你是谁", "你叫什么", "你是什么模型", "什么模型", "你是机器人", "你能做什么",
            "测试", "test", "hello", "hi", "在吗", "谢谢", "再见");

    private QuestionLogParser() {
    }

    /**
     * 提取提问文本，无法识别返回 null
     */
    public static String extractQuestion(String requestParams) {
        if (!StringUtils.hasText(requestParams)) {
            return null;
        }
        String text = requestParams.trim();
        if (text.startsWith("{") && text.endsWith("}")) {
            return extractFromMap(text);
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            return extractFromArray(text);
        }
        return null;
    }

    /**
     * 归一化：全角转半角、去空白与标点、英文小写，作为聚合去重键
     */
    public static String normalize(String question) {
        if (!StringUtils.hasText(question)) {
            return "";
        }
        StringBuilder builder = new StringBuilder(question.length());
        for (char origin : question.toCharArray()) {
            char ch = toHalfWidth(origin);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            // 只保留文字与数字，标点不参与去重判断
            if (Character.isLetterOrDigit(ch)) {
                builder.append(Character.toLowerCase(ch));
            }
        }
        return builder.toString();
    }

    /**
     * 是否属于不适合推荐给用户的提问（寒暄、测试、过短或过长）
     */
    public static boolean isTrivial(String question) {
        String normalized = normalize(question);
        if (normalized.length() < MIN_QUESTION_LENGTH || normalized.length() > MAX_QUESTION_LENGTH) {
            return true;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return TRIVIAL_KEYWORDS.stream().anyMatch(keyword -> matches(lower, keyword));
    }

    /**
     * 英文关键字按首尾匹配，避免 "hi" 命中 "which" 这类误判；中文关键字直接包含判断
     */
    private static boolean matches(String text, String keyword) {
        if (keyword.chars().allMatch(ch -> ch < 128)) {
            return text.equals(keyword) || text.startsWith(keyword) || text.endsWith(keyword);
        }
        return text.contains(keyword);
    }

    private static String extractFromMap(String text) {
        String body = text.substring(1, text.length() - 1).trim();
        int index = body.indexOf(MESSAGE_PREFIX);
        if (index < 0) {
            return null;
        }
        // @Loggable("message") 只挑出 message 一个参数，Map.toString() 形如 {message=问题}；
        // 提问内容本身可能带 “=” 与 “, ”，不能再按键值分隔符截断，否则会把问题截成前半句
        return clean(body.substring(index + MESSAGE_PREFIX.length()));
    }

    private static String extractFromArray(String text) {
        String body = text.substring(1, text.length() - 1).trim();
        // 参数顺序为 message、retrievalMode、sources、sessionId，尾部非 message 参数一律剥离
        body = TAIL_ARGS.matcher(body).replaceFirst("").trim();
        if (NULL_ARG.equals(body)) {
            return null;
        }
        return clean(body);
    }

    private static String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value
                .replace("\\r\\n", " ")
                .replace("\\n", " ")
                .replace("\\r", " ")
                .replace("\\t", " ")
                .replace("\\\"", "\"")
                .replace("\\", " ")
                .trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private static char toHalfWidth(char ch) {
        if (ch == '\u3000') {
            return ' ';
        }
        if (ch >= '\uFF01' && ch <= '\uFF5E') {
            return (char) (ch - 0xFEE0);
        }
        return ch;
    }
}