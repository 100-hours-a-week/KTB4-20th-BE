-- Align remaining internal BIGINT primary keys with the approved identifier policy.
-- Existing seed rows keep their explicit IDs; subsequent inserts use the next generated value.
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
