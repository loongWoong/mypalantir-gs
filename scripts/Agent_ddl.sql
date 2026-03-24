-- Agent 对话与消息表（启动时在默认数据库自动创建）
-- gs314syn.agent_conversation definition

CREATE TABLE IF NOT EXISTS `agent_conversation` (
  `id` varchar(64) NOT NULL,
  `user_id` varchar(128) NOT NULL,
  `title` varchar(255) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_preview` varchar(512) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'active',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- gs314syn.agent_message definition

CREATE TABLE IF NOT EXISTS `agent_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` varchar(64) NOT NULL,
  `role` varchar(32) NOT NULL,
  `content` text NOT NULL,
  `steps_json` text,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_agent_msg_conv` (`conversation_id`),
  CONSTRAINT `fk_agent_msg_conv` FOREIGN KEY (`conversation_id`) REFERENCES `agent_conversation` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=203 DEFAULT CHARSET=utf8mb4 ;