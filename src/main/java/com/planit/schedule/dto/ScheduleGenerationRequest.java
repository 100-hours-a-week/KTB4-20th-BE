package com.planit.schedule.dto;

import com.planit.schedule.domain.ScheduleGenerationReason;

/** 일정 생성 요청 본문. 본문이나 reason을 생략하면 일반 생성(GENERAL)으로 처리한다. */
public record ScheduleGenerationRequest(
        ScheduleGenerationReason reason
) {

    public ScheduleGenerationReason reasonOrDefault() {
        return reason == null ? ScheduleGenerationReason.GENERAL : reason;
    }
}
