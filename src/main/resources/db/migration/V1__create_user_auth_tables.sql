-- PlanIt Flyway migration: V1  create user auth tables

CREATE TABLE `image_files` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '이미지 파일 ID',
	`image_key`	VARCHAR(1024)	NULL	COMMENT '원본 이미지 객체 저장소 키',
	`thumbnail_key`	VARCHAR(1024)	NULL	COMMENT '썸네일 이미지 객체 저장소 키',
	`image_purpose`	VARCHAR(20)	NULL,
	`original_filename`	VARCHAR(255)	NULL	COMMENT '사용자가 업로드한 원본 파일명',
	`mime_type`	VARCHAR(100)	NULL	COMMENT '이미지 MIME 타입',
	`size_bytes`	INT	NULL	COMMENT '원본 이미지 파일 크기(byte)',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)	COMMENT '이미지 메타데이터 생성 시각',
	`deleted_at`	DATETIME(6)	NULL	COMMENT '논리 삭제 시각',
	`storage_deleted_at`	DATETIME(6)	NULL	COMMENT '객체 저장소에서 실제 파일이 삭제된 시각',
	CONSTRAINT `PK_IMAGE_FILES` PRIMARY KEY (
		`id`
	)
);


CREATE TABLE `users` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '사용자 ID',
	`image_file_id`	BIGINT	NOT NULL	COMMENT '이미지 파일 ID',
	`public_id`	BINARY(16)	NULL,
	`username`	VARCHAR(20)	NULL,
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`deleted_at`	DATETIME(6)	NULL	COMMENT '회원 탈퇴 시각',
	`age_range`	VARCHAR(10)	NULL	COMMENT '연령대',
	`birth_year`	VARCHAR(10)	NULL,
	`birth_day`	VARCHAR(10)	NULL,
	`birthday_type`	VARCHAR(10)	NULL,
	`gender`	VARCHAR(10)	NULL,
	CONSTRAINT `PK_USERS` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `users`
    ADD CONSTRAINT `fk_users_image_file_id`
    FOREIGN KEY (`image_file_id`) REFERENCES `image_files` (`id`);

CREATE TABLE `oauth_accounts` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT,
	`user_id`	BIGINT	NOT NULL	COMMENT '사용자 ID',
	`provider`	VARCHAR(20)	NULL,
	`provider_user_id`	VARCHAR(255)	NULL,
	`created_at`	DATETIME(6)	NULL,
	CONSTRAINT `PK_OAUTH_ACCOUNTS` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `oauth_accounts`
    ADD CONSTRAINT `fk_oauth_accounts_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

CREATE TABLE `refresh_tokens` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT 'Refresh Token 이력 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '사용자 ID',
	`token_hash`	CHAR(64)	NOT NULL	COMMENT '원문 대신 저장하는 토큰 해시',
	`issued_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`expires_at`	DATETIME(6)	NOT NULL,
	`revoked_at`	DATETIME(6)	NULL,
	CONSTRAINT `PK_REFRESH_TOKENS` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `refresh_tokens` ADD CONSTRAINT `UK_REFRESH_TOKENS_TOKEN_HASH` UNIQUE (
	`token_hash`
);

CREATE INDEX `IDX_REFRESH_TOKENS_USER_REVOKED` ON `refresh_tokens` (
	`user_id`,
	`revoked_at`
);

ALTER TABLE `refresh_tokens` ADD CONSTRAINT `FK_USERS_TO_REFRESH_TOKENS` FOREIGN KEY (
	`user_id`
)
REFERENCES `users` (
	`id`
);
