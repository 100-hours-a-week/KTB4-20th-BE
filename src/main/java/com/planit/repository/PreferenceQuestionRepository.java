package com.planit.repository;

import com.planit.domain.PreferenceQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreferenceQuestionRepository
        extends JpaRepository<PreferenceQuestion, Long> {

    List<PreferenceQuestion> findAllByOrderByDisplayOrderAsc();
}
