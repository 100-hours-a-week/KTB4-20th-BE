ALTER TABLE text_chat_messages
    ADD CONSTRAINT UK_TEXT_CHAT_MESSAGES_CHAT_MESSAGE
        UNIQUE (chat_message_id);

CREATE INDEX IX_CHAT_MESSAGES_ROOM_STATUS_CREATED_ID
    ON chat_messages (regional_chat_room_id, status, created_at, id);
