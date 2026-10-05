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
