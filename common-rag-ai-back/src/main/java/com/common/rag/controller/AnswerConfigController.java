package com.common.rag.controller;

/**
 * @Title: AnswerConfigController
 * @Package com.common.rag.controller
 * @description: 知识库问答提示词配置
 */

import com.common.rag.annotation.Loggable;
import com.common.rag.core.ApplicationConstant;
import com.common.rag.core.BaseResponse;
import com.common.rag.core.ResultUtils;
import com.common.rag.pojo.dto.AnswerConfigDTO;
import com.common.rag.service.AnswerConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AnswerConfigController", description = "知识库问答提示词配置接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/knowledge/answer")
public class AnswerConfigController {

    @Autowired
    private AnswerConfigService answerConfigService;

    @Operation(summary = "config", description = "读取问答提示词配置")
    @GetMapping("/config")
    public BaseResponse getConfig() {
        return ResultUtils.success(answerConfigService.getEffectiveConfig());
    }

    @Operation(summary = "saveConfig", description = "保存问答提示词配置，保存后下一次问答立即生效")
    @PostMapping("/config")
    @Loggable("保存问答提示词配置")
    public BaseResponse saveConfig(@RequestBody AnswerConfigDTO request) {
        return ResultUtils.success(answerConfigService.saveConfig(request), "配置已保存");
    }

    @Operation(summary = "defaultConfig", description = "系统默认提示词，供页面恢复默认")
    @GetMapping("/default")
    public BaseResponse getDefaultConfig() {
        return ResultUtils.success(answerConfigService.getDefaultConfig());
    }
}
