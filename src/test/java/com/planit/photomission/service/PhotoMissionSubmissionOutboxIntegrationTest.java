package com.planit.photomission.service;

import com.planit.domain.Region;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.mq.job.AiJobType;
import com.planit.mq.outbox.MessageOutbox;
import com.planit.mq.outbox.MessageOutboxRepository;
import com.planit.photomission.domain.Mission;
import com.planit.photomission.domain.MissionGroupState;
import com.planit.photomission.domain.MissionScope;
import com.planit.photomission.domain.PhotoEvaluation;
import com.planit.photomission.messaging.PhotoEvaluationRequestedMessage;
import com.planit.photomission.repository.MissionPhotoRepository;
import com.planit.photomission.repository.PhotoEvaluationRepository;
import com.planit.repository.ImageFileRepository;
import com.planit.schedule.domain.Place;
import com.planit.schedule.domain.PlaceDetails;
import com.planit.schedule.domain.Schedule;
import com.planit.schedule.domain.ScheduleDay;
import com.planit.schedule.domain.ScheduleVisit;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class PhotoMissionSubmissionOutboxIntegrationTest {

    @Autowired
    private PhotoMissionSubmissionTransactionService submissionService;

    @MockitoSpyBean
    private MessageOutboxRepository outboxRepository;

    @Autowired
    private MissionPhotoRepository photoRepository;

    @Autowired
    private PhotoEvaluationRepository evaluationRepository;

    @Autowired
    private ImageFileRepository imageFileRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("photoMissionClock")
    private Clock photoMissionClock;

    @DisplayName("포토미션 판정을 접수하면 PHOTO_JUDGE Outbox를 같은 트랜잭션에 저장한다")
    @Test
    void savesPhotoJudgeOutboxWithQueuedEvaluation() {
        transactionTemplate.executeWithoutResult(status -> {
            Fixture fixture = createFixture();

            QueuedSubmission submission = submit(fixture);

            MessageOutbox outbox = outboxRepository.findAll().stream()
                    .filter(found -> found.getJobType() == AiJobType.PHOTO_JUDGE)
                    .filter(found -> found.getJobId()
                            .equals(submission.evaluationId()))
                    .findFirst()
                    .orElseThrow();
            PhotoEvaluation evaluation = evaluationRepository
                    .findById(submission.evaluationId())
                    .orElseThrow();
            PhotoEvaluationRequestedMessage payload = objectMapper.readValue(
                    outbox.getPayload(),
                    PhotoEvaluationRequestedMessage.class
            );

            assertThat(outbox.getJobId()).isEqualTo(submission.evaluationId());
            assertThat(payload.schemaVersion()).isEqualTo(1);
            assertThat(payload.evaluationId())
                    .isEqualTo(submission.evaluationId());
            assertThat(payload.missionPhotoId())
                    .isEqualTo(submission.photoId());
            assertThat(payload.deadlineAt())
                    .isEqualTo(evaluation.getDeadlineAt());

            status.setRollbackOnly();
        });
    }

    @DisplayName("Outbox 저장이 실패하면 사진과 QUEUED 판정도 함께 롤백한다")
    @Test
    void rollsBackPhotoAndEvaluationWhenOutboxEnqueueFails() {
        long imageCount = imageFileRepository.count();
        long photoCount = photoRepository.count();
        long evaluationCount = evaluationRepository.count();
        long outboxCount = outboxRepository.count();
        doThrow(new IllegalStateException("outbox save failed"))
                .when(outboxRepository).save(any(MessageOutbox.class));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                ignored -> submit(createFixture())
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox save failed");

        assertThat(imageFileRepository.count()).isEqualTo(imageCount);
        assertThat(photoRepository.count()).isEqualTo(photoCount);
        assertThat(evaluationRepository.count()).isEqualTo(evaluationCount);
        assertThat(outboxRepository.count()).isEqualTo(outboxCount);
    }

    private QueuedSubmission submit(Fixture fixture) {
        return submissionService.save(
                fixture.userPublicId().toString(),
                fixture.missionId(),
                UUID.randomUUID(),
                "missions/" + UUID.randomUUID() + ".jpg",
                "photo.jpg",
                "image/jpeg",
                1024,
                null,
                null
        );
    }

    private Fixture createFixture() {
        LocalDate tripDate = LocalDate.now(photoMissionClock);
        LocalDateTime now = LocalDateTime.now(photoMissionClock);
        Region region = entityManager.find(Region.class, 1L);
        Trip trip = new Trip(
                region,
                "아웃박스여행",
                tripDate,
                tripDate.plusDays(1),
                (byte) 4,
                tripDate.minusDays(1).atTime(23, 59, 59)
        );
        entityManager.persist(trip);
        User user = new User(null, UUID.randomUUID(), "Outbox테스트사용자");
        entityManager.persist(user);
        TripMember member = TripMember.createMember(trip, user);
        entityManager.persist(member);
        Schedule schedule = new Schedule(trip, now);
        entityManager.persist(schedule);
        ScheduleDay day = new ScheduleDay(schedule, (byte) 1, tripDate);
        entityManager.persist(day);
        Place place = new Place(
                region,
                new PlaceDetails(
                        "outbox_" + UUID.randomUUID(),
                        "첨성대",
                        "관광명소",
                        "경주시 인왕동",
                        null,
                        BigDecimal.valueOf(129.2190),
                        BigDecimal.valueOf(35.8347),
                        null,
                        null
                ),
                now
        );
        entityManager.persist(place);
        ScheduleVisit visit = new ScheduleVisit(
                day,
                place,
                1,
                "역사 취향 반영",
                now
        );
        entityManager.persist(visit);
        Mission mission = Mission.create(
                visit,
                1,
                MissionScope.GROUP,
                "유적지 전경 담기",
                "역사",
                now
        );
        entityManager.persist(mission);
        entityManager.persist(MissionGroupState.create(mission, now));
        entityManager.flush();
        return new Fixture(user.getPublicId(), mission.getId());
    }

    private record Fixture(UUID userPublicId, Long missionId) {
    }
}
