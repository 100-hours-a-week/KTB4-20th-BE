ALTER TABLE `photo_evaluations`
    ADD COLUMN `deadline_at` DATETIME(6) NULL
        COMMENT '요청 접수부터 최종 결과 반영까지의 절대 마감 시각'
        AFTER `requested_at`;
