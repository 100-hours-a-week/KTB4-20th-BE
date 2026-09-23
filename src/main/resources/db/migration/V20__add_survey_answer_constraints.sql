-- PlanIt Flyway migration: V20 add survey answer constraints

ALTER TABLE `surveys`
    ADD CONSTRAINT `uk_surveys_trip_member_id`
    UNIQUE (`trip_member_id`);

ALTER TABLE `survey_answers`
    ADD CONSTRAINT `uk_survey_answers_survey_question`
    UNIQUE (`survey_id`, `preference_question_id`),
    ADD CONSTRAINT `chk_survey_answers_score`
    CHECK (`score` BETWEEN 1 AND 5);
