-- PlanIt Flyway migration: V21 seed survey questions and exclusions

ALTER TABLE `preference_questions`
    ADD COLUMN `display_order` SMALLINT UNSIGNED NULL
    COMMENT '설문 문항 표시 순서' AFTER `question_text`,
    ADD CONSTRAINT `uk_preference_questions_code` UNIQUE (`code`),
    ADD CONSTRAINT `uk_preference_questions_display_order`
    UNIQUE (`display_order`);

INSERT INTO `preference_questions` (
    `id`, `code`, `category_code`, `question_text`, `display_order`
) VALUES
    (1, 'HISTORY_CULTURE_MUSEUM', 'HISTORY_CULTURE',
     '나는 여행지에서 박물관이나 미술관을 방문하는 것을 좋아한다', 1),
    (2, 'HISTORY_CULTURE_HERITAGE', 'HISTORY_CULTURE',
     '나는 유적지나 오래된 건축물을 둘러보는 데 흥미를 느낀다', 2),
    (3, 'HISTORY_CULTURE_BACKGROUND', 'HISTORY_CULTURE',
     '나는 여행지의 역사적 배경을 알아보는 것을 즐긴다', 3),
    (4, 'NATURE_BEACH', 'NATURE',
     '나는 바다나 해변에서 시간을 보내는 것을 좋아한다', 4),
    (5, 'NATURE_SCENERY', 'NATURE',
     '나는 공원, 산책로, 숲처럼 자연 경관이 있는 곳을 선호한다', 5),
    (6, 'NATURE_VIEWPOINT', 'NATURE',
     '나는 전망대나 뷰 포인트에서 풍경을 보는 것을 즐긴다', 6),
    (7, 'FOOD_RESTAURANT', 'FOOD',
     '나는 여행지의 맛집을 찾아다니는 것을 중요하게 생각한다', 7),
    (8, 'FOOD_CAFE_DESSERT', 'FOOD',
     '나는 카페나 디저트 가게를 방문하는 것을 좋아한다', 8),
    (9, 'FOOD_BAR_PUB', 'FOOD',
     '나는 여행지에서 바(bar)나 펍처럼 술을 즐길 수 있는 곳에 가는 것을 좋아한다', 9),
    (10, 'ACTIVITY_NIGHTLIFE', 'ACTIVITY_ENTERTAINMENT',
     '나는 여행지에서 클럽이나 바처럼 활동적인 밤 문화를 즐긴다', 10),
    (11, 'ACTIVITY_FACILITY', 'ACTIVITY_ENTERTAINMENT',
     '나는 동물원, 수족관, 놀이공원, 수영장 같은 체험형 시설을 방문하는 것을 좋아한다', 11),
    (12, 'ACTIVITY_PHYSICAL', 'ACTIVITY_ENTERTAINMENT',
     '나는 몸을 움직이는 체험형 활동에 참여하는 것을 선호한다', 12),
    (13, 'CONVENIENCE_SHOPPING', 'CONVENIENCE_RELAXATION',
     '나는 쇼핑몰이나 편의시설이 잘 갖춰진 곳을 선호한다', 13),
    (14, 'RELAXATION_SPA_MASSAGE', 'CONVENIENCE_RELAXATION',
     '나는 스파나 마사지처럼 휴식을 위한 서비스를 이용하는 것을 좋아한다', 14),
    (15, 'CONVENIENCE_ACCESSIBILITY', 'CONVENIENCE_RELAXATION',
     '나는 이동이 편리하고 접근성이 좋은 장소를 중요하게 생각한다', 15);

ALTER TABLE `preference_questions`
    MODIFY COLUMN `display_order` SMALLINT UNSIGNED NOT NULL
    COMMENT '설문 문항 표시 순서';

CREATE TABLE `survey_exclusion_categories` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '설문 제외 항목 ID',
    `code` VARCHAR(50) NOT NULL COMMENT '설문 제외 항목 코드',
    `name` VARCHAR(50) NOT NULL COMMENT '설문 제외 항목 표시값',
    CONSTRAINT `PK_SURVEY_EXCLUSION_CATEGORIES` PRIMARY KEY (`id`),
    CONSTRAINT `uk_survey_exclusion_categories_code` UNIQUE (`code`)
) ENGINE=InnoDB COMMENT='사용자가 절대 하고 싶지 않은 것의 사전 목록';

INSERT INTO `survey_exclusion_categories` (`id`, `code`, `name`) VALUES
    (1, 'NOISY_PLACE', '시끄러운_곳'),
    (2, 'OUTDOOR_ACTIVITY', '야외_활동'),
    (3, 'SEAFOOD', '해산물'),
    (4, 'RELIGIOUS_FACILITY', '종교시설'),
    (5, 'ANIMAL_FACILITY', '동물시설'),
    (6, 'HEIGHT_AVERSION', '고소공포');

CREATE TABLE `survey_excluded_categories` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '설문별 제외 항목 ID',
    `survey_id` BIGINT NOT NULL COMMENT '취향 조사 ID',
    `exclusion_category_id` BIGINT NOT NULL COMMENT '설문 제외 항목 ID',
    CONSTRAINT `PK_SURVEY_EXCLUDED_CATEGORIES` PRIMARY KEY (`id`),
    CONSTRAINT `uk_survey_excluded_categories_survey_category`
        UNIQUE (`survey_id`, `exclusion_category_id`),
    CONSTRAINT `fk_survey_excluded_categories_survey_id`
        FOREIGN KEY (`survey_id`) REFERENCES `surveys` (`id`),
    CONSTRAINT `fk_survey_excluded_categories_category_id`
        FOREIGN KEY (`exclusion_category_id`)
        REFERENCES `survey_exclusion_categories` (`id`)
) ENGINE=InnoDB COMMENT='설문에서 선택한 절대 하고 싶지 않은 것';
