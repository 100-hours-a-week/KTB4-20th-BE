package com.planit.mq.job;

import com.planit.domain.Region;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.notification.delivery.NotificationEventPublisher;
import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationType;
import com.planit.notification.repository.NotificationRepository;
import com.planit.photomission.domain.MissionGenerationJob;
import com.planit.photomission.domain.MissionGenerationStatus;
import com.planit.photomission.repository.MissionGenerationJobRepository;
import com.planit.repository.RegionRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import com.planit.schedule.domain.ScheduleGenerationJob;
import com.planit.schedule.domain.ScheduleGenerationJobStatus;
import com.planit.schedule.repository.ScheduleGenerationJobRepository;
import com.planit.schedule.service.ScheduleGenerationMode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 커밋 후 실시간 발행은 AiJobNotificationServiceTest에서 확인하고, 여기서는 작업 상태와 저장된 알림을 확인한다. */
@SpringBootTest
@Transactional
class AiJobTerminationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);

    @Autowired
    private AiJobTerminationService service;

    @Autowired
    private ScheduleGenerationJobRepository scheduleJobRepository;

    @Autowired
    private MissionGenerationJobRepository missionJobRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private NotificationEventPublisher eventPublisher;

    private Trip trip;
    private ScheduleGenerationJob scheduleJob;
    private MissionGenerationJob missionJob;

    @BeforeEach
    void setUp() {
        Region region = regionRepository.findById(1L).orElseThrow();
        LocalDate tripDate = LocalDate.of(2026, 12, 1);
        trip = tripRepository.save(new Trip(
                region,
                "경주 여행",
                tripDate,
                tripDate.plusDays(2),
                (byte) 4,
                tripDate.minusDays(1).atTime(23, 59, 59)
        ));
        User host = userRepository.save(new User(null, UUID.randomUUID(), "작업종료방장"));
        User member = userRepository.save(new User(null, UUID.randomUUID(), "작업종료멤버"));
        TripMember hostMember = tripMemberRepository.save(TripMember.createHost(trip, host));
        tripMemberRepository.save(TripMember.createMember(trip, member));
        scheduleJob = scheduleJobRepository.saveAndFlush(ScheduleGenerationJob.queued(
                trip, hostMember, ScheduleGenerationMode.GENERAL_REGENERATION, UUID.randomUUID(), NOW
        ));
        missionJob = missionJobRepository.saveAndFlush(MissionGenerationJob.start(trip, NOW));
        jdbcTemplate.update("UPDATE mission_generation_jobs SET status = 'QUEUED' WHERE id = ?", missionJob.getId());
    }

    @DisplayName("일정 생성 작업을 성공으로 끝내면 다음 요청을 받을 수 있게 하고 활성 멤버 전원에게 성공 알림을 저장한다")
    @Test
    void succeedsScheduleGeneration() {
        assertThat(service.succeedScheduleGeneration(scheduleJob.getId())).isTrue();

        ScheduleGenerationJob finished = reload(scheduleJob);
        assertThat(finished.getStatus()).isEqualTo(ScheduleGenerationJobStatus.SUCCEEDED);
        assertThat(finished.getActiveSlot()).isNull();
        assertThat(finished.getFinishedAt()).isNotNull();
        assertThat(finished.getErrorCode()).isNull();
        assertThat(scheduleNotifications())
                .hasSize(2)
                .extracting(Notification::getType)
                .containsOnly(NotificationType.SCHEDULE_GENERATION_SUCCEEDED);
    }

    @DisplayName("일정 생성 작업을 실패로 끝내면 실패 원인을 남기고 실패 알림을 저장한다")
    @Test
    void failsScheduleGeneration() {
        assertThat(service.failScheduleGeneration(scheduleJob.getId(), "STALE")).isTrue();

        ScheduleGenerationJob finished = reload(scheduleJob);
        assertThat(finished.getStatus()).isEqualTo(ScheduleGenerationJobStatus.FAILED);
        assertThat(finished.getActiveSlot()).isNull();
        assertThat(finished.getErrorCode()).isEqualTo("STALE");
        assertThat(scheduleNotifications())
                .hasSize(2)
                .extracting(Notification::getType)
                .containsOnly(NotificationType.SCHEDULE_GENERATION_FAILED);
    }

    @DisplayName("이미 끝난 일정 생성 작업은 상태와 알림이 바뀌지 않는다")
    @Test
    void ignoresFinishedScheduleGeneration() {
        service.succeedScheduleGeneration(scheduleJob.getId());

        assertThat(service.failScheduleGeneration(scheduleJob.getId(), "STALE")).isFalse();
        assertThat(service.succeedScheduleGeneration(scheduleJob.getId())).isFalse();

        assertThat(reload(scheduleJob).getStatus()).isEqualTo(ScheduleGenerationJobStatus.SUCCEEDED);
        assertThat(scheduleNotifications())
                .hasSize(2)
                .extracting(Notification::getType)
                .containsOnly(NotificationType.SCHEDULE_GENERATION_SUCCEEDED);
    }

    @DisplayName("성공 결과가 왔지만 여행이 삭제됐으면 TRIP_DELETED로 실패 처리하고 알리지 않는다")
    @Test
    void failsScheduleGenerationOfDeletedTrip() {
        deleteTrip();

        assertThat(service.succeedScheduleGeneration(scheduleJob.getId())).isFalse();

        ScheduleGenerationJob finished = reload(scheduleJob);
        assertThat(finished.getStatus()).isEqualTo(ScheduleGenerationJobStatus.FAILED);
        assertThat(finished.getActiveSlot()).isNull();
        assertThat(finished.getErrorCode()).isEqualTo(AiJobTerminationService.TRIP_DELETED);
        assertThat(scheduleNotifications()).isEmpty();
    }

    @DisplayName("삭제된 여행의 일정 생성 작업은 실패로 끝내되 알리지 않는다")
    @Test
    void failsWithoutNotificationForDeletedTrip() {
        deleteTrip();

        assertThat(service.failScheduleGeneration(scheduleJob.getId(), "DELIVERY_LIMIT")).isTrue();

        assertThat(reload(scheduleJob).getErrorCode()).isEqualTo("DELIVERY_LIMIT");
        assertThat(scheduleNotifications()).isEmpty();
    }

    @DisplayName("포토미션 생성 작업은 알림 없이 성공 또는 실패로 끝내고, 이미 끝났으면 바꾸지 않는다")
    @Test
    void finishesMissionGeneration() {
        assertThat(service.succeedMissionGeneration(missionJob.getId())).isTrue();
        assertThat(service.failMissionGeneration(missionJob.getId(), "STALE")).isFalse();

        assertThat(missionStatus()).isEqualTo(MissionGenerationStatus.SUCCEEDED);
        assertThat(notificationRepository.findAll())
                .noneMatch(notification -> notification.getDedupeKey().startsWith("AI_JOB:PHOTO_MISSION_GENERATE:"));
    }

    @DisplayName("성공 결과가 왔지만 여행이 삭제됐으면 포토미션 생성 작업을 실패로 끝낸다")
    @Test
    void failsMissionGenerationOfDeletedTrip() {
        deleteTrip();

        assertThat(service.succeedMissionGeneration(missionJob.getId())).isFalse();

        assertThat(missionStatus()).isEqualTo(MissionGenerationStatus.FAILED);
    }

    @DisplayName("작업 종류에 맞는 실패 처리로 넘기고, 포토미션 판정은 받지 않는다")
    @Test
    void failsByJobType() {
        assertThat(service.fail(AiJobType.SCHEDULE_GENERATE, scheduleJob.getId(), "PUBLISH_FAILED")).isTrue();
        assertThat(service.fail(AiJobType.PHOTO_MISSION_GENERATE, missionJob.getId(), "PUBLISH_FAILED")).isTrue();

        assertThat(reload(scheduleJob).getErrorCode()).isEqualTo("PUBLISH_FAILED");
        assertThat(missionStatus()).isEqualTo(MissionGenerationStatus.FAILED);
        assertThatThrownBy(() -> service.fail(AiJobType.PHOTO_JUDGE, 1L, "PUBLISH_FAILED"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("없는 작업은 종료하지 않는다")
    @Test
    void ignoresMissingJob() {
        assertThat(service.succeedScheduleGeneration(-1L)).isFalse();
        assertThat(service.failScheduleGeneration(-1L, "STALE")).isFalse();
        assertThat(service.succeedMissionGeneration(-1L)).isFalse();
        assertThat(service.failMissionGeneration(-1L, "STALE")).isFalse();
    }

    private void deleteTrip() {
        trip.delete(NOW);
        entityManager.flush();
        entityManager.clear();
    }

    private ScheduleGenerationJob reload(ScheduleGenerationJob job) {
        return scheduleJobRepository.findById(job.getId()).orElseThrow();
    }

    private MissionGenerationStatus missionStatus() {
        return missionJobRepository.findById(missionJob.getId()).orElseThrow().getStatus();
    }

    private List<Notification> scheduleNotifications() {
        return notificationRepository.findAll().stream()
                .filter(notification -> notification.getDedupeKey()
                        .startsWith("AI_JOB:SCHEDULE_GENERATE:" + scheduleJob.getId() + ":"))
                .toList();
    }
}
