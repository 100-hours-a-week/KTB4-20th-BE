-- PlanIt Flyway migration: V29 align photo mission persistence

-- 포토미션은 아직 출시 전이므로 V8 구조의 기존 미션을 새 의미로
-- 추측해서 변환하지 않는다. 예상하지 못한 데이터가 있으면 DDL 전에
-- 중단하고 별도 데이터 이관 정책을 먼저 결정한다.
CREATE TEMPORARY TABLE `v29_photo_mission_preflight` (
    `violation_count` INT NOT NULL,
    CONSTRAINT `ck_v29_photo_mission_no_legacy_data`
        CHECK (`violation_count` = 0)
);

INSERT INTO `v29_photo_mission_preflight` (`violation_count`)
SELECT COUNT(*) FROM `missions`;

DROP TEMPORARY TABLE `v29_photo_mission_preflight`;

ALTER TABLE `missions`
    DROP FOREIGN KEY `fk_missions_mission_generation_job_id`,
    DROP COLUMN `mission_generation_job_id`,
    DROP COLUMN `status`,
    DROP COLUMN `title`,
    DROP COLUMN `conditions_json`,
    ADD COLUMN `schedule_visit_id` BIGINT NOT NULL
        COMMENT '미션 대상 방문 장소 ID'
        AFTER `schedule_day_id`,
    ADD COLUMN `primary_category` VARCHAR(50) NULL
        COMMENT 'AI가 선택한 대표 취향 카테고리'
        AFTER `description`;

DROP TABLE `mission_generation_jobs`;

ALTER TABLE `missions`
    ADD CONSTRAINT `fk_missions_schedule_visit_id`
        FOREIGN KEY (`schedule_visit_id`) REFERENCES `schedule_visits` (`id`);

ALTER TABLE `mission_participations`
    ADD CONSTRAINT `uk_mission_participations_mission_member`
        UNIQUE (`mission_id`, `trip_member_id`);

CREATE TABLE `mission_group_states` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '단체 미션 공유 상태 ID',
    `mission_id` BIGINT NOT NULL COMMENT '단체 포토 미션 ID',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING, COMPLETED',
    `retry_count` TINYINT NOT NULL DEFAULT 0
        COMMENT 'AI 사진 판정 실패 횟수',
    `completion_method` VARCHAR(20) NULL COMMENT 'AI, MANUAL',
    `completed_at` DATETIME(6) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT `PK_MISSION_GROUP_STATES` PRIMARY KEY (`id`),
    CONSTRAINT `uk_mission_group_states_mission` UNIQUE (`mission_id`),
    CONSTRAINT `fk_mission_group_states_mission_id`
        FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`)
);

ALTER TABLE `mission_photos`
    MODIFY COLUMN `mission_participation_id` BIGINT NULL
        COMMENT '개인 미션 참여 ID, 단체 미션은 NULL',
    ADD COLUMN `submitted_by_member_id` BIGINT NOT NULL
        COMMENT '사진 제출자 여행 멤버십 ID'
        AFTER `mission_id`,
    ADD COLUMN `idempotency_key` BINARY(16) NOT NULL
        COMMENT '중복 사진 제출 방지 키'
        AFTER `image_file_id`,
    ADD COLUMN `photo_latitude` DECIMAL(10, 7) NULL
        COMMENT '사진 EXIF 위도'
        AFTER `idempotency_key`,
    ADD COLUMN `photo_longitude` DECIMAL(10, 7) NULL
        COMMENT '사진 EXIF 경도'
        AFTER `photo_latitude`;

ALTER TABLE `mission_photos`
    ADD CONSTRAINT `fk_mission_photos_submitted_by_member_id`
        FOREIGN KEY (`submitted_by_member_id`) REFERENCES `trip_members` (`id`),
    ADD CONSTRAINT `uk_mission_photos_idempotency_key`
        UNIQUE (`idempotency_key`),
    ADD CONSTRAINT `uk_mission_photos_participation_active`
        UNIQUE (`mission_participation_id`, `active_slot`),
    ADD CONSTRAINT `uk_mission_photos_mission_representative`
        UNIQUE (`mission_id`, `representative_slot`);

ALTER TABLE `photo_evaluations`
    ADD COLUMN `execution_status` VARCHAR(20) NOT NULL
        COMMENT 'RUNNING, SUCCEEDED, FAILED'
        AFTER `attempt_no`,
    ADD COLUMN `reason` VARCHAR(40) NULL
        COMMENT 'LOCATION_MISMATCH'
        AFTER `verdict`,
    ADD COLUMN `retry_hint` VARCHAR(1000) NULL
        COMMENT 'RETRY 판정 재촬영 안내'
        AFTER `landmark_confidence`,
    ADD COLUMN `requested_at` DATETIME(6) NOT NULL
        COMMENT 'AI 판정 요청 시각'
        AFTER `error_code`;

ALTER TABLE `photo_evaluations`
    CHANGE COLUMN `verdict` `result` VARCHAR(20) NULL
        COMMENT 'SUCCESS, RETRY, FAIL',
    CHANGE COLUMN `recognized_items_json` `detected_labels_json` JSON NULL
        COMMENT '사진에서 감지한 항목과 미션 관련 여부',
    MODIFY COLUMN `evaluated_at` DATETIME(6) NULL,
    DROP COLUMN `landmark_name`,
    ADD CONSTRAINT `uk_photo_evaluations_photo_attempt`
        UNIQUE (`mission_photo_id`, `attempt_no`);
