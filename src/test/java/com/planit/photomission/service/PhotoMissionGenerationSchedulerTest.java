package com.planit.photomission.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PhotoMissionGenerationSchedulerTest {

    @DisplayName("스케줄 실행 시 서울 현재 시각으로 생성 대상 여행을 처리한다")
    @Test
    void generatesDueTripsAtScheduledTime() {
        PhotoMissionGenerationService service =
                mock(PhotoMissionGenerationService.class);
        ZoneId seoul = ZoneId.of("Asia/Seoul");
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-13T15:00:00Z"),
                seoul
        );
        PhotoMissionGenerationScheduler scheduler =
                new PhotoMissionGenerationScheduler(service, clock);

        scheduler.generate();

        verify(service).generateDueTrips(
                LocalDateTime.of(2026, 10, 14, 0, 0)
        );
    }
}
