-- CURRENT: PlanIt JPA 엔티티 설계의 현행 기준 DDL이다.
-- JPA 엔티티 설계를 위한 참고 DDL이며 직접 실행용 마이그레이션이 아니다.
-- PK 외 FK, UNIQUE, CHECK와 인덱스는 필요한 도메인 규칙을 별도로 검토한 뒤 JPA 매핑에 반영한다.

CREATE TABLE `regional_chat_room_members` (
	`id`	BIGINT	NOT NULL,
	`user_id`	BIGINT	NOT NULL	COMMENT '사용자 ID',
	`regional_chat_room_id`	BIGINT	NOT NULL	COMMENT '지역 공개 채팅방 ID',
	`joined_at`	DATETIME(6)	NULL,
	`left_at`	DATETIME(6)	NULL
);

CREATE TABLE `refresh_tokens` (
	`id`	BIGINT	NOT NULL	COMMENT 'Refresh Token 이력 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '사용자 ID',
	`token_hash`	CHAR(64)	NOT NULL	COMMENT '원문 대신 저장하는 토큰 해시',
	`issued_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`expires_at`	DATETIME(6)	NOT NULL,
	`revoked_at`	DATETIME(6)	NULL
);

CREATE TABLE `weather_schedule_decisions` (
	`id`	BIGINT	NOT NULL	COMMENT '날씨 기반 일정 결정 ID',
	`weather_check_id`	BIGINT	NOT NULL	COMMENT '날씨 조회 ID',
	`decided_by_member_id`	BIGINT	NOT NULL	COMMENT '결정한 방장 멤버십 ID',
	`replacement_generation_job_id`	BIGINT	NULL	COMMENT '변경 선택으로 시작한 대체 일정 작업 ID',
	`decision`	VARCHAR(20)	NOT NULL	COMMENT 'KEEP, REGENERATE',
	`decided_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE `schedule_legs` (
	`id`	BIGINT	NOT NULL	COMMENT '장소 간 이동 구간 ID',
	`schedule_day_id`	BIGINT	NOT NULL	COMMENT '일정 일차 ID',
	`from_visit_id`	BIGINT	NOT NULL	COMMENT '출발 방문 장소 ID',
	`to_visit_id`	BIGINT	NOT NULL	COMMENT '도착 방문 장소 ID',
	`leg_order`	SMALLINT	NOT NULL	COMMENT 'Day 내 이동 구간 순서',
	`distance_meters`	INT	NOT NULL	COMMENT '예상 이동거리 m',
	`required_time`	INT	NULL	COMMENT '예상 이동 소요 시간(초)'
);

CREATE TABLE `trips_noti_link` (
	`id`	BIGINT	NOT NULL,
	`trip_id`	BIGINT	NOT NULL	COMMENT '여행방 ID',
	`notification_id`	BIGINT	NOT NULL	COMMENT '알림 ID'
);

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

CREATE TABLE `community_posts` (
	`id`	BIGINT	NOT NULL	COMMENT '커뮤니티 일정 게시물 ID',
	`like_count`	INT	NOT NULL	DEFAULT 0,
	`view_count`	INT	NOT NULL	DEFAULT 0,
	`popularity_score`	DECIMAL(20, 8)	NOT NULL	DEFAULT 0	COMMENT '시간 경과를 반영한 인기 점수',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE `mission_photos` (
	`id`	BIGINT	NOT NULL	COMMENT '포토 미션 제출 사진 ID',
	`mission_participation_id`	BIGINT	NOT NULL	COMMENT '멤버별 미션 참여 ID',
	`mission_id`	BIGINT	NOT NULL	COMMENT '대표 사진 제약을 위한 미션 ID',
	`image_file_id`	BIGINT	NOT NULL	COMMENT '이미지 파일 ID',
	`active_slot`	TINYINT	NULL	COMMENT '참여자의 현재 사진일 때만 1',
	`representative_slot`	TINYINT	NULL	COMMENT '그룹 대표 사진일 때만 1',
	`submitted_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`replaced_at`	DATETIME(6)	NULL,
	`deleted_at`	DATETIME(6)	NULL
);

CREATE TABLE `users` (
	`id`	BIGINT	NOT NULL	COMMENT '사용자 ID',
	`image_file_id`	BIGINT	NOT NULL	COMMENT '이미지 파일 ID',
	`public_id`	BINARY(16)	NULL,
	`username`	VARCHAR(20)	NULL,
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`deleted_at`	DATETIME(6)	NULL	COMMENT '회원 탈퇴 시각',
	`age_range`	VARCHAR(10)	NULL	COMMENT '연령대',
	`birth_year`	VARCHAR(10)	NULL,
	`birth_day`	VARCHAR(10)	NULL,
	`birthday_type`	VARCHAR(10)	NULL,
	`gender`	VARCHAR(10)	NULL
);

CREATE TABLE `regions` (
	`id`	BIGINT	NOT NULL	COMMENT '지역 ID',
	`broad_region_code`	VARCHAR(50)	NULL,
	`broad_region_name`	VARCHAR(50)	NOT NULL,
	`sub_region_code`	VARCHAR(50)	NOT NULL,
	`sub_region_name`	VARCHAR(50)	NOT NULL,
	`latitude`	DECIMAL(9,6)	NULL,
	`longitude`	DECIMAL(9,6)	NULL
);

CREATE TABLE `places` (
	`id`	BIGINT	NOT NULL	COMMENT '장소 ID',
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
	`updated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

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

CREATE TABLE `missions` (
	`id`	BIGINT	NOT NULL	COMMENT '포토 미션 ID',
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
	`updated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE `trip_invitations` (
	`id`	BIGINT	NOT NULL	COMMENT '초대 링크 ID',
	`trip_id`	BIGINT	NOT NULL	COMMENT '여행방 ID',
	`token_hash`	CHAR(64)	NOT NULL	COMMENT '초대 토큰 원문 대신 저장하는 해시'
);

CREATE TABLE `trip_course_community_posts` (
	`id`	BIGINT	NOT NULL,
	`schedule_id`	BIGINT	NOT NULL	COMMENT '일정 버전 ID',
	`region_id`	BIGINT	NOT NULL	COMMENT '지역 ID',
	`community_post_id`	BIGINT	NOT NULL	COMMENT '커뮤니티 일정 게시물 ID',
	`trip_id`	BIGINT	NOT NULL	COMMENT '여행방 ID'
);

CREATE TABLE `survey_place_preferences` (
	`id`	BIGINT	NOT NULL	COMMENT '설문 장소 선호 ID',
	`survey_id`	BIGINT	NOT NULL	COMMENT '취향 조사 ID',
	`place_id`	BIGINT	NOT NULL	COMMENT 'Google Places 장소 ID'
);

CREATE TABLE `chat_violations` (
	`id`	BIGINT	NOT NULL	COMMENT '채팅 정책 위반 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '위반 사용자 ID',
	`chat_message_id`	BIGINT	NOT NULL	COMMENT '차단된 메시지 ID',
	`violation_sequence`	INT	NOT NULL	COMMENT '사용자별 누적 위반 순서',
	`reason_code`	VARCHAR(100)	NOT NULL	COMMENT '반복, 과속, 욕설·모욕 등',
	`occurred_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE `chat_sanctions` (
	`id`	BIGINT	NOT NULL	COMMENT '채팅 이용 정지 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '제재 사용자 ID',
	`triggered_violation_id`	BIGINT	NOT NULL	COMMENT '제재를 발생시킨 위반 ID',
	`sanction_sequence`	INT	NOT NULL	COMMENT '사용자별 제재 순서',
	`sanction_level`	TINYINT	NOT NULL	COMMENT '1~6, 이후에도 6 유지',
	`duration_days`	TINYINT	NOT NULL	COMMENT '1, 4, 7, 14, 30, 60일',
	`starts_at`	DATETIME(6)	NOT NULL,
	`ends_at`	DATETIME(6)	NOT NULL,
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT 'ACTIVE, EXPIRED, LIFTED',
	`updated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE `surveys` (
	`id`	BIGINT	NOT NULL	COMMENT '취향 조사 ID',
	`trip_member_id`	BIGINT	NOT NULL	COMMENT '여행방 멤버십 ID',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`updated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`submitted_at`	DATETIME(6)	NULL
);

CREATE TABLE `community_view_events` (
	`id`	BIGINT	NOT NULL	COMMENT '게시물 조회 이벤트 ID',
	`community_post_id`	BIGINT	NOT NULL	COMMENT '게시물 ID',
	`user_id`	BIGINT	NULL	COMMENT '로그인 사용자 ID'
);

CREATE TABLE `schedule_generation_snapshots` (
	`id`	BIGINT	NOT NULL	COMMENT '생성 입력 설문 스냅샷 ID',
	`generation_job_id`	BIGINT	NOT NULL	COMMENT 'AI 일정 생성 작업 ID',
	`survey_id`	BIGINT	NOT NULL	COMMENT '참조한 설문 ID',
	`answers_snapshot`	JSON	NOT NULL	COMMENT '작업 시작 시점 카테고리 응답 불변본',
	`place_preferences_snapshot`	JSON	NOT NULL	COMMENT '작업 시작 시점 장소 선호 불변본',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE `trip_members` (
	`id`	BIGINT	NOT NULL	COMMENT '여행방 멤버십 ID',
	`trip_id`	BIGINT	NOT NULL	COMMENT '여행방 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '사용자 ID',
	`role`	VARCHAR(20)	NOT NULL	DEFAULT 'MEMBER'	COMMENT 'HOST, MEMBER',
	`host_slot`	TINYINT	NULL	COMMENT '활성 방장일 때만 1, 여행별 방장 중복 방지',
	`active_slot`	TINYINT	NOT NULL,
	`joined_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)	COMMENT '참여 순서 기준 시각',
	`left_at`	DATETIME(6)	NULL	COMMENT '나가기 또는 내보내기 시각'
);

CREATE TABLE `notifications` (
	`id`	BIGINT	NOT NULL	COMMENT '알림 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '수신 사용자 ID',
	`title`	VARCHAR(200)	NOT NULL,
	`body`	VARCHAR(1000)	NOT NULL,
	`notification_type`	VARCHAR(50)	NULL,
	`target_type`	VARCHAR(50)	NULL	COMMENT '이동 대상 종류',
	`target_id`	BIGINT	NULL	COMMENT '이동 대상 ID',
	`target_status`	VARCHAR(20)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT 'ACTIVE, EXPIRED',
	`dedupe_key`	VARCHAR(191)	NOT NULL	COMMENT '여행·사용자·날짜·유형 중복 방지 키',
	`read_at`	DATETIME(6)	NULL,
	`target_expired_at`	DATETIME(6)	NULL,
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`deleted_at`	DATETIME(6)	NULL
);

CREATE TABLE `survey_answers` (
	`id`	BIGINT	NOT NULL	COMMENT '설문 답변 ID',
	`survey_id`	BIGINT	NOT NULL	COMMENT '취향 조사 ID',
	`preference_question_id`	BIGINT	NOT NULL	COMMENT '취향 카테고리 ID',
	`score`	TINYINT	NOT NULL	DEFAULT 3	COMMENT '1~5점, 초기 중립값 3'
);

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

CREATE TABLE `community_likes` (
	`id`	BIGINT	NOT NULL	COMMENT '커뮤니티 좋아요 ID',
	`community_post_id`	BIGINT	NOT NULL	COMMENT '게시물 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '좋아요 사용자 ID'
);

CREATE TABLE `regional_chat_rooms` (
	`id`	BIGINT	NOT NULL	COMMENT '지역 공개 채팅방 ID',
	`region_id`	BIGINT	NOT NULL	COMMENT '행정구역 ID',
	`name`	VARCHAR(30)	NOT NULL
);

CREATE TABLE `chat_policy_consents` (
	`id`	BIGINT	NOT NULL	COMMENT '채팅 정책 동의 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '사용자 ID',
	`chat_policy_version_id`	BIGINT	NOT NULL	COMMENT '동의한 정책 버전 ID',
	`consented_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE `preference_questions` (
	`id`	BIGINT	NOT NULL	COMMENT '취향 카테고리 ID',
	`code`	VARCHAR(50)	NOT NULL	COMMENT '카테고리 코드',
	`category_code`	VARCHAR(30)	NOT NULL	COMMENT '취향 카테고리 코드',
	`question_text`	VARCHAR(300)	NOT NULL	COMMENT '설문 문항 내용'
);

CREATE TABLE `weather_checks` (
	`id`	BIGINT	NOT NULL	COMMENT '여행 D-1 날씨 조회 ID',
	`trip_id`	BIGINT	NOT NULL	COMMENT '여행방 ID',
	`scheduled_check_at`	DATETIME(6)	NOT NULL	COMMENT 'D-1 오전 10시 예약 시각',
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'SCHEDULED'	COMMENT 'SCHEDULED, SUCCEEDED, FAILED',
	`weather_condition`	VARCHAR(100)	NULL	COMMENT '외부 날씨 조건 코드',
	`checked_at`	DATETIME(6)	NULL,
	`error_code`	VARCHAR(100)	NULL	COMMENT '날씨 조회 실패 내부 코드'
);

CREATE TABLE `mission_participations` (
	`id`	BIGINT	NOT NULL	COMMENT '멤버별 미션 참여 ID',
	`mission_id`	BIGINT	NOT NULL	COMMENT '포토 미션 ID',
	`trip_member_id`	BIGINT	NOT NULL	COMMENT '여행 멤버십 ID',
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'PENDING'	COMMENT 'PENDING, COMPLETED',
	`retry_count`	TINYINT	NOT NULL	DEFAULT 0	COMMENT '사진 삭제·교체 후에도 유지되는 AI 재시도 횟수',
	`completion_method`	VARCHAR(20)	NULL	COMMENT 'AI, MANUAL',
	`completed_at`	DATETIME(6)	NULL,
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6),
	`updated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE `trips` (
	`id`	BIGINT	NOT NULL	COMMENT '여행방 ID',
	`region_id`	BIGINT	NOT NULL	COMMENT '선택한 광역 지역 ID',
	`name`	VARCHAR(12)	NOT NULL	COMMENT '여행방 이름, 최대 12자',
	`start_date`	DATE	NOT NULL	COMMENT '여행 시작일',
	`end_date`	DATE	NOT NULL	COMMENT '여행 종료일',
	`capacity`	TINYINT	NOT NULL	DEFAULT 4	COMMENT '방장 포함 정원, 2~8명',
	`survey_deadline_at`	DATETIME(6)	NOT NULL	COMMENT '취향 조사 마감 시각, 선택일 23:59',
	`deleted_at`	DATETIME(6)	NULL	COMMENT '삭제된 여행방 시각',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE `chat_messages` (
	`id`	BIGINT	NOT NULL	COMMENT '공개 채팅 메시지 ID',
	`regional_chat_room_id`	BIGINT	NOT NULL	COMMENT '지역 공개 채팅방 ID',
	`sender_user_id`	BIGINT	NOT NULL	COMMENT '발신 사용자 ID',
	`client_message_id`	BINARY(16)	NOT NULL	COMMENT '클라이언트 중복 전송 방지 ID',
	`message_type`	VARCHAR(20)	NOT NULL	COMMENT 'TEXT, IMAGE',
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'VISIBLE'	COMMENT 'VISIBLE, BLOCKED, DELETED',
	`blocked_reason`	VARCHAR(100)	NULL	COMMENT '반복, 과속, 금지 표현 등 차단 사유',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE `schedule_days` (
	`id`	BIGINT	NOT NULL	COMMENT '일정 일차 ID',
	`schedule_id`	BIGINT	NOT NULL	COMMENT '일정 버전 ID',
	`day_number`	TINYINT	NOT NULL	COMMENT 'Day 1부터 시작',
	`schedule_date`	DATE	NOT NULL	COMMENT '실제 여행 날짜'
);

CREATE TABLE `photo_evaluations` (
	`id`	BIGINT	NOT NULL	COMMENT '사진 AI 판정 ID',
	`mission_photo_id`	BIGINT	NOT NULL	COMMENT '판정 대상 사진 ID',
	`attempt_no`	TINYINT	NOT NULL	COMMENT '사진 기준 판정 시도 순서',
	`match_score`	DECIMAL(5, 2)	NULL	COMMENT '0~100 일치도',
	`verdict`	VARCHAR(20)	NOT NULL	COMMENT 'SUCCESS, NEAR, FAILED, UNRECOGNIZED',
	`recognized_items_json`	JSON	NULL	COMMENT '인식 항목과 미션 조건 일치 항목',
	`landmark_name`	VARCHAR(200)	NULL	COMMENT '가장 높은 신뢰도의 랜드마크',
	`landmark_confidence`	DECIMAL(5, 2)	NULL	COMMENT '랜드마크 인식 신뢰도',
	`error_code`	VARCHAR(100)	NULL,
	`evaluated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE `image_files` (
	`id`	BIGINT	NOT NULL	COMMENT '이미지 파일 ID',
	`image_key`	VARCHAR(1024)	NULL	COMMENT '원본 이미지 객체 저장소 키',
	`thumbnail_key`	VARCHAR(1024)	NULL	COMMENT '썸네일 이미지 객체 저장소 키',
	`image_purpose`	VARCHAR(20)	NULL,
	`original_filename`	VARCHAR(255)	NULL	COMMENT '사용자가 업로드한 원본 파일명',
	`mime_type`	VARCHAR(100)	NULL	COMMENT '이미지 MIME 타입',
	`size_bytes`	INT	NULL	COMMENT '원본 이미지 파일 크기(byte)',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)	COMMENT '이미지 메타데이터 생성 시각',
	`deleted_at`	DATETIME(6)	NULL	COMMENT '논리 삭제 시각',
	`storage_deleted_at`	DATETIME(6)	NULL	COMMENT '객체 저장소에서 실제 파일이 삭제된 시각'
);

CREATE TABLE `image_chat_messages` (
	`id`	BIGINT	NOT NULL,
	`image_file_id`	BIGINT	NOT NULL	COMMENT '이미지 파일 ID',
	`chat_message_id`	BIGINT	NOT NULL	COMMENT '공개 채팅 메시지 ID'
);

CREATE TABLE `oauth_accounts` (
	`id`	BIGINT	NOT NULL,
	`user_id`	BIGINT	NOT NULL	COMMENT '사용자 ID',
	`provider`	VARCHAR(20)	NULL,
	`provider_user_id`	VARCHAR(255)	NULL,
	`created_at`	DATETIME(6)	NULL
);

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

CREATE TABLE `weather_noti_link` (
	`id`	BIGINT	NOT NULL,
	`weather_check_id`	BIGINT	NOT NULL	COMMENT '여행 D-1 날씨 조회 ID',
	`notification_id`	BIGINT	NOT NULL	COMMENT '알림 ID'
);

CREATE TABLE `mission_generation_jobs` (
	`id`	BIGINT	NOT NULL	COMMENT '일별 포토 미션 생성 작업 ID',
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
	`updated_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE `text_chat_messages` (
	`id`	BIGINT	NOT NULL,
	`chat_message_id`	BIGINT	NOT NULL	COMMENT '공개 채팅 메시지 ID',
	`text_content`	VARCHAR(1000)	NULL
);

ALTER TABLE `regional_chat_room_members` ADD CONSTRAINT `PK_REGIONAL_CHAT_ROOM_MEMBERS` PRIMARY KEY (
	`id`
);

ALTER TABLE `refresh_tokens` ADD CONSTRAINT `PK_REFRESH_TOKENS` PRIMARY KEY (
	`id`
);

ALTER TABLE `weather_schedule_decisions` ADD CONSTRAINT `PK_WEATHER_SCHEDULE_DECISIONS` PRIMARY KEY (
	`id`
);

ALTER TABLE `schedule_legs` ADD CONSTRAINT `PK_SCHEDULE_LEGS` PRIMARY KEY (
	`id`
);

ALTER TABLE `trips_noti_link` ADD CONSTRAINT `PK_TRIPS_NOTI_LINK` PRIMARY KEY (
	`id`
);

ALTER TABLE `schedule_generation_jobs` ADD CONSTRAINT `PK_SCHEDULE_GENERATION_JOBS` PRIMARY KEY (
	`id`
);

ALTER TABLE `community_posts` ADD CONSTRAINT `PK_COMMUNITY_POSTS` PRIMARY KEY (
	`id`
);

ALTER TABLE `mission_photos` ADD CONSTRAINT `PK_MISSION_PHOTOS` PRIMARY KEY (
	`id`
);

ALTER TABLE `users` ADD CONSTRAINT `PK_USERS` PRIMARY KEY (
	`id`
);

ALTER TABLE `regions` ADD CONSTRAINT `PK_REGIONS` PRIMARY KEY (
	`id`
);

ALTER TABLE `places` ADD CONSTRAINT `PK_PLACES` PRIMARY KEY (
	`id`
);

ALTER TABLE `schedule_visits` ADD CONSTRAINT `PK_SCHEDULE_VISITS` PRIMARY KEY (
	`id`
);

ALTER TABLE `missions` ADD CONSTRAINT `PK_MISSIONS` PRIMARY KEY (
	`id`
);

ALTER TABLE `trip_invitations` ADD CONSTRAINT `PK_TRIP_INVITATIONS` PRIMARY KEY (
	`id`
);

ALTER TABLE `trip_course_community_posts` ADD CONSTRAINT `PK_TRIP_COURSE_COMMUNITY_POSTS` PRIMARY KEY (
	`id`
);

ALTER TABLE `survey_place_preferences` ADD CONSTRAINT `PK_SURVEY_PLACE_PREFERENCES` PRIMARY KEY (
	`id`
);

ALTER TABLE `chat_violations` ADD CONSTRAINT `PK_CHAT_VIOLATIONS` PRIMARY KEY (
	`id`
);

ALTER TABLE `chat_sanctions` ADD CONSTRAINT `PK_CHAT_SANCTIONS` PRIMARY KEY (
	`id`
);

ALTER TABLE `surveys` ADD CONSTRAINT `PK_SURVEYS` PRIMARY KEY (
	`id`
);

ALTER TABLE `community_view_events` ADD CONSTRAINT `PK_COMMUNITY_VIEW_EVENTS` PRIMARY KEY (
	`id`
);

ALTER TABLE `schedule_generation_snapshots` ADD CONSTRAINT `PK_SCHEDULE_GENERATION_SNAPSHOTS` PRIMARY KEY (
	`id`
);

ALTER TABLE `trip_members` ADD CONSTRAINT `PK_TRIP_MEMBERS` PRIMARY KEY (
	`id`
);

ALTER TABLE `notifications` ADD CONSTRAINT `PK_NOTIFICATIONS` PRIMARY KEY (
	`id`
);

ALTER TABLE `survey_answers` ADD CONSTRAINT `PK_SURVEY_ANSWERS` PRIMARY KEY (
	`id`
);

ALTER TABLE `schedules` ADD CONSTRAINT `PK_SCHEDULES` PRIMARY KEY (
	`id`
);

ALTER TABLE `community_likes` ADD CONSTRAINT `PK_COMMUNITY_LIKES` PRIMARY KEY (
	`id`
);

ALTER TABLE `regional_chat_rooms` ADD CONSTRAINT `PK_REGIONAL_CHAT_ROOMS` PRIMARY KEY (
	`id`
);

ALTER TABLE `chat_policy_consents` ADD CONSTRAINT `PK_CHAT_POLICY_CONSENTS` PRIMARY KEY (
	`id`
);

ALTER TABLE `preference_questions` ADD CONSTRAINT `PK_PREFERENCE_QUESTIONS` PRIMARY KEY (
	`id`
);

ALTER TABLE `weather_checks` ADD CONSTRAINT `PK_WEATHER_CHECKS` PRIMARY KEY (
	`id`
);

ALTER TABLE `mission_participations` ADD CONSTRAINT `PK_MISSION_PARTICIPATIONS` PRIMARY KEY (
	`id`
);

ALTER TABLE `trips` ADD CONSTRAINT `PK_TRIPS` PRIMARY KEY (
	`id`
);

ALTER TABLE `chat_messages` ADD CONSTRAINT `PK_CHAT_MESSAGES` PRIMARY KEY (
	`id`
);

ALTER TABLE `schedule_days` ADD CONSTRAINT `PK_SCHEDULE_DAYS` PRIMARY KEY (
	`id`
);

ALTER TABLE `photo_evaluations` ADD CONSTRAINT `PK_PHOTO_EVALUATIONS` PRIMARY KEY (
	`id`
);

ALTER TABLE `image_files` ADD CONSTRAINT `PK_IMAGE_FILES` PRIMARY KEY (
	`id`
);

ALTER TABLE `image_chat_messages` ADD CONSTRAINT `PK_IMAGE_CHAT_MESSAGES` PRIMARY KEY (
	`id`
);

ALTER TABLE `oauth_accounts` ADD CONSTRAINT `PK_OAUTH_ACCOUNTS` PRIMARY KEY (
	`id`
);

ALTER TABLE `chat_policy_versions` ADD CONSTRAINT `PK_CHAT_POLICY_VERSIONS` PRIMARY KEY (
	`id`
);

ALTER TABLE `weather_noti_link` ADD CONSTRAINT `PK_WEATHER_NOTI_LINK` PRIMARY KEY (
	`id`
);

ALTER TABLE `mission_generation_jobs` ADD CONSTRAINT `PK_MISSION_GENERATION_JOBS` PRIMARY KEY (
	`id`
);

ALTER TABLE `text_chat_messages` ADD CONSTRAINT `PK_TEXT_CHAT_MESSAGES` PRIMARY KEY (
	`id`
);
