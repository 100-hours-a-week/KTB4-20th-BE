-- PlanIt Flyway migration: V7  create community tables

CREATE TABLE `community_posts` (
	`id`	BIGINT	NOT NULL	COMMENT '커뮤니티 일정 게시물 ID',
	`like_count`	INT	NOT NULL	DEFAULT 0,
	`view_count`	INT	NOT NULL	DEFAULT 0,
	`popularity_score`	DECIMAL(20, 8)	NOT NULL	DEFAULT 0	COMMENT '시간 경과를 반영한 인기 점수',
	`created_at`	DATETIME(6)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(6)
);

ALTER TABLE `community_posts` ADD CONSTRAINT `PK_COMMUNITY_POSTS` PRIMARY KEY (
	`id`
);

CREATE TABLE `trip_course_community_posts` (
	`id`	BIGINT	NOT NULL,
	`schedule_id`	BIGINT	NOT NULL	COMMENT '일정 버전 ID',
	`region_id`	BIGINT	NOT NULL	COMMENT '지역 ID',
	`community_post_id`	BIGINT	NOT NULL	COMMENT '커뮤니티 일정 게시물 ID',
	`trip_id`	BIGINT	NOT NULL	COMMENT '여행방 ID'
);

ALTER TABLE `trip_course_community_posts` ADD CONSTRAINT `PK_TRIP_COURSE_COMMUNITY_POSTS` PRIMARY KEY (
	`id`
);

ALTER TABLE `trip_course_community_posts`
    ADD CONSTRAINT `fk_trip_course_community_posts_schedule_id`
    FOREIGN KEY (`schedule_id`) REFERENCES `schedules` (`id`);

ALTER TABLE `trip_course_community_posts`
    ADD CONSTRAINT `fk_trip_course_community_posts_region_id`
    FOREIGN KEY (`region_id`) REFERENCES `sub_regions` (`id`);

ALTER TABLE `trip_course_community_posts`
    ADD CONSTRAINT `fk_trip_course_community_posts_community_post_id`
    FOREIGN KEY (`community_post_id`) REFERENCES `community_posts` (`id`);

ALTER TABLE `trip_course_community_posts`
    ADD CONSTRAINT `fk_trip_course_community_posts_trip_id`
    FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`);

CREATE TABLE `community_likes` (
	`id`	BIGINT	NOT NULL	COMMENT '커뮤니티 좋아요 ID',
	`community_post_id`	BIGINT	NOT NULL	COMMENT '게시물 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '좋아요 사용자 ID'
);

ALTER TABLE `community_likes` ADD CONSTRAINT `PK_COMMUNITY_LIKES` PRIMARY KEY (
	`id`
);

ALTER TABLE `community_likes`
    ADD CONSTRAINT `fk_community_likes_community_post_id`
    FOREIGN KEY (`community_post_id`) REFERENCES `community_posts` (`id`);

ALTER TABLE `community_likes`
    ADD CONSTRAINT `fk_community_likes_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

CREATE TABLE `community_view_events` (
	`id`	BIGINT	NOT NULL	COMMENT '게시물 조회 이벤트 ID',
	`community_post_id`	BIGINT	NOT NULL	COMMENT '게시물 ID',
	`user_id`	BIGINT	NULL	COMMENT '로그인 사용자 ID'
);

ALTER TABLE `community_view_events` ADD CONSTRAINT `PK_COMMUNITY_VIEW_EVENTS` PRIMARY KEY (
	`id`
);

ALTER TABLE `community_view_events`
    ADD CONSTRAINT `fk_community_view_events_community_post_id`
    FOREIGN KEY (`community_post_id`) REFERENCES `community_posts` (`id`);

ALTER TABLE `community_view_events`
    ADD CONSTRAINT `fk_community_view_events_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

