-- LEGACY: 현행 기준이 아니며 erd/planit_consolidated.sql을 사용한다.
-- KTB 20th V3 ERD - Slice 3
-- Domains: regional open chat, community, photo mission, notification
-- Source boundary:
--   1. FIGMA_FINAL_BUSINESS_RULES.md
--   2. Figma final V3 screens
-- Target: MySQL 8.x
-- Depends on Slice 1 and 2 tables.

CREATE TABLE regional_chat_rooms (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '지역 공개 채팅방 ID',
    region_id BIGINT UNSIGNED NOT NULL COMMENT '행정구역 ID',
    name VARCHAR(100) NOT NULL COMMENT '채팅방 표시명',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, INACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_regional_chat_rooms_region (region_id),
    CONSTRAINT chk_regional_chat_rooms_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT fk_regional_chat_rooms_region
        FOREIGN KEY (region_id) REFERENCES regions (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='여행방과 무관한 행정구역별 공개 채팅';

CREATE TABLE chat_policy_versions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '채팅 운영 정책 버전 ID',
    version VARCHAR(30) NOT NULL COMMENT '정책 버전 문자열',
    title VARCHAR(200) NOT NULL,
    content LONGTEXT NOT NULL COMMENT '사용자에게 표시할 정책 본문',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT, ACTIVE, RETIRED',
    effective_at DATETIME(6) NULL COMMENT '정책 적용 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_policy_versions_version (version),
    CONSTRAINT chk_chat_policy_versions_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT chk_chat_policy_versions_effective
        CHECK ((status = 'DRAFT' AND effective_at IS NULL)
            OR (status IN ('ACTIVE', 'RETIRED') AND effective_at IS NOT NULL))
) ENGINE=InnoDB COMMENT='입장 시 동의받는 지역 채팅 운영 정책';

CREATE TABLE chat_policy_consents (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '채팅 정책 동의 ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '사용자 ID',
    chat_policy_version_id BIGINT UNSIGNED NOT NULL COMMENT '동의한 정책 버전 ID',
    consented_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_policy_consents_user_version (user_id, chat_policy_version_id),
    CONSTRAINT fk_chat_policy_consents_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_chat_policy_consents_version
        FOREIGN KEY (chat_policy_version_id) REFERENCES chat_policy_versions (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='사용자별 채팅 정책 동의 이력';

CREATE TABLE chat_messages (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '공개 채팅 메시지 ID',
    regional_chat_room_id BIGINT UNSIGNED NOT NULL COMMENT '지역 공개 채팅방 ID',
    sender_user_id BIGINT UNSIGNED NOT NULL COMMENT '발신 사용자 ID',
    client_message_id VARCHAR(100) NOT NULL COMMENT '클라이언트 중복 전송 방지 ID',
    message_type VARCHAR(20) NOT NULL COMMENT 'TEXT, IMAGE',
    text_content VARCHAR(1000) NULL COMMENT '텍스트 메시지',
    image_storage_key VARCHAR(512) NULL COMMENT '이미지 객체 저장소 키',
    image_mime_type VARCHAR(20) NULL COMMENT 'image/jpeg, image/png',
    image_size_bytes INT UNSIGNED NULL COMMENT '최대 5MB',
    status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE' COMMENT 'VISIBLE, BLOCKED, DELETED',
    blocked_reason VARCHAR(100) NULL COMMENT '반복, 과속, 금지 표현 등 차단 사유',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_messages_client (regional_chat_room_id, sender_user_id, client_message_id),
    KEY idx_chat_messages_room_created (regional_chat_room_id, created_at),
    CONSTRAINT chk_chat_messages_type
        CHECK (message_type IN ('TEXT', 'IMAGE')),
    CONSTRAINT chk_chat_messages_status
        CHECK (status IN ('VISIBLE', 'BLOCKED', 'DELETED')),
    CONSTRAINT chk_chat_messages_content
        CHECK ((message_type = 'TEXT' AND CHAR_LENGTH(TRIM(text_content)) >= 1
                AND image_storage_key IS NULL AND image_mime_type IS NULL AND image_size_bytes IS NULL)
            OR (message_type = 'IMAGE' AND text_content IS NULL AND image_storage_key IS NOT NULL
                AND image_mime_type IN ('image/jpeg', 'image/png')
                AND image_size_bytes BETWEEN 1 AND 5242880)),
    CONSTRAINT fk_chat_messages_room
        FOREIGN KEY (regional_chat_room_id) REFERENCES regional_chat_rooms (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_chat_messages_sender
        FOREIGN KEY (sender_user_id) REFERENCES users (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='지역 공개 채팅의 텍스트 및 이미지 메시지';

CREATE TABLE chat_violations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '채팅 정책 위반 ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '위반 사용자 ID',
    chat_message_id BIGINT UNSIGNED NOT NULL COMMENT '차단된 메시지 ID',
    violation_sequence INT UNSIGNED NOT NULL COMMENT '사용자별 누적 위반 순서',
    reason_code VARCHAR(100) NOT NULL COMMENT '반복, 과속, 욕설·모욕 등',
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_violations_user_sequence (user_id, violation_sequence),
    KEY idx_chat_violations_user_occurred (user_id, occurred_at),
    CONSTRAINT chk_chat_violations_sequence
        CHECK (violation_sequence >= 1),
    CONSTRAINT fk_chat_violations_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_chat_violations_message
        FOREIGN KEY (chat_message_id) REFERENCES chat_messages (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='사용자 단위로 누적하는 채팅 위반';

CREATE TABLE chat_sanctions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '채팅 이용 정지 ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '제재 사용자 ID',
    triggered_violation_id BIGINT UNSIGNED NOT NULL COMMENT '제재를 발생시킨 위반 ID',
    sanction_sequence INT UNSIGNED NOT NULL COMMENT '사용자별 제재 순서',
    sanction_level TINYINT UNSIGNED NOT NULL COMMENT '1~6, 이후에도 6 유지',
    duration_days TINYINT UNSIGNED NOT NULL COMMENT '1, 4, 7, 14, 30, 60일',
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, EXPIRED, LIFTED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_sanctions_user_sequence (user_id, sanction_sequence),
    KEY idx_chat_sanctions_user_status_end (user_id, status, ends_at),
    CONSTRAINT chk_chat_sanctions_level
        CHECK (sanction_level BETWEEN 1 AND 6),
    CONSTRAINT chk_chat_sanctions_duration
        CHECK (duration_days IN (1, 4, 7, 14, 30, 60)),
    CONSTRAINT chk_chat_sanctions_status
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'LIFTED')),
    CONSTRAINT chk_chat_sanctions_period
        CHECK (starts_at < ends_at),
    CONSTRAINT fk_chat_sanctions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_chat_sanctions_violation
        FOREIGN KEY (triggered_violation_id) REFERENCES chat_violations (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='3회 이상 위반부터 적용되는 단계별 채팅 정지';

CREATE TABLE community_posts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '커뮤니티 일정 게시물 ID',
    trip_id BIGINT UNSIGNED NOT NULL COMMENT '완료된 여행 ID',
    schedule_version_id BIGINT UNSIGNED NOT NULL COMMENT '공개된 확정 일정 버전 ID',
    region_id BIGINT UNSIGNED NOT NULL COMMENT '지역 필터용 하위 지역 ID',
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED, HIDDEN',
    like_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    view_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    popularity_score DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '시간 경과를 반영한 인기 점수',
    published_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_community_posts_trip (trip_id),
    KEY idx_community_posts_region_score (region_id, popularity_score, published_at),
    CONSTRAINT chk_community_posts_status
        CHECK (status IN ('PUBLISHED', 'HIDDEN')),
    CONSTRAINT fk_community_posts_trip
        FOREIGN KEY (trip_id) REFERENCES trips (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_community_posts_schedule
        FOREIGN KEY (schedule_version_id) REFERENCES schedule_versions (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_community_posts_region
        FOREIGN KEY (region_id) REFERENCES regions (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='여행 종료 후 자동 공개되는 확정 일정';

CREATE TABLE community_likes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '커뮤니티 좋아요 ID',
    community_post_id BIGINT UNSIGNED NOT NULL COMMENT '게시물 ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '좋아요 사용자 ID',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_community_likes_post_user (community_post_id, user_id),
    CONSTRAINT fk_community_likes_post
        FOREIGN KEY (community_post_id) REFERENCES community_posts (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_community_likes_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='다시 누르면 취소 가능한 사용자별 좋아요';

CREATE TABLE community_view_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '게시물 조회 이벤트 ID',
    community_post_id BIGINT UNSIGNED NOT NULL COMMENT '게시물 ID',
    user_id BIGINT UNSIGNED NULL COMMENT '로그인 사용자 ID',
    idempotency_key VARCHAR(100) NOT NULL COMMENT '동일 요청 중복 증가 방지 키',
    viewed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_community_view_idempotency (idempotency_key),
    KEY idx_community_view_post_time (community_post_id, viewed_at),
    CONSTRAINT fk_community_view_events_post
        FOREIGN KEY (community_post_id) REFERENCES community_posts (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_community_view_events_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='중복 집계 정책 확정 전 요청 멱등성만 보장하는 조회 이벤트';

CREATE TABLE mission_generation_jobs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '일별 포토 미션 생성 작업 ID',
    trip_id BIGINT UNSIGNED NOT NULL COMMENT '여행 ID',
    schedule_day_id BIGINT UNSIGNED NOT NULL COMMENT '오늘 여행 동선 ID',
    weather_check_id BIGINT UNSIGNED NULL COMMENT '생성에 반영한 날씨 조회 ID',
    mission_date DATE NOT NULL COMMENT '미션 대상 Day 날짜',
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED' COMMENT 'QUEUED, RUNNING, SUCCEEDED, FAILED',
    stage VARCHAR(30) NOT NULL DEFAULT 'LOADING_PREFERENCES' COMMENT '화면에 표시할 생성 단계',
    active_slot TINYINT UNSIGNED NULL COMMENT '해당 여행 Day의 활성 작업일 때만 1',
    idempotency_key VARCHAR(100) NOT NULL,
    automatic_attempt_count TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '자동 시도 최대 3회',
    manual_retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '사용자 수동 재시도, 제한 없음',
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    error_code VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_mission_generation_idempotency (idempotency_key),
    UNIQUE KEY uk_mission_generation_one_active (trip_id, mission_date, active_slot),
    KEY idx_mission_generation_trip_date (trip_id, mission_date, created_at),
    CONSTRAINT chk_mission_generation_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_mission_generation_stage
        CHECK (stage IN ('LOADING_PREFERENCES', 'CHECKING_ROUTE', 'CREATING_MISSIONS', 'COMPLETED')),
    CONSTRAINT chk_mission_generation_automatic_attempt
        CHECK (automatic_attempt_count BETWEEN 0 AND 3),
    CONSTRAINT chk_mission_generation_active_slot
        CHECK ((status IN ('QUEUED', 'RUNNING') AND active_slot = 1)
            OR (status IN ('SUCCEEDED', 'FAILED') AND active_slot IS NULL)),
    CONSTRAINT fk_mission_generation_trip
        FOREIGN KEY (trip_id) REFERENCES trips (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_mission_generation_schedule_day
        FOREIGN KEY (schedule_day_id) REFERENCES schedule_days (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_mission_generation_weather
        FOREIGN KEY (weather_check_id) REFERENCES weather_checks (id)
        ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='그룹 취향, 오늘 동선, 날씨 기반 포토 미션 생성';

CREATE TABLE photo_missions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '포토 미션 ID',
    mission_generation_job_id BIGINT UNSIGNED NOT NULL COMMENT '미션 생성 작업 ID',
    trip_id BIGINT UNSIGNED NOT NULL COMMENT '여행 ID',
    schedule_day_id BIGINT UNSIGNED NOT NULL COMMENT '미션 대상 Day ID',
    mission_order SMALLINT UNSIGNED NOT NULL COMMENT 'Day 내 표시 순서',
    mission_scope VARCHAR(20) NOT NULL COMMENT 'PERSONAL, GROUP',
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    conditions_json JSON NOT NULL COMMENT 'AI 판정용 미션 조건',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, CLOSED',
    expires_at DATETIME(6) NOT NULL COMMENT '여행 종료 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_photo_missions_job_order (mission_generation_job_id, mission_order),
    CONSTRAINT chk_photo_missions_scope
        CHECK (mission_scope IN ('PERSONAL', 'GROUP')),
    CONSTRAINT chk_photo_missions_status
        CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT fk_photo_missions_generation_job
        FOREIGN KEY (mission_generation_job_id) REFERENCES mission_generation_jobs (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_photo_missions_trip
        FOREIGN KEY (trip_id) REFERENCES trips (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_photo_missions_schedule_day
        FOREIGN KEY (schedule_day_id) REFERENCES schedule_days (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='개인형 또는 그룹형 일별 포토 미션';

CREATE TABLE mission_participations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '멤버별 미션 참여 ID',
    photo_mission_id BIGINT UNSIGNED NOT NULL COMMENT '포토 미션 ID',
    trip_member_id BIGINT UNSIGNED NOT NULL COMMENT '여행 멤버십 ID',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, COMPLETED',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '사진 삭제·교체 후에도 유지되는 AI 재시도 횟수',
    completion_method VARCHAR(20) NULL COMMENT 'AI, MANUAL',
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_mission_participation_member (photo_mission_id, trip_member_id),
    CONSTRAINT chk_mission_participations_status
        CHECK (status IN ('PENDING', 'COMPLETED')),
    CONSTRAINT chk_mission_participations_completion
        CHECK ((status = 'PENDING' AND completion_method IS NULL AND completed_at IS NULL)
            OR (status = 'COMPLETED' AND completion_method IN ('AI', 'MANUAL') AND completed_at IS NOT NULL)),
    CONSTRAINT fk_mission_participations_mission
        FOREIGN KEY (photo_mission_id) REFERENCES photo_missions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_mission_participations_member
        FOREIGN KEY (trip_member_id) REFERENCES trip_members (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='개인·그룹 미션의 멤버별 참여와 완료 상태';

CREATE TABLE mission_photos (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '포토 미션 제출 사진 ID',
    mission_participation_id BIGINT UNSIGNED NOT NULL COMMENT '멤버별 미션 참여 ID',
    photo_mission_id BIGINT UNSIGNED NOT NULL COMMENT '대표 사진 제약을 위한 미션 ID',
    storage_key VARCHAR(512) NOT NULL COMMENT '원본 사진 객체 저장소 키',
    mime_type VARCHAR(20) NOT NULL COMMENT 'image/jpeg, image/png',
    size_bytes INT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, REPLACED, DELETED',
    active_slot TINYINT UNSIGNED NULL COMMENT '참여자의 현재 사진일 때만 1',
    representative_slot TINYINT UNSIGNED NULL COMMENT '그룹 대표 사진일 때만 1',
    submitted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    replaced_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mission_photos_one_active (mission_participation_id, active_slot),
    UNIQUE KEY uk_mission_photos_one_representative (photo_mission_id, representative_slot),
    CONSTRAINT chk_mission_photos_mime
        CHECK (mime_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT chk_mission_photos_status
        CHECK (status IN ('ACTIVE', 'REPLACED', 'DELETED')),
    CONSTRAINT chk_mission_photos_slots
        CHECK ((status = 'ACTIVE' AND active_slot = 1)
            OR (status IN ('REPLACED', 'DELETED') AND active_slot IS NULL)),
    CONSTRAINT fk_mission_photos_participation
        FOREIGN KEY (mission_participation_id) REFERENCES mission_participations (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_mission_photos_mission
        FOREIGN KEY (photo_mission_id) REFERENCES photo_missions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='교체·삭제 이력을 보존하는 미션 제출 사진';

CREATE TABLE photo_evaluations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '사진 AI 판정 ID',
    mission_photo_id BIGINT UNSIGNED NOT NULL COMMENT '판정 대상 사진 ID',
    attempt_no INT UNSIGNED NOT NULL COMMENT '사진 기준 판정 시도 순서',
    match_score DECIMAL(5, 2) NULL COMMENT '0~100 일치도',
    verdict VARCHAR(20) NOT NULL COMMENT 'SUCCESS, NEAR, FAILED, UNRECOGNIZED',
    recognized_items_json JSON NULL COMMENT '인식 항목과 미션 조건 일치 항목',
    landmark_name VARCHAR(200) NULL COMMENT '가장 높은 신뢰도의 랜드마크',
    landmark_confidence DECIMAL(5, 2) NULL COMMENT '랜드마크 인식 신뢰도',
    error_code VARCHAR(100) NULL,
    evaluated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_photo_evaluations_attempt (mission_photo_id, attempt_no),
    CONSTRAINT chk_photo_evaluations_attempt
        CHECK (attempt_no >= 1),
    CONSTRAINT chk_photo_evaluations_score
        CHECK (match_score IS NULL OR match_score BETWEEN 0 AND 100),
    CONSTRAINT chk_photo_evaluations_landmark_confidence
        CHECK (landmark_confidence IS NULL OR landmark_confidence BETWEEN 0 AND 100),
    CONSTRAINT chk_photo_evaluations_verdict
        CHECK (verdict IN ('SUCCESS', 'NEAR', 'FAILED', 'UNRECOGNIZED')),
    CONSTRAINT fk_photo_evaluations_photo
        FOREIGN KEY (mission_photo_id) REFERENCES mission_photos (id)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='80·40% 기준의 미션 판정과 독립된 랜드마크 결과';

CREATE TABLE notifications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '알림 ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '수신 사용자 ID',
    trip_id BIGINT UNSIGNED NULL COMMENT '관련 여행 ID',
    weather_check_id BIGINT UNSIGNED NULL COMMENT '관련 날씨 조회 ID',
    notification_type VARCHAR(50) NOT NULL COMMENT 'D1, WEATHER_ALERT, WEATHER_FAILED, DECISION_PENDING, SCHEDULE_CHANGED 등',
    title VARCHAR(200) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    target_type VARCHAR(50) NULL COMMENT '이동 대상 종류',
    target_id BIGINT UNSIGNED NULL COMMENT '이동 대상 ID',
    target_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, EXPIRED',
    dedupe_key VARCHAR(191) NOT NULL COMMENT '여행·사용자·알림 유형 중복 방지 키',
    read_at DATETIME(6) NULL,
    target_expired_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notifications_dedupe (dedupe_key),
    KEY idx_notifications_user_created (user_id, created_at),
    KEY idx_notifications_user_unread (user_id, read_at),
    CONSTRAINT chk_notifications_target_status
        CHECK (target_status IN ('ACTIVE', 'EXPIRED')),
    CONSTRAINT chk_notifications_target_expired
        CHECK ((target_status = 'ACTIVE' AND target_expired_at IS NULL)
            OR (target_status = 'EXPIRED' AND target_expired_at IS NOT NULL)),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_notifications_trip
        FOREIGN KEY (trip_id) REFERENCES trips (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_notifications_weather_check
        FOREIGN KEY (weather_check_id) REFERENCES weather_checks (id)
        ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='만료된 이동 대상도 목록과 읽음 상태를 보존하는 알림';
