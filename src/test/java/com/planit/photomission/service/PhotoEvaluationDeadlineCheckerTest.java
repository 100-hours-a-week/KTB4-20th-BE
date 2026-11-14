package com.planit.photomission.service;

import com.planit.photomission.repository.PhotoEvaluationRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhotoEvaluationDeadlineCheckerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 21, 0);

    private final PhotoEvaluationRepository repository = mock(PhotoEvaluationRepository.class);
    private final PhotoEvaluationFailureService failureService = mock(PhotoEvaluationFailureService.class);
    private final PhotoEvaluationDeadlineChecker checker = new PhotoEvaluationDeadlineChecker(
            repository,
            failureService,
            Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL)
    );

    @DisplayName("deadline이 지난 QUEUED 판정을 실패 처리한다")
    @Test
    void failsExpiredEvaluations() {
        when(repository.findQueuedIdsDeadlineAtOrBefore(NOW)).thenReturn(List.of(51L, 52L));

        checker.check();

        verify(failureService).failDeadline(51L);
        verify(failureService).failDeadline(52L);
    }

    @DisplayName("한 작업의 실패 처리에서 오류가 나도 다음 deadline 작업을 계속 처리한다")
    @Test
    void continuesAfterFailure() {
        when(repository.findQueuedIdsDeadlineAtOrBefore(NOW)).thenReturn(List.of(51L, 52L));
        doThrow(new IllegalStateException("db down")).when(failureService).failDeadline(51L);

        checker.check();

        verify(failureService).failDeadline(52L);
    }

    @DisplayName("3분마다 실행하며 별도 ShedLock을 사용한다")
    @Test
    void runsEveryThreeMinutes() throws NoSuchMethodException {
        var check = PhotoEvaluationDeadlineChecker.class.getMethod("check");

        org.assertj.core.api.Assertions.assertThat(check.getAnnotation(Scheduled.class).cron())
                .isEqualTo("0 */3 * * * *");
        org.assertj.core.api.Assertions.assertThat(check.getAnnotation(Scheduled.class).zone())
                .isEqualTo("Asia/Seoul");
        org.assertj.core.api.Assertions.assertThat(check.getAnnotation(SchedulerLock.class).name())
                .isEqualTo("photoEvaluationDeadlineChecker");
    }
}
