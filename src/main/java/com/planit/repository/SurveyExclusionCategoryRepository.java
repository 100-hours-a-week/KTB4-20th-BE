package com.planit.repository;

import com.planit.domain.SurveyExclusionCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SurveyExclusionCategoryRepository
        extends JpaRepository<SurveyExclusionCategory, Long> {

    List<SurveyExclusionCategory> findAllByOrderByIdAsc();
}
