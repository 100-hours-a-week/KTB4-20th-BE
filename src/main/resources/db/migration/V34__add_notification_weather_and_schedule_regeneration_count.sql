-- PlanIt Flyway migration: V34 add notification weather condition and schedule regeneration count

ALTER TABLE `notifications`
    ADD COLUMN `weather_condition` VARCHAR(30) NULL
        COMMENT '이상 기후 알림의 대표 날씨 조건' AFTER `notification_type`;

ALTER TABLE `trips`
    ADD COLUMN `schedule_regeneration_count` TINYINT NOT NULL DEFAULT 0
        COMMENT '날씨 사유를 제외한 일정 재생성 성공 횟수';
