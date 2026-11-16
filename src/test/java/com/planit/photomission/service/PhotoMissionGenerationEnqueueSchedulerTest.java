package com.planit.photomission.service;

import com.planit.mq.intake.AiJobIntakeGuard;
import com.planit.mq.job.AiJobType;
import com.planit.repository.TripRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PhotoMissionGenerationEnqueueSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime MIDNIGHT = LocalDateTime.of(2026, 12, 1, 0, 0);

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final PhotoMissionGenerationEnqueueService enqueueService = mock(PhotoMissionGenerationEnqueueService.class);
    private final AiJobIntakeGuard intakeGuard = mock(AiJobIntakeGuard.class);
    private final PhotoMissionGenerationEnqueueScheduler scheduler = new PhotoMissionGenerationEnqueueScheduler(
            tripRepository,
            enqueueService,
            intakeGuard,
            Clock.fixed(MIDNIGHT.atZone(SEOUL).toInstant(), SEOUL)
    );

    @BeforeEach
    void setUp() {
        when(intakeGuard.isAsync(AiJobType.PHOTO_MISSION_GENERATE)).thenReturn(true);
    }

    @DisplayName("전환 플래그가 켜져 있으면 그날 시작하는 대상 여행을 하나씩 접수한다")
    @Test
    void enqueuesDueTrips() {
        when(tripRepository.findPhotoMissionGenerationTargetIds(LocalDate.of(2026, 12, 1), MIDNIGHT.minusMinutes(5)))
                .thenReturn(List.of(1L, 2L));

        scheduler.enqueue();

        verify(enqueueService).enqueue(1L, MIDNIGHT);
        verify(enqueueService).enqueue(2L, MIDNIGHT);
    }

    @DisplayName("한 여행의 접수가 실패해도 나머지 여행을 계속 접수한다")
    @Test
    void continuesAfterFailure() {
        when(tripRepository.findPhotoMissionGenerationTargetIds(LocalDate.of(2026, 12, 1), MIDNIGHT.minusMinutes(5)))
                .thenReturn(List.of(1L, 2L));
        when(enqueueService.enqueue(1L, MIDNIGHT)).thenThrow(new IllegalStateException("확정된 여행 일정이 없습니다"));

        scheduler.enqueue();

        verify(enqueueService).enqueue(2L, MIDNIGHT);
    }

    @DisplayName("전환 플래그가 꺼져 있으면 기존 동기 스케줄러에 맡기고 접수하지 않는다")
    @Test
    void skipsWhenSync() {
        when(intakeGuard.isAsync(AiJobType.PHOTO_MISSION_GENERATE)).thenReturn(false);

        scheduler.enqueue();

        verifyNoInteractions(tripRepository, enqueueService);
    }

    @DisplayName("매일 00:00(서울)에 실행하고 기존 스케줄러와 다른 이름의 락을 잡는다")
    @Test
    void runsAtMidnightWithOwnLock() throws NoSuchMethodException {
        var enqueue = PhotoMissionGenerationEnqueueScheduler.class.getMethod("enqueue");
        var generate = PhotoMissionGenerationScheduler.class.getMethod("generate");

        assertThat(enqueue.getAnnotation(Scheduled.class).cron()).isEqualTo("0 0 0 * * *");
        assertThat(enqueue.getAnnotation(Scheduled.class).zone()).isEqualTo("Asia/Seoul");
        assertThat(enqueue.getAnnotation(SchedulerLock.class).name()).isEqualTo("photoMissionGenerationEnqueue")
                .isNotEqualTo(generate.getAnnotation(SchedulerLock.class).name());
        assertThat(enqueue.getAnnotation(SchedulerLock.class).lockAtMostFor()).isEqualTo("PT5M");
    }
}
