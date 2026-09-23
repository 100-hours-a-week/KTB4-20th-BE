package com.planit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "survey_answers")
public class SurveyAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @ManyToOne(optional = false)
    @JoinColumn(name = "preference_question_id", nullable = false)
    private PreferenceQuestion preferenceQuestion;

    @Column(name = "score", nullable = false)
    private int score;

    protected SurveyAnswer() {
    }

    public SurveyAnswer(
            Survey survey,
            PreferenceQuestion preferenceQuestion,
            int score
    ) {
        this.survey = survey;
        this.preferenceQuestion = preferenceQuestion;
        this.score = score;
    }

    public PreferenceQuestion getPreferenceQuestion() {
        return preferenceQuestion;
    }

    public int getScore() {
        return score;
    }
}
