UPDATE regional_chat_room_members
SET joined_at = CURRENT_TIMESTAMP(6)
WHERE joined_at IS NULL;

ALTER TABLE regional_chat_room_members
    MODIFY joined_at DATETIME(6) NOT NULL,
    ADD CONSTRAINT UK_REGIONAL_CHAT_ROOM_MEMBERS_USER_ROOM
        UNIQUE (user_id, regional_chat_room_id);
