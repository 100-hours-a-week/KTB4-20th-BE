package com.planit.mq.job;

import com.planit.mq.outbox.MessageOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 작업 종료와 알림은 AiJobTerminationServiceTest, Outbox 쿼리는 MessageOutboxRepositoryTest에서 확인한다. */
class StaleJobFailureServiceTest {

    private final AiJobTerminationService terminationService = mock(AiJobTerminationService.class);
    private final MessageOutboxRepository outboxRepository = mock(MessageOutboxRepository.class);
    private final StaleJobFailureService service = new StaleJobFailureService(terminationService, outboxRepository);

    @DisplayName("작업을 STALE로 끝낸 뒤 남은 발행 대기 Outbox를 실패로 바꾼다")
    @Test
    void failsJobThenPendingOutbox() {
        when(terminationService.fail(AiJobType.SCHEDULE_GENERATE, 1L, "STALE")).thenReturn(true);

        assertThat(service.fail(AiJobType.SCHEDULE_GENERATE, 1L)).isTrue();

        var order = inOrder(terminationService, outboxRepository);
        order.verify(terminationService).fail(AiJobType.SCHEDULE_GENERATE, 1L, "STALE");
        order.verify(outboxRepository).markFailedPendingByJob(AiJobType.SCHEDULE_GENERATE, 1L, "STALE");
    }

    @DisplayName("그 사이 다른 경로가 작업을 끝냈으면 Outbox를 건드리지 않는다")
    @Test
    void skipsWhenAlreadyFinished() {
        when(terminationService.fail(AiJobType.PHOTO_MISSION_GENERATE, 2L, "STALE")).thenReturn(false);

        assertThat(service.fail(AiJobType.PHOTO_MISSION_GENERATE, 2L)).isFalse();

        verify(outboxRepository, never()).markFailedPendingByJob(any(), anyLong(), anyString());
    }
}
