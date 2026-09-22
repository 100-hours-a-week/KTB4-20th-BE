ALTER TABLE `chat_policy_versions`
    ADD COLUMN `active_slot` TINYINT
        GENERATED ALWAYS AS (
            CASE WHEN `status` = 'ACTIVE' THEN 1 ELSE NULL END
        ) STORED,
    ADD CONSTRAINT `UK_CHAT_POLICY_VERSIONS_ACTIVE_SLOT` UNIQUE (`active_slot`);
