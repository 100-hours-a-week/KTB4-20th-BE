UPDATE `mission_participations`
SET `status` = 'PENDING'
WHERE `status` = 'FAILED';

UPDATE `mission_group_states`
SET `status` = 'PENDING'
WHERE `status` = 'FAILED';

ALTER TABLE `mission_participations`
    MODIFY COLUMN `status` VARCHAR(20) NOT NULL
        COMMENT 'PENDING, COMPLETED';

ALTER TABLE `mission_group_states`
    MODIFY COLUMN `status` VARCHAR(20) NOT NULL
        COMMENT 'PENDING, COMPLETED';
