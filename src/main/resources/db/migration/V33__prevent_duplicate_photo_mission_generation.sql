-- PlanIt Flyway migration: V33 prevent duplicate photo mission generation

ALTER TABLE `missions`
    ADD CONSTRAINT `uk_missions_schedule_visit`
        UNIQUE (`schedule_visit_id`);
