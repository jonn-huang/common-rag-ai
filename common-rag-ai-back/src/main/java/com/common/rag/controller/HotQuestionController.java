package com.common.rag.controller;

import com.common.rag.annotation.Loggable;
import com.common.rag.core.ApplicationConstant;
import com.common.rag.core.BaseResponse;
import com.common.rag.core.PageResult;
import com.common.rag.core.ResultUtils;
import com.common.rag.pojo.dto.HotQuestionDTO;
import com.common.rag.pojo.dto.HotQuestionPageQueryDTO;
import com.common.rag.service.HotQuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description: 热门问题管理控制器，供运营置顶、屏蔽、排序与人工新增
 */
@Tag(name = "HotQuestionController", description = "热门问题管理")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/hot-question")
public class HotQuestionController {

    private final HotQuestionService hotQuestionService;

    public HotQuestionController(HotQuestionService hotQuestionService) {
        this.hotQuestionService = hotQuestionService;
    }

    @PostMapping("/page")
    @Operation(summary = "page", description = "分页查询热门问题")
    public BaseResponse<PageResult> page(@RequestBody HotQuestionPageQueryDTO queryDTO) {
        return ResultUtils.success(hotQuestionService.pageQuery(queryDTO));
    }

    @PostMapping("/save")
    @Loggable("dto")
    @Operation(summary = "save", description = "新增或编辑热门问题")
    public BaseResponse<Boolean> save(@RequestBody HotQuestionDTO dto) {
        return ResultUtils.success(hotQuestionService.saveQuestion(dto));
    }

    @PostMapping("/status")
    @Operation(summary = "status", description = "调整置顶、排序与启用状态")
    public BaseResponse<Boolean> changeStatus(@RequestParam Long id,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) Integer pinned,
                                              @RequestParam(required = false) Integer sortNum) {
        return ResultUtils.success(hotQuestionService.changeStatus(id, status, pinned, sortNum));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "delete", description = "删除热门问题")
    public BaseResponse<Boolean> delete(@RequestParam Long id) {
        return ResultUtils.success(hotQuestionService.deleteQuestion(id));
    }

    @PostMapping("/rebuild")
    @Loggable
    @Operation(summary = "rebuild", description = "立即按真实提问重新挖掘热门问题")
    public BaseResponse<Integer> rebuild() {
        int changed = hotQuestionService.refreshFromLogs();
        return ResultUtils.success(changed);
    }
}