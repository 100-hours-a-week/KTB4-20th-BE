-- V25 지역 통합으로 같은 지역에 같은 Google 장소가 여러 행일 수 있으므로,
-- 가장 작은 ID로 방문 장소 참조를 합친 뒤 중복 행을 지우고 UNIQUE 제약조건을 추가한다.
CREATE TEMPORARY TABLE `place_duplicate_map` (
    `duplicate_id` BIGINT NOT NULL PRIMARY KEY,
    `keep_id` BIGINT NOT NULL
);

INSERT INTO `place_duplicate_map` (`duplicate_id`, `keep_id`)
SELECT place.id, keeper.keep_id
FROM `places` place
JOIN (
    SELECT `region_id`, `google_place_id`, MIN(`id`) AS keep_id
    FROM `places`
    GROUP BY `region_id`, `google_place_id`
    HAVING COUNT(*) > 1
) keeper
    ON keeper.region_id = place.region_id
    AND keeper.google_place_id = place.google_place_id
WHERE place.id <> keeper.keep_id;

UPDATE `schedule_visits` visit
JOIN `place_duplicate_map` map
    ON map.duplicate_id = visit.place_id
SET visit.place_id = map.keep_id;

DELETE place
FROM `places` place
JOIN `place_duplicate_map` map
    ON map.duplicate_id = place.id;

DROP TEMPORARY TABLE `place_duplicate_map`;

ALTER TABLE `places`
    ADD CONSTRAINT `UK_PLACES_REGION_GOOGLE_PLACE`
        UNIQUE (`region_id`, `google_place_id`);
