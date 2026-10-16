package com.planit.notification.precheck;

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

    public TripRemindPrecheckCreateResult execute(LocalDate targetDate) {
        TripRemindPrecheckCreateResult createResult =
                precheckService.createIfAbsent(targetDate);
        Optional<TripRemindPrecheckSnapshot> snapshot =
                precheckStore.find(targetDate);

        if (snapshot.isPresent()
                && snapshot.get().cache().status()
                == TripRemindNotificationWorkflowStatus.PRECHECKED) {
            generationService.generate(targetDate);
            transitionToGenerated(targetDate);
        }

        return createResult;
    }

    private void transitionToGenerated(LocalDate targetDate) {
        TripRemindPrecheckTransitionResult result = precheckStore.transition(
                targetDate,
                TripRemindNotificationWorkflowStatus.PRECHECKED,
                TripRemindNotificationWorkflowStatus.NOTI_GENERATED
        );
        if (result != TripRemindPrecheckTransitionResult.UPDATED) {
            throw new IllegalStateException(
                    "알림 생성 완료 상태로 전이할 수 없습니다: " + result
            );
        }
    }
}
