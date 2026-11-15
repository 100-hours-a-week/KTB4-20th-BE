package com.planit.schedule.service;

import com.planit.ai.AiPlaceSelectionRequest;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.mq.intake.AiJobIntakeGuard;
import com.planit.mq.intake.AiJobTemporarilyUnavailableException;
import com.planit.mq.job.AiJobType;
import com.planit.mq.message.AiRequestMessageWriter;
import com.planit.mq.outbox.OutboxService;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.schedule.domain.ScheduleGenerationJob;
import com.planit.schedule.domain.ScheduleGenerationJobStatus;
import com.planit.schedule.domain.ScheduleGenerationReason;
import com.planit.schedule.repository.ScheduleGenerationJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ScheduleGenerationIntakeServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);
    private static final String USER_ID = UUID.randomUUID().toString();
    private static final long TRIP_ID = 7L;
    private static final long MEMBER_ID = 3L;
    private static final long JOB_ID = 42L;

    private final ScheduleGenerationJobRepository jobRepository = mock(ScheduleGenerationJobRepository.class);
    private final ScheduleGenerationPreparationService preparationService =
            mock(ScheduleGenerationPreparationService.class);
    private final AiJobIntakeGuard intakeGuard = mock(AiJobIntakeGuard.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripMemberRepository tripMemberRepository = mock(TripMemberRepository.class);
    private final OutboxService outboxService = mock(OutboxService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final ScheduleGenerationIntakeService service = new ScheduleGenerationIntakeService(
            jobRepository,
            preparationService,
            intakeGuard,
            tripRepository,
            tripMemberRepository,
            new AiRequestMessageWriter(jsonMapper),
            outboxService,
            Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL),
            transactionManager
    );

    private final Trip trip = mock(Trip.class);
    private final AiPlaceSelectionRequest request = new AiPlaceSelectionRequest(
            "경주", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2), List.of()
    );

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(trip.getId()).thenReturn(TRIP_ID);
        when(tripRepository.getReferenceById(TRIP_ID)).thenReturn(trip);
        when(tripMemberRepository.getReferenceById(MEMBER_ID)).thenReturn(mock(TripMember.class));
        when(preparationService.prepare(USER_ID, TRIP_ID, ScheduleGenerationReason.GENERAL))
                .thenReturn(new ScheduleGenerationPreparation(
                        request, ScheduleGenerationMode.GENERAL_REGENERATION, 1L, 2L, MEMBER_ID
                ));
        when(jobRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ScheduleGenerationJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", JOB_ID);
            return job;
        });
    }

    @DisplayName("작업을 QUEUED로 저장하고 생성 시각 + 28분 마감의 요청 메시지를 같은 트랜잭션에서 Outbox에 남긴다")
    @Test
    void acceptsNewRequest() {
        UUID idempotencyKey = UUID.randomUUID();

        assertThat(service.accept(USER_ID, TRIP_ID, ScheduleGenerationReason.GENERAL, idempotencyKey))
                .isEqualTo(JOB_ID);

        ArgumentCaptor<ScheduleGenerationJob> job = ArgumentCaptor.forClass(ScheduleGenerationJob.class);
        verify(jobRepository).saveAndFlush(job.capture());
        assertThat(job.getValue().getStatus()).isEqualTo(ScheduleGenerationJobStatus.QUEUED);
        assertThat(job.getValue().getJobType()).isEqualTo(ScheduleGenerationMode.GENERAL_REGENERATION);
        assertThat(job.getValue().getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(job.getValue().getCreatedAt()).isEqualTo(NOW);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outboxService).enqueue(eq(AiJobType.SCHEDULE_GENERATE), eq(JOB_ID), payload.capture());
        JsonNode json = jsonMapper.readTree(payload.getValue());
        assertThat(json.get("jobId").asString()).isEqualTo("42");
        assertThat(json.get("deadlineAt").asString()).isEqualTo("2026-11-12T12:28:00+09:00");
        assertThat(json.get("request").get("region").asString()).isEqualTo("경주");
        verify(transactionManager).commit(any());
    }

    @DisplayName("Idempotency-Key가 없으면 서버가 만든 키로 접수한다")
    @Test
    void generatesIdempotencyKey() {
        service.accept(USER_ID, TRIP_ID, ScheduleGenerationReason.GENERAL, null);

        ArgumentCaptor<ScheduleGenerationJob> job = ArgumentCaptor.forClass(ScheduleGenerationJob.class);
        verify(jobRepository).saveAndFlush(job.capture());
        assertThat(job.getValue().getIdempotencyKey()).isNotNull();
        verify(jobRepository, never()).findByIdempotencyKey(any());
    }

    @DisplayName("같은 Idempotency-Key로 이미 접수했으면 검증과 저장 없이 그 작업을 돌려준다")
    @Test
    void returnsAcceptedJobForSameKey() {
        UUID idempotencyKey = UUID.randomUUID();
        givenJobWithKey(idempotencyKey, TRIP_ID);

        assertThat(service.accept(USER_ID, TRIP_ID, ScheduleGenerationReason.GENERAL, idempotencyKey))
                .isEqualTo(JOB_ID);

        verifyNoInteractions(intakeGuard, preparationService, outboxService);
        verify(jobRepository, never()).saveAndFlush(any());
    }

    @DisplayName("다른 여행에 쓴 Idempotency-Key면 409로 거부한다")
    @Test
    void rejectsKeyReusedForOtherTrip() {
        UUID idempotencyKey = UUID.randomUUID();
        givenJobWithKey(idempotencyKey, TRIP_ID + 1);

        assertThatThrownBy(() -> service.accept(USER_ID, TRIP_ID, ScheduleGenerationReason.GENERAL, idempotencyKey))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @DisplayName("큐가 막혀 있으면 사전 검증과 저장을 하지 않고 503으로 거부한다")
    @Test
    void rejectsWhenBlocked() {
        doThrow(new AiJobTemporarilyUnavailableException(Duration.ofSeconds(60)))
                .when(intakeGuard).requireAvailable(AiJobType.SCHEDULE_GENERATE);

        assertThatThrownBy(() -> service.accept(USER_ID, TRIP_ID, ScheduleGenerationReason.GENERAL, null))
                .isInstanceOf(AiJobTemporarilyUnavailableException.class);
        verifyNoInteractions(preparationService, outboxService);
    }

    @DisplayName("같은 여행에 진행 중인 작업이 있어 저장이 막히면 409로 거부한다")
    @Test
    void rejectsWhenJobInProgress() {
        doThrow(new DataIntegrityViolationException("uk_schedule_generation_jobs_active"))
                .when(jobRepository).saveAndFlush(any());

        assertThatThrownBy(() -> service.accept(USER_ID, TRIP_ID, ScheduleGenerationReason.GENERAL, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_JOB_ALREADY_IN_PROGRESS);
        verify(outboxService, never()).enqueue(any(), any(), anyString());
    }

    @DisplayName("같은 키로 동시에 들어온 요청이 먼저 저장했으면 그 작업을 돌려준다")
    @Test
    void returnsConcurrentlyAcceptedJob() {
        UUID idempotencyKey = UUID.randomUUID();
        ScheduleGenerationJob accepted = jobOf(TRIP_ID);
        when(jobRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty(), Optional.of(accepted));
        doThrow(new DataIntegrityViolationException("uk_schedule_generation_jobs_idempotency"))
                .when(jobRepository).saveAndFlush(any());

        assertThat(service.accept(USER_ID, TRIP_ID, ScheduleGenerationReason.GENERAL, idempotencyKey))
                .isEqualTo(JOB_ID);
    }

    private void givenJobWithKey(UUID idempotencyKey, long tripId) {
        ScheduleGenerationJob job = jobOf(tripId);
        when(jobRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(job));
    }

    private static ScheduleGenerationJob jobOf(long tripId) {
        Trip jobTrip = mock(Trip.class);
        when(jobTrip.getId()).thenReturn(tripId);
        ScheduleGenerationJob job = mock(ScheduleGenerationJob.class);
        when(job.getTrip()).thenReturn(jobTrip);
        when(job.getId()).thenReturn(JOB_ID);
        return job;
    }
}
