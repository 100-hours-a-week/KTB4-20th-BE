package com.planit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "preference_questions")
public class PreferenceQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "category_code", nullable = false, length = 30)
    private String categoryCode;

    @Column(name = "question_text", nullable = false, length = 300)
    private String questionText;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    protected PreferenceQuestion() {
    }

    public Long getId() {
        return id;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }
}
