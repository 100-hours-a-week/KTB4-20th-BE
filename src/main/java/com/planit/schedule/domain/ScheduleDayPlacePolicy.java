package com.planit.schedule.domain;

public final class ScheduleDayPlacePolicy {

    private static final int DEFAULT_GENERATED_PLACE_MIN_COUNT = 5;
    private static final int DEFAULT_EDITABLE_PLACE_MIN_COUNT = 3;
    private static final int LAST_DAY_PLACE_MIN_COUNT = 2;

    private ScheduleDayPlacePolicy() {
    }

    public static int minimumGeneratedPlaces(int dayNumber, int dayCount) {
        return isLastDayOfMultiDay(dayNumber, dayCount)
                ? LAST_DAY_PLACE_MIN_COUNT
                : DEFAULT_GENERATED_PLACE_MIN_COUNT;
    }

    public static int minimumEditablePlaces(int dayNumber, int dayCount) {
        return isLastDayOfMultiDay(dayNumber, dayCount)
                ? LAST_DAY_PLACE_MIN_COUNT
                : DEFAULT_EDITABLE_PLACE_MIN_COUNT;
    }

    private static boolean isLastDayOfMultiDay(int dayNumber, int dayCount) {
        return dayCount >= 2 && dayNumber == dayCount;
    }
}
