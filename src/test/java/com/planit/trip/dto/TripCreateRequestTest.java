package com.planit.trip.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TripCreateRequestTest {

    private static final LocalDate START_DATE =
            LocalDate.of(2026, 10, 1);

    private static final LocalDate SURVEY_DEADLINE_DATE =
            LocalDate.of(2026, 9, 30);

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void acceptsValidRequest() {
        TripCreateRequest request = new TripCreateRequest(
                "제주 Trip",
                1L,
                START_DATE,
                4,
                SURVEY_DEADLINE_DATE
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void appliesDefaultCapacity() {
        TripCreateRequest request = new TripCreateRequest(
                "부산 여행",
                1L,
                START_DATE,
                null,
                SURVEY_DEADLINE_DATE
        );

        assertThat(request.capacity()).isEqualTo(4);
    }

    @Test
    void acceptsMissingSurveyDeadlineDate() {
        TripCreateRequest request = new TripCreateRequest(
                "부산 여행",
                1L,
                START_DATE,
                4,
                null
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void trimsName() {
        TripCreateRequest request = new TripCreateRequest(
                "  부산 여행  ",
                1L,
                START_DATE,
                4,
                SURVEY_DEADLINE_DATE
        );

        assertThat(request.name()).isEqualTo("부산 여행");
    }

    @Test
    void rejectsBlankName() {
        TripCreateRequest request = new TripCreateRequest(
                "   ",
                1L,
                START_DATE,
                4,
                SURVEY_DEADLINE_DATE
        );

        assertInvalidField(request, "name");
    }

    @Test
    void rejectsNameLongerThanTwelveCharacters() {
        TripCreateRequest request = new TripCreateRequest(
                "ABCDEFGHIJKLM",
                1L,
                START_DATE,
                4,
                SURVEY_DEADLINE_DATE
        );

        assertInvalidField(request, "name");
    }

    @Test
    void rejectsUnsupportedNameCharacters() {
        TripCreateRequest request = new TripCreateRequest(
                "제주여행1",
                1L,
                START_DATE,
                4,
                SURVEY_DEADLINE_DATE
        );

        assertInvalidField(request, "name");
    }

    @Test
    void rejectsMissingSubRegionId() {
        TripCreateRequest request = new TripCreateRequest(
                "제주 여행",
                null,
                START_DATE,
                4,
                SURVEY_DEADLINE_DATE
        );

        assertInvalidField(request, "subRegionId");
    }

    @Test
    void rejectsNonPositiveSubRegionId() {
        TripCreateRequest request = new TripCreateRequest(
                "제주 여행",
                0L,
                START_DATE,
                4,
                SURVEY_DEADLINE_DATE
        );

        assertInvalidField(request, "subRegionId");
    }

    @Test
    void rejectsMissingStartDate() {
        TripCreateRequest request = new TripCreateRequest(
                "제주 여행",
                1L,
                null,
                4,
                SURVEY_DEADLINE_DATE
        );

        assertInvalidField(request, "startDate");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 9})
    void rejectsCapacityOutsideAllowedRange(int capacity) {
        TripCreateRequest request = new TripCreateRequest(
                "제주 여행",
                1L,
                START_DATE,
                capacity,
                SURVEY_DEADLINE_DATE
        );

        assertInvalidField(request, "capacity");
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 8})
    void acceptsCapacityBoundaryValues(int capacity) {
        TripCreateRequest request = new TripCreateRequest(
                "제주 여행",
                1L,
                START_DATE,
                capacity,
                SURVEY_DEADLINE_DATE
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    private void assertInvalidField(
            TripCreateRequest request,
            String field
    ) {
        assertThat(validator.validate(request))
                .extracting(violation ->
                        violation.getPropertyPath().toString())
                .contains(field);
    }
}
