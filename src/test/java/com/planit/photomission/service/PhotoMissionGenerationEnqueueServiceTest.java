package com.planit.photomission.service;

import com.planit.domain.Region;
import com.planit.domain.Trip;
import com.planit.domain.User;
import com.planit.domain.TripMember;
import com.planit.mq.outbox.MessageOutbox;
import com.planit.mq.outbox.MessageOutboxRepository;
import com.planit.mq.outbox.MessageOutboxStatus;
import com.planit.mq.outbox.OutboxPublisher;
import com.planit.photomission.domain.MissionGenerationJob;
import com.planit.photomission.domain.MissionGenerationStatus;
import com.planit.photomission.repository.MissionGenerationJobRepository;
import com.planit.repository.RegionRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import com.planit.schedule.domain.Place;
import com.planit.schedule.domain.PlaceDetails;
import com.planit.schedule.domain.Schedule;
import com.planit.schedule.domain.ScheduleDay;
import com.planit.schedule.domain.ScheduleVisit;
import com.planit.schedule.repository.PlaceRepository;
import com.planit.schedule.repository.ScheduleDayRepository;
import com.planit.schedule.repository.ScheduleRepository;
import com.planit.schedule.repository.ScheduleVisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 커밋 후 발행은 OutboxServiceTest에서 확인하고, 여기서는 한 트랜잭션에 저장되는 작업과 Outbox를 확인한다. */
@SpringBootTest
@Transactional
class PhotoMissionGenerationEnqueueServiceTest {

    private static final LocalDate TRIP_DATE = LocalDate.of(2026, 12, 1);
    private static final LocalDateTime MIDNIGHT = TRIP_DATE.atStartOfDay();

    @Autowired
    private PhotoMissionGenerationEnqueueService service;

    @Autowired
    private MissionGenerationJobRepository jobRepository;

    @Autowired
    private MessageOutboxRepository outboxRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleDayRepository scheduleDayRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private ScheduleVisitRepository visitRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OutboxPublisher outboxPublisher;

    private Trip trip;
    private ScheduleVisit visit;

    @BeforeEach
    void setUp() {
        Region region = regionRepository.findById(1L).orElseThrow();
        trip = tripRepository.save(new Trip(
                region, "포토미션 접수 여행", TRIP_DATE, TRIP_DATE.plusDays(1), (byte) 4,
                TRIP_DATE.minusDays(1).atTime(23, 59, 59)
        ));
        User user = userRepository.save(new User(null, UUID.randomUUID(), "포토미션접수사용자"));
        TripMember member = tripMemberRepository.save(TripMember.createMember(trip, user));
        Schedule schedule = scheduleRepository.save(new Schedule(trip, MIDNIGHT.minusDays(2)));
        ScheduleDay day = scheduleDayRepository.save(new ScheduleDay(schedule, (byte) 1, TRIP_DATE));
        Place place = placeRepository.save(new Place(
                region,
                new PlaceDetails("place_" + UUID.randomUUID(), "첨성대", "관광명소", "경주시 인왕동", null,
                        BigDecimal.valueOf(129.2190), BigDecimal.valueOf(35.8347), null, null),
                MIDNIGHT.minusDays(2)
        ));
        visit = visitRepository.save(new ScheduleVisit(day, place, 1, "역사 취향 반영", MIDNIGHT.minusDays(2)));
        assertThat(member.getId()).isNotNull();
    }

    @DisplayName("처음 접수하면 작업을 QUEUED로 저장하고 당일 06:00 마감의 요청 메시지를 Outbox에 남긴다")
    @Test
    void enqueuesNewJob() {
        Long jobId = service.enqueue(trip.getId(), MIDNIGHT);

        MissionGenerationJob job = jobRepository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(MissionGenerationStatus.QUEUED);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getLastAttemptedAt()).isEqualTo(MIDNIGHT);

        MessageOutbox outbox = outboxOf(jobId);
        assertThat(outbox.getStatus()).isEqualTo(MessageOutboxStatus.PENDING);
        assertThat(outbox.getRoutingKey()).isEqualTo("ai-photomission-generation");
        JsonNode payload = objectMapper.readTree(outbox.getPayload());
        assertThat(payload.get("jobId").asString()).isEqualTo(jobId.toString());
        assertThat(payload.get("jobType").asString()).isEqualTo("PHOTO_MISSION_GENERATE");
        assertThat(payload.get("deadlineAt").asString()).isEqualTo("2026-12-01T06:00:00+09:00");
        assertThat(payload.get("request").get("itinerary_id").asString()).isEqualTo("trip_" + trip.getId());
        assertThat(payload.get("request").get("places").get(0).get("id").asString())
                .isEqualTo(visit.getPlace().getGooglePlaceId());
    }

    @DisplayName("실패한 작업이 있으면 같은 행을 다시 접수하고 접수 횟수를 올린다")
    @Test
    void requeuesFailedJob() {
        MissionGenerationJob failed = MissionGenerationJob.start(trip, MIDNIGHT.minusDays(1));
        failed.fail();
        Long failedId = jobRepository.saveAndFlush(failed).getId();

        Long jobId = service.enqueue(trip.getId(), MIDNIGHT);

        assertThat(jobId).isEqualTo(failedId);
        MissionGenerationJob job = jobRepository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(MissionGenerationStatus.QUEUED);
        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThat(outboxOf(jobId).getStatus()).isEqualTo(MessageOutboxStatus.PENDING);
    }

    @DisplayName("이미 접수한 작업이 있으면 다시 접수하지 않는다")
    @Test
    void rejectsAlreadyQueuedJob() {
        jobRepository.saveAndFlush(MissionGenerationJob.queued(trip, MIDNIGHT));

        assertThatThrownBy(() -> service.enqueue(trip.getId(), MIDNIGHT.plusMinutes(5)))
                .isInstanceOf(IllegalStateException.class);
    }

    @DisplayName("요청 본문은 기존 동기 HTTP 요청처럼 snake_case 필드 이름을 유지한다")
    @Test
    void usesSyncRequestShape() {
        Long jobId = service.enqueue(trip.getId(), MIDNIGHT);

        JsonNode place = objectMapper.readTree(outboxOf(jobId).getPayload()).get("request").get("places").get(0);
        assertThat(place.has("displayName")).isTrue();
        assertThat(place.has("selected_for")).isTrue();
        assertThat(place.has("matched_preferences")).isTrue();
    }

    private MessageOutbox outboxOf(Long jobId) {
        List<MessageOutbox> outboxes = outboxRepository.findAll().stream()
                .filter(outbox -> outbox.getJobId().equals(jobId))
                .toList();
        assertThat(outboxes).hasSize(1);
        return outboxes.getFirst();
    }
}
