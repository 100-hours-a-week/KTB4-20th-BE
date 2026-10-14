ALTER TABLE `notifications`
    ADD COLUMN `delivered_at` DATETIME(6) NULL AFTER `read_at`,
    ADD CONSTRAINT `uk_notifications_dedupe` UNIQUE (`dedupe_key`);
