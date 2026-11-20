-- 热点词统计表：清理累加统计产生的脏数据并加去重约束
-- 执行前请备份数据库；ALTER 语句在索引已存在时会报错，可忽略后继续执行后续语句

-- 1. 清掉历史脏计数（旧逻辑每小时累加一次，导致同一句提问被重复统计 N 次）
TRUNCATE TABLE word_frequency;

-- 2. 单词唯一，防止同一词出现多行导致统计口径分裂
ALTER TABLE word_frequency ADD UNIQUE KEY uk_word (word);

-- 3. 热词重算按 method_name + request_time 过滤日志
ALTER TABLE log_info ADD INDEX idx_method_time (method_name, request_time);

-- 4. 数据由「内容安全 - 热点词分析 - 重新统计」或整点定时任务重新生成
-- 5. Redis 中的 wordFrequencyList 缓存会在 5 分钟内自然过期，也可手动 DEL