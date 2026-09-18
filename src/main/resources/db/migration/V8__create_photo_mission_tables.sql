-- PlanIt Flyway migration: V8  create photo mission tables

CREATE TABLE `mission_generation_jobs` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '일별 포토 미션 생성 작업 ID',
	`trip_id`	BIGINT	NOT NULL	COMMENT '여행 ID',
	`schedule_day_id`	BIGINT	NOT NULL	COMMENT '오늘 여행 동선 ID',
	`weather_check_id`	BIGINT	NULL	COMMENT '생성에 반영한 날씨 조회 ID',
	`mission_date`	DATE	NOT NULL	COMMENT '미션 대상 Day 날짜',
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'QUEUED'	COMMENT 'QUEUED, RUNNING, SUCCEEDED, FAILED',
	`stage`	VARCHAR(30)	NOT NULL	DEFAULT 'LOADING_PREFERENCES'	COMMENT '화면에 표시할 생성 단계',
	`active_slot`	TINYINT	NULL	COMMENT '해당 여행 Day의 활성 작업일 때만 1',
	`idempotency_key`	BINARY(16)	NOT NULL,
	`automatic_attempt_count`	TINYINT	NOT NULL	DEFAULT 0	COMMENT '자동 시도 최대 3회',
	`manual_retry_count`	INT	NOT NULL	DEFAULT 0	COMMENT '사용자 수동 재시도, 제한 없음',
	`started_at`	DATETIME(6)	NULL,
	`finished_at`	DATETIME(6)	NULL,
	`error_code`	VARCHAR(100)	NULL,
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`updated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	CONSTRAINT `PK_MISSION_GENERATION_JOBS` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `mission_generation_jobs`
    ADD CONSTRAINT `fk_mission_generation_jobs_trip_id`
    FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`);

ALTER TABLE `mission_generation_jobs`
    ADD CONSTRAINT `fk_mission_generation_jobs_schedule_day_id`
    FOREIGN KEY (`schedule_day_id`) REFERENCES `schedule_days` (`id`);

ALTER TABLE `mission_generation_jobs`
    ADD CONSTRAINT `fk_mission_generation_jobs_weather_check_id`
    FOREIGN KEY (`weather_check_id`) REFERENCES `weather_checks` (`id`);

CREATE TABLE `missions` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '포토 미션 ID',
	`mission_generation_job_id`	BIGINT	NOT NULL	COMMENT '미션 생성 작업 ID',
	`trip_id`	BIGINT	NOT NULL	COMMENT '여행 ID',
	`schedule_day_id`	BIGINT	NOT NULL	COMMENT '미션 대상 Day ID',
	`mission_order`	TINYINT UNSIGNED	NOT NULL	COMMENT 'Day 내 표시 순서',
	`mission_scope`	VARCHAR(20)	NOT NULL	COMMENT 'PERSONAL, GROUP',
	`title`	VARCHAR(200)	NOT NULL,
	`description`	VARCHAR(1000)	NOT NULL,
	`conditions_json`	JSON	NOT NULL	COMMENT 'AI 판정용 미션 조건',
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT 'ACTIVE, CLOSED',
	`expires_at`	DATETIME(6)	NOT NULL	COMMENT '여행 종료 시각',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`updated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	CONSTRAINT `PK_MISSIONS` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `missions`
    ADD CONSTRAINT `fk_missions_mission_generation_job_id`
    FOREIGN KEY (`mission_generation_job_id`) REFERENCES `mission_generation_jobs` (`id`);

ALTER TABLE `missions`
    ADD CONSTRAINT `fk_missions_trip_id`
    FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`);

ALTER TABLE `missions`
    ADD CONSTRAINT `fk_missions_schedule_day_id`
    FOREIGN KEY (`schedule_day_id`) REFERENCES `schedule_days` (`id`);

CREATE TABLE `mission_participations` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '멤버별 미션 참여 ID',
	`mission_id`	BIGINT	NOT NULL	COMMENT '포토 미션 ID',
	`trip_member_id`	BIGINT	NOT NULL	COMMENT '여행 멤버십 ID',
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'PENDING'	COMMENT 'PENDING, COMPLETED',
	`retry_count`	TINYINT	NOT NULL	DEFAULT 0	COMMENT '사진 삭제·교체 후에도 유지되는 AI 재시도 횟수',
	`completion_method`	VARCHAR(20)	NULL	COMMENT 'AI, MANUAL',
	`completed_at`	DATETIME(6)	NULL,
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`updated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	CONSTRAINT `PK_MISSION_PARTICIPATIONS` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `mission_participations`
    ADD CONSTRAINT `fk_mission_participations_mission_id`
    FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`);

ALTER TABLE `mission_participations`
    ADD CONSTRAINT `fk_mission_participations_trip_member_id`
    FOREIGN KEY (`trip_member_id`) REFERENCES `trip_members` (`id`);

CREATE TABLE `mission_photos` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '포토 미션 제출 사진 ID',
	`mission_participation_id`	BIGINT	NOT NULL	COMMENT '멤버별 미션 참여 ID',
	`mission_id`	BIGINT	NOT NULL	COMMENT '대표 사진 제약을 위한 미션 ID',
	`image_file_id`	BIGINT	NOT NULL	COMMENT '이미지 파일 ID',
	`active_slot`	TINYINT	NULL	COMMENT '참여자의 현재 사진일 때만 1',
	`representative_slot`	TINYINT	NULL	COMMENT '그룹 대표 사진일 때만 1',
	`submitted_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`replaced_at`	DATETIME(6)	NULL,
	`deleted_at`	DATETIME(6)	NULL,
	CONSTRAINT `PK_MISSION_PHOTOS` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `mission_photos`
    ADD CONSTRAINT `fk_mission_photos_mission_participation_id`
    FOREIGN KEY (`mission_participation_id`) REFERENCES `mission_participations` (`id`);

ALTER TABLE `mission_photos`
    ADD CONSTRAINT `fk_mission_photos_mission_id`
    FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`);

ALTER TABLE `mission_photos`
    ADD CONSTRAINT `fk_mission_photos_image_file_id`
    FOREIGN KEY (`image_file_id`) REFERENCES `image_files` (`id`);

CREATE TABLE `photo_evaluations` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '사진 AI 판정 ID',
	`mission_photo_id`	BIGINT	NOT NULL	COMMENT '판정 대상 사진 ID',
	`attempt_no`	TINYINT	NOT NULL	COMMENT '사진 기준 판정 시도 순서',
	`match_score`	DECIMAL(5, 2)	NULL	COMMENT '0~100 일치도',
	`verdict`	VARCHAR(20)	NOT NULL	COMMENT 'SUCCESS, NEAR, FAILED, UNRECOGNIZED',
	`recognized_items_json`	JSON	NULL	COMMENT '인식 항목과 미션 조건 일치 항목',
	`landmark_name`	VARCHAR(200)	NULL	COMMENT '가장 높은 신뢰도의 랜드마크',
	`landmark_confidence`	DECIMAL(5, 2)	NULL	COMMENT '랜드마크 인식 신뢰도',
	`error_code`	VARCHAR(100)	NULL,
	`evaluated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	CONSTRAINT `PK_PHOTO_EVALUATIONS` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `photo_evaluations`
    ADD CONSTRAINT `fk_photo_evaluations_mission_photo_id`
    FOREIGN KEY (`mission_photo_id`) REFERENCES `mission_photos` (`id`);
