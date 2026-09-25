package com.planit.repository;

import com.planit.domain.ImageFile;
import com.planit.domain.PreferenceQuestion;
import com.planit.domain.Region;
import com.planit.domain.Survey;
import com.planit.domain.SurveyAnswer;
import com.planit.domain.SurveyExcludedCategory;
import com.planit.domain.SurveyExclusionCategory;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SurveySummaryRepositoryTest {

    @Autowired
    private ImageFileRepository imageFileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    @Autowired
    private SurveyRepository surveyRepository;

    @Autowired
    private PreferenceQuestionRepository preferenceQuestionRepository;

    @Autowired
    private SurveyAnswerRepository surveyAnswerRepository;

    @Autowired
    private SurveyExclusionCategoryRepository exclusionCategoryRepository;

    @Autowired
    private SurveyExcludedCategoryRepository excludedCategoryRepository;

    @Test
    void loadsSubmittedSurveyDataUsedBySummary() {
        Trip trip = createTrip();
        TripMember host = tripMemberRepository.save(
                TripMember.createHost(trip, createUser("설문방장"))
        );
        TripMember member = tripMemberRepository.save(
                TripMember.createMember(trip, createUser("설문멤버"))
        );
        TripMember waitingMember = tripMemberRepository.save(
                TripMember.createMember(trip, createUser("설문대기"))
        );

        Survey hostSurvey = submittedSurvey(host);
        Survey memberSurvey = submittedSurvey(member);
        surveyRepository.save(new Survey(waitingMember, LocalDateTime.now()));

        PreferenceQuestion firstQuestion = preferenceQuestionRepository
                .findById(1L)
                .orElseThrow();
        PreferenceQuestion secondQuestion = preferenceQuestionRepository
                .findById(2L)
                .orElseThrow();
        surveyAnswerRepository.saveAll(List.of(
                new SurveyAnswer(hostSurvey, secondQuestion, 4),
                new SurveyAnswer(memberSurvey, firstQuestion, 5)
        ));

        SurveyExclusionCategory seafood = exclusionCategoryRepository
                .findById(3L)
                .orElseThrow();
        excludedCategoryRepository.saveAll(List.of(
                new SurveyExcludedCategory(hostSurvey, seafood),
                new SurveyExcludedCategory(memberSurvey, seafood)
        ));

        List<Survey> submittedSurveys = surveyRepository
                .findByTripMemberInAndSubmittedAtIsNotNull(
                        List.of(host, member, waitingMember)
                );
        List<SurveyAnswer> answers = surveyAnswerRepository
                .findBySurveyInOrderByPreferenceQuestionDisplayOrderAsc(
                        submittedSurveys
                );
        List<SurveyExcludedCategory> exclusions = excludedCategoryRepository
                .findBySurveyInOrderByExclusionCategoryIdAsc(
                        submittedSurveys
                );

        assertThat(submittedSurveys)
                .containsExactlyInAnyOrder(hostSurvey, memberSurvey);
        assertThat(answers)
                .extracting(answer -> answer.getPreferenceQuestion()
                        .getDisplayOrder())
                .containsExactly((short) 1, (short) 2);
        assertThat(exclusions)
                .extracting(excluded -> excluded.getExclusionCategory()
                        .getCode())
                .containsExactly("SEAFOOD", "SEAFOOD");
    }

    private User createUser(String username) {
        ImageFile imageFile = imageFileRepository.findAll().stream()
                .findFirst()
                .orElseThrow();
        return userRepository.save(new User(
                imageFile,
                UUID.randomUUID(),
                username
        ));
    }

    private Trip createTrip() {
        LocalDate tripDate = LocalDate.of(2099, 9, 26);
        Region region = regionRepository.findById(1L)
                .orElseThrow();
        return tripRepository.save(new Trip(
                region,
                "설문 테스트",
                tripDate,
                (byte) 4,
                tripDate.minusDays(1).atTime(23, 59, 59)
        ));
    }

    private Survey submittedSurvey(TripMember member) {
        LocalDateTime submittedAt = LocalDateTime.of(2099, 9, 20, 12, 0);
        Survey survey = new Survey(member, submittedAt.minusMinutes(5));
        survey.submit(submittedAt);
        return surveyRepository.save(survey);
    }
}
