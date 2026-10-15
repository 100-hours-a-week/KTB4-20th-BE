-- PlanIt Flyway migration: V33 support scheduled photo mission generation

CREATE TABLE `shedlock` (
    `name` VARCHAR(64) NOT NULL,
    `lock_until` TIMESTAMP(3) NOT NULL,
    `locked_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `locked_by` VARCHAR(255) NOT NULL,
    CONSTRAINT `PK_SHEDLOCK` PRIMARY KEY (`name`)
);

ALTER TABLE `missions`
    ADD CONSTRAINT `uk_missions_schedule_visit`
        UNIQUE (`schedule_visit_id`);
