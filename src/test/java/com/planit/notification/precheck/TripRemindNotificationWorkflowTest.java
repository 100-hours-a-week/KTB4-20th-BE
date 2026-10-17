package com.planit.notification.precheck;

import com.planit.notification.delivery.NotificationEventPublisher;
import com.planit.notification.delivery.NotificationPublishEvent;
import com.planit.notification.delivery.NotificationPublishTargetService;
import com.planit.notification.domain.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        NotificationPublishTargetService publishTargetService =
                mock(NotificationPublishTargetService.class);
        NotificationEventPublisher eventPublisher =
                mock(NotificationEventPublisher.class);
        TripRemindNotificationWorkflow workflow =
                new TripRemindNotificationWorkflow(
                        precheckService,
                        precheckStore,
                        generationService,
                        publishTargetService,
                        eventPublisher
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
        when(publishTargetService.findTodayUndelivered())
                .thenReturn(List.of());
        when(precheckStore.transition(
                targetDate,
                TripRemindNotificationWorkflowStatus.NOTI_GENERATED,
                TripRemindNotificationWorkflowStatus.DONE
        )).thenReturn(TripRemindPrecheckTransitionResult.UPDATED);

        TripRemindPrecheckCreateResult result =
                workflow.execute(targetDate);

        assertThat(result).isEqualTo(
                TripRemindPrecheckCreateResult.CREATED
        );
        verify(precheckService).createIfAbsent(targetDate);
        InOrder order = inOrder(
                generationService,
                precheckStore,
                publishTargetService
        );
        order.verify(generationService).generate(targetDate);
        order.verify(precheckStore).transition(
                targetDate,
                TripRemindNotificationWorkflowStatus.PRECHECKED,
                TripRemindNotificationWorkflowStatus.NOTI_GENERATED
        );
        order.verify(publishTargetService).findTodayUndelivered();
        order.verify(precheckStore).transition(
                targetDate,
                TripRemindNotificationWorkflowStatus.NOTI_GENERATED,
                TripRemindNotificationWorkflowStatus.DONE
        );
        verifyNoInteractions(eventPublisher);
    }

    @DisplayName("알림 생성 완료 상태는 미전달 알림을 발행한 뒤 DONE으로 전이한다")
    @Test
    void publishesAndCompletesGeneratedStatus() {
        TripRemindPrecheckService precheckService =
                mock(TripRemindPrecheckService.class);
        TripRemindPrecheckStore precheckStore =
                mock(TripRemindPrecheckStore.class);
        TripRemindNotificationGenerationService generationService =
                mock(TripRemindNotificationGenerationService.class);
        NotificationPublishTargetService publishTargetService =
                mock(NotificationPublishTargetService.class);
        NotificationEventPublisher eventPublisher =
                mock(NotificationEventPublisher.class);
        TripRemindNotificationWorkflow workflow =
                new TripRemindNotificationWorkflow(
                        precheckService,
                        precheckStore,
                        generationService,
                        publishTargetService,
                        eventPublisher
                );
        LocalDate targetDate = LocalDate.of(2026, 10, 16);
        NotificationPublishEvent first = event(16001L);
        NotificationPublishEvent second = event(16002L);
        when(precheckService.createIfAbsent(targetDate))
                .thenReturn(TripRemindPrecheckCreateResult.ALREADY_EXISTS);
        when(precheckStore.find(targetDate)).thenReturn(Optional.of(
                snapshot(TripRemindNotificationWorkflowStatus.NOTI_GENERATED)
        ));
        when(publishTargetService.findTodayUndelivered())
                .thenReturn(List.of(first, second));
        when(precheckStore.transition(
                targetDate,
                TripRemindNotificationWorkflowStatus.NOTI_GENERATED,
                TripRemindNotificationWorkflowStatus.DONE
        )).thenReturn(TripRemindPrecheckTransitionResult.UPDATED);

        assertThat(workflow.execute(targetDate)).isEqualTo(
                TripRemindPrecheckCreateResult.ALREADY_EXISTS
        );
        verifyNoInteractions(generationService);
        InOrder order = inOrder(
                publishTargetService,
                eventPublisher,
                precheckStore
        );
        order.verify(publishTargetService).findTodayUndelivered();
        order.verify(eventPublisher).publish(first);
        order.verify(eventPublisher).publish(second);
        order.verify(precheckStore).transition(
                targetDate,
                TripRemindNotificationWorkflowStatus.NOTI_GENERATED,
                TripRemindNotificationWorkflowStatus.DONE
        );
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
        NotificationPublishTargetService publishTargetService =
                mock(NotificationPublishTargetService.class);
        NotificationEventPublisher eventPublisher =
                mock(NotificationEventPublisher.class);
        TripRemindNotificationWorkflow workflow =
                new TripRemindNotificationWorkflow(
                        precheckService,
                        precheckStore,
                        generationService,
                        publishTargetService,
                        eventPublisher
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
        verifyNoInteractions(publishTargetService, eventPublisher);
    }

    @DisplayName("알림 발행이 실패하면 DONE으로 전이하지 않는다")
    @Test
    void doesNotCompleteWhenPublishFails() {
        TripRemindPrecheckService precheckService =
                mock(TripRemindPrecheckService.class);
        TripRemindPrecheckStore precheckStore =
                mock(TripRemindPrecheckStore.class);
        TripRemindNotificationGenerationService generationService =
                mock(TripRemindNotificationGenerationService.class);
        NotificationPublishTargetService publishTargetService =
                mock(NotificationPublishTargetService.class);
        NotificationEventPublisher eventPublisher =
                mock(NotificationEventPublisher.class);
        TripRemindNotificationWorkflow workflow =
                new TripRemindNotificationWorkflow(
                        precheckService,
                        precheckStore,
                        generationService,
                        publishTargetService,
                        eventPublisher
                );
        LocalDate targetDate = LocalDate.of(2026, 10, 16);
        NotificationPublishEvent event = event(16001L);
        when(precheckService.createIfAbsent(targetDate))
                .thenReturn(TripRemindPrecheckCreateResult.ALREADY_EXISTS);
        when(precheckStore.find(targetDate)).thenReturn(Optional.of(
                snapshot(TripRemindNotificationWorkflowStatus.NOTI_GENERATED)
        ));
        when(publishTargetService.findTodayUndelivered())
                .thenReturn(List.of(event));
        doThrow(new IllegalStateException("Redis 발행 실패"))
                .when(eventPublisher).publish(event);

        assertThatThrownBy(() -> workflow.execute(targetDate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis 발행 실패");
        verify(precheckStore, never()).transition(
                targetDate,
                TripRemindNotificationWorkflowStatus.NOTI_GENERATED,
                TripRemindNotificationWorkflowStatus.DONE
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

    private NotificationPublishEvent event(Long notificationId) {
        return new NotificationPublishEvent(
                UUID.randomUUID(),
                notificationId,
                NotificationType.TRIP_D1,
                "내일 여행이 시작돼요",
                "여행 일정과 날씨를 확인해 주세요.",
                OffsetDateTime.parse("2026-10-16T10:00:00+09:00")
        );
    }
}
