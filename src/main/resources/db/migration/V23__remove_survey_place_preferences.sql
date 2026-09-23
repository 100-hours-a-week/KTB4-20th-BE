-- PlanIt Flyway migration: V23 remove deleted survey place preference feature

DROP TABLE `survey_place_preferences`;

ALTER TABLE `schedule_generation_snapshots`
    ADD COLUMN `excluded_categories_snapshot` JSON NULL
    COMMENT '작업 시작 시점 제외 카테고리 불변본'
    AFTER `answers_snapshot`;

UPDATE `schedule_generation_snapshots`
SET `excluded_categories_snapshot` = JSON_ARRAY()
WHERE `excluded_categories_snapshot` IS NULL;

ALTER TABLE `schedule_generation_snapshots`
    MODIFY COLUMN `excluded_categories_snapshot` JSON NOT NULL
    COMMENT '작업 시작 시점 제외 카테고리 불변본',
    DROP COLUMN `place_preferences_snapshot`;
