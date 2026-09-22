-- Preserve the checksum of the already published V6 migration while aligning
-- chat policy version IDs with the approved BIGINT AUTO_INCREMENT policy.
ALTER TABLE `chat_policy_consents`
    DROP FOREIGN KEY `fk_chat_policy_consents_chat_policy_version_id`;

ALTER TABLE `chat_policy_versions`
    MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '채팅 운영 정책 버전 ID';

ALTER TABLE `chat_policy_consents`
    ADD CONSTRAINT `fk_chat_policy_consents_chat_policy_version_id`
    FOREIGN KEY (`chat_policy_version_id`) REFERENCES `chat_policy_versions` (`id`);
