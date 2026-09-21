INSERT INTO `image_files` (
    `image_purpose`,
    `created_at`
)
SELECT
    'DEFAULT_PROFILE',
    CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (
    SELECT 1
    FROM `image_files`
    WHERE `image_purpose` = 'DEFAULT_PROFILE'
      AND `deleted_at` IS NULL
);
