-- PlanIt Flyway migration: V9  create notification tables

CREATE TABLE `notifications` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT	COMMENT '알림 ID',
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
	`deleted_at`	DATETIME(6)	NULL,
	CONSTRAINT `PK_NOTIFICATIONS` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `notifications`
    ADD CONSTRAINT `fk_notifications_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

CREATE TABLE `trips_noti_link` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT,
	`trip_id`	BIGINT	NOT NULL	COMMENT '여행방 ID',
	`notification_id`	BIGINT	NOT NULL	COMMENT '알림 ID',
	CONSTRAINT `PK_TRIPS_NOTI_LINK` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `trips_noti_link`
    ADD CONSTRAINT `fk_trips_noti_link_trip_id`
    FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`);

ALTER TABLE `trips_noti_link`
    ADD CONSTRAINT `fk_trips_noti_link_notification_id`
    FOREIGN KEY (`notification_id`) REFERENCES `notifications` (`id`);

CREATE TABLE `weather_noti_link` (
	`id`	BIGINT	NOT NULL AUTO_INCREMENT,
	`weather_check_id`	BIGINT	NOT NULL	COMMENT '여행 D-1 날씨 조회 ID',
	`notification_id`	BIGINT	NOT NULL	COMMENT '알림 ID',
	CONSTRAINT `PK_WEATHER_NOTI_LINK` PRIMARY KEY (
		`id`
	)
);


ALTER TABLE `weather_noti_link`
    ADD CONSTRAINT `fk_weather_noti_link_weather_check_id`
    FOREIGN KEY (`weather_check_id`) REFERENCES `weather_checks` (`id`);

ALTER TABLE `weather_noti_link`
    ADD CONSTRAINT `fk_weather_noti_link_notification_id`
    FOREIGN KEY (`notification_id`) REFERENCES `notifications` (`id`);
