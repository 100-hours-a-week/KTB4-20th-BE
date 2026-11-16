-- AI 작업 비동기 처리를 위한 확장 단계 마이그레이션.
-- 기존 동기 코드와 함께 동작해야 하므로 테이블, 상태 값, 제약조건, 인덱스를 추가만 한다.

CREATE TABLE `message_outbox` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '메시지 발행 Outbox ID',
    `job_type`              VARCHAR(40)  NOT NULL COMMENT 'SCHEDULE_GENERATE, PHOTO_MISSION_GENERATE, PHOTO_JUDGE',
    `job_id`                BIGINT       NOT NULL COMMENT 'job_type별 작업 테이블의 ID',
    `message_id`            BINARY(16)   NOT NULL COMMENT '메시지 ID(중복 확인용)',
    `exchange_name`         VARCHAR(100) NOT NULL COMMENT '발행할 exchange',
    `routing_key`           VARCHAR(100) NOT NULL COMMENT '라우팅 키',
    `payload`               JSON         NOT NULL COMMENT '메시지 본문',
    `status`                VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, PUBLISHED, FAILED',
    `nack_attempt_count`    TINYINT      NOT NULL DEFAULT 0 COMMENT 'NACK 재시도 횟수',
    `failure_attempt_count` TINYINT      NOT NULL DEFAULT 0 COMMENT 'confirm 유실·연결 예외 재시도 횟수',
    `next_attempt_at`       DATETIME(6)  NOT NULL COMMENT '다음 발행 시도 시각(선점 lease 겸용)',
    `last_error`            VARCHAR(500) NULL COMMENT '마지막 발행 실패 이유',
    `created_at`            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `published_at`          DATETIME(6)  NULL COMMENT '발행(confirm) 완료 시각',
    CONSTRAINT `PK_MESSAGE_OUTBOX` PRIMARY KEY (`id`),
    CONSTRAINT `uk_message_outbox_message_id` UNIQUE (`message_id`)
);

-- Relay가 발행 대상(PENDING, next_attempt_at 경과)을 찾을 때 사용
CREATE INDEX `idx_message_outbox_poll`
    ON `message_outbox` (`status`, `next_attempt_at`);

-- Stale Job Checker가 작업의 Outbox 행을 찾을 때 사용
CREATE INDEX `idx_message_outbox_job`
    ON `message_outbox` (`job_type`, `job_id`);

-- schedule_generation_jobs는 V5에서 만들어졌으나 아직 사용하지 않았다.
-- 비동기 일정 생성에서 쓰도록 값 범위를 맞추고, 중복 방지 제약과 Stale 조회 인덱스를 추가한다.
ALTER TABLE `schedule_generation_jobs`
    MODIFY COLUMN `job_type` VARCHAR(30) NOT NULL DEFAULT 'INITIAL'
        COMMENT 'INITIAL, GENERAL_REGENERATION, WEATHER_REGENERATION',
    MODIFY COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
        COMMENT 'QUEUED, SUCCEEDED, FAILED',
    MODIFY COLUMN `updated_at` DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);

-- 여행별 진행 중인 작업은 하나만 허용한다. 끝난 작업은 active_slot이 NULL이라 여러 개 남을 수 있다.
ALTER TABLE `schedule_generation_jobs`
    ADD CONSTRAINT `uk_schedule_generation_jobs_active`
        UNIQUE (`trip_id`, `active_slot`),
    ADD CONSTRAINT `uk_schedule_generation_jobs_idempotency`
        UNIQUE (`idempotency_key`);

-- Stale Job Checker가 기준 시간을 넘긴 QUEUED 작업을 찾을 때 사용
CREATE INDEX `idx_schedule_generation_jobs_stale`
    ON `schedule_generation_jobs` (`status`, `created_at`);

ALTER TABLE `mission_generation_jobs`
    MODIFY COLUMN `status` VARCHAR(20) NOT NULL
        COMMENT 'QUEUED, RUNNING, SUCCEEDED, FAILED';

-- Stale Job Checker가 06:00 이전에 접수된 QUEUED 작업을 찾을 때 사용
CREATE INDEX `idx_mission_generation_jobs_stale`
    ON `mission_generation_jobs` (`status`, `last_attempted_at`);
