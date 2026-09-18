-- PlanIt Flyway migration: V5  create schedule tables

CREATE TABLE `schedule_generation_jobs` (
	`id`	BIGINT	NOT NULL	COMMENT 'AI 일정 생성 작업 ID',
	`trip_id`	BIGINT	NOT NULL	COMMENT '여행방 ID',
	`requested_by_member_id`	BIGINT	NOT NULL	COMMENT '생성을 요청한 방장 멤버십 ID',
	`job_type`	VARCHAR(30)	NOT NULL	DEFAULT 'INITIAL'	COMMENT 'INITIAL, WEATHER_REPLAN',
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'QUEUED'	COMMENT 'QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED',
	`stage`	VARCHAR(30)	NOT NULL	DEFAULT 'GATHERING_PREFERENCES'	COMMENT '화면에 표시할 생성 단계',
	`active_slot`	TINYINT	NULL	COMMENT '활성 작업일 때만 1, 여행별 동시 작업 중복 방지',
	`idempotency_key`	BINARY(16)	NOT NULL	COMMENT '중복 요청 방지 식별값',
	`input_cutoff_at`	DATETIME(6)	NOT NULL	COMMENT '설문 입력 스냅샷 기준 시각',
	`attempt_count`	TINYINT	NOT NULL	DEFAULT 0,
	`started_at`	DATETIME(6)	NULL,
	`finished_at`	DATETIME(6)	NULL,
	`error_code`	VARCHAR(100)	NULL	COMMENT '사용자에게 노출하지 않는 내부 오류 코드',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`updated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

ALTER TABLE `schedule_generation_jobs` ADD CONSTRAINT `PK_SCHEDULE_GENERATION_JOBS` PRIMARY KEY (
	`id`
);

ALTER TABLE `schedule_generation_jobs`
    ADD CONSTRAINT `fk_schedule_generation_jobs_trip_id`
    FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`);

ALTER TABLE `schedule_generation_jobs`
    ADD CONSTRAINT `fk_schedule_generation_jobs_requested_by_member_id`
    FOREIGN KEY (`requested_by_member_id`) REFERENCES `trip_members` (`id`);

CREATE TABLE `schedule_generation_snapshots` (
	`id`	BIGINT	NOT NULL	COMMENT '생성 입력 설문 스냅샷 ID',
	`generation_job_id`	BIGINT	NOT NULL	COMMENT 'AI 일정 생성 작업 ID',
	`survey_id`	BIGINT	NOT NULL	COMMENT '참조한 설문 ID',
	`answers_snapshot`	JSON	NOT NULL	COMMENT '작업 시작 시점 카테고리 응답 불변본',
	`place_preferences_snapshot`	JSON	NOT NULL	COMMENT '작업 시작 시점 장소 선호 불변본',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

ALTER TABLE `schedule_generation_snapshots` ADD CONSTRAINT `PK_SCHEDULE_GENERATION_SNAPSHOTS` PRIMARY KEY (
	`id`
);

ALTER TABLE `schedule_generation_snapshots`
    ADD CONSTRAINT `fk_schedule_generation_snapshots_generation_job_id`
    FOREIGN KEY (`generation_job_id`) REFERENCES `schedule_generation_jobs` (`id`);

ALTER TABLE `schedule_generation_snapshots`
    ADD CONSTRAINT `fk_schedule_generation_snapshots_survey_id`
    FOREIGN KEY (`survey_id`) REFERENCES `surveys` (`id`);

CREATE TABLE `schedules` (
	`id`	BIGINT	NOT NULL	COMMENT '일정 버전 ID',
	`trip_id`	BIGINT	NOT NULL	COMMENT '여행방 ID',
	`generation_job_id`	BIGINT	NULL	COMMENT '생성한 AI 작업 ID',
	`confirmed_by_member_id`	BIGINT	NULL	COMMENT '일정을 확정한 방장 멤버십 ID',
	`strategy`	VARCHAR(30)	NOT NULL	COMMENT 'SHORTEST, PREFERENCE, BALANCED, WEATHER_ALTERNATIVE, MANUAL_EDIT',
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'DRAFT'	COMMENT 'DRAFT, ACTIVE, SUPERSEDED',
	`active_confirmed_slot`	TINYINT	NULL	COMMENT '현재 확정 일정일 때만 1',
	`summary`	VARCHAR(500)	NULL	COMMENT '후보 일정 요약',
	`confirmed_at`	DATETIME(6)	NULL,
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

ALTER TABLE `schedules` ADD CONSTRAINT `PK_SCHEDULES` PRIMARY KEY (
	`id`
);

ALTER TABLE `schedules`
    ADD CONSTRAINT `fk_schedules_trip_id`
    FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`);

ALTER TABLE `schedules`
    ADD CONSTRAINT `fk_schedules_generation_job_id`
    FOREIGN KEY (`generation_job_id`) REFERENCES `schedule_generation_jobs` (`id`);

ALTER TABLE `schedules`
    ADD CONSTRAINT `fk_schedules_confirmed_by_member_id`
    FOREIGN KEY (`confirmed_by_member_id`) REFERENCES `trip_members` (`id`);

CREATE TABLE `schedule_days` (
	`id`	BIGINT	NOT NULL	COMMENT '일정 일차 ID',
	`schedule_id`	BIGINT	NOT NULL	COMMENT '일정 버전 ID',
	`day_number`	TINYINT	NOT NULL	COMMENT 'Day 1부터 시작',
	`schedule_date`	DATE	NOT NULL	COMMENT '실제 여행 날짜'
);

ALTER TABLE `schedule_days` ADD CONSTRAINT `PK_SCHEDULE_DAYS` PRIMARY KEY (
	`id`
);

ALTER TABLE `schedule_days`
    ADD CONSTRAINT `fk_schedule_days_schedule_id`
    FOREIGN KEY (`schedule_id`) REFERENCES `schedules` (`id`);

CREATE TABLE `schedule_visits` (
	`id`	BIGINT	NOT NULL	COMMENT '일정 방문 장소 ID',
	`schedule_day_id`	BIGINT	NOT NULL	COMMENT '일정 일차 ID',
	`place_id`	BIGINT	NOT NULL	COMMENT '장소 ID',
	`visit_order`	SMALLINT	NOT NULL	COMMENT 'Day 내 방문 순서',
	`place_name_snapshot`	VARCHAR(200)	NOT NULL	COMMENT '일정 확정 시점 장소명',
	`address_snapshot`	VARCHAR(255)	NULL	COMMENT '일정 확정 시점 주소',
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT 'ACTIVE, REMOVED',
	`removed_at`	DATETIME(6)	NULL	COMMENT '방장 수정으로 제거된 시각',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`updated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`source`	VARCHAR(20)	NULL,
	`selection_reason`	VARCHAR(500)	NULL
);

ALTER TABLE `schedule_visits` ADD CONSTRAINT `PK_SCHEDULE_VISITS` PRIMARY KEY (
	`id`
);

ALTER TABLE `schedule_visits`
    ADD CONSTRAINT `fk_schedule_visits_schedule_day_id`
    FOREIGN KEY (`schedule_day_id`) REFERENCES `schedule_days` (`id`);

ALTER TABLE `schedule_visits`
    ADD CONSTRAINT `fk_schedule_visits_place_id`
    FOREIGN KEY (`place_id`) REFERENCES `places` (`id`);

CREATE TABLE `schedule_legs` (
	`id`	BIGINT	NOT NULL	COMMENT '장소 간 이동 구간 ID',
	`schedule_day_id`	BIGINT	NOT NULL	COMMENT '일정 일차 ID',
	`from_visit_id`	BIGINT	NOT NULL	COMMENT '출발 방문 장소 ID',
	`to_visit_id`	BIGINT	NOT NULL	COMMENT '도착 방문 장소 ID',
	`leg_order`	SMALLINT	NOT NULL	COMMENT 'Day 내 이동 구간 순서',
	`distance_meters`	INT	NOT NULL	COMMENT '예상 이동거리 m',
	`required_time`	INT	NULL	COMMENT '예상 이동 소요 시간(초)'
);

ALTER TABLE `schedule_legs` ADD CONSTRAINT `PK_SCHEDULE_LEGS` PRIMARY KEY (
	`id`
);

ALTER TABLE `schedule_legs`
    ADD CONSTRAINT `fk_schedule_legs_schedule_day_id`
    FOREIGN KEY (`schedule_day_id`) REFERENCES `schedule_days` (`id`);

ALTER TABLE `schedule_legs`
    ADD CONSTRAINT `fk_schedule_legs_from_visit_id`
    FOREIGN KEY (`from_visit_id`) REFERENCES `schedule_visits` (`id`);

ALTER TABLE `schedule_legs`
    ADD CONSTRAINT `fk_schedule_legs_to_visit_id`
    FOREIGN KEY (`to_visit_id`) REFERENCES `schedule_visits` (`id`);

CREATE TABLE `weather_checks` (
	`id`	BIGINT	NOT NULL	COMMENT '여행 D-1 날씨 조회 ID',
	`trip_id`	BIGINT	NOT NULL	COMMENT '여행방 ID',
	`scheduled_check_at`	DATETIME(6)	NOT NULL	COMMENT 'D-1 오전 10시 예약 시각',
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'SCHEDULED'	COMMENT 'SCHEDULED, SUCCEEDED, FAILED',
	`weather_condition`	VARCHAR(100)	NULL	COMMENT '외부 날씨 조건 코드',
	`checked_at`	DATETIME(6)	NULL,
	`error_code`	VARCHAR(100)	NULL	COMMENT '날씨 조회 실패 내부 코드'
);

ALTER TABLE `weather_checks` ADD CONSTRAINT `PK_WEATHER_CHECKS` PRIMARY KEY (
	`id`
);

ALTER TABLE `weather_checks`
    ADD CONSTRAINT `fk_weather_checks_trip_id`
    FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`);

CREATE TABLE `weather_schedule_decisions` (
	`id`	BIGINT	NOT NULL	COMMENT '날씨 기반 일정 결정 ID',
	`weather_check_id`	BIGINT	NOT NULL	COMMENT '날씨 조회 ID',
	`decided_by_member_id`	BIGINT	NOT NULL	COMMENT '결정한 방장 멤버십 ID',
	`replacement_generation_job_id`	BIGINT	NULL	COMMENT '변경 선택으로 시작한 대체 일정 작업 ID',
	`decision`	VARCHAR(20)	NOT NULL	COMMENT 'KEEP, REGENERATE',
	`decided_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

ALTER TABLE `weather_schedule_decisions` ADD CONSTRAINT `PK_WEATHER_SCHEDULE_DECISIONS` PRIMARY KEY (
	`id`
);

ALTER TABLE `weather_schedule_decisions`
    ADD CONSTRAINT `fk_weather_schedule_decisions_weather_check_id`
    FOREIGN KEY (`weather_check_id`) REFERENCES `weather_checks` (`id`);

ALTER TABLE `weather_schedule_decisions`
    ADD CONSTRAINT `fk_weather_schedule_decisions_decided_by_member_id`
    FOREIGN KEY (`decided_by_member_id`) REFERENCES `trip_members` (`id`);

ALTER TABLE `weather_schedule_decisions`
    ADD CONSTRAINT `fk_weather_schedule_decisions_replacement_generation_job_id`
    FOREIGN KEY (`replacement_generation_job_id`) REFERENCES `schedule_generation_jobs` (`id`);

