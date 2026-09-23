package com.planit.repository;

import com.planit.domain.Survey;
import com.planit.domain.SurveyExcludedCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SurveyExcludedCategoryRepository
        extends JpaRepository<SurveyExcludedCategory, Long> {

    List<SurveyExcludedCategory>
    findBySurveyOrderByExclusionCategoryIdAsc(Survey survey);

    void deleteBySurvey(Survey survey);
}
