-- LEGACY: 현행 기준이 아니며 erd/planit_consolidated.sql을 사용한다.
-- KTB 20th V3 ERD - Slice 1
-- Source boundary:
--   1. FIGMA_FINAL_BUSINESS_RULES.md
--   2. Figma final V3 screens
-- Target: MySQL 8.x

CREATE TABLE users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '사용자 ID',
    nickname VARCHAR(50) NOT NULL COMMENT '서비스 표시 이름',
    profile_image_url VARCHAR(2048) NULL COMMENT '프로필 이미지 URL',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, DELETED',
    deleted_at DATETIME(6) NULL COMMENT '회원 탈퇴 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT chk_users_status
        CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT chk_users_deleted_state
        CHECK ((status = 'DELETED' AND deleted_at IS NOT NULL)
            OR (status = 'ACTIVE' AND deleted_at IS NULL))
) ENGINE=InnoDB COMMENT='서비스 사용자';

CREATE TABLE oauth_accounts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'OAuth 계정 ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '사용자 ID',
    provider VARCHAR(20) NOT NULL COMMENT 'OAuth 제공자, 현재 KAKAO',
    provider_user_id VARCHAR(191) NOT NULL COMMENT '제공자 사용자 식별값',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_oauth_provider_user (provider, provider_user_id),
    UNIQUE KEY uk_oauth_user_provider (user_id, provider),
    CONSTRAINT fk_oauth_accounts_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='사용자 OAuth 연결';

CREATE TABLE auth_sessions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '기기별 인증 세션 ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '사용자 ID',
    device_key VARCHAR(128) NOT NULL COMMENT '기기 세션 식별값',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, REVOKED, EXPIRED',
    last_seen_at DATETIME(6) NULL COMMENT '마지막 사용 시각',
    expires_at DATETIME(6) NOT NULL COMMENT '세션 만료 시각',
    revoked_at DATETIME(6) NULL COMMENT '세션 폐기 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_auth_sessions_user_status (user_id, status),
    CONSTRAINT chk_auth_sessions_status
        CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT fk_auth_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='기기별 로그인 세션';

CREATE TABLE refresh_tokens (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Refresh Token 이력 ID',
    auth_session_id BIGINT UNSIGNED NOT NULL COMMENT '인증 세션 ID',
    parent_token_id BIGINT UNSIGNED NULL COMMENT '회전 전 토큰 ID',
    token_hash CHAR(64) NOT NULL COMMENT '원문 대신 저장하는 토큰 해시',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, USED, REVOKED, EXPIRED',
    issued_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL COMMENT '회전에 사용된 시각',
    revoked_at DATETIME(6) NULL COMMENT '토큰 패밀리 폐기 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_hash (token_hash),
    KEY idx_refresh_tokens_session_status (auth_session_id, status),
    CONSTRAINT chk_refresh_tokens_status
        CHECK (status IN ('ACTIVE', 'USED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT fk_refresh_tokens_session
        FOREIGN KEY (auth_session_id) REFERENCES auth_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_refresh_tokens_parent
        FOREIGN KEY (parent_token_id) REFERENCES refresh_tokens (id)
        ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='회전 및 재사용 탐지용 Refresh Token 이력';

CREATE TABLE regions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '지역 ID',
    parent_region_id BIGINT UNSIGNED NULL COMMENT '상위 광역 지역 ID',
    code VARCHAR(50) NOT NULL COMMENT '서비스 지역 코드',
    name VARCHAR(100) NOT NULL COMMENT '지역명',
    region_level VARCHAR(20) NOT NULL COMMENT 'METROPOLITAN, SUBREGION',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '서비스 제공 여부',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_regions_code (code),
    KEY idx_regions_parent_active (parent_region_id, is_active),
    CONSTRAINT chk_regions_level
        CHECK (region_level IN ('METROPOLITAN', 'SUBREGION')),
    CONSTRAINT fk_regions_parent
        FOREIGN KEY (parent_region_id) REFERENCES regions (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='광역 및 하위 여행 지역';

CREATE TABLE places (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '장소 ID',
    region_id BIGINT UNSIGNED NOT NULL COMMENT '장소가 속한 하위 지역 ID',
    kakao_place_id VARCHAR(100) NOT NULL COMMENT '카카오 장소 식별값',
    name VARCHAR(200) NOT NULL COMMENT '장소명',
    category_name VARCHAR(255) NULL COMMENT '카카오 장소 카테고리',
    address VARCHAR(255) NULL COMMENT '지번 주소',
    road_address VARCHAR(255) NULL COMMENT '도로명 주소',
    longitude DECIMAL(10, 7) NOT NULL COMMENT '경도, Kakao x',
    latitude DECIMAL(10, 7) NOT NULL COMMENT '위도, Kakao y',
    phone VARCHAR(50) NULL,
    place_url VARCHAR(2048) NULL COMMENT '카카오 장소 URL',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_places_kakao_place (kakao_place_id),
    KEY idx_places_region_name (region_id, name),
    CONSTRAINT chk_places_longitude
        CHECK (longitude BETWEEN -180.0 AND 180.0),
    CONSTRAINT chk_places_latitude
        CHECK (latitude BETWEEN -90.0 AND 90.0),
    CONSTRAINT fk_places_region
        FOREIGN KEY (region_id) REFERENCES regions (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='카카오 장소 검색 결과의 서비스 저장본';

CREATE TABLE trips (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '여행방 ID',
    broad_region_id BIGINT UNSIGNED NOT NULL COMMENT '선택한 광역 지역 ID',
    sub_region_id BIGINT UNSIGNED NOT NULL COMMENT '선택한 하위 지역 ID',
    name VARCHAR(12) NOT NULL COMMENT '여행방 이름, 최대 12자',
    start_date DATE NOT NULL COMMENT '여행 시작일',
    end_date DATE NOT NULL COMMENT '여행 종료일',
    capacity TINYINT UNSIGNED NOT NULL DEFAULT 4 COMMENT '방장 포함 정원, 2~8명',
    survey_deadline_at DATETIME(6) NOT NULL COMMENT '취향 조사 마감 시각, 선택일 23:59',
    deleted_at DATETIME(6) NULL COMMENT '삭제된 여행방 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_trips_region_dates (sub_region_id, start_date, end_date),
    KEY idx_trips_active_dates (deleted_at, start_date, end_date),
    CONSTRAINT chk_trips_name
        CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 12),
    CONSTRAINT chk_trips_capacity
        CHECK (capacity BETWEEN 2 AND 8),
    CONSTRAINT chk_trips_date_range
        CHECK (start_date <= end_date AND DATEDIFF(end_date, start_date) <= 9),
    CONSTRAINT chk_trips_regions
        CHECK (broad_region_id <> sub_region_id),
    CONSTRAINT fk_trips_broad_region
        FOREIGN KEY (broad_region_id) REFERENCES regions (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_trips_sub_region
        FOREIGN KEY (sub_region_id) REFERENCES regions (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='그룹 여행의 기준 단위';

CREATE TABLE trip_members (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '여행방 멤버십 ID',
    trip_id BIGINT UNSIGNED NOT NULL COMMENT '여행방 ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '사용자 ID',
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT 'HOST, MEMBER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, LEFT, KICKED',
    host_slot TINYINT UNSIGNED NULL COMMENT '활성 방장일 때만 1, 여행별 방장 중복 방지',
    joined_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '참여 순서 기준 시각',
    left_at DATETIME(6) NULL COMMENT '나가기 또는 내보내기 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_trip_members_trip_user (trip_id, user_id),
    UNIQUE KEY uk_trip_members_one_host (trip_id, host_slot),
    KEY idx_trip_members_active_order (trip_id, status, joined_at),
    KEY idx_trip_members_user_active (user_id, status),
    CONSTRAINT chk_trip_members_role
        CHECK (role IN ('HOST', 'MEMBER')),
    CONSTRAINT chk_trip_members_status
        CHECK (status IN ('ACTIVE', 'LEFT', 'KICKED')),
    CONSTRAINT chk_trip_members_host_slot
        CHECK ((role = 'HOST' AND status = 'ACTIVE' AND host_slot = 1)
            OR (role = 'MEMBER' AND host_slot IS NULL)
            OR (role = 'HOST' AND status <> 'ACTIVE' AND host_slot IS NULL)),
    CONSTRAINT chk_trip_members_left_at
        CHECK ((status = 'ACTIVE' AND left_at IS NULL)
            OR (status IN ('LEFT', 'KICKED') AND left_at IS NOT NULL)),
    CONSTRAINT fk_trip_members_trip
        FOREIGN KEY (trip_id) REFERENCES trips (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_trip_members_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='여행방 참여자와 방장 권한';

CREATE TABLE trip_invitations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '초대 링크 ID',
    trip_id BIGINT UNSIGNED NOT NULL COMMENT '여행방 ID',
    created_by_member_id BIGINT UNSIGNED NULL COMMENT '초대 링크 생성 멤버십 ID',
    token_hash CHAR(64) NOT NULL COMMENT '초대 토큰 원문 대신 저장하는 해시',
    revoked_at DATETIME(6) NULL COMMENT '명시적으로 폐기된 시각, 시간 만료는 사용하지 않음',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_trip_invitations_token_hash (token_hash),
    KEY idx_trip_invitations_trip_active (trip_id, revoked_at),
    CONSTRAINT fk_trip_invitations_trip
        FOREIGN KEY (trip_id) REFERENCES trips (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_trip_invitations_creator
        FOREIGN KEY (created_by_member_id) REFERENCES trip_members (id)
        ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='로그인 전후에도 보존되는 여행방 초대 링크';
