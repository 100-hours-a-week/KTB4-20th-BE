package com.planit.schedule.repository;

import com.planit.schedule.domain.PlaceDetails;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlaceRepositoryImpl implements PlaceRepositoryCustom {

    private static final String VALUE_PLACEHOLDERS =
            "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final int COLUMN_COUNT = 12;

    private final JdbcTemplate jdbcTemplate;

    public PlaceRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insertMissingAll(
            Long regionId,
            List<PlaceDetails> places,
            LocalDateTime now
    ) {
        if (places.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO places (
                    region_id,
                    google_place_id,
                    name,
                    category_name,
                    address,
                    road_address,
                    longitude,
                    latitude,
                    phone,
                    place_url,
                    created_at,
                    updated_at
                ) VALUES %s
                ON DUPLICATE KEY UPDATE id = id
                """.formatted(String.join(
                ", ",
                Collections.nCopies(places.size(), VALUE_PLACEHOLDERS)
        ));
        List<Object> parameters = new ArrayList<>(
                places.size() * COLUMN_COUNT
        );
        for (PlaceDetails place : places) {
            parameters.add(regionId);
            parameters.add(place.googlePlaceId().trim());
            parameters.add(place.name().trim());
            parameters.add(optional(place.categoryName()));
            parameters.add(optional(place.address()));
            parameters.add(optional(place.roadAddress()));
            parameters.add(place.longitude());
            parameters.add(place.latitude());
            parameters.add(optional(place.phone()));
            parameters.add(optional(place.placeUrl()));
            parameters.add(now);
            parameters.add(now);
        }
        jdbcTemplate.update(sql, parameters.toArray());
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
