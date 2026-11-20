package com.common.rag.controller;

import com.common.rag.annotation.Loggable;
import com.common.rag.core.ApplicationConstant;
import com.common.rag.core.ErrorCode;
import com.common.rag.context.BaseContext;
import com.common.rag.entity.SensitiveWord;
import com.common.rag.exception.BusinessException;
import com.common.rag.service.SensitiveWordService;
import com.common.rag.utils.SearchUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;


/**
 * @Title: ChatController
 * @Package com.common.rag.controller
 * @description: 对话接口
 */

@Tag(name="AiRagController",description = "chat对话接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/chat")
public class ChatController {

    @Autowired
    private  ChatClient chatClient;

    @Autowired
    private SensitiveWordService sensitiveWordService;


    public ChatController(ChatClient.Builder builder,ChatMemory chatMemory) {

        this.chatClient = builder
                .defaultSystem("""
                        你是一家名为“XS公司”的知识库系统的客户客服代理。请友好乐于助人，充满喜悦地回复。
                        """)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build() // CHAT MEMORY

                        )
                .build();
    }

    @Operation(summary = "stream",description = "流式对话接口")
    @GetMapping(value = "/stream",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Loggable("message")
    public Flux<String> streamRagChat(@RequestParam(value = "message", defaultValue = "你好" ) String message,
                                      @RequestParam(value = "prompt", defaultValue = "你是一名AI助手，致力于帮助人们解决问题.") String prompt){
        return doStream(message, prompt, null);
    }

    /**
     * 前端统一用 POST 提交，单独提供 POST 入口避免 405
     */
    @Operation(summary = "stream post",description = "流式对话接口POST版本")
    @PostMapping(value = "/stream",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Loggable("message")
    public Flux<String> streamRagChatPost(@RequestParam(value = "message", defaultValue = "你好" ) String message,
                                          @RequestParam(value = "prompt", defaultValue = "你是一名AI助手，致力于帮助人们解决问题.") String prompt,
                                          @RequestParam(value = "sessionId", required = false) String sessionId){
        return doStream(message, prompt, sessionId);
    }

    private Flux<String> doStream(String message, String prompt, String sessionId){
        List<SensitiveWord> list = sensitiveWordService.list();

        for(SensitiveWord sensitiveWord: list){
            if (message.contains(sensitiveWord.getWord())){
                return Flux.just("包含敏感词:" + sensitiveWord.getWord());
            }
        }

        return chatClient.prompt()
                .system(prompt)
                .advisors(a -> a
                        .param(ChatMemory.CONVERSATION_ID, resolveConversationId(sessionId)))
                .user(message)
                .stream()
                .content();
    }

    /**
     * 同一用户的不同会话使用独立记忆，避免多会话上下文串拢
     */
    private String resolveConversationId(String sessionId){
        Long userId = BaseContext.getCurrentId();
        if (!StringUtils.hasText(sessionId)){
            return userId == null ? "anonymous" : String.valueOf(userId);
        }
        return userId == null ? sessionId : userId + ":" + sessionId;
    }






}
