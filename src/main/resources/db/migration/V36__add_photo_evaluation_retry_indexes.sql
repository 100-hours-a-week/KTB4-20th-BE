CREATE INDEX `idx_photo_evaluations_retry_pending`
    ON `photo_evaluations` (`execution_status`, `retry_after_at`);

CREATE INDEX `idx_photo_evaluations_stale_running`
    ON `photo_evaluations` (`execution_status`, `requested_at`);
