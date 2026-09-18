-- PlanIt Flyway migration: V4  create trip tables

CREATE TABLE `places` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '장소 ID',
	`region_id`	BIGINT	NOT NULL	COMMENT '장소가 속한 하위 지역 ID',
	`google_place_id`	VARCHAR(100)	NOT NULL	COMMENT 'Google Places 장소 식별값',
	`name`	VARCHAR(200)	NOT NULL	COMMENT '장소명',
	`category_name`	VARCHAR(255)	NULL	COMMENT 'Google Places 장소 카테고리',
	`address`	VARCHAR(255)	NULL	COMMENT '지번 주소',
	`road_address`	VARCHAR(255)	NULL	COMMENT '도로명 주소',
	`longitude`	DECIMAL(10, 7)	NOT NULL	COMMENT 'Google Places 장소 경도',
	`latitude`	DECIMAL(10, 7)	NOT NULL	COMMENT 'Google Places 장소 위도',
	`phone`	VARCHAR(50)	NULL,
	`place_url`	VARCHAR(2083)	NULL	COMMENT 'Google Maps 장소 URL',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`updated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	CONSTRAINT `PK_PLACES` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `places`
    ADD CONSTRAINT `fk_places_region_id`
    FOREIGN KEY (`region_id`) REFERENCES `sub_regions` (`id`);

CREATE TABLE `trips` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '여행방 ID',
	`sub_region_id`	BIGINT	NOT NULL	COMMENT '선택한 하위 지역 ID',
	`name`	VARCHAR(12)	NOT NULL	COMMENT '여행방 이름, 최대 12자',
	`start_date`	DATE	NOT NULL	COMMENT '여행 시작일',
	`end_date`	DATE	NOT NULL	COMMENT '여행 종료일',
	`capacity`	TINYINT	NOT NULL	DEFAULT 4	COMMENT '방장 포함 정원, 2~8명',
	`survey_deadline_at`	DATETIME(6)	NOT NULL	COMMENT '취향 조사 마감 시각, 선택일 23:59',
	`deleted_at`	DATETIME(6)	NULL	COMMENT '삭제된 여행방 시각',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	CONSTRAINT `PK_TRIPS` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `trips`
    ADD CONSTRAINT `fk_trips_sub_region_id`
    FOREIGN KEY (`sub_region_id`) REFERENCES `sub_regions` (`id`);

CREATE TABLE `trip_members` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '여행방 멤버십 ID',
	`trip_id`	BIGINT	NOT NULL	COMMENT '여행방 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '사용자 ID',
	`role`	VARCHAR(20)	NOT NULL	DEFAULT 'MEMBER'	COMMENT 'HOST, MEMBER',
	`host_slot`	TINYINT	NULL	COMMENT '활성 방장일 때만 1, 여행별 방장 중복 방지',
	`active_slot`	TINYINT	NOT NULL,
	`joined_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)	COMMENT '참여 순서 기준 시각',
	`left_at`	DATETIME(6)	NULL	COMMENT '나가기 또는 내보내기 시각',
	CONSTRAINT `PK_TRIP_MEMBERS` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `trip_members`
    ADD CONSTRAINT `fk_trip_members_trip_id`
    FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`);

ALTER TABLE `trip_members`
    ADD CONSTRAINT `fk_trip_members_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

CREATE TABLE `trip_invitations` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '초대 링크 ID',
	`trip_id`	BIGINT	NOT NULL	COMMENT '여행방 ID',
	`token_hash`	CHAR(64)	NOT NULL	COMMENT '초대 토큰 원문 대신 저장하는 해시',
	CONSTRAINT `PK_TRIP_INVITATIONS` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `trip_invitations`
    ADD CONSTRAINT `fk_trip_invitations_trip_id`
    FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`);

CREATE TABLE `preference_questions` (
	`id`	BIGINT	NOT NULL	COMMENT '취향 카테고리 ID',
	`code`	VARCHAR(50)	NOT NULL	COMMENT '카테고리 코드',
	`category_code`	VARCHAR(30)	NOT NULL	COMMENT '취향 카테고리 코드',
	`question_text`	VARCHAR(300)	NOT NULL	COMMENT '설문 문항 내용'
);

ALTER TABLE `preference_questions` ADD CONSTRAINT `PK_PREFERENCE_QUESTIONS` PRIMARY KEY (
	`id`
);

CREATE TABLE `surveys` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '취향 조사 ID',
	`trip_member_id`	BIGINT	NOT NULL	COMMENT '여행방 멤버십 ID',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`updated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`submitted_at`	DATETIME(6)	NULL,
	CONSTRAINT `PK_SURVEYS` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `surveys`
    ADD CONSTRAINT `fk_surveys_trip_member_id`
    FOREIGN KEY (`trip_member_id`) REFERENCES `trip_members` (`id`);

CREATE TABLE `survey_answers` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '설문 답변 ID',
	`survey_id`	BIGINT	NOT NULL	COMMENT '취향 조사 ID',
	`preference_question_id`	BIGINT	NOT NULL	COMMENT '취향 카테고리 ID',
	`score`	TINYINT	NOT NULL	DEFAULT 3	COMMENT '1~5점, 초기 중립값 3',
	CONSTRAINT `PK_SURVEY_ANSWERS` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `survey_answers`
    ADD CONSTRAINT `fk_survey_answers_survey_id`
    FOREIGN KEY (`survey_id`) REFERENCES `surveys` (`id`);

ALTER TABLE `survey_answers`
    ADD CONSTRAINT `fk_survey_answers_preference_question_id`
    FOREIGN KEY (`preference_question_id`) REFERENCES `preference_questions` (`id`);

CREATE TABLE `survey_place_preferences` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '설문 장소 선호 ID',
	`survey_id`	BIGINT	NOT NULL	COMMENT '취향 조사 ID',
	`place_id`	BIGINT	NOT NULL	COMMENT 'Google Places 장소 ID',
	CONSTRAINT `PK_SURVEY_PLACE_PREFERENCES` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `survey_place_preferences`
    ADD CONSTRAINT `fk_survey_place_preferences_survey_id`
    FOREIGN KEY (`survey_id`) REFERENCES `surveys` (`id`);

ALTER TABLE `survey_place_preferences`
    ADD CONSTRAINT `fk_survey_place_preferences_place_id`
    FOREIGN KEY (`place_id`) REFERENCES `places` (`id`);
