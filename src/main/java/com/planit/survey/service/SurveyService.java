package com.planit.survey.service;

import com.planit.survey.dto.SurveyResponse;
import com.planit.survey.dto.SurveySaveRequest;

public interface SurveyService {

    SurveyResponse getMySurvey(String userPublicId, Long tripId);

    SurveyResponse saveMySurvey(
            String userPublicId,
            Long tripId,
            SurveySaveRequest request
    );
}
