-- Agent 对话与消息表（Doris 语法，启动时在默认数据库自动创建）
-- 使用 UNIQUE KEY 以支持 UPDATE 操作

CREATE TABLE IF NOT EXISTS `agent_conversation` (
  `id` varchar(64) NOT NULL,
  `user_id` varchar(128) NOT NULL,
  `title` varchar(255) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_preview` varchar(512) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'active'
)
UNIQUE KEY(`id`)
DISTRIBUTED BY HASH(`id`) BUCKETS 1
PROPERTIES("replication_num" = "1");


CREATE TABLE IF NOT EXISTS `agent_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` varchar(64) NOT NULL,
  `role` varchar(32) NOT NULL,
  `content` string NOT NULL,
  `steps_json` string,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
)
UNIQUE KEY(`id`)
DISTRIBUTED BY HASH(`id`) BUCKETS 1
PROPERTIES("replication_num" = "1");
