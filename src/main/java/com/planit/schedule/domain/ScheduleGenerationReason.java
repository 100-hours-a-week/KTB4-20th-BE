package com.planit.schedule.domain;

/** 일정 생성 요청 사유. 날씨 사유 재생성은 사용자 재생성 횟수에 포함하지 않는다. */
public enum ScheduleGenerationReason {
    GENERAL,
    WEATHER
}
