package com.planit.photomission.service;

import com.planit.mq.intake.AiJobIntakeGuard;
import com.planit.mq.job.AiJobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PhotoMissionGenerationSchedulerTest {

    private final PhotoMissionGenerationService service = mock(PhotoMissionGenerationService.class);
    private final AiJobIntakeGuard intakeGuard = mock(AiJobIntakeGuard.class);
    private final PhotoMissionGenerationScheduler scheduler = new PhotoMissionGenerationScheduler(
            service,
            intakeGuard,
            Clock.fixed(Instant.parse("2026-10-13T15:00:00Z"), ZoneId.of("Asia/Seoul"))
    );

    @DisplayName("스케줄 실행 시 서울 현재 시각으로 생성 대상 여행을 처리한다")
    @Test
    void generatesDueTripsAtScheduledTime() {
        scheduler.generate();

        verify(service).generateDueTrips(
                LocalDateTime.of(2026, 10, 14, 0, 0)
        );
    }

    @DisplayName("전환 플래그가 켜져 있으면 메시지 큐 접수 스케줄러에 맡기고 동기 생성을 하지 않는다")
    @Test
    void skipsWhenAsync() {
        when(intakeGuard.isAsync(AiJobType.PHOTO_MISSION_GENERATE)).thenReturn(true);

        scheduler.generate();

        verifyNoInteractions(service);
    }
}
