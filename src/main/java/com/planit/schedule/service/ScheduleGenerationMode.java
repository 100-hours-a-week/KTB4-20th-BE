package com.planit.schedule.service;

/** 현재 일정 존재 여부와 요청 사유로 결정한 실제 생성 방식이다. */
public enum ScheduleGenerationMode {
    /** 확정 일정이 없는 여행의 최초 생성 */
    INITIAL,
    /** 기존 일정을 대체하고 재생성 횟수를 차감하는 일반 재생성 */
    GENERAL_REGENERATION,
    /** 이상 기후 알림을 받은 여행의 기존 일정을 대체하며 횟수를 차감하지 않는 재생성 */
    WEATHER_REGENERATION
}
