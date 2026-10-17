package com.planit.photomission.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PhotoMissionEvaluationRetrySchedulerTest {

    @DisplayName("현재 시각을 기준으로 재시도 대기 판정을 처리한다")
    @Test
    void retriesDueEvaluations() {
        PhotoMissionEvaluationRetryService service =
                mock(PhotoMissionEvaluationRetryService.class);
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-17T01:00:00Z"),
                zone
        );
        PhotoMissionEvaluationRetryScheduler scheduler =
                new PhotoMissionEvaluationRetryScheduler(service, clock);

        scheduler.retry();

        verify(service).retryDue(LocalDateTime.of(2026, 10, 17, 10, 0));
    }
}
