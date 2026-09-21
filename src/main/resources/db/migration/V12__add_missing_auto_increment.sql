-- Align remaining internal BIGINT primary keys with the approved identifier policy.
-- Existing seed rows keep their explicit IDs; subsequent inserts use the next generated value.
ALTER TABLE `sub_regions`
    DROP FOREIGN KEY `fk_sub_regions_broad_region_id`;

ALTER TABLE `places`
    DROP FOREIGN KEY `fk_places_region_id`;

ALTER TABLE `trips`
    DROP FOREIGN KEY `fk_trips_sub_region_id`;

ALTER TABLE `regional_chat_rooms`
    DROP FOREIGN KEY `fk_regional_chat_rooms_region_id`;

ALTER TABLE `trip_course_community_posts`
    DROP FOREIGN KEY `fk_trip_course_community_posts_region_id`;

ALTER TABLE `survey_answers`
    DROP FOREIGN KEY `fk_survey_answers_preference_question_id`;

ALTER TABLE `regional_chat_room_members`
    DROP FOREIGN KEY `fk_regional_chat_room_members_regional_chat_room_id`;

ALTER TABLE `chat_messages`
    DROP FOREIGN KEY `fk_chat_messages_regional_chat_room_id`;

ALTER TABLE `broad_regions`
    MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '광역 지역 ID';

ALTER TABLE `sub_regions`
    MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '하위 지역 ID';

ALTER TABLE `preference_questions`
    MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '취향 카테고리 ID';

ALTER TABLE `regional_chat_rooms`
    MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '지역 공개 채팅방 ID';

ALTER TABLE `chat_prohibited_terms`
    MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '채팅 금칙어 ID';

ALTER TABLE `sub_regions`
    ADD CONSTRAINT `fk_sub_regions_broad_region_id`
    FOREIGN KEY (`broad_region_id`) REFERENCES `broad_regions` (`id`);

ALTER TABLE `places`
    ADD CONSTRAINT `fk_places_region_id`
    FOREIGN KEY (`region_id`) REFERENCES `sub_regions` (`id`);

ALTER TABLE `trips`
    ADD CONSTRAINT `fk_trips_sub_region_id`
    FOREIGN KEY (`sub_region_id`) REFERENCES `sub_regions` (`id`);

ALTER TABLE `regional_chat_rooms`
    ADD CONSTRAINT `fk_regional_chat_rooms_region_id`
    FOREIGN KEY (`region_id`) REFERENCES `sub_regions` (`id`);

ALTER TABLE `trip_course_community_posts`
    ADD CONSTRAINT `fk_trip_course_community_posts_region_id`
    FOREIGN KEY (`region_id`) REFERENCES `sub_regions` (`id`);

ALTER TABLE `survey_answers`
    ADD CONSTRAINT `fk_survey_answers_preference_question_id`
    FOREIGN KEY (`preference_question_id`) REFERENCES `preference_questions` (`id`);

ALTER TABLE `regional_chat_room_members`
    ADD CONSTRAINT `fk_regional_chat_room_members_regional_chat_room_id`
    FOREIGN KEY (`regional_chat_room_id`) REFERENCES `regional_chat_rooms` (`id`);

ALTER TABLE `chat_messages`
    ADD CONSTRAINT `fk_chat_messages_regional_chat_room_id`
    FOREIGN KEY (`regional_chat_room_id`) REFERENCES `regional_chat_rooms` (`id`);
