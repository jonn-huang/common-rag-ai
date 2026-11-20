-- 知识库检索配置：单条全局配置（id = 1），categories 为空表示全局知识库
CREATE TABLE IF NOT EXISTS `retrieval_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `categories` varchar(1024) DEFAULT NULL COMMENT '绑定的知识库分类，JSON 数组，空表示全局知识库',
  `retrieval_mode` varchar(32) NOT NULL DEFAULT 'HYBRID' COMMENT '检索方式：SEMANTIC/FULL_TEXT/HYBRID',
  `vector_top_k` int NOT NULL DEFAULT 10 COMMENT '语义检索召回数量',
  `similarity_threshold` double NOT NULL DEFAULT 0.4 COMMENT '语义检索最低相似度阈值',
  `keyword_top_k` int NOT NULL DEFAULT 10 COMMENT '全文检索召回数量',
  `keyword_min_score` double DEFAULT NULL COMMENT '全文检索最低 BM25 得分，空表示不限制',
  `fusion_strategy` varchar(16) NOT NULL DEFAULT 'RRF' COMMENT '混合融合方式：WEIGHTED/RRF，默认 RRF',
  `vector_weight` double NOT NULL DEFAULT 0.6 COMMENT '混合检索向量权重',
  `keyword_weight` double NOT NULL DEFAULT 0.4 COMMENT '混合检索关键词权重',
  `rerank_enabled` tinyint NOT NULL DEFAULT 0 COMMENT '是否开启 Rerank 重排',
  `rerank_top_n` int NOT NULL DEFAULT 8 COMMENT '重排后保留条数',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库检索配置';

-- 关键词检索索引：ETL 时同步写入分片原文，供 BM25 全文检索使用
CREATE TABLE IF NOT EXISTS `knowledge_chunk` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `file_id` bigint NOT NULL COMMENT '文件主键',
  `file_name` varchar(512) DEFAULT NULL COMMENT '文件名',
  `category` varchar(128) DEFAULT NULL COMMENT '知识库分类',
  `chunk_index` int DEFAULT NULL COMMENT '分片序号，从 1 开始',
  `content` mediumtext COMMENT '分片原文',
  `vector_id` varchar(64) DEFAULT NULL COMMENT '分片对应向量ID',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_file_id` (`file_id`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库分片关键词索引';

-- 存量迁移：混合检索默认融合方式由加权融合改为 RRF 排名融合（可重复执行）
ALTER TABLE `retrieval_config`
  MODIFY COLUMN `fusion_strategy` varchar(16) NOT NULL DEFAULT 'RRF' COMMENT '混合融合方式：WEIGHTED/RRF，默认 RRF';

UPDATE `retrieval_config`
SET `fusion_strategy` = 'RRF'
WHERE `fusion_strategy` IS NULL OR `fusion_strategy` = '' OR `fusion_strategy` = 'WEIGHTED';
