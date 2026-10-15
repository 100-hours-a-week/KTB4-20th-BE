package com.planit.notification.precheck;

import com.planit.ai.AiPrecheckClient;
import com.planit.ai.AiPrecheckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TripRemindPrecheckService {

    private final AiPrecheckClient aiPrecheckClient;
    private final TripRemindPrecheckStore precheckStore;

    public TripRemindPrecheckCreateResult createIfAbsent(
            LocalDate targetDate
    ) {
        Objects.requireNonNull(targetDate, "여행 시작 대상일은 필수입니다");

        if (precheckStore.find(targetDate).isPresent()) {
            return TripRemindPrecheckCreateResult.ALREADY_EXISTS;
        }

        AiPrecheckResponse response = aiPrecheckClient.precheck();
        TripRemindPrecheckCache cache = TripRemindPrecheckCache.from(
                response.data()
        );

        return precheckStore.create(targetDate, cache);
    }
}
