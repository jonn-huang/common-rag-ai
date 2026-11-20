-- 知识库问答提示词配置：单条全局配置（id = 1），由「我的知识库 - 问答提示词」页面维护
CREATE TABLE IF NOT EXISTS `answer_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `system_prompt` mediumtext COMMENT '系统提示词模板，支持 {refusal} 与 {current_data} 占位符',
  `refusal_message` varchar(512) DEFAULT NULL COMMENT '超出范围或资料不足时的固定回复',
  `refuse_when_no_context` tinyint NOT NULL DEFAULT 1 COMMENT '检索未命中时是否直接拒答',
  `min_answer_score` double DEFAULT NULL COMMENT '语义检索最高相似度低于该值即拒答，空表示不判断',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库问答提示词配置';
