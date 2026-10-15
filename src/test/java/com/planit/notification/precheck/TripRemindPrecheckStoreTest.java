package com.planit.notification.precheck;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TripRemindPrecheckStoreTest {

    private StringRedisTemplate redisTemplate;
    private TripRemindPrecheckStore store;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = JsonMapper.builder().build();
        store = new TripRemindPrecheckStore(redisTemplate, objectMapper);
    }

    @DisplayName("최초 사전 점검 JSON을 20시간 TTL과 함께 생성한다")
    @Test
    void createsPrecheckWithTwentyHourTtl() {
        LocalDate targetDate = LocalDate.of(2026, 10, 11);
        TripRemindPrecheckCache cache = cache();
        String expectedJson = JsonMapper.builder().build()
                .writeValueAsString(cache);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("planit:trip-remind:precheck:2026-10-11")),
                anyString(),
                eq("72000")
        )).thenReturn("CREATED");

        TripRemindPrecheckCreateResult result = store.create(
                targetDate,
                cache
        );

        assertThat(result).isEqualTo(
                TripRemindPrecheckCreateResult.CREATED
        );
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("planit:trip-remind:precheck:2026-10-11")),
                eq(expectedJson),
                eq("72000")
        );
        assertThat(expectedJson).contains("\"status\":\"PRECHECKED\"");
        assertThat(expectedJson).doesNotContain("status_code");
    }

    @DisplayName("이미 사전 점검 키가 있으면 정상적인 중복 실행 결과를 반환한다")
    @Test
    void returnsAlreadyExistsForExistingPrecheck() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                any(),
                anyString(),
                anyString()
        )).thenReturn("ALREADY_EXISTS");

        TripRemindPrecheckCreateResult result = store.create(
                LocalDate.of(2026, 10, 11),
                cache()
        );

        assertThat(result).isEqualTo(
                TripRemindPrecheckCreateResult.ALREADY_EXISTS
        );
    }

    @DisplayName("Valkey가 저장 결과를 반환하지 않으면 실패한다")
    @Test
    void rejectsMissingValkeyResult() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                any(),
                anyString(),
                anyString()
        )).thenReturn(null);

        assertThatThrownBy(() -> store.create(
                LocalDate.of(2026, 10, 11),
                cache()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Valkey 사전 점검 저장 결과가 없습니다.");
    }

    @DisplayName("사전 점검 JSON과 남은 TTL을 함께 조회한다")
    @Test
    void findsPrecheckWithRemainingTtl() {
        String json = JsonMapper.builder().build()
                .writeValueAsString(cache());
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("planit:trip-remind:precheck:2026-10-11"))
        )).thenReturn(List.of(json, "71940"));

        TripRemindPrecheckSnapshot snapshot = store.find(
                LocalDate.of(2026, 10, 11)
        ).orElseThrow();

        assertThat(snapshot.cache()).isEqualTo(cache());
        assertThat(snapshot.ttlSeconds()).isEqualTo(71_940L);
    }

    @DisplayName("사전 점검 키가 없으면 빈 결과를 반환한다")
    @Test
    void returnsEmptyForMissingPrecheck() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                any()
        )).thenReturn(null);

        assertThat(store.find(LocalDate.of(2026, 10, 11))).isEmpty();
    }

    @DisplayName("사전 점검 JSON을 역직렬화할 수 없으면 실패한다")
    @Test
    void rejectsInvalidPrecheckJson() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                any()
        )).thenReturn(List.of("{invalid-json}", "71940"));

        assertThatThrownBy(() -> store.find(
                LocalDate.of(2026, 10, 11)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Valkey 사전 점검 JSON을 해석할 수 없습니다.");
    }

    @DisplayName("사전 점검 키에 TTL이 없으면 실패한다")
    @Test
    void rejectsPrecheckWithoutTtl() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                any()
        )).thenReturn(List.of("{}", "-1"));

        assertThatThrownBy(() -> store.find(
                LocalDate.of(2026, 10, 11)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Valkey 사전 점검 TTL이 비정상입니다: -1");
    }

    @DisplayName("조회 시점에 만료된 사전 점검 키는 실패한다")
    @Test
    void rejectsExpiredPrecheck() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                any()
        )).thenReturn(List.of("{}", "-2"));

        assertThatThrownBy(() -> store.find(
                LocalDate.of(2026, 10, 11)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Valkey 사전 점검 TTL이 비정상입니다: -2");
    }

    @DisplayName("사전 점검 TTL이 최초 설정값보다 크면 실패한다")
    @Test
    void rejectsPrecheckWithExcessiveTtl() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                any()
        )).thenReturn(List.of("{}", "72001"));

        assertThatThrownBy(() -> store.find(
                LocalDate.of(2026, 10, 11)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Valkey 사전 점검 TTL이 비정상입니다: 72001");
    }

    @DisplayName("여행 시작 대상일 없이 사전 점검을 조회할 수 없다")
    @Test
    void rejectsMissingTargetDateForFind() {
        assertThatThrownBy(() -> store.find(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("여행 시작 대상일은 필수입니다");
    }

    @DisplayName("PRECHECKED 상태를 NOTI_GENERATED로 변경한다")
    @Test
    void transitionsPrecheckedToNotificationGenerated() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("planit:trip-remind:precheck:2026-10-11")),
                eq("PRECHECKED"),
                eq("NOTI_GENERATED")
        )).thenReturn("UPDATED");

        TripRemindPrecheckTransitionResult result = store.transition(
                LocalDate.of(2026, 10, 11),
                TripRemindNotificationWorkflowStatus.PRECHECKED,
                TripRemindNotificationWorkflowStatus.NOTI_GENERATED
        );

        assertThat(result).isEqualTo(
                TripRemindPrecheckTransitionResult.UPDATED
        );
    }

    @DisplayName("NOTI_GENERATED 상태를 DONE으로 변경한다")
    @Test
    void transitionsNotificationGeneratedToDone() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                any(),
                anyString(),
                anyString()
        )).thenReturn("UPDATED");

        TripRemindPrecheckTransitionResult result = store.transition(
                LocalDate.of(2026, 10, 11),
                TripRemindNotificationWorkflowStatus.NOTI_GENERATED,
                TripRemindNotificationWorkflowStatus.DONE
        );

        assertThat(result).isEqualTo(
                TripRemindPrecheckTransitionResult.UPDATED
        );
    }

    @DisplayName("사전 점검 키가 없으면 상태 전이 실패 결과를 반환한다")
    @Test
    void returnsKeyNotFoundForTransition() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                any(),
                anyString(),
                anyString()
        )).thenReturn("KEY_NOT_FOUND");

        assertThat(store.transition(
                LocalDate.of(2026, 10, 11),
                TripRemindNotificationWorkflowStatus.PRECHECKED,
                TripRemindNotificationWorkflowStatus.NOTI_GENERATED
        )).isEqualTo(TripRemindPrecheckTransitionResult.KEY_NOT_FOUND);
    }

    @DisplayName("현재 상태가 기대 상태와 다르면 불일치 결과를 반환한다")
    @Test
    void returnsStatusMismatchForTransition() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                any(),
                anyString(),
                anyString()
        )).thenReturn("STATUS_MISMATCH");

        assertThat(store.transition(
                LocalDate.of(2026, 10, 11),
                TripRemindNotificationWorkflowStatus.PRECHECKED,
                TripRemindNotificationWorkflowStatus.NOTI_GENERATED
        )).isEqualTo(TripRemindPrecheckTransitionResult.STATUS_MISMATCH);
    }

    @DisplayName("순서를 건너뛰는 상태 전이는 Valkey 호출 전에 거부한다")
    @Test
    void rejectsSkippedTransition() {
        assertThatThrownBy(() -> store.transition(
                LocalDate.of(2026, 10, 11),
                TripRemindNotificationWorkflowStatus.PRECHECKED,
                TripRemindNotificationWorkflowStatus.DONE
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PRECHECKED -> DONE");

        verifyNoInteractions(redisTemplate);
    }

    @DisplayName("완료 상태에서는 더 이상 상태를 변경할 수 없다")
    @Test
    void rejectsTransitionFromDone() {
        assertThatThrownBy(() -> store.transition(
                LocalDate.of(2026, 10, 11),
                TripRemindNotificationWorkflowStatus.DONE,
                TripRemindNotificationWorkflowStatus.PRECHECKED
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DONE -> PRECHECKED");

        verifyNoInteractions(redisTemplate);
    }

    @DisplayName("지원하지 않는 상태 전이 결과를 반환하면 실패한다")
    @Test
    void rejectsUnsupportedTransitionResult() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                any(),
                anyString(),
                anyString()
        )).thenReturn("UNKNOWN");

        assertThatThrownBy(() -> store.transition(
                LocalDate.of(2026, 10, 11),
                TripRemindNotificationWorkflowStatus.PRECHECKED,
                TripRemindNotificationWorkflowStatus.NOTI_GENERATED
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("상태 전이 결과입니다: UNKNOWN");
    }

    private static TripRemindPrecheckCache cache() {
        return new TripRemindPrecheckCache(
                Instant.parse("2026-10-10T01:00:03Z"),
                TripRemindNotificationWorkflowStatus.PRECHECKED,
                List.of(new TripRemindPrecheckCache.Forecast(
                        LocalDate.of(2026, 10, 11),
                        List.of(new TripRemindPrecheckCache.RegionForecast(
                                "REGION-SEOUL",
                                true,
                                List.of(new TripRemindPrecheckCache.Reason(
                                        "weather_risk",
                                        "heavy_rain"
                                ))
                        ))
                ))
        );
    }
}
