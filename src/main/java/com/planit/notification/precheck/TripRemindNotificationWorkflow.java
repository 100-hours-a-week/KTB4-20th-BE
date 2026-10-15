package com.planit.notification.precheck;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class TripRemindNotificationWorkflow {

    private final TripRemindPrecheckService precheckService;

    public TripRemindPrecheckCreateResult execute(LocalDate targetDate) {
        return precheckService.createIfAbsent(targetDate);
    }
}
