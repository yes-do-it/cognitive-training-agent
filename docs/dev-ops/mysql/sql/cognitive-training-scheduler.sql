SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS `cognitive_training`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `cognitive_training`;

CREATE TABLE IF NOT EXISTS `training_user` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `external_user_id` varchar(64) NOT NULL,
    `nickname` varchar(64) NOT NULL,
    `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
    `timezone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_training_user_external_id` (`external_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认知训练用户';

CREATE TABLE IF NOT EXISTS `training_content` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `title` varchar(128) NOT NULL,
    `content_type` varchar(32) NOT NULL DEFAULT 'TEXT',
    `difficulty` tinyint unsigned DEFAULT NULL,
    `content_body` text NOT NULL,
    `status` varchar(16) NOT NULL DEFAULT 'ENABLED',
    `version` int NOT NULL DEFAULT 1,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_training_content_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认知训练内容';

CREATE TABLE IF NOT EXISTS `training_plan` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `user_id` bigint unsigned NOT NULL,
    `name` varchar(128) NOT NULL,
    `start_date` date NOT NULL,
    `end_date` date NOT NULL,
    `schedule_time` time NOT NULL,
    `enabled` tinyint(1) NOT NULL DEFAULT '1',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_training_plan_enabled` (`enabled`, `start_date`, `end_date`),
    CONSTRAINT `ck_training_plan_date` CHECK (`end_date` >= `start_date`),
    CONSTRAINT `fk_training_plan_user` FOREIGN KEY (`user_id`) REFERENCES `training_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认知训练计划';

CREATE TABLE IF NOT EXISTS `training_plan_content` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `plan_id` bigint unsigned NOT NULL,
    `content_id` bigint unsigned NOT NULL,
    `sort_order` int NOT NULL DEFAULT 0,
    `enabled` tinyint(1) NOT NULL DEFAULT '1',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_training_plan_content` (`plan_id`, `content_id`),
    KEY `idx_training_plan_content_order` (`plan_id`, `enabled`, `sort_order`),
    CONSTRAINT `fk_training_plan_content_plan` FOREIGN KEY (`plan_id`) REFERENCES `training_plan` (`id`),
    CONSTRAINT `fk_training_plan_content_content` FOREIGN KEY (`content_id`) REFERENCES `training_content` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='训练计划与内容关联';

CREATE TABLE IF NOT EXISTS `training_task` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `user_id` bigint unsigned NOT NULL,
    `plan_id` bigint unsigned NOT NULL,
    `content_id` bigint unsigned DEFAULT NULL,
    `training_date` date NOT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'PENDING',
    `scheduled_at` datetime DEFAULT NULL,
    `completed_at` datetime DEFAULT NULL,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_training_task_user_plan_date` (`user_id`, `plan_id`, `training_date`),
    KEY `idx_training_task_user_date` (`user_id`, `training_date`),
    KEY `idx_training_task_plan_status` (`plan_id`, `status`),
    CONSTRAINT `fk_training_task_user` FOREIGN KEY (`user_id`) REFERENCES `training_user` (`id`),
    CONSTRAINT `fk_training_task_plan` FOREIGN KEY (`plan_id`) REFERENCES `training_plan` (`id`),
    CONSTRAINT `fk_training_task_content` FOREIGN KEY (`content_id`) REFERENCES `training_content` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日认知训练任务';

CREATE TABLE IF NOT EXISTS `training_task_execution` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `task_id` bigint unsigned NOT NULL,
    `user_id` bigint unsigned NOT NULL,
    `content_id` bigint unsigned DEFAULT NULL,
    `attempt_no` int NOT NULL,
    `idempotency_key` varchar(128) NOT NULL,
    `status` varchar(16) NOT NULL DEFAULT 'RUNNING',
    `started_at` datetime NOT NULL,
    `finished_at` datetime DEFAULT NULL,
    `duration_seconds` int DEFAULT NULL,
    `score` int DEFAULT NULL,
    `result_data` text,
    `error_message` varchar(512) DEFAULT NULL,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_training_execution_idempotency` (`idempotency_key`),
    UNIQUE KEY `uk_training_execution_attempt` (`task_id`, `attempt_no`),
    KEY `idx_training_execution_task` (`task_id`, `status`),
    CONSTRAINT `fk_training_execution_task` FOREIGN KEY (`task_id`) REFERENCES `training_task` (`id`),
    CONSTRAINT `fk_training_execution_user` FOREIGN KEY (`user_id`) REFERENCES `training_user` (`id`),
    CONSTRAINT `fk_training_execution_content` FOREIGN KEY (`content_id`) REFERENCES `training_content` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='训练任务执行记录';

SET NAMES utf8mb4;
USE cognitive_training;

CREATE TABLE IF NOT EXISTS training_knowledge_index_task (
    task_id varchar(64) NOT NULL,
    content_ids text NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'QUEUED',
    requested_count int NOT NULL,
    indexed_count int NOT NULL DEFAULT 0,
    chunk_counts text DEFAULT NULL,
    error_message varchar(512) DEFAULT NULL,
    attempt_no int NOT NULL DEFAULT 0,
    max_attempts int NOT NULL DEFAULT 3,
    next_retry_at datetime DEFAULT NULL,
    started_at datetime DEFAULT NULL,
    finished_at datetime DEFAULT NULL,
    created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (task_id),
    KEY idx_training_knowledge_index_task_dispatch (status, next_retry_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认知训练知识库索引任务';

CREATE TABLE IF NOT EXISTS training_content_version_history (
    id bigint unsigned NOT NULL AUTO_INCREMENT,
    content_id bigint unsigned NOT NULL,
    version int NOT NULL,
    title varchar(128) NOT NULL,
    content_type varchar(32) NOT NULL,
    difficulty tinyint unsigned DEFAULT NULL,
    content_body text NOT NULL,
    status varchar(16) NOT NULL,
    source_type varchar(16) NOT NULL DEFAULT 'UPDATE',
    source_file_name varchar(255) DEFAULT NULL,
    created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_training_content_version (content_id, version),
    KEY idx_training_content_history_content (content_id, version),
    CONSTRAINT fk_training_content_history_content
        FOREIGN KEY (content_id) REFERENCES training_content (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认知训练内容版本快照';

CREATE TABLE IF NOT EXISTS training_content_import_record (
    id bigint unsigned NOT NULL AUTO_INCREMENT,
    content_id bigint unsigned DEFAULT NULL,
    file_name varchar(255) NOT NULL,
    content_type varchar(32) NOT NULL,
    file_size bigint unsigned NOT NULL DEFAULT 0,
    status varchar(16) NOT NULL DEFAULT 'PROCESSING',
    error_message varchar(512) DEFAULT NULL,
    index_task_id varchar(64) DEFAULT NULL,
    created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at datetime DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_training_content_import_content (content_id, created_at),
    KEY idx_training_content_import_status (status, created_at),
    CONSTRAINT fk_training_content_import_content
        FOREIGN KEY (content_id) REFERENCES training_content (id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认知训练内容导入记录';
