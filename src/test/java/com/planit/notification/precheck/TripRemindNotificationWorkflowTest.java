package com.planit.notification.precheck;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripRemindNotificationWorkflowTest {

    @DisplayName("워크플로는 대상일의 Precheck 생성을 실행한다")
    @Test
    void executesPrecheckForTargetDate() {
        TripRemindPrecheckService precheckService =
                mock(TripRemindPrecheckService.class);
        TripRemindNotificationWorkflow workflow =
                new TripRemindNotificationWorkflow(precheckService);
        LocalDate targetDate = LocalDate.of(2026, 10, 16);
        when(precheckService.createIfAbsent(targetDate))
                .thenReturn(TripRemindPrecheckCreateResult.CREATED);

        TripRemindPrecheckCreateResult result =
                workflow.execute(targetDate);

        assertThat(result).isEqualTo(
                TripRemindPrecheckCreateResult.CREATED
        );
        verify(precheckService).createIfAbsent(targetDate);
    }
}
