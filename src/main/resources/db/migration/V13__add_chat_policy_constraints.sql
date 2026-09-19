ALTER TABLE `chat_policy_versions`
    ADD CONSTRAINT `UK_CHAT_POLICY_VERSIONS_VERSION` UNIQUE (`version`);

ALTER TABLE `chat_policy_consents`
    ADD CONSTRAINT `UK_CHAT_POLICY_CONSENTS_USER_VERSION`
        UNIQUE (`user_id`, `chat_policy_version_id`);
