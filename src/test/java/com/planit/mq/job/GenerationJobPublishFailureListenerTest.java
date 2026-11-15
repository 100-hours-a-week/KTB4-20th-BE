package com.planit.mq.job;

import com.planit.domain.Region;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.mq.outbox.MessageOutbox;
import com.planit.mq.outbox.MessageOutboxRepository;
import com.planit.mq.outbox.MessageOutboxStatus;
import com.planit.mq.outbox.OutboxFailureService;
import com.planit.mq.outbox.PublishFailureCode;
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

/** Outbox 발행 포기부터 작업 실패, 알림 저장까지 OutboxFailureService 한 번의 호출로 끝나는지 확인한다. */
@SpringBootTest
@Transactional
class GenerationJobPublishFailureListenerTest {

    /** 커밋하지 않는 테스트지만 Relay 대상과 겹치지 않게 먼 미래로 둔다. */
    private static final LocalDateTime NOW = LocalDateTime.of(2099, 1, 1, 0, 0);

    @Autowired
    private OutboxFailureService outboxFailureService;

    @Autowired
    private MessageOutboxRepository outboxRepository;

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

    @MockitoBean
    private NotificationEventPublisher eventPublisher;

    private ScheduleGenerationJob scheduleJob;
    private MissionGenerationJob missionJob;

    @BeforeEach
    void setUp() {
        Region region = regionRepository.findById(1L).orElseThrow();
        LocalDate tripDate = LocalDate.of(2026, 12, 1);
        Trip trip = tripRepository.save(new Trip(
                region,
                "경주 여행",
                tripDate,
                tripDate.plusDays(2),
                (byte) 4,
                tripDate.minusDays(1).atTime(23, 59, 59)
        ));
        User host = userRepository.save(new User(null, UUID.randomUUID(), "발행실패방장"));
        TripMember hostMember = tripMemberRepository.save(TripMember.createHost(trip, host));
        scheduleJob = scheduleJobRepository.saveAndFlush(ScheduleGenerationJob.queued(
                trip, hostMember, ScheduleGenerationMode.GENERAL_REGENERATION, UUID.randomUUID(), NOW
        ));
        missionJob = missionJobRepository.saveAndFlush(MissionGenerationJob.start(trip, NOW));
        jdbcTemplate.update("UPDATE mission_generation_jobs SET status = 'QUEUED' WHERE id = ?", missionJob.getId());
    }

    @DisplayName("일정 생성 메시지 발행을 포기하면 Outbox와 작업을 실패로 바꾸고 실패 알림을 저장한다")
    @Test
    void failsScheduleGenerationJob() {
        MessageOutbox outbox = pendingOutbox(AiJobType.SCHEDULE_GENERATE, scheduleJob.getId(), "ai-schedule-generation");

        assertThat(outboxFailureService.fail(outbox.getId(), PublishFailureCode.PUBLISH_RETURNED, "NO_ROUTE")).isTrue();

        assertThat(outboxStatus(outbox)).isEqualTo(MessageOutboxStatus.FAILED);
        ScheduleGenerationJob failed = scheduleJobRepository.findById(scheduleJob.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ScheduleGenerationJobStatus.FAILED);
        assertThat(failed.getActiveSlot()).isNull();
        assertThat(failed.getErrorCode()).isEqualTo("PUBLISH_RETURNED");
        assertThat(scheduleNotifications())
                .singleElement()
                .extracting(Notification::getType)
                .isEqualTo(NotificationType.SCHEDULE_GENERATION_FAILED);
    }

    @DisplayName("포토미션 생성 메시지 발행을 포기하면 작업을 실패로 바꾼다")
    @Test
    void failsMissionGenerationJob() {
        MessageOutbox outbox = pendingOutbox(
                AiJobType.PHOTO_MISSION_GENERATE, missionJob.getId(), "ai-photomission-generation"
        );

        assertThat(outboxFailureService.fail(outbox.getId(), PublishFailureCode.PUBLISH_FAILED, "NACK")).isTrue();

        assertThat(outboxStatus(outbox)).isEqualTo(MessageOutboxStatus.FAILED);
        assertThat(missionJobRepository.findById(missionJob.getId()).orElseThrow().getStatus())
                .isEqualTo(MissionGenerationStatus.FAILED);
    }

    @DisplayName("포토미션 판정 메시지는 Outbox만 실패로 바꾸고 판정 처리 쪽에 맡긴다")
    @Test
    void ignoresPhotoJudgeJob() {
        MessageOutbox outbox = pendingOutbox(AiJobType.PHOTO_JUDGE, 1L, "ai-photomission-verification");

        assertThat(outboxFailureService.fail(outbox.getId(), PublishFailureCode.PUBLISH_FAILED, "NACK")).isTrue();

        assertThat(outboxStatus(outbox)).isEqualTo(MessageOutboxStatus.FAILED);
    }

    @DisplayName("이미 실패 처리한 Outbox는 작업과 알림을 다시 건드리지 않는다")
    @Test
    void ignoresAlreadyFailedOutbox() {
        MessageOutbox outbox = pendingOutbox(AiJobType.SCHEDULE_GENERATE, scheduleJob.getId(), "ai-schedule-generation");
        outboxFailureService.fail(outbox.getId(), PublishFailureCode.PUBLISH_RETURNED, "NO_ROUTE");

        assertThat(outboxFailureService.fail(outbox.getId(), PublishFailureCode.PUBLISH_FAILED, "NACK")).isFalse();

        assertThat(scheduleJobRepository.findById(scheduleJob.getId()).orElseThrow().getErrorCode())
                .isEqualTo("PUBLISH_RETURNED");
        assertThat(scheduleNotifications()).hasSize(1);
    }

    private MessageOutbox pendingOutbox(AiJobType jobType, Long jobId, String routingKey) {
        return outboxRepository.saveAndFlush(MessageOutbox.pending(
                jobType, jobId, "ai.request", routingKey, "{}", NOW, NOW.plusSeconds(10)
        ));
    }

    private MessageOutboxStatus outboxStatus(MessageOutbox outbox) {
        return outboxRepository.findById(outbox.getId()).orElseThrow().getStatus();
    }

    private List<Notification> scheduleNotifications() {
        return notificationRepository.findAll().stream()
                .filter(notification -> notification.getDedupeKey()
                        .startsWith("AI_JOB:SCHEDULE_GENERATE:" + scheduleJob.getId() + ":"))
                .toList();
    }
}
