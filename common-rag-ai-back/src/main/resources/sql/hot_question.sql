-- 问答页热门问题推荐：自动挖掘 + 运营干预
-- 执行前请备份数据库；表已存在时请跳过

CREATE TABLE IF NOT EXISTS `hot_question`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `question`    varchar(255) NOT NULL COMMENT '展示给用户的问题文案',
    `normalized`  varchar(255) NOT NULL COMMENT '归一化问题，去重键',
    `keywords`    varchar(255)          DEFAULT NULL COMMENT '关联热词，逗号分隔',
    `source`      varchar(16)  NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO 自动挖掘 / MANUAL 人工维护',
    `hit_count`   int          NOT NULL DEFAULT '0' COMMENT '累计提问次数',
    `pinned`      tinyint      NOT NULL DEFAULT '0' COMMENT '是否置顶：1 置顶 0 普通',
    `sort_num`    int          NOT NULL DEFAULT '0' COMMENT '排序值，越小越靠前',
    `status`      tinyint      NOT NULL DEFAULT '1' COMMENT '状态：1 启用 0 屏蔽',
    `create_time` datetime              DEFAULT NULL,
    `update_time` datetime              DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_normalized` (`normalized`),
    KEY `idx_status_pinned` (`status`, `pinned`, `sort_num`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='问答页热门问题推荐';

-- 数据由「内容安全 - 热门问题配置 - 立即挖掘」或整点定时任务生成