package com.common.rag.controller;

import com.common.rag.core.ApplicationConstant;
import com.common.rag.core.BaseResponse;
import com.common.rag.core.ResultUtils;
import com.common.rag.pojo.vo.HotQuestionVO;
import com.common.rag.service.HotQuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @description: 问答页热门问题接口，供所有登录用户取用，与 RAG 对话接口同前缀
 */
@Tag(name = "AiHotQuestionController", description = "问答页热门问题")
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/ai")
public class AiHotQuestionController {

    /**
     * 问答页首屏默认展示的问题条数
     */
    private static final int DEFAULT_LIMIT = 6;

    private final HotQuestionService hotQuestionService;

    public AiHotQuestionController(HotQuestionService hotQuestionService) {
        this.hotQuestionService = hotQuestionService;
    }

    @GetMapping("/hot-questions")
    @Operation(summary = "hot questions", description = "按真实提问挖掘的热门问题，用于问答页引导提问")
    public BaseResponse<List<HotQuestionVO>> hotQuestions(
            @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit) {
        return ResultUtils.success(hotQuestionService.listForChat(limit));
    }
}