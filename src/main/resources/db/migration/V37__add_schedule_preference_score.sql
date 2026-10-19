ALTER TABLE `schedules`
    ADD COLUMN `preference_score` INT NULL
        COMMENT 'AI 취향 반영 점수. SHORTEST는 NULL'
        AFTER `active_confirmed_slot`;
