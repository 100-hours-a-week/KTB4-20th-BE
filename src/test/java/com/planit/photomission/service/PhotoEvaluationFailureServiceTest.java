package com.planit.photomission.service;

import com.planit.mq.job.AiJobType;
import com.planit.mq.outbox.MessageOutboxRepository;
import com.planit.photomission.repository.PhotoEvaluationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhotoEvaluationFailureServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 21, 0);

    private final PhotoEvaluationRepository evaluationRepository = mock(PhotoEvaluationRepository.class);
    private final MessageOutboxRepository outboxRepository = mock(MessageOutboxRepository.class);
    private final PhotoEvaluationFailureService service = new PhotoEvaluationFailureService(
            evaluationRepository,
            outboxRepository,
            Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL)
    );

    @DisplayName("요청 DLQ 작업은 x-retry-count 기반 실행 차수와 실패 원인을 저장한다")
    @Test
    void failsFromDlq() {
        when(evaluationRepository.failQueued(51L, 4, "AI_TIMEOUT", NOW)).thenReturn(1);

        assertThat(service.failFromDlq(51L, 4, "AI_TIMEOUT")).isTrue();

        verify(evaluationRepository).failQueued(51L, 4, "AI_TIMEOUT", NOW);
    }

    @DisplayName("deadline을 넘긴 작업은 실행 차수를 바꾸지 않고 실패 처리한 뒤 PENDING Outbox를 중단한다")
    @Test
    void failsDeadlineAndPendingOutbox() {
        when(evaluationRepository.failQueuedPreservingAttempt(51L, "PROCESSING_TIMEOUT", NOW)).thenReturn(1);

        assertThat(service.failDeadline(51L)).isTrue();

        verify(outboxRepository).markFailedPendingByJob(
                AiJobType.PHOTO_JUDGE,
                51L,
                "PROCESSING_TIMEOUT"
        );
    }

    @DisplayName("이미 종료된 deadline 작업은 Outbox를 변경하지 않는다")
    @Test
    void skipsPendingOutboxWhenAlreadyFinished() {
        when(evaluationRepository.failQueuedPreservingAttempt(51L, "PROCESSING_TIMEOUT", NOW)).thenReturn(0);

        assertThat(service.failDeadline(51L)).isFalse();

        verify(outboxRepository, never()).markFailedPendingByJob(
                AiJobType.PHOTO_JUDGE,
                51L,
                "PROCESSING_TIMEOUT"
        );
    }
}
