-- PlanIt Flyway migration: V32 create mission generation jobs

CREATE TABLE `mission_generation_jobs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '여행별 포토미션 생성 작업 ID',
    `trip_id` BIGINT NOT NULL
        COMMENT '포토미션 생성 대상 여행 ID',
    `status` VARCHAR(20) NOT NULL
        COMMENT 'RUNNING, SUCCEEDED, FAILED',
    `attempt_count` TINYINT NOT NULL
        COMMENT '생성 시도 횟수',
    `last_attempted_at` DATETIME(6) NOT NULL
        COMMENT '마지막 생성 시도 시각',
    CONSTRAINT `PK_MISSION_GENERATION_JOBS` PRIMARY KEY (`id`),
    CONSTRAINT `uk_mission_generation_jobs_trip`
        UNIQUE (`trip_id`),
    CONSTRAINT `fk_mission_generation_jobs_trip_id`
        FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`)
);
