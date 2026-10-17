package com.planit.photomission.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

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

    @DisplayName("재시도 대상은 기본 30초 주기로 확인한다")
    @Test
    void usesThirtySecondDefaultDelay() throws NoSuchMethodException {
        Scheduled scheduled = PhotoMissionEvaluationRetryScheduler.class
                .getDeclaredMethod("retry")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelayString()).isEqualTo(
                "${photo-mission.evaluation-retry-delay-ms:30000}"
        );
    }
}
