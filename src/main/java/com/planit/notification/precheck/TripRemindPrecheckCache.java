package com.planit.notification.precheck;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.planit.ai.AiPrecheckResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record TripRemindPrecheckCache(
        @JsonProperty("generated_at")
        Instant generatedAt,
        TripRemindNotificationWorkflowStatus status,
        List<Forecast> forecasts
) {

    public TripRemindPrecheckCache {
        Objects.requireNonNull(generatedAt, "외부 요인 사전 점검 생성 시각은 필수입니다");
        Objects.requireNonNull(status, "여행 리마인드 알림 작업 상태는 필수입니다");
        forecasts = List.copyOf(Objects.requireNonNull(
                forecasts,
                "외부 요인 점검 목록은 필수입니다"
        ));
    }

    public static TripRemindPrecheckCache from(
            AiPrecheckResponse.Data data
    ) {
        Objects.requireNonNull(data, "AI 외부 요인 사전 점검 데이터는 필수입니다");

        return new TripRemindPrecheckCache(
                data.generatedAt(),
                TripRemindNotificationWorkflowStatus.PRECHECKED,
                data.forecasts().stream()
                        .map(Forecast::from)
                        .toList()
        );
    }

    public record Forecast(
            LocalDate date,
            List<RegionForecast> regions
    ) {

        public Forecast {
            Objects.requireNonNull(date, "외부 요인 점검 날짜는 필수입니다");
            regions = List.copyOf(Objects.requireNonNull(
                    regions,
                    "지역별 외부 요인 점검 목록은 필수입니다"
            ));
        }

        private static Forecast from(
                AiPrecheckResponse.Forecast forecast
        ) {
            return new Forecast(
                    forecast.date(),
                    forecast.regions().stream()
                            .map(RegionForecast::from)
                            .toList()
            );
        }
    }

    public record RegionForecast(
            String region,
            @JsonProperty("has_issue")
            boolean hasIssue,
            List<Reason> reasons
    ) {

        public RegionForecast {
            Objects.requireNonNull(region, "외부 요인 점검 지역 코드는 필수입니다");
            reasons = List.copyOf(Objects.requireNonNull(
                    reasons,
                    "외부 요인 제약 사유 목록은 필수입니다"
            ));
        }

        private static RegionForecast from(
                AiPrecheckResponse.RegionForecast forecast
        ) {
            return new RegionForecast(
                    forecast.region(),
                    forecast.hasIssue(),
                    forecast.reasons().stream()
                            .map(Reason::from)
                            .toList()
            );
        }
    }

    public record Reason(
            String category,
            String detail
    ) {

        private static Reason from(
                AiPrecheckResponse.Reason reason
        ) {
            return new Reason(reason.category(), reason.detail());
        }
    }
}
