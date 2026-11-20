-- 分片关键词索引补充向量 ID：单条分片编辑/删除时精确定位 Milvus 中的对应向量
-- 已建库升级脚本，执行一次即可
ALTER TABLE `knowledge_chunk`
  ADD COLUMN `vector_id` VARCHAR(64) DEFAULT NULL COMMENT '分片对应向量ID' AFTER `content`;
