-- PlanIt Flyway migration: V22 align survey numeric column types

ALTER TABLE `survey_answers`
    MODIFY COLUMN `score` INT NOT NULL DEFAULT 3
    COMMENT '1~5점, 초기 중립값 3';
