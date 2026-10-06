ALTER TABLE `users`
    MODIFY COLUMN `image_file_id` BIGINT NULL
    COMMENT '사용자 프로필 이미지 ID, NULL이면 이름 기반 아바타 사용';

UPDATE `users` u
    JOIN `image_files` i ON i.id = u.image_file_id
    SET u.image_file_id = NULL
WHERE i.image_purpose = 'DEFAULT_PROFILE';

DELETE FROM `image_files`
WHERE `image_purpose` = 'DEFAULT_PROFILE';
