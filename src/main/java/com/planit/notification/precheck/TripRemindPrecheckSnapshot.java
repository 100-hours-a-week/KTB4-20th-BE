package com.planit.notification.precheck;

import java.util.Objects;

public record TripRemindPrecheckSnapshot(
        TripRemindPrecheckCache cache,
        long ttlSeconds
) {

    public TripRemindPrecheckSnapshot {
        Objects.requireNonNull(cache, "사전 점검 캐시는 필수입니다");
    }
}
