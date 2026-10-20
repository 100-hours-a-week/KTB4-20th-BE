package com.planit.notification.precheck;

import com.planit.notification.delivery.NotificationEventPublisher;
import com.planit.notification.delivery.NotificationPublishTargetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TripRemindNotificationWorkflow {

    private final TripRemindPrecheckService precheckService;
    private final TripRemindPrecheckStore precheckStore;
    private final TripRemindNotificationGenerationService generationService;
    private final NotificationPublishTargetService publishTargetService;
    private final NotificationEventPublisher eventPublisher;

    public TripRemindPrecheckCreateResult execute(LocalDate targetDate) {
        TripRemindPrecheckCreateResult createResult =
                precheckService.createIfAbsent(targetDate);
        Optional<TripRemindPrecheckSnapshot> snapshot =
                precheckStore.find(targetDate);

        if (snapshot.isEmpty()) {
            return createResult;
        }

        switch (snapshot.get().cache().status()) {
            case PRECHECKED -> {
                generationService.generate(targetDate);
                transition(
                        targetDate,
                        TripRemindNotificationWorkflowStatus.PRECHECKED,
                        TripRemindNotificationWorkflowStatus.NOTI_GENERATED
                );
                publishAndComplete(targetDate);
            }
            case NOTI_GENERATED -> publishAndComplete(targetDate);
            case DONE -> {
            }
        }

        return createResult;
    }

    private void publishAndComplete(LocalDate targetDate) {
        publishTargetService.findTodayUndelivered()
                .forEach(eventPublisher::publish);
        transition(
                targetDate,
                TripRemindNotificationWorkflowStatus.NOTI_GENERATED,
                TripRemindNotificationWorkflowStatus.DONE
        );
    }

    private void transition(
            LocalDate targetDate,
            TripRemindNotificationWorkflowStatus expectedStatus,
            TripRemindNotificationWorkflowStatus nextStatus
    ) {
        TripRemindPrecheckTransitionResult result = precheckStore.transition(
                targetDate,
                expectedStatus,
                nextStatus
        );
        if (result != TripRemindPrecheckTransitionResult.UPDATED) {
            throw new IllegalStateException(
                    "알림 작업 상태를 전이할 수 없습니다: " + result
            );
        }
    }
}
