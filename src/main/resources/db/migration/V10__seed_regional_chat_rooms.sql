-- PlanIt Flyway migration: seed one regional chat room for every supported sub-region.
-- Regional chat room IDs intentionally match sub-region IDs for deterministic initial data.

INSERT INTO regional_chat_rooms (
    id,
    region_id,
    name
)
SELECT
    sr.id,
    sr.id,
    CONCAT(br.broad_region_name, ' ', sr.sub_region_name)
FROM sub_regions sr
JOIN broad_regions br
    ON br.id = sr.broad_region_id
ORDER BY sr.id;
