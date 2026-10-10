ALTER TABLE `schedules`
    ADD CONSTRAINT `UK_SCHEDULES_TRIP_ACTIVE_CONFIRMED_SLOT`
        UNIQUE (`trip_id`, `active_confirmed_slot`);

ALTER TABLE `schedule_days`
    ADD CONSTRAINT `UK_SCHEDULE_DAYS_SCHEDULE_DAY_NUMBER`
        UNIQUE (`schedule_id`, `day_number`),
    ADD CONSTRAINT `UK_SCHEDULE_DAYS_SCHEDULE_DATE`
        UNIQUE (`schedule_id`, `schedule_date`);
