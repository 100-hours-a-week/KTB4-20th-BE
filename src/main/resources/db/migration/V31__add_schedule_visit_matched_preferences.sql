-- PlanIt Flyway migration: V31 add schedule visit matched preferences

ALTER TABLE `schedule_visits`
    ADD COLUMN `matched_preferences_json` JSON NULL
        COMMENT '장소 선정 시 반영된 그룹 취향'
        AFTER `selection_reason`;
