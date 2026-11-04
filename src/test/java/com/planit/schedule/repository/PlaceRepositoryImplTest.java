package com.planit.schedule.repository;

import com.planit.schedule.domain.PlaceDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PlaceRepositoryImplTest {

    @DisplayName("없는 장소를 하나의 다중 VALUES 쿼리로 삽입한다")
    @Test
    void insertsMissingPlacesWithOneQuery() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlaceRepositoryImpl repository = new PlaceRepositoryImpl(jdbcTemplate);
        LocalDateTime now = LocalDateTime.of(2026, 11, 4, 12, 0);

        repository.insertMissingAll(
                10L,
                List.of(
                        details("google-1", " 장소 1 "),
                        details("google-2", "장소 2")
                ),
                now
        );

        ArgumentCaptor<String> sqlCaptor =
                ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parametersCaptor =
                ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                parametersCaptor.capture()
        );
        assertThat(sqlCaptor.getValue())
                .contains("INSERT INTO places")
                .contains("ON DUPLICATE KEY UPDATE id = id");
        assertThat(parametersCaptor.getValue()).hasSize(24);
        assertThat(parametersCaptor.getValue()[0]).isEqualTo(10L);
        assertThat(parametersCaptor.getValue()[1]).isEqualTo("google-1");
        assertThat(parametersCaptor.getValue()[2]).isEqualTo("장소 1");
        assertThat(parametersCaptor.getValue()[12]).isEqualTo(10L);
        assertThat(parametersCaptor.getValue()[13]).isEqualTo("google-2");
    }

    private PlaceDetails details(String id, String name) {
        return new PlaceDetails(
                id,
                name,
                null,
                null,
                null,
                BigDecimal.valueOf(129.0),
                BigDecimal.valueOf(35.0),
                null,
                null
        );
    }
}
