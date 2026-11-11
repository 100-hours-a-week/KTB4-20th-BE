package com.planit.mq.job;

import com.planit.photomission.repository.MissionGenerationJobRepository;
import com.planit.schedule.repository.ScheduleGenerationJobRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaleJobCheckerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 10, 30);

    private final ScheduleGenerationJobRepository scheduleJobRepository = mock(ScheduleGenerationJobRepository.class);
    private final MissionGenerationJobRepository missionJobRepository = mock(MissionGenerationJobRepository.class);
    private final StaleJobFailureService failureService = mock(StaleJobFailureService.class);
    private final StaleJobChecker checker = new StaleJobChecker(
            scheduleJobRepository,
            missionJobRepository,
            failureService,
            Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL)
    );

    @DisplayName("일정 생성은 28분 전, 포토미션 생성은 가장 최근 06:00 전에 접수한 작업을 실패로 끝낸다")
    @Test
    void failsStaleJobs() {
        when(scheduleJobRepository.findQueuedIdsCreatedBefore(NOW.minusMinutes(28))).thenReturn(List.of(1L, 2L));
        when(missionJobRepository.findQueuedIdsAttemptedBefore(NOW.toLocalDate().atTime(6, 0)))
                .thenReturn(List.of(3L));

        checker.check();

        verify(failureService).fail(AiJobType.SCHEDULE_GENERATE, 1L);
        verify(failureService).fail(AiJobType.SCHEDULE_GENERATE, 2L);
        verify(failureService).fail(AiJobType.PHOTO_MISSION_GENERATE, 3L);
    }

    @DisplayName("한 작업에서 오류가 나도 나머지 작업을 계속 처리한다")
    @Test
    void continuesAfterError() {
        when(scheduleJobRepository.findQueuedIdsCreatedBefore(NOW.minusMinutes(28))).thenReturn(List.of(1L, 2L));
        when(failureService.fail(AiJobType.SCHEDULE_GENERATE, 1L)).thenThrow(new IllegalStateException("db down"));

        checker.check();

        verify(failureService).fail(AiJobType.SCHEDULE_GENERATE, 2L);
    }

    @DisplayName("포토미션 생성 기준 시각은 06:00 전이면 전날 06:00, 06:00부터는 당일 06:00이다")
    @ParameterizedTest
    @CsvSource({
            "2026-11-12T05:59:59, 2026-11-11T06:00",
            "2026-11-12T06:00:00, 2026-11-12T06:00",
            "2026-11-12T23:57:00, 2026-11-12T06:00",
            "2026-11-12T00:00:00, 2026-11-11T06:00"
    })
    void computesMissionGenerationCutoff(LocalDateTime now, LocalDateTime cutoff) {
        assertThat(StaleJobChecker.missionGenerationCutoff(now)).isEqualTo(cutoff);
    }

    @DisplayName("3분마다 서울 시간으로 실행하고, 다음 실행 전에 풀리는 staleJobChecker 락을 잡는다")
    @Test
    void runsEveryThreeMinutesWithLock() throws NoSuchMethodException {
        var check = StaleJobChecker.class.getMethod("check");

        assertThat(check.getAnnotation(Scheduled.class).cron()).isEqualTo("0 */3 * * * *");
        assertThat(check.getAnnotation(Scheduled.class).zone()).isEqualTo("Asia/Seoul");
        assertThat(check.getAnnotation(SchedulerLock.class).name()).isEqualTo("staleJobChecker");
        assertThat(check.getAnnotation(SchedulerLock.class).lockAtMostFor()).isEqualTo("PT179S");
    }
}
