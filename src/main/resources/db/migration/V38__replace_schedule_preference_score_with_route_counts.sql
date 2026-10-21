ALTER TABLE `schedules`
    DROP COLUMN `preference_score`,
    ADD COLUMN `preference_count` INT NULL
        COMMENT 'AI 추천 경로의 취향 장소 수. SHORTEST와 기존 일정은 NULL'
        AFTER `active_confirmed_slot`,
    ADD COLUMN `discovery_count` INT NULL
        COMMENT 'AI 추천 경로의 발견 장소 수. SHORTEST와 기존 일정은 NULL'
        AFTER `preference_count`;

UPDATE `schedules`
SET `strategy` = 'DIVERSITY',
    `summary` = '새로운 장소를 다양하게 발견하는 일정'
WHERE `strategy` = 'BALANCED';
