package com.common.rag.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.common.rag.core.PageResult;
import com.common.rag.entity.HotQuestion;
import com.common.rag.pojo.dto.HotQuestionDTO;
import com.common.rag.pojo.dto.HotQuestionPageQueryDTO;
import com.common.rag.pojo.vo.HotQuestionVO;

import java.util.List;

/**
 * 问答页热门问题：自动挖掘 + 运营干预
 */
public interface HotQuestionService extends IService<HotQuestion> {

    /**
     * 按真实提问重算热门问题，只更新自动挖掘的行，人工维护的行不覆盖
     *
     * @return 本次新增或更新的行数
     */
    int refreshFromLogs();

    /**
     * 问答页首屏取用：只返回启用的条目，按置顶、排序值、提问次数排序
     */
    List<HotQuestionVO> listForChat(int limit);

    /**
     * 管理页分页查询
     */
    PageResult pageQuery(HotQuestionPageQueryDTO queryDTO);

    /**
     * 新增或编辑，编辑过文案的自动行会转为人工维护，避免被挖掘结果覆盖
     */
    boolean saveQuestion(HotQuestionDTO dto);

    /**
     * 调整置顶、排序与启用状态，传 null 表示该项不变
     */
    boolean changeStatus(Long id, Integer status, Integer pinned, Integer sortNum);

    /**
     * 删除单条
     */
    boolean deleteQuestion(Long id);

    /**
     * 清理问答页缓存，挖掘与运营保存后必须调用
     */
    void evictCache();
}