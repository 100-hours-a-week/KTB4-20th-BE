package com.planit.notification.precheck;

import com.planit.ai.AiPrecheckClient;
import com.planit.ai.AiPrecheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TripRemindPrecheckServiceTest {

    private AiPrecheckClient aiPrecheckClient;
    private TripRemindPrecheckStore precheckStore;
    private TripRemindPrecheckService service;

    @BeforeEach
    void setUp() {
        aiPrecheckClient = mock(AiPrecheckClient.class);
        precheckStore = mock(TripRemindPrecheckStore.class);
        service = new TripRemindPrecheckService(
                aiPrecheckClient,
                precheckStore
        );
    }

    @DisplayName("기존 사전 점검이 있으면 AI를 호출하지 않는다")
    @Test
    void skipsAiCallWhenPrecheckExists() {
        LocalDate targetDate = LocalDate.of(2026, 10, 11);
        when(precheckStore.find(targetDate)).thenReturn(Optional.of(
                new TripRemindPrecheckSnapshot(cache(), 71_940L)
        ));

        TripRemindPrecheckCreateResult result =
                service.createIfAbsent(targetDate);

        assertThat(result).isEqualTo(
                TripRemindPrecheckCreateResult.ALREADY_EXISTS
        );
        verifyNoInteractions(aiPrecheckClient);
        verify(precheckStore, never()).create(any(), any());
    }

    @DisplayName("사전 점검이 없으면 AI 응답을 PRECHECKED 상태로 저장한다")
    @Test
    void createsPrecheckedCacheFromAiResponse() {
        LocalDate targetDate = LocalDate.of(2026, 10, 11);
        AiPrecheckResponse response = response();
        when(precheckStore.find(targetDate)).thenReturn(Optional.empty());
        when(aiPrecheckClient.precheck()).thenReturn(response);
        when(precheckStore.create(targetDate, cache()))
                .thenReturn(TripRemindPrecheckCreateResult.CREATED);

        TripRemindPrecheckCreateResult result =
                service.createIfAbsent(targetDate);

        assertThat(result).isEqualTo(
                TripRemindPrecheckCreateResult.CREATED
        );
        verify(aiPrecheckClient).precheck();
        verify(precheckStore).create(targetDate, cache());
    }

    @DisplayName("동시 생성으로 기존 키가 생기면 중복 실행 결과를 반환한다")
    @Test
    void returnsAlreadyExistsAfterConcurrentCreation() {
        LocalDate targetDate = LocalDate.of(2026, 10, 11);
        when(precheckStore.find(targetDate)).thenReturn(Optional.empty());
        when(aiPrecheckClient.precheck()).thenReturn(response());
        when(precheckStore.create(targetDate, cache()))
                .thenReturn(TripRemindPrecheckCreateResult.ALREADY_EXISTS);

        assertThat(service.createIfAbsent(targetDate)).isEqualTo(
                TripRemindPrecheckCreateResult.ALREADY_EXISTS
        );
    }

    @DisplayName("AI 사전 점검이 실패하면 Valkey JSON을 생성하지 않는다")
    @Test
    void doesNotCreateCacheWhenAiPrecheckFails() {
        LocalDate targetDate = LocalDate.of(2026, 10, 11);
        when(precheckStore.find(targetDate)).thenReturn(Optional.empty());
        when(aiPrecheckClient.precheck()).thenThrow(
                new RestClientException("AI 호출 실패")
        );

        assertThatThrownBy(() -> service.createIfAbsent(targetDate))
                .isInstanceOf(RestClientException.class)
                .hasMessage("AI 호출 실패");
        verify(precheckStore, never()).create(any(), any());
    }

    @DisplayName("여행 시작 대상일 없이 사전 점검을 실행할 수 없다")
    @Test
    void rejectsMissingTargetDate() {
        assertThatThrownBy(() -> service.createIfAbsent(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("여행 시작 대상일은 필수입니다");

        verifyNoInteractions(aiPrecheckClient, precheckStore);
    }

    private static AiPrecheckResponse response() {
        LocalDate firstDate = LocalDate.of(2026, 10, 11);
        List<AiPrecheckResponse.Forecast> forecasts = new ArrayList<>();
        forecasts.add(new AiPrecheckResponse.Forecast(
                firstDate,
                List.of(new AiPrecheckResponse.RegionForecast(
                        "REGION-SEOUL",
                        true,
                        List.of(new AiPrecheckResponse.Reason(
                                "weather_risk",
                                "heavy_rain"
                        ))
                ))
        ));
        for (int day = 1; day < 9; day++) {
            forecasts.add(new AiPrecheckResponse.Forecast(
                    firstDate.plusDays(day),
                    List.of()
            ));
        }

        return new AiPrecheckResponse(200, new AiPrecheckResponse.Data(
                Instant.parse("2026-10-10T01:00:03Z"),
                forecasts
        ));
    }

    private static TripRemindPrecheckCache cache() {
        return TripRemindPrecheckCache.from(response().data());
    }
}
