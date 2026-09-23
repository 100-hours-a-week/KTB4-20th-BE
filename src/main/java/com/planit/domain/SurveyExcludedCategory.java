package com.planit.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "survey_excluded_categories")
public class SurveyExcludedCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @ManyToOne(optional = false)
    @JoinColumn(name = "exclusion_category_id", nullable = false)
    private SurveyExclusionCategory exclusionCategory;

    protected SurveyExcludedCategory() {
    }

    public SurveyExcludedCategory(
            Survey survey,
            SurveyExclusionCategory exclusionCategory
    ) {
        this.survey = survey;
        this.exclusionCategory = exclusionCategory;
    }

    public SurveyExclusionCategory getExclusionCategory() {
        return exclusionCategory;
    }
}
