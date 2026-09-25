-- Consolidate the broad/sub-region hierarchy into the five supported travel regions.
-- Existing user-created data is migrated only when its old region maps to one of
-- the supported regions. The guard fails before DDL changes if unsupported
-- trip/place/community data exists, preventing silent reassignment or deletion.

CREATE TEMPORARY TABLE `region_migration_map` (
    `old_region_id` BIGINT NOT NULL,
    `new_region_id` BIGINT NOT NULL,
    CONSTRAINT `pk_region_migration_map` PRIMARY KEY (`old_region_id`)
);

INSERT INTO `region_migration_map` (`old_region_id`, `new_region_id`)
SELECT
    sr.id,
    CASE
        WHEN br.broad_region_code = 'BR-SEOUL' THEN 1
        WHEN sr.sub_region_code = 'SR-GYEONGBUK-002' THEN 2
        WHEN br.broad_region_code = 'BR-BUSAN' THEN 3
        WHEN sr.sub_region_code = 'SR-JEONBUK-012' THEN 4
        WHEN br.broad_region_code = 'BR-JEJU' THEN 5
    END
FROM `sub_regions` sr
JOIN `broad_regions` br
    ON br.id = sr.broad_region_id
WHERE br.broad_region_code IN ('BR-SEOUL', 'BR-BUSAN', 'BR-JEJU')
   OR sr.sub_region_code IN ('SR-GYEONGBUK-002', 'SR-JEONBUK-012');

CREATE TEMPORARY TABLE `region_migration_guard_counts` (
    `unsupported_reference_count` BIGINT NOT NULL
);

INSERT INTO `region_migration_guard_counts` (`unsupported_reference_count`)
SELECT COUNT(*)
FROM `trips` trip
LEFT JOIN `region_migration_map` map
    ON map.old_region_id = trip.sub_region_id
WHERE map.old_region_id IS NULL;

INSERT INTO `region_migration_guard_counts` (`unsupported_reference_count`)
SELECT COUNT(*)
FROM `places` place
LEFT JOIN `region_migration_map` map
    ON map.old_region_id = place.region_id
WHERE map.old_region_id IS NULL;

INSERT INTO `region_migration_guard_counts` (`unsupported_reference_count`)
SELECT COUNT(*)
FROM `trip_course_community_posts` post
LEFT JOIN `region_migration_map` map
    ON map.old_region_id = post.region_id
WHERE map.old_region_id IS NULL;

CREATE TEMPORARY TABLE `region_migration_guard` (
    `unsupported_reference_count` BIGINT NOT NULL,
    CONSTRAINT `chk_region_migration_guard_zero`
        CHECK (`unsupported_reference_count` = 0)
);

INSERT INTO `region_migration_guard` (`unsupported_reference_count`)
SELECT SUM(`unsupported_reference_count`)
FROM `region_migration_guard_counts`;

DROP TEMPORARY TABLE `region_migration_guard`;
DROP TEMPORARY TABLE `region_migration_guard_counts`;

-- Regional chat is reference-seeded data. Rebuild it from the new five-region
-- catalog and remove memberships/messages tied to the retired 229-room catalog.
DELETE FROM `chat_sanctions`;
DELETE FROM `chat_violations`;
DELETE FROM `text_chat_messages`;
DELETE FROM `image_chat_messages`;
DELETE FROM `chat_messages`;
DELETE FROM `regional_chat_room_members`;
DELETE FROM `regional_chat_rooms`;

ALTER TABLE `places`
    DROP FOREIGN KEY `fk_places_region_id`;

ALTER TABLE `trips`
    DROP FOREIGN KEY `fk_trips_sub_region_id`;

ALTER TABLE `regional_chat_rooms`
    DROP FOREIGN KEY `fk_regional_chat_rooms_region_id`;

ALTER TABLE `trip_course_community_posts`
    DROP FOREIGN KEY `fk_trip_course_community_posts_region_id`;

UPDATE `places` place
JOIN `region_migration_map` map
    ON map.old_region_id = place.region_id
SET place.region_id = map.new_region_id;

UPDATE `trips` trip
JOIN `region_migration_map` map
    ON map.old_region_id = trip.sub_region_id
SET trip.sub_region_id = map.new_region_id;

UPDATE `trip_course_community_posts` post
JOIN `region_migration_map` map
    ON map.old_region_id = post.region_id
SET post.region_id = map.new_region_id;

DROP TABLE `sub_regions`;
DROP TABLE `broad_regions`;

CREATE TABLE `regions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '지역 ID',
    `region_code` VARCHAR(50) NOT NULL COMMENT '서비스 지역 코드',
    `region_name` VARCHAR(50) NOT NULL COMMENT '지역 이름',
    `latitude` DECIMAL(9, 6) NOT NULL COMMENT '지역 중심 위도',
    `longitude` DECIMAL(9, 6) NOT NULL COMMENT '지역 중심 경도',
    CONSTRAINT `PK_REGIONS` PRIMARY KEY (`id`),
    CONSTRAINT `UK_REGIONS_CODE` UNIQUE (`region_code`),
    CONSTRAINT `UK_REGIONS_NAME` UNIQUE (`region_name`)
);

INSERT INTO `regions` (
    `id`,
    `region_code`,
    `region_name`,
    `latitude`,
    `longitude`
) VALUES
    (1, 'REGION-SEOUL', '서울', 37.566500, 126.978000),
    (2, 'REGION-GYEONGJU', '경주', 35.856200, 129.224700),
    (3, 'REGION-BUSAN', '부산', 35.179600, 129.075600),
    (4, 'REGION-JEONJU', '전주', 35.824200, 127.148000),
    (5, 'REGION-JEJU', '제주', 33.499600, 126.531200);

ALTER TABLE `trips`
    CHANGE COLUMN `sub_region_id` `region_id` BIGINT NOT NULL COMMENT '선택한 여행 지역 ID';

ALTER TABLE `places`
    ADD CONSTRAINT `fk_places_region_id`
    FOREIGN KEY (`region_id`) REFERENCES `regions` (`id`);

ALTER TABLE `trips`
    ADD CONSTRAINT `fk_trips_region_id`
    FOREIGN KEY (`region_id`) REFERENCES `regions` (`id`);

ALTER TABLE `regional_chat_rooms`
    ADD CONSTRAINT `fk_regional_chat_rooms_region_id`
    FOREIGN KEY (`region_id`) REFERENCES `regions` (`id`);

ALTER TABLE `trip_course_community_posts`
    ADD CONSTRAINT `fk_trip_course_community_posts_region_id`
    FOREIGN KEY (`region_id`) REFERENCES `regions` (`id`);

INSERT INTO `regional_chat_rooms` (`id`, `region_id`, `name`)
SELECT region.id, region.id, region.region_name
FROM `regions` region
ORDER BY region.id;

DROP TEMPORARY TABLE `region_migration_map`;
