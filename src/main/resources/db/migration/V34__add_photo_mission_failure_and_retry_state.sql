ALTER TABLE `photo_evaluations`
    ADD COLUMN `retry_after_at` DATETIME(6) NULL
        COMMENT 'AI 판정 재시도 예정 시각'
        AFTER `evaluated_at`,
    MODIFY COLUMN `execution_status` VARCHAR(20) NOT NULL
        COMMENT 'RUNNING, SUCCEEDED, RETRY_PENDING, FAILED';

ALTER TABLE `mission_participations`
    MODIFY COLUMN `status` VARCHAR(20) NOT NULL
        COMMENT 'PENDING, COMPLETED, FAILED';

ALTER TABLE `mission_group_states`
    MODIFY COLUMN `status` VARCHAR(20) NOT NULL
        COMMENT 'PENDING, COMPLETED, FAILED';
