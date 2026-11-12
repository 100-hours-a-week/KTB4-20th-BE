package com.planit.schedule.service;

import com.planit.ai.AiPlaceSelectionRequest;

/**
 * 일정 생성 요청을 검증하고 만든 값.
 *
 * @param requestedByMemberId 요청한 방장의 trip_members.id. 비동기 접수 작업의 요청자로 저장한다.
 */
public record ScheduleGenerationPreparation(
        AiPlaceSelectionRequest request,
        ScheduleGenerationMode mode,
        long regionId,
        long tripDayCount,
        long requestedByMemberId
) {
}
