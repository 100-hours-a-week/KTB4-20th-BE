-- LEGACY: 현행 기준이 아니며 erd/planit_consolidated.sql을 사용한다.
-- KTB 20th V3 ERD - Slice 2: survey, AI schedule, weather replan
-- Source boundary:
--   1. FIGMA_FINAL_BUSINESS_RULES.md
--   2. Figma final V3 screens
-- Target: MySQL 8.x
-- Depends on Slice 1 tables: trips, trip_members, places

CREATE TABLE preference_categories (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '취향 카테고리 ID',
    code VARCHAR(50) NOT NULL COMMENT '카테고리 코드',
    name VARCHAR(100) NOT NULL COMMENT '화면 표시명',
    display_order SMALLINT UNSIGNED NOT NULL COMMENT '설문 표시 순서',
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_preference_categories_code (code),
    UNIQUE KEY uk_preference_categories_order (display_order),
    CONSTRAINT chk_preference_categories_order
        CHECK (display_order >= 1)
) ENGINE=InnoDB COMMENT='5점 척도 취향 카테고리';

CREATE TABLE surveys (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '취향 조사 ID',
    trip_member_id BIGINT UNSIGNED NOT NULL COMMENT '여행방 멤버십 ID',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT, SUBMITTED',
    revision INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '제출 완료 때 증가하는 최종 결과 버전',
    submitted_at DATETIME(6) NULL COMMENT '마지막 제출 완료 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_surveys_trip_member (trip_member_id),
    CONSTRAINT chk_surveys_status
        CHECK (status IN ('DRAFT', 'SUBMITTED')),
    CONSTRAINT chk_surveys_submission
        CHECK ((status = 'DRAFT' AND submitted_at IS NULL)
            OR (status = 'SUBMITTED' AND revision >= 1 AND submitted_at IS NOT NULL)),
    CONSTRAINT fk_surveys_trip_member
        FOREIGN KEY (trip_member_id) REFERENCES trip_members (id)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='사용자와 여행 조합의 현재 최종 설문';

CREATE TABLE survey_answers (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '설문 답변 ID',
    survey_id BIGINT UNSIGNED NOT NULL COMMENT '취향 조사 ID',
    preference_category_id BIGINT UNSIGNED NOT NULL COMMENT '취향 카테고리 ID',
    score TINYINT UNSIGNED NOT NULL DEFAULT 3 COMMENT '1~5점, 초기 중립값 3',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_survey_answers_category (survey_id, preference_category_id),
    CONSTRAINT chk_survey_answers_score
        CHECK (score BETWEEN 1 AND 5),
    CONSTRAINT fk_survey_answers_survey
        FOREIGN KEY (survey_id) REFERENCES surveys (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_survey_answers_category
        FOREIGN KEY (preference_category_id) REFERENCES preference_categories (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='카테고리별 5점 척도 응답';

CREATE TABLE survey_place_preferences (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '설문 장소 선호 ID',
    survey_id BIGINT UNSIGNED NOT NULL COMMENT '취향 조사 ID',
    place_id BIGINT UNSIGNED NOT NULL COMMENT '카카오 장소 ID',
    preference_type VARCHAR(20) NOT NULL DEFAULT 'WANT_VISIT' COMMENT 'WANT_VISIT, MUST_VISIT, EXCLUDE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_survey_place_preference (survey_id, place_id),
    CONSTRAINT chk_survey_place_preference_type
        CHECK (preference_type IN ('WANT_VISIT', 'MUST_VISIT', 'EXCLUDE')),
    CONSTRAINT fk_survey_place_preferences_survey
        FOREIGN KEY (survey_id) REFERENCES surveys (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_survey_place_preferences_place
        FOREIGN KEY (place_id) REFERENCES places (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='희망, 필수 방문, 제외 장소';

CREATE TABLE schedule_generation_jobs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'AI 일정 생성 작업 ID',
    trip_id BIGINT UNSIGNED NOT NULL COMMENT '여행방 ID',
    requested_by_member_id BIGINT UNSIGNED NOT NULL COMMENT '생성을 요청한 방장 멤버십 ID',
    job_type VARCHAR(30) NOT NULL DEFAULT 'INITIAL' COMMENT 'INITIAL, WEATHER_REPLAN',
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED' COMMENT 'QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED',
    stage VARCHAR(30) NOT NULL DEFAULT 'GATHERING_PREFERENCES' COMMENT '화면에 표시할 생성 단계',
    active_slot TINYINT UNSIGNED NULL COMMENT '활성 작업일 때만 1, 여행별 동시 작업 중복 방지',
    idempotency_key VARCHAR(100) NOT NULL COMMENT '중복 요청 방지 식별값',
    input_cutoff_at DATETIME(6) NOT NULL COMMENT '설문 입력 스냅샷 기준 시각',
    attempt_count TINYINT UNSIGNED NOT NULL DEFAULT 0,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    error_code VARCHAR(100) NULL COMMENT '사용자에게 노출하지 않는 내부 오류 코드',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_generation_idempotency (idempotency_key),
    UNIQUE KEY uk_schedule_generation_one_active (trip_id, active_slot),
    KEY idx_schedule_generation_trip_created (trip_id, created_at),
    CONSTRAINT chk_schedule_generation_type
        CHECK (job_type IN ('INITIAL', 'WEATHER_REPLAN')),
    CONSTRAINT chk_schedule_generation_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_schedule_generation_stage
        CHECK (stage IN ('GATHERING_PREFERENCES', 'FINDING_PLACES', 'OPTIMIZING_ROUTE', 'COMPLETED')),
    CONSTRAINT chk_schedule_generation_active_slot
        CHECK ((status IN ('QUEUED', 'RUNNING') AND active_slot = 1)
            OR (status IN ('SUCCEEDED', 'FAILED', 'CANCELLED') AND active_slot IS NULL)),
    CONSTRAINT fk_schedule_generation_trip
        FOREIGN KEY (trip_id) REFERENCES trips (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_schedule_generation_requester
        FOREIGN KEY (requested_by_member_id) REFERENCES trip_members (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='최초 또는 날씨 대체 AI 일정 생성 작업';

CREATE TABLE schedule_generation_snapshots (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '생성 입력 설문 스냅샷 ID',
    generation_job_id BIGINT UNSIGNED NOT NULL COMMENT 'AI 일정 생성 작업 ID',
    trip_member_id BIGINT UNSIGNED NOT NULL COMMENT '입력에 포함한 활성 멤버십 ID',
    survey_id BIGINT UNSIGNED NOT NULL COMMENT '참조한 설문 ID',
    survey_revision INT UNSIGNED NOT NULL COMMENT '작업 시작 시점 설문 버전',
    answers_snapshot JSON NOT NULL COMMENT '작업 시작 시점 카테고리 응답 불변본',
    place_preferences_snapshot JSON NOT NULL COMMENT '작업 시작 시점 장소 선호 불변본',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_snapshot_member (generation_job_id, trip_member_id),
    CONSTRAINT chk_schedule_snapshot_revision
        CHECK (survey_revision >= 1),
    CONSTRAINT fk_schedule_snapshot_job
        FOREIGN KEY (generation_job_id) REFERENCES schedule_generation_jobs (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_schedule_snapshot_member
        FOREIGN KEY (trip_member_id) REFERENCES trip_members (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_schedule_snapshot_survey
        FOREIGN KEY (survey_id) REFERENCES surveys (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='진행 중 작업에서 변경되지 않는 설문 입력';

CREATE TABLE schedule_versions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '일정 버전 ID',
    trip_id BIGINT UNSIGNED NOT NULL COMMENT '여행방 ID',
    generation_job_id BIGINT UNSIGNED NULL COMMENT '생성한 AI 작업 ID',
    source_schedule_version_id BIGINT UNSIGNED NULL COMMENT '수정 또는 날씨 대체 전 일정 버전 ID',
    confirmed_by_member_id BIGINT UNSIGNED NULL COMMENT '일정을 확정한 방장 멤버십 ID',
    version_no INT UNSIGNED NOT NULL COMMENT '여행방 내 일정 버전 번호',
    version_type VARCHAR(20) NOT NULL COMMENT 'CANDIDATE, CONFIRMED',
    strategy VARCHAR(30) NOT NULL COMMENT 'SHORTEST, PREFERENCE, BALANCED, WEATHER_ALTERNATIVE, MANUAL_EDIT',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT, ACTIVE, SUPERSEDED',
    active_confirmed_slot TINYINT UNSIGNED NULL COMMENT '현재 확정 일정일 때만 1',
    summary VARCHAR(500) NULL COMMENT '후보 일정 요약',
    confirmed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_versions_trip_version (trip_id, version_no),
    UNIQUE KEY uk_schedule_versions_one_active (trip_id, active_confirmed_slot),
    KEY idx_schedule_versions_job_strategy (generation_job_id, strategy),
    CONSTRAINT chk_schedule_versions_type
        CHECK (version_type IN ('CANDIDATE', 'CONFIRMED')),
    CONSTRAINT chk_schedule_versions_strategy
        CHECK (strategy IN ('SHORTEST', 'PREFERENCE', 'BALANCED', 'WEATHER_ALTERNATIVE', 'MANUAL_EDIT')),
    CONSTRAINT chk_schedule_versions_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'SUPERSEDED')),
    CONSTRAINT chk_schedule_versions_active
        CHECK ((version_type = 'CONFIRMED' AND status = 'ACTIVE' AND active_confirmed_slot = 1
                AND confirmed_by_member_id IS NOT NULL AND confirmed_at IS NOT NULL)
            OR (NOT (version_type = 'CONFIRMED' AND status = 'ACTIVE') AND active_confirmed_slot IS NULL)),
    CONSTRAINT fk_schedule_versions_trip
        FOREIGN KEY (trip_id) REFERENCES trips (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_schedule_versions_generation_job
        FOREIGN KEY (generation_job_id) REFERENCES schedule_generation_jobs (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_schedule_versions_source
        FOREIGN KEY (source_schedule_version_id) REFERENCES schedule_versions (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_schedule_versions_confirmer
        FOREIGN KEY (confirmed_by_member_id) REFERENCES trip_members (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='세 후보와 방장이 확정한 일정의 버전';

CREATE TABLE schedule_days (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '일정 일차 ID',
    schedule_version_id BIGINT UNSIGNED NOT NULL COMMENT '일정 버전 ID',
    day_number TINYINT UNSIGNED NOT NULL COMMENT 'Day 1부터 시작',
    schedule_date DATE NOT NULL COMMENT '실제 여행 날짜',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_days_number (schedule_version_id, day_number),
    UNIQUE KEY uk_schedule_days_date (schedule_version_id, schedule_date),
    CONSTRAINT chk_schedule_days_number
        CHECK (day_number BETWEEN 1 AND 10),
    CONSTRAINT fk_schedule_days_version
        FOREIGN KEY (schedule_version_id) REFERENCES schedule_versions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='일정 버전의 Day 단위';

CREATE TABLE schedule_stops (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '일정 방문 장소 ID',
    schedule_day_id BIGINT UNSIGNED NOT NULL COMMENT '일정 일차 ID',
    place_id BIGINT UNSIGNED NOT NULL COMMENT '장소 ID',
    stop_order SMALLINT UNSIGNED NOT NULL COMMENT 'Day 내 방문 순서',
    arrival_time TIME NULL,
    departure_time TIME NULL,
    stay_minutes SMALLINT UNSIGNED NULL COMMENT '예상 체류 시간',
    place_name_snapshot VARCHAR(200) NOT NULL COMMENT '일정 확정 시점 장소명',
    address_snapshot VARCHAR(255) NULL COMMENT '일정 확정 시점 주소',
    is_required TINYINT(1) NOT NULL DEFAULT 0 COMMENT '필수 방문 장소 여부',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, REMOVED',
    removed_at DATETIME(6) NULL COMMENT '방장 수정으로 제거된 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_stops_order (schedule_day_id, stop_order),
    KEY idx_schedule_stops_place (place_id),
    CONSTRAINT chk_schedule_stops_order
        CHECK (stop_order >= 1),
    CONSTRAINT chk_schedule_stops_status
        CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT chk_schedule_stops_removed
        CHECK ((status = 'ACTIVE' AND removed_at IS NULL)
            OR (status = 'REMOVED' AND removed_at IS NOT NULL)),
    CONSTRAINT fk_schedule_stops_day
        FOREIGN KEY (schedule_day_id) REFERENCES schedule_days (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_schedule_stops_place
        FOREIGN KEY (place_id) REFERENCES places (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='일정의 순서가 있는 방문 장소';

CREATE TABLE schedule_legs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '장소 간 이동 구간 ID',
    schedule_day_id BIGINT UNSIGNED NOT NULL COMMENT '일정 일차 ID',
    from_stop_id BIGINT UNSIGNED NOT NULL COMMENT '출발 방문 장소 ID',
    to_stop_id BIGINT UNSIGNED NOT NULL COMMENT '도착 방문 장소 ID',
    leg_order SMALLINT UNSIGNED NOT NULL COMMENT 'Day 내 이동 구간 순서',
    distance_meters INT UNSIGNED NOT NULL COMMENT '예상 이동거리 m',
    duration_seconds INT UNSIGNED NOT NULL COMMENT '예상 이동시간 초',
    transport_mode VARCHAR(20) NULL COMMENT 'WALK, TRANSIT, CAR 등 외부 경로 결과',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_legs_order (schedule_day_id, leg_order),
    UNIQUE KEY uk_schedule_legs_pair (from_stop_id, to_stop_id),
    CONSTRAINT chk_schedule_legs_order
        CHECK (leg_order >= 1),
    CONSTRAINT chk_schedule_legs_distinct_stops
        CHECK (from_stop_id <> to_stop_id),
    CONSTRAINT fk_schedule_legs_day
        FOREIGN KEY (schedule_day_id) REFERENCES schedule_days (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_schedule_legs_from_stop
        FOREIGN KEY (from_stop_id) REFERENCES schedule_stops (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_schedule_legs_to_stop
        FOREIGN KEY (to_stop_id) REFERENCES schedule_stops (id)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='방문 장소 사이 거리와 예상 이동시간';

CREATE TABLE weather_checks (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '여행 D-1 날씨 조회 ID',
    trip_id BIGINT UNSIGNED NOT NULL COMMENT '여행방 ID',
    scheduled_check_at DATETIME(6) NOT NULL COMMENT 'D-1 오전 10시 예약 시각',
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' COMMENT 'SCHEDULED, SUCCEEDED, FAILED',
    weather_condition VARCHAR(30) NULL COMMENT '여행 기간 전체의 대표 날씨 조건',
    checked_at DATETIME(6) NULL,
    error_code VARCHAR(100) NULL COMMENT '날씨 조회 실패 내부 코드',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_weather_checks_trip (trip_id),
    KEY idx_weather_checks_schedule (status, scheduled_check_at),
    CONSTRAINT chk_weather_checks_status
        CHECK (status IN ('SCHEDULED', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_weather_checks_condition
        CHECK (weather_condition IS NULL OR weather_condition IN
            ('NORMAL', 'RAIN', 'HEAVY_RAIN', 'HEAT', 'COLD', 'FINE_DUST', 'TYPHOON', 'HEAVY_SNOW', 'YELLOW_DUST')),
    CONSTRAINT fk_weather_checks_trip
        FOREIGN KEY (trip_id) REFERENCES trips (id)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='여행 전날 날씨 조회와 장애 이력';

CREATE TABLE weather_schedule_decisions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '날씨 기반 일정 결정 ID',
    weather_check_id BIGINT UNSIGNED NOT NULL COMMENT '날씨 조회 ID',
    decided_by_member_id BIGINT UNSIGNED NOT NULL COMMENT '결정한 방장 멤버십 ID',
    replacement_generation_job_id BIGINT UNSIGNED NULL COMMENT '변경 선택으로 시작한 대체 일정 작업 ID',
    decision VARCHAR(20) NOT NULL COMMENT 'KEEP, REGENERATE',
    decided_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_weather_schedule_decision (weather_check_id),
    UNIQUE KEY uk_weather_replacement_job (replacement_generation_job_id),
    CONSTRAINT chk_weather_schedule_decision
        CHECK (decision IN ('KEEP', 'REGENERATE')),
    CONSTRAINT chk_weather_schedule_replacement
        CHECK ((decision = 'KEEP' AND replacement_generation_job_id IS NULL)
            OR (decision = 'REGENERATE' AND replacement_generation_job_id IS NOT NULL)),
    CONSTRAINT fk_weather_decision_check
        FOREIGN KEY (weather_check_id) REFERENCES weather_checks (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_weather_decision_member
        FOREIGN KEY (decided_by_member_id) REFERENCES trip_members (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_weather_decision_replacement_job
        FOREIGN KEY (replacement_generation_job_id) REFERENCES schedule_generation_jobs (id)
        ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='방장의 기존 일정 유지 또는 날씨 대체 일정 결정';
