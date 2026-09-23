package com.planit.repository;

import com.planit.domain.SurveyExclusionCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyExclusionCategoryRepository
        extends JpaRepository<SurveyExclusionCategory, Long> {
}
