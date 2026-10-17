package com.planit.notification.domain;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 이상 기후 알림에 표시할 대표 날씨 조건이다.
 * 선언 순서가 심각도 우선순위이며, 여러 사유가 겹치면 앞선 조건 하나만 사용한다.
 */
public enum WeatherCondition {
    TYPHOON("태풍"),
    HEAVY_RAIN("폭우"),
    HEAVY_SNOW("폭설"),
    HEAT("폭염"),
    COLD("한파"),
    RAIN("비"),
    YELLOW_DUST("황사"),
    FINE_DUST("미세먼지");

    private final String label;

    WeatherCondition(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** AI precheck 사유의 detail(예: heavy_rain)을 날씨 조건으로 변환한다. 알 수 없는 값은 비어 있다. */
    public static Optional<WeatherCondition> fromDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return Optional.empty();
        }
        String normalized = detail.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(condition -> condition.name().equals(normalized))
                .findFirst();
    }

    /** 여러 사유 중 가장 심각한 날씨 조건을 고른다. 인식 가능한 사유가 없으면 비어 있다. */
    public static Optional<WeatherCondition> mostSevere(Collection<String> details) {
        Objects.requireNonNull(details, "날씨 사유 목록은 필수입니다");
        return details.stream()
                .map(WeatherCondition::fromDetail)
                .flatMap(Optional::stream)
                .min(Comparator.naturalOrder());
    }
}
