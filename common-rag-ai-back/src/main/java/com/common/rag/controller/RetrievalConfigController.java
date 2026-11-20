package com.common.rag.controller;

/**
 * @Title: RetrievalConfigController
 * @Package com.common.rag.controller
 * @description: 知识库检索配置
 */

import com.common.rag.annotation.Loggable;
import com.common.rag.core.ApplicationConstant;
import com.common.rag.core.BaseResponse;
import com.common.rag.core.ErrorCode;
import com.common.rag.core.ResultUtils;
import com.common.rag.etl.KnowledgeChunkIndexService;
import com.common.rag.pojo.dto.RetrievalConfigDTO;
import com.common.rag.retrieval.KnowledgeRetrievalService;
import com.common.rag.service.AliOssFileService;
import com.common.rag.service.RetrievalConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "RetrievalConfigController", description = "知识库检索配置接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/knowledge/retrieval")
public class RetrievalConfigController {

    @Autowired
    private RetrievalConfigService retrievalConfigService;

    @Autowired
    private KnowledgeChunkIndexService knowledgeChunkIndexService;

    @Autowired
    private KnowledgeRetrievalService knowledgeRetrievalService;

    @Autowired
    private AliOssFileService aliOssFileService;

    @Operation(summary = "config", description = "读取知识库检索配置")
    @GetMapping("/config")
    public BaseResponse getConfig() {
        return ResultUtils.success(retrievalConfigService.getEffectiveConfig());
    }

    @Operation(summary = "saveConfig", description = "保存知识库检索配置")
    @PostMapping("/config")
    @Loggable("保存知识库检索配置")
    public BaseResponse saveConfig(@RequestBody RetrievalConfigDTO request) {
        return ResultUtils.success(retrievalConfigService.saveConfig(request), "配置已保存");
    }

    @Operation(summary = "categories", description = "知识库已有分类，供多分类绑定")
    @GetMapping("/categories")
    public BaseResponse listCategories() {
        return ResultUtils.success(retrievalConfigService.listCategories());
    }

    @Operation(summary = "rebuildIndex", description = "重建关键词检索索引（历史文件补齐 BM25）")
    @PostMapping("/rebuild-index")
    @Loggable("重建关键词检索索引")
    public BaseResponse rebuildIndex() {
        return ResultUtils.success(knowledgeChunkIndexService.rebuild(aliOssFileService.list()), "关键词索引已重建");
    }

    @Operation(summary = "preview", description = "按当前配置试检索，用于校验召回效果")
    @GetMapping("/preview")
    public BaseResponse preview(@RequestParam(value = "query", required = false) String query) {
        if (!StringUtils.hasText(query)) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "检索内容不能为空");
        }
        return ResultUtils.success(knowledgeRetrievalService.preview(query));
    }
}
