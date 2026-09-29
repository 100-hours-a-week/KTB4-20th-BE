package com.planit.trip.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
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

    @DisplayName("유효한 여행 생성 요청을 허용한다")
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

    @DisplayName("정원을 생략하면 기본 정원을 적용한다")
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

    @DisplayName("설문 마감일이 없는 요청을 허용한다")
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

    @DisplayName("여행 이름의 앞뒤 공백을 제거한다")
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

    @DisplayName("빈 여행 이름을 거부한다")
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

    @DisplayName("12자를 초과한 여행 이름을 거부한다")
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

    @DisplayName("허용되지 않은 문자가 포함된 여행 이름을 거부한다")
    @Test
    void rejectsUnsupportedNameCharacters() {
        TripCreateRequest request = new TripCreateRequest(
                "제주여행!",
                1L,
                START_DATE,
                4,
                SURVEY_DEADLINE_DATE
        );

        assertInvalidField(request, "name");
    }

    @DisplayName("지역 ID가 없는 요청을 거부한다")
    @Test
    void rejectsMissingRegionId() {
        TripCreateRequest request = new TripCreateRequest(
                "제주 여행",
                null,
                START_DATE,
                4,
                SURVEY_DEADLINE_DATE
        );

        assertInvalidField(request, "regionId");
    }

    @DisplayName("0 이하의 지역 ID를 거부한다")
    @Test
    void rejectsNonPositiveRegionId() {
        TripCreateRequest request = new TripCreateRequest(
                "제주 여행",
                0L,
                START_DATE,
                4,
                SURVEY_DEADLINE_DATE
        );

        assertInvalidField(request, "regionId");
    }

    @DisplayName("여행 시작일이 없는 요청을 거부한다")
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

    @DisplayName("허용 범위를 벗어난 여행 정원을 거부한다")
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

    @DisplayName("허용 범위의 경계값에 해당하는 여행 정원을 허용한다")
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
