-- Preserve the checksum of the already published V6 migration while aligning
-- chat policy version IDs with the approved BIGINT AUTO_INCREMENT policy.
ALTER TABLE `chat_policy_versions`
    MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '채팅 운영 정책 버전 ID';
