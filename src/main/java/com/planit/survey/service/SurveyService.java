package com.planit.survey.service;

import com.planit.survey.dto.SurveyResponse;
import com.planit.survey.dto.SurveySaveRequest;
import com.planit.survey.dto.SurveySummaryResponse;

public interface SurveyService {

    SurveyResponse getMySurvey(String userPublicId, Long tripId);

    SurveySummaryResponse getSurveySummary(
            String userPublicId,
            Long tripId
    );

    SurveyResponse saveMySurvey(
            String userPublicId,
            Long tripId,
            SurveySaveRequest request
    );
}
