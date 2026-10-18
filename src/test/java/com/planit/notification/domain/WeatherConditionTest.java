package com.planit.notification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherConditionTest {

    @DisplayName("AI 사유 detail을 대소문자와 관계없이 날씨 조건으로 변환한다")
    @Test
    void convertsDetailIgnoringCase() {
        assertThat(WeatherCondition.fromDetail("heavy_rain"))
                .contains(WeatherCondition.HEAVY_RAIN);
        assertThat(WeatherCondition.fromDetail(" FINE_DUST "))
                .contains(WeatherCondition.FINE_DUST);
        assertThat(WeatherCondition.fromDetail("unknown")).isEmpty();
        assertThat(WeatherCondition.fromDetail(null)).isEmpty();
    }

    @DisplayName("여러 사유 중 우선순위가 가장 높은 날씨 조건을 고른다")
    @Test
    void picksMostSevereCondition() {
        assertThat(WeatherCondition.mostSevere(
                List.of("rain", "typhoon", "heat", "unknown")
        )).contains(WeatherCondition.TYPHOON);
        assertThat(WeatherCondition.mostSevere(List.of("unknown"))).isEmpty();
    }
}
