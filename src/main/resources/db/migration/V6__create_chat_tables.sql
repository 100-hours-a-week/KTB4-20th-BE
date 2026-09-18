-- PlanIt Flyway migration: V6  create chat tables

CREATE TABLE `regional_chat_rooms` (
	`id`	BIGINT	NOT NULL	COMMENT '지역 공개 채팅방 ID',
	`region_id`	BIGINT	NOT NULL	COMMENT '행정구역 ID',
	`name`	VARCHAR(30)	NOT NULL
);

ALTER TABLE `regional_chat_rooms` ADD CONSTRAINT `PK_REGIONAL_CHAT_ROOMS` PRIMARY KEY (
	`id`
);

ALTER TABLE `regional_chat_rooms`
    ADD CONSTRAINT `fk_regional_chat_rooms_region_id`
    FOREIGN KEY (`region_id`) REFERENCES `sub_regions` (`id`);

CREATE TABLE `regional_chat_room_members` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT,
	`user_id`	BIGINT	NOT NULL	COMMENT '사용자 ID',
	`regional_chat_room_id`	BIGINT	NOT NULL	COMMENT '지역 공개 채팅방 ID',
	`joined_at`	DATETIME(6)	NULL,
	`left_at`	DATETIME(6)	NULL,
	CONSTRAINT `PK_REGIONAL_CHAT_ROOM_MEMBERS` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `regional_chat_room_members`
    ADD CONSTRAINT `fk_regional_chat_room_members_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `regional_chat_room_members`
    ADD CONSTRAINT `fk_regional_chat_room_members_regional_chat_room_id`
    FOREIGN KEY (`regional_chat_room_id`) REFERENCES `regional_chat_rooms` (`id`);

CREATE TABLE `chat_policy_versions` (
	`id`	BIGINT	NOT NULL	COMMENT '채팅 운영 정책 버전 ID',
	`version`	VARCHAR(30)	NOT NULL	COMMENT '정책 버전 문자열',
	`title`	VARCHAR(200)	NOT NULL,
	`content`	LONGTEXT	NOT NULL	COMMENT '사용자에게 표시할 정책 본문',
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'DRAFT'	COMMENT 'DRAFT, ACTIVE, RETIRED',
	`effective_at`	DATETIME(6)	NULL	COMMENT '정책 적용 시각',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`updated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

ALTER TABLE `chat_policy_versions` ADD CONSTRAINT `PK_CHAT_POLICY_VERSIONS` PRIMARY KEY (
	`id`
);

CREATE TABLE `chat_policy_consents` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '채팅 정책 동의 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '사용자 ID',
	`chat_policy_version_id`	BIGINT	NOT NULL	COMMENT '동의한 정책 버전 ID',
	`consented_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	CONSTRAINT `PK_CHAT_POLICY_CONSENTS` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `chat_policy_consents`
    ADD CONSTRAINT `fk_chat_policy_consents_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `chat_policy_consents`
    ADD CONSTRAINT `fk_chat_policy_consents_chat_policy_version_id`
    FOREIGN KEY (`chat_policy_version_id`) REFERENCES `chat_policy_versions` (`id`);

CREATE TABLE `chat_messages` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '공개 채팅 메시지 ID',
	`regional_chat_room_id`	BIGINT	NOT NULL	COMMENT '지역 공개 채팅방 ID',
	`sender_user_id`	BIGINT	NOT NULL	COMMENT '발신 사용자 ID',
	`client_message_id`	BINARY(16)	NOT NULL	COMMENT '클라이언트 중복 전송 방지 ID',
	`message_type`	VARCHAR(20)	NOT NULL	COMMENT 'TEXT, IMAGE',
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'VISIBLE'	COMMENT 'VISIBLE, BLOCKED',
	`blocked_reason`	VARCHAR(100)	NULL	COMMENT '반복, 과속, 금지 표현 등 차단 사유',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	CONSTRAINT `PK_CHAT_MESSAGES` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `chat_messages` ADD CONSTRAINT `UK_CHAT_MESSAGES_SENDER_CLIENT_MESSAGE` UNIQUE (
	`sender_user_id`,
	`client_message_id`
);

ALTER TABLE `chat_messages`
    ADD CONSTRAINT `fk_chat_messages_regional_chat_room_id`
    FOREIGN KEY (`regional_chat_room_id`) REFERENCES `regional_chat_rooms` (`id`);

ALTER TABLE `chat_messages`
    ADD CONSTRAINT `fk_chat_messages_sender_user_id`
    FOREIGN KEY (`sender_user_id`) REFERENCES `users` (`id`);

CREATE TABLE `text_chat_messages` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT,
	`chat_message_id`	BIGINT	NOT NULL	COMMENT '공개 채팅 메시지 ID',
	`text_content`	VARCHAR(1000)	NOT NULL	COMMENT '정규화 후 Unicode 코드 포인트 기준 1~1000자',
	CONSTRAINT `PK_TEXT_CHAT_MESSAGES` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `text_chat_messages`
    ADD CONSTRAINT `fk_text_chat_messages_chat_message_id`
    FOREIGN KEY (`chat_message_id`) REFERENCES `chat_messages` (`id`);

CREATE TABLE `image_chat_messages` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT,
	`image_file_id`	BIGINT	NOT NULL	COMMENT '이미지 파일 ID',
	`chat_message_id`	BIGINT	NOT NULL	COMMENT '공개 채팅 메시지 ID',
	CONSTRAINT `PK_IMAGE_CHAT_MESSAGES` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `image_chat_messages`
    ADD CONSTRAINT `fk_image_chat_messages_image_file_id`
    FOREIGN KEY (`image_file_id`) REFERENCES `image_files` (`id`);

ALTER TABLE `image_chat_messages`
    ADD CONSTRAINT `fk_image_chat_messages_chat_message_id`
    FOREIGN KEY (`chat_message_id`) REFERENCES `chat_messages` (`id`);

CREATE TABLE `chat_prohibited_terms` (
	`id`	BIGINT	NOT NULL	COMMENT '채팅 금칙어 ID',
	`term`	VARCHAR(100)	NOT NULL	COMMENT '운영자가 관리하는 원본 금칙어',
	`normalized_term`	VARCHAR(100)	NOT NULL	COMMENT '메시지 판정에 사용하는 정규화 금칙어',
	`match_type`	VARCHAR(20)	NOT NULL	DEFAULT 'CONTAINS'	COMMENT 'EXACT, CONTAINS',
	`is_active`	BOOLEAN	NOT NULL	DEFAULT TRUE,
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`updated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

ALTER TABLE `chat_prohibited_terms` ADD CONSTRAINT `PK_CHAT_PROHIBITED_TERMS` PRIMARY KEY (
	`id`
);

ALTER TABLE `chat_prohibited_terms` ADD CONSTRAINT `UK_CHAT_PROHIBITED_TERMS_NORMALIZED_TYPE` UNIQUE (
	`normalized_term`,
	`match_type`
);

CREATE TABLE `chat_violations` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '채팅 정책 위반 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '위반 사용자 ID',
	`chat_message_id`	BIGINT	NOT NULL	COMMENT '차단된 메시지 ID',
	`violation_sequence`	INT	NOT NULL	COMMENT '사용자별 누적 위반 순서',
	`reason_code`	VARCHAR(100)	NOT NULL	COMMENT '반복, 과속, 욕설·모욕 등',
	`occurred_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	CONSTRAINT `PK_CHAT_VIOLATIONS` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `chat_violations`
    ADD CONSTRAINT `fk_chat_violations_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `chat_violations`
    ADD CONSTRAINT `fk_chat_violations_chat_message_id`
    FOREIGN KEY (`chat_message_id`) REFERENCES `chat_messages` (`id`);

CREATE TABLE `chat_sanctions` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '채팅 이용 정지 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '제재 사용자 ID',
	`triggered_violation_id`	BIGINT	NOT NULL	COMMENT '제재를 발생시킨 위반 ID',
	`sanction_sequence`	INT	NOT NULL	COMMENT '사용자별 제재 순서',
	`sanction_level`	TINYINT	NOT NULL	COMMENT '1~6, 이후에도 6 유지',
	`duration_days`	TINYINT	NOT NULL	COMMENT '1, 4, 7, 14, 30, 60일',
	`starts_at`	DATETIME(6)	NOT NULL,
	`ends_at`	DATETIME(6)	NOT NULL,
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT 'ACTIVE, EXPIRED',
	`updated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	CONSTRAINT `PK_CHAT_SANCTIONS` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `chat_sanctions`
    ADD CONSTRAINT `fk_chat_sanctions_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `chat_sanctions`
    ADD CONSTRAINT `fk_chat_sanctions_triggered_violation_id`
    FOREIGN KEY (`triggered_violation_id`) REFERENCES `chat_violations` (`id`);
