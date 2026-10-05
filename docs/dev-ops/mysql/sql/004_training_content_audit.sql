SET NAMES utf8mb4;
USE cognitive_training;

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
