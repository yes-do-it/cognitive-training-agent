SET NAMES utf8mb4;
USE cognitive_training;

CREATE TABLE IF NOT EXISTS training_task_adjustment_audit (
    id bigint unsigned NOT NULL AUTO_INCREMENT,
    task_id bigint unsigned NOT NULL,
    user_id bigint unsigned NOT NULL,
    training_date date NOT NULL,
    old_content_id bigint unsigned DEFAULT NULL,
    new_content_id bigint unsigned NOT NULL,
    operator_id varchar(64) NOT NULL,
    reason varchar(512) NOT NULL,
    created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_training_task_adjustment_task (task_id, id),
    KEY idx_training_task_adjustment_user_date (user_id, training_date, id),
    CONSTRAINT fk_training_task_adjustment_task
        FOREIGN KEY (task_id) REFERENCES training_task (id),
    CONSTRAINT fk_training_task_adjustment_user
        FOREIGN KEY (user_id) REFERENCES training_user (id),
    CONSTRAINT fk_training_task_adjustment_old_content
        FOREIGN KEY (old_content_id) REFERENCES training_content (id),
    CONSTRAINT fk_training_task_adjustment_new_content
        FOREIGN KEY (new_content_id) REFERENCES training_content (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='当天训练任务调整审计记录';