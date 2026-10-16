package com.planit.notification.precheck;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TripRemindNotificationWorkflowTest {

    @DisplayName("워크플로는 대상일의 Precheck 생성을 실행한다")
    @Test
    void executesPrecheckForTargetDate() {
        TripRemindPrecheckService precheckService =
                mock(TripRemindPrecheckService.class);
        TripRemindPrecheckStore precheckStore =
                mock(TripRemindPrecheckStore.class);
        TripRemindNotificationGenerationService generationService =
                mock(TripRemindNotificationGenerationService.class);
        TripRemindNotificationWorkflow workflow =
                new TripRemindNotificationWorkflow(
                        precheckService,
                        precheckStore,
                        generationService
                );
        LocalDate targetDate = LocalDate.of(2026, 10, 16);
        when(precheckService.createIfAbsent(targetDate))
                .thenReturn(TripRemindPrecheckCreateResult.CREATED);
        when(precheckStore.find(targetDate)).thenReturn(Optional.of(
                snapshot(TripRemindNotificationWorkflowStatus.PRECHECKED)
        ));
        when(precheckStore.transition(
                targetDate,
                TripRemindNotificationWorkflowStatus.PRECHECKED,
                TripRemindNotificationWorkflowStatus.NOTI_GENERATED
        )).thenReturn(TripRemindPrecheckTransitionResult.UPDATED);

        TripRemindPrecheckCreateResult result =
                workflow.execute(targetDate);

        assertThat(result).isEqualTo(
                TripRemindPrecheckCreateResult.CREATED
        );
        verify(precheckService).createIfAbsent(targetDate);
        InOrder order = inOrder(generationService, precheckStore);
        order.verify(generationService).generate(targetDate);
        order.verify(precheckStore).transition(
                targetDate,
                TripRemindNotificationWorkflowStatus.PRECHECKED,
                TripRemindNotificationWorkflowStatus.NOTI_GENERATED
        );
    }

    @DisplayName("이미 알림 생성이 끝난 작업은 후속 구현 전까지 종료한다")
    @Test
    void stopsAtGeneratedStatus() {
        TripRemindPrecheckService precheckService =
                mock(TripRemindPrecheckService.class);
        TripRemindPrecheckStore precheckStore =
                mock(TripRemindPrecheckStore.class);
        TripRemindNotificationGenerationService generationService =
                mock(TripRemindNotificationGenerationService.class);
        TripRemindNotificationWorkflow workflow =
                new TripRemindNotificationWorkflow(
                        precheckService,
                        precheckStore,
                        generationService
                );
        LocalDate targetDate = LocalDate.of(2026, 10, 16);
        when(precheckService.createIfAbsent(targetDate))
                .thenReturn(TripRemindPrecheckCreateResult.ALREADY_EXISTS);
        when(precheckStore.find(targetDate)).thenReturn(Optional.of(
                snapshot(TripRemindNotificationWorkflowStatus.NOTI_GENERATED)
        ));

        assertThat(workflow.execute(targetDate)).isEqualTo(
                TripRemindPrecheckCreateResult.ALREADY_EXISTS
        );
        verifyNoInteractions(generationService);
    }

    @DisplayName("알림 저장이 실패하면 Redis 상태를 전이하지 않는다")
    @Test
    void doesNotTransitionWhenGenerationFails() {
        TripRemindPrecheckService precheckService =
                mock(TripRemindPrecheckService.class);
        TripRemindPrecheckStore precheckStore =
                mock(TripRemindPrecheckStore.class);
        TripRemindNotificationGenerationService generationService =
                mock(TripRemindNotificationGenerationService.class);
        TripRemindNotificationWorkflow workflow =
                new TripRemindNotificationWorkflow(
                        precheckService,
                        precheckStore,
                        generationService
                );
        LocalDate targetDate = LocalDate.of(2026, 10, 16);
        when(precheckService.createIfAbsent(targetDate))
                .thenReturn(TripRemindPrecheckCreateResult.ALREADY_EXISTS);
        when(precheckStore.find(targetDate)).thenReturn(Optional.of(
                snapshot(TripRemindNotificationWorkflowStatus.PRECHECKED)
        ));
        doThrow(new IllegalStateException("DB 저장 실패"))
                .when(generationService).generate(targetDate);

        assertThatThrownBy(() -> workflow.execute(targetDate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DB 저장 실패");
        verify(precheckStore, never()).transition(
                targetDate,
                TripRemindNotificationWorkflowStatus.PRECHECKED,
                TripRemindNotificationWorkflowStatus.NOTI_GENERATED
        );
    }

    private TripRemindPrecheckSnapshot snapshot(
            TripRemindNotificationWorkflowStatus status
    ) {
        return new TripRemindPrecheckSnapshot(
                new TripRemindPrecheckCache(
                        Instant.parse("2026-10-15T01:00:00Z"),
                        status,
                        List.of()
                ),
                1_000L
        );
    }
}
