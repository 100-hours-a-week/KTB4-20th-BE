package com.planit.repository;

import com.planit.domain.Survey;
import com.planit.domain.SurveyAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SurveyAnswerRepository
        extends JpaRepository<SurveyAnswer, Long> {

    List<SurveyAnswer> findBySurveyOrderByPreferenceQuestionIdAsc(Survey survey);

    void deleteBySurvey(Survey survey);
}
