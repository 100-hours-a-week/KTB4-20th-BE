ALTER TABLE `photo_evaluations`
    ADD COLUMN `execution_attempt_count` TINYINT NOT NULL DEFAULT 0
        COMMENT '오류 재시도 기준 AI 실행 차수(x-retry-count + 1)'
        AFTER `attempt_no`,
    ADD COLUMN `active_slot` TINYINT NULL
        COMMENT '판정 진행 중일 때만 1'
        AFTER `execution_status`;

UPDATE `photo_evaluations` evaluation
JOIN (
    SELECT `mission_photo_id`, MAX(`id`) AS `keep_id`
    FROM `photo_evaluations`
    WHERE `execution_status` IN ('QUEUED', 'RUNNING', 'RETRY_PENDING')
    GROUP BY `mission_photo_id`
) latest
    ON latest.`mission_photo_id` = evaluation.`mission_photo_id`
SET evaluation.`execution_status` = 'FAILED',
    evaluation.`error_code` = 'DUPLICATE_ACTIVE_EVALUATION'
WHERE evaluation.`execution_status` IN ('QUEUED', 'RUNNING', 'RETRY_PENDING')
  AND evaluation.`id` <> latest.`keep_id`;

UPDATE `photo_evaluations`
SET `active_slot` = 1
WHERE `execution_status` IN ('QUEUED', 'RUNNING', 'RETRY_PENDING');

ALTER TABLE `photo_evaluations`
    ADD CONSTRAINT `uk_photo_evaluations_active`
        UNIQUE (`mission_photo_id`, `active_slot`);
