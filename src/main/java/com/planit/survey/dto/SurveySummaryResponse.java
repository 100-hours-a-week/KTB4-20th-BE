package com.planit.survey.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SurveySummaryResponse(
        String tripId,
        OffsetDateTime deadlineAt,
        int activeMemberCount,
        int submittedCount,
        int progressPercent,
        boolean allSubmitted,
        boolean mySurveySubmitted,
        boolean deadlinePassed,
        List<MemberSubmission> memberSubmissions,
        List<CategoryAverage> categoryAverages,
        List<ExcludedCategory> excludedCategories
) {
    public SurveySummaryResponse {
        memberSubmissions = List.copyOf(memberSubmissions);
        categoryAverages = List.copyOf(categoryAverages);
        excludedCategories = List.copyOf(excludedCategories);
    }

    public record MemberSubmission(
            UUID userPublicId,
            boolean submitted
    ) {
    }

    public record CategoryAverage(
            String categoryCode,
            double averageScore,
            int preferencePercent
    ) {
    }

    public record ExcludedCategory(
            String code,
            String name
    ) {
    }
}
