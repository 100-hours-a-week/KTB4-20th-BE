package com.planit.notification.aijob;

import com.planit.notification.delivery.NotificationEventPublisher;
import com.planit.notification.delivery.NotificationPublishEvent;
import com.planit.notification.domain.Notification;
import com.planit.notification.repository.NotificationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * AI 작업 결과 알림을 작업 상태 변경과 같은 트랜잭션에서 저장하고, 커밋된 뒤에 실시간으로 발행한다.
 * 롤백되면 알림도 남지 않고 발행하지 않는다.
 */
@Service
public class AiJobNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AiJobNotificationService.class);

    private final NotificationStore notificationStore;
    private final NotificationEventPublisher eventPublisher;

    public AiJobNotificationService(
            NotificationStore notificationStore,
            NotificationEventPublisher eventPublisher
    ) {
        this.notificationStore = notificationStore;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 작업 상태를 바꾼 트랜잭션 안에서 호출한다. 트랜잭션 없이 부르면 예외가 난다.
     * 중복 방지 키가 같은 알림이 이미 있으면 새로 저장하지 않는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveAndPublishAfterCommit(List<Notification> notifications) {
        List<NotificationPublishEvent> events = notifications.stream()
                .map(notification -> NotificationPublishEvent.from(
                        notification,
                        notificationStore.saveIdempotently(notification)
                ))
                .toList();
        if (events.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                events.forEach(AiJobNotificationService.this::publish);
            }
        });
    }

    /** 실시간 발행에 실패해도 알림은 저장돼 있으므로 사용자가 다시 접속하면 미전달 알림으로 받는다. */
    private void publish(NotificationPublishEvent event) {
        try {
            eventPublisher.publish(event);
        } catch (RuntimeException e) {
            log.warn("AI 작업 알림 실시간 발행 실패, 미전달 알림으로 복구됨 notificationId={}",
                    event.notificationId(), e);
        }
    }
}
