package com.planit.notification.aijob;

import com.planit.domain.User;
import com.planit.mq.job.AiJobType;
import com.planit.notification.delivery.NotificationEventPublisher;
import com.planit.notification.delivery.NotificationPublishEvent;
import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationType;
import com.planit.notification.repository.NotificationStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiJobNotificationServiceTest {

    private final NotificationStore notificationStore = mock(NotificationStore.class);
    private final NotificationEventPublisher eventPublisher = mock(NotificationEventPublisher.class);
    private final AiJobNotificationService service =
            new AiJobNotificationService(notificationStore, eventPublisher);

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @DisplayName("알림은 바로 저장하고, 실시간 발행은 커밋된 뒤 저장된 알림 ID로 한다")
    @Test
    void publishesAfterCommitWithSavedId() {
        Notification first = notification(1L);
        Notification second = notification(2L);
        when(notificationStore.saveIdempotently(first)).thenReturn(101L);
        when(notificationStore.saveIdempotently(second)).thenReturn(102L);

        service.saveAndPublishAfterCommit(List.of(first, second));

        verify(notificationStore).saveIdempotently(first);
        verify(notificationStore).saveIdempotently(second);
        verify(eventPublisher, never()).publish(any());

        commit();

        ArgumentCaptor<NotificationPublishEvent> events = ArgumentCaptor.forClass(NotificationPublishEvent.class);
        verify(eventPublisher, times(2)).publish(events.capture());
        assertThat(events.getAllValues())
                .extracting(NotificationPublishEvent::notificationId)
                .containsExactly(101L, 102L);
        assertThat(events.getAllValues().getFirst().recipientPublicId())
                .isEqualTo(first.getRecipient().getPublicId());
    }

    @DisplayName("한 알림의 실시간 발행이 실패해도 나머지 알림은 발행한다")
    @Test
    void continuesPublishingAfterFailure() {
        Notification first = notification(1L);
        Notification second = notification(2L);
        when(notificationStore.saveIdempotently(first)).thenReturn(101L);
        when(notificationStore.saveIdempotently(second)).thenReturn(102L);
        doThrow(new IllegalStateException("redis down"))
                .doNothing()
                .when(eventPublisher).publish(any());

        service.saveAndPublishAfterCommit(List.of(first, second));
        commit();

        verify(eventPublisher, times(2)).publish(any());
    }

    @DisplayName("알릴 대상이 없으면 커밋 후 작업을 등록하지 않는다")
    @Test
    void registersNothingWithoutNotifications() {
        service.saveAndPublishAfterCommit(List.of());

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    private void commit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }

    private Notification notification(Long recipientId) {
        User recipient = new User(null, UUID.randomUUID(), "알림수신자" + recipientId);
        ReflectionTestUtils.setField(recipient, "id", recipientId);
        return Notification.createAiJobTripNotification(
                recipient,
                AiJobType.SCHEDULE_GENERATE,
                42L,
                1001L,
                NotificationType.SCHEDULE_GENERATION_SUCCEEDED,
                "일정이 완성됐어요",
                "경주 여행의 일정 3가지를 확인해 보세요.",
                LocalDateTime.of(2026, 11, 10, 10, 0)
        );
    }
}
