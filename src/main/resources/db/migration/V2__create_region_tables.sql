-- PlanIt Flyway migration: V2  create region tables

CREATE TABLE `broad_regions` (
	`id`	BIGINT	NOT NULL	COMMENT '광역 지역 ID',
	`broad_region_code`	VARCHAR(50)	NOT NULL,
	`broad_region_name`	VARCHAR(50)	NOT NULL
);

ALTER TABLE `broad_regions` ADD CONSTRAINT `PK_BROAD_REGIONS` PRIMARY KEY (
	`id`
);

CREATE TABLE `sub_regions` (
	`id`	BIGINT	NOT NULL	COMMENT '하위 지역 ID',
	`broad_region_id`	BIGINT	NOT NULL	COMMENT '소속 광역 지역 ID',
	`sub_region_code`	VARCHAR(50)	NOT NULL,
	`sub_region_name`	VARCHAR(50)	NOT NULL,
	`latitude`	DECIMAL(9,6)	NULL,
	`longitude`	DECIMAL(9,6)	NULL
);

ALTER TABLE `sub_regions` ADD CONSTRAINT `PK_SUB_REGIONS` PRIMARY KEY (
	`id`
);

ALTER TABLE `sub_regions`
    ADD CONSTRAINT `fk_sub_regions_broad_region_id`
    FOREIGN KEY (`broad_region_id`) REFERENCES `broad_regions` (`id`);

