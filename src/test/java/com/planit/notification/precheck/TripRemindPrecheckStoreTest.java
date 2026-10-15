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
