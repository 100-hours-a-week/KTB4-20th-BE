-- LEGACY: 현행 기준이 아니며 erd/planit_consolidated.sql을 사용한다.
-- ERDCloud compatibility patch for Slice 2 -> Slice 1 relationships.
-- MySQL applies these constraints normally. ERDCloud may ignore references
-- when the referenced table was created in an earlier import batch.

ALTER TABLE surveys
    ADD CONSTRAINT fk_surveys_trip_member
    FOREIGN KEY (trip_member_id) REFERENCES trip_members (id)
    ON DELETE CASCADE ON UPDATE RESTRICT;

ALTER TABLE schedule_generation_jobs
    ADD CONSTRAINT fk_schedule_generation_trip
    FOREIGN KEY (trip_id) REFERENCES trips (id)
    ON DELETE CASCADE ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_schedule_generation_requester
    FOREIGN KEY (requested_by_member_id) REFERENCES trip_members (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE survey_place_preferences
    ADD CONSTRAINT fk_survey_place_preferences_place
    FOREIGN KEY (place_id) REFERENCES places (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE schedule_generation_snapshots
    ADD CONSTRAINT fk_schedule_snapshot_member
    FOREIGN KEY (trip_member_id) REFERENCES trip_members (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE schedule_versions
    ADD CONSTRAINT fk_schedule_versions_trip
    FOREIGN KEY (trip_id) REFERENCES trips (id)
    ON DELETE CASCADE ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_schedule_versions_confirmer
    FOREIGN KEY (confirmed_by_member_id) REFERENCES trip_members (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE schedule_stops
    ADD CONSTRAINT fk_schedule_stops_place
    FOREIGN KEY (place_id) REFERENCES places (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE weather_checks
    ADD CONSTRAINT fk_weather_checks_trip
    FOREIGN KEY (trip_id) REFERENCES trips (id)
    ON DELETE CASCADE ON UPDATE RESTRICT;

ALTER TABLE weather_schedule_decisions
    ADD CONSTRAINT fk_weather_decision_member
    FOREIGN KEY (decided_by_member_id) REFERENCES trip_members (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT;
