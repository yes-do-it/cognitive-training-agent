USE `cognitive_training`;

CREATE TABLE IF NOT EXISTS `training_plan_audit` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `plan_id` bigint unsigned NOT NULL,
    `user_id` bigint unsigned NOT NULL,
    `action` varchar(32) NOT NULL,
    `reason` varchar(512) NOT NULL,
    `snapshot_data` text,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_training_plan_audit_plan` (`plan_id`, `id`),
    KEY `idx_training_plan_audit_user` (`user_id`, `created_at`),
    CONSTRAINT `fk_training_plan_audit_plan` FOREIGN KEY (`plan_id`) REFERENCES `training_plan` (`id`),
    CONSTRAINT `fk_training_plan_audit_user` FOREIGN KEY (`user_id`) REFERENCES `training_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='训练计划确认与撤销审计记录';
