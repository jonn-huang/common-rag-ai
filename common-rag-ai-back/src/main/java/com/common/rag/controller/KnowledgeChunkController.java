package com.common.rag.controller;

import com.common.rag.annotation.Loggable;
import com.common.rag.core.ApplicationConstant;
import com.common.rag.core.BaseResponse;
import com.common.rag.core.ErrorCode;
import com.common.rag.core.ResultUtils;
import com.common.rag.pojo.dto.ChunkUpdateDTO;
import com.common.rag.service.KnowledgeChunkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "KnowledgeChunkController", description = "知识库分片管理接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/knowledge/chunks")
public class KnowledgeChunkController {

    @Autowired
    private KnowledgeChunkService knowledgeChunkService;

    /**
     * 按文件分页查询已入库分片，只读知识库表，不重新解析原文件
     */
    @Operation(summary = "list", description = "按文件分页查询分片")
    @GetMapping
    public BaseResponse list(@RequestParam("fileId") Long fileId,
                             @RequestParam(value = "keyword", required = false) String keyword,
                             @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                             @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        return ResultUtils.success(knowledgeChunkService.pageByFile(fileId, keyword, page, pageSize));
    }

    /**
     * 修改分片正文：同步更新向量库与全文索引，避免检索仍命中旧内容
     */
    @Operation(summary = "update", description = "修改分片正文并同步向量")
    @PostMapping("/update")
    @Loggable("修改分片内容")
    public BaseResponse update(@RequestBody ChunkUpdateDTO request) {
        if (request == null || request.getId() == null) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "分片 id 不能为空");
        }
        return ResultUtils.success(knowledgeChunkService.updateContent(request.getId(), request.getContent()), "分片内容已更新");
    }

    /**
     * 删除分片：同时清理向量库与文件上的向量ID记录
     */
    @Operation(summary = "delete", description = "删除分片并同步向量")
    @DeleteMapping("/delete")
    @Loggable("删除分片")
    public BaseResponse delete(@RequestParam List<Long> ids) {
        return ResultUtils.success(knowledgeChunkService.deleteChunks(ids), "删除成功");
    }
}
