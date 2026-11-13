package com.planit.photomission.service;

import com.planit.domain.ImageFile;
import com.planit.domain.Region;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.mq.job.AiJobType;
import com.planit.mq.outbox.OutboxPublisher;
import com.planit.mq.result.AiResultEnvelope;
import com.planit.mq.result.AiResultHandler;
import com.planit.mq.topology.AiExchange;
import com.planit.mq.topology.AiResultQueue;
import com.planit.notification.domain.NotificationTargetType;
import com.planit.notification.repository.NotificationRepository;
import com.planit.photomission.domain.Mission;
import com.planit.photomission.domain.MissionCompletionStatus;
import com.planit.photomission.domain.MissionGroupState;
import com.planit.photomission.domain.MissionPhoto;
import com.planit.photomission.domain.MissionScope;
import com.planit.photomission.domain.PhotoEvaluation;
import com.planit.photomission.domain.PhotoEvaluationExecutionStatus;
import com.planit.photomission.repository.MissionGroupStateRepository;
import com.planit.photomission.repository.MissionPhotoRepository;
import com.planit.photomission.repository.MissionRepository;
import com.planit.photomission.repository.PhotoEvaluationRepository;
import com.planit.repository.ImageFileRepository;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@Testcontainers
class PhotoEvaluationResultRedeliveryIntegrationTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 11, 12, 20, 0);

    @Container
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4.1-management")
    );

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("spring.rabbitmq.virtual-host", () -> "/");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CachingConnectionFactory connectionFactory;

    @Autowired
    private PhotoResultGate resultGate;

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

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
    private MissionRepository missionRepository;

    @Autowired
    private MissionGroupStateRepository groupStateRepository;

    @Autowired
    private MissionPhotoRepository photoRepository;

    @Autowired
    private PhotoEvaluationRepository evaluationRepository;

    @Autowired
    private ImageFileRepository imageFileRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    @Qualifier("photoMissionClock")
    private Clock clock;

    @MockitoSpyBean
    private PhotoMissionResultApplier resultApplier;

    @BeforeEach
    void setUp() {
        resultGate.reset();
        rabbitTemplate.execute(channel -> channel.queuePurge(AiResultQueue.TASK_QUEUE));
    }

    @AfterEach
    void stopListeners() {
        listenerRegistry.stop();
    }

    @DisplayName("결과 커밋 후 ACK 전에 연결이 끊겨 재전달돼도 포토미션 결과는 한 번만 반영한다")
    @Test
    void appliesRedeliveredResultOnlyOnce() throws Exception {
        EvaluationFixture fixture = createQueuedGroupEvaluation();
        resultGate.pauseAfterFirstCommit();

        sendPhotoEvaluationResult(fixture.evaluationId());

        assertThat(resultGate.awaitFirstCommit()).isTrue();
        connectionFactory.resetConnection();
        resultGate.resumeAfterConnectionLoss();

        assertThat(resultGate.awaitRedelivery()).isTrue();
        verify(resultApplier, times(1)).apply(any(), any());

        PhotoEvaluation storedEvaluation = evaluationRepository
                .findById(fixture.evaluationId())
                .orElseThrow();
        MissionGroupState storedState = groupStateRepository
                .findByMissionId(fixture.missionId())
                .orElseThrow();
        MissionPhoto storedPhoto = photoRepository
                .findById(fixture.photoId())
                .orElseThrow();
        assertThat(resultGate.deliveryCount()).isGreaterThanOrEqualTo(2);
        assertThat(storedEvaluation.getExecutionStatus())
                .isEqualTo(PhotoEvaluationExecutionStatus.SUCCEEDED);
        assertThat(storedState.getStatus())
                .isEqualTo(MissionCompletionStatus.COMPLETED);
        assertThat(storedPhoto.getRepresentativeSlot()).isEqualTo((byte) 1);
        assertThat(notificationRepository
                .countByRecipientPublicIdAndReadAtIsNullAndDeletedAtIsNull(
                        fixture.recipientPublicId()
                )).isEqualTo(1);
        var notification = notificationRepository.findPageForRecipient(
                fixture.recipientPublicId(),
                null,
                null,
                PageRequest.of(0, 1)
        ).getFirst();
        assertThat(notification.getTargetType())
                .isEqualTo(NotificationTargetType.PHOTO_MISSION);
        assertThat(notification.getTargetId()).isEqualTo(fixture.missionId());
    }

    private EvaluationFixture createQueuedGroupEvaluation() {
        Region region = regionRepository.findById(1L).orElseThrow();
        LocalDate tripDate = LocalDate.of(2027, 1, 10);
        Trip trip = tripRepository.save(new Trip(
                region,
                "재전달여행",
                tripDate,
                tripDate.plusDays(2),
                (byte) 4,
                tripDate.minusDays(1).atTime(23, 59, 59)
        ));
        User user = userRepository.save(new User(
                null,
                UUID.randomUUID(),
                "재전달테스트사용자"
        ));
        TripMember member = tripMemberRepository.save(
                TripMember.createMember(trip, user)
        );
        Schedule schedule = scheduleRepository.save(new Schedule(trip, NOW));
        ScheduleDay day = scheduleDayRepository.save(
                new ScheduleDay(schedule, (byte) 1, tripDate)
        );
        Place place = placeRepository.save(new Place(
                region,
                new PlaceDetails(
                        "place_" + UUID.randomUUID(),
                        "첨성대",
                        "관광명소",
                        "경주시 인왕동",
                        null,
                        BigDecimal.valueOf(129.2190),
                        BigDecimal.valueOf(35.8347),
                        null,
                        null
                ),
                NOW
        ));
        ScheduleVisit visit = visitRepository.save(
                new ScheduleVisit(day, place, 1, "역사 취향 반영", NOW)
        );
        Mission mission = missionRepository.save(Mission.create(
                visit,
                1,
                MissionScope.GROUP,
                "다 같이 사진 찍기",
                null,
                NOW
        ));
        groupStateRepository.save(MissionGroupState.create(mission, NOW));
        ImageFile imageFile = imageFileRepository.save(ImageFile.profile(
                "missions/" + UUID.randomUUID() + ".jpg",
                "image/jpeg",
                1024
        ));
        MissionPhoto photo = photoRepository.save(MissionPhoto.group(
                mission,
                member,
                imageFile,
                UUID.randomUUID(),
                null,
                null,
                NOW
        ));
        PhotoEvaluation evaluation = evaluationRepository.saveAndFlush(
                PhotoEvaluation.queue(
                        photo,
                        NOW,
                        LocalDateTime.now(clock).plusMinutes(16)
                )
        );
        return new EvaluationFixture(
                evaluation.getId(),
                mission.getId(),
                photo.getId(),
                user.getPublicId()
        );
    }

    private void sendPhotoEvaluationResult(Long evaluationId) {
        String body = """
                {"schemaVersion":1,"jobId":"%d","jobType":"PHOTO_JUDGE",
                 "requestMessageId":"photo-%d","status":"SUCCEEDED","errorKind":null,
                 "result":{"result":"success","reason":null,"match_score":95.0,
                 "detected_labels":[{"name":"첨성대","matched":true}],
                 "landmark_confidence":92.0,"retry_hint":null}}
                """.formatted(evaluationId, evaluationId);
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setHeader(OutboxPublisher.RETRY_COUNT_HEADER, 0);
        rabbitTemplate.send(
                AiExchange.RESULT,
                AiResultQueue.ROUTING_KEY,
                new Message(body.getBytes(StandardCharsets.UTF_8), properties)
        );
    }

    private record EvaluationFixture(
            Long evaluationId,
            Long missionId,
            Long photoId,
            UUID recipientPublicId
    ) {
    }

    @TestConfiguration
    static class HandlerConfig {

        @Bean(name = "photoEvaluationResultHandler")
        PhotoResultGate photoEvaluationResultHandler(
                PhotoEvaluationResultTransactionService transactionService,
                ObjectMapper objectMapper,
                @Qualifier("photoMissionClock") Clock clock
        ) {
            return new PhotoResultGate(new PhotoEvaluationResultHandler(
                    transactionService,
                    objectMapper,
                    clock
            ));
        }
    }

    static class PhotoResultGate implements AiResultHandler {

        private final PhotoEvaluationResultHandler delegate;
        private final AtomicInteger deliveries = new AtomicInteger();
        private volatile boolean pauseAfterFirstCommit;
        private volatile CountDownLatch firstCommit;
        private volatile CountDownLatch resume;
        private volatile CountDownLatch redelivery;

        PhotoResultGate(PhotoEvaluationResultHandler delegate) {
            this.delegate = delegate;
            reset();
        }

        @Override
        public AiJobType jobType() {
            return AiJobType.PHOTO_JUDGE;
        }

        @Override
        public void handle(AiResultEnvelope result, int retryCount) {
            int delivery = deliveries.incrementAndGet();
            delegate.handle(result, retryCount);
            if (pauseAfterFirstCommit && delivery == 1) {
                firstCommit.countDown();
                await(resume);
            }
            if (delivery >= 2) {
                redelivery.countDown();
            }
        }

        void reset() {
            deliveries.set(0);
            pauseAfterFirstCommit = false;
            firstCommit = new CountDownLatch(1);
            resume = new CountDownLatch(1);
            redelivery = new CountDownLatch(1);
        }

        void pauseAfterFirstCommit() {
            pauseAfterFirstCommit = true;
        }

        boolean awaitFirstCommit() throws InterruptedException {
            return firstCommit.await(5, TimeUnit.SECONDS);
        }

        void resumeAfterConnectionLoss() {
            resume.countDown();
        }

        boolean awaitRedelivery() throws InterruptedException {
            return redelivery.await(10, TimeUnit.SECONDS);
        }

        int deliveryCount() {
            return deliveries.get();
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("테스트 진행 신호를 기다리지 못했습니다");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("테스트 진행 대기가 중단됐습니다", exception);
            }
        }
    }
}
