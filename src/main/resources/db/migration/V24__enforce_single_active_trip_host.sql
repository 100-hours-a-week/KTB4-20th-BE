ALTER TABLE trip_members
    ADD CONSTRAINT UK_TRIP_MEMBERS_ACTIVE_HOST
        UNIQUE (trip_id, host_slot);
