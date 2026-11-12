package com.planit.schedule.service;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.mq.config.RabbitMqConfig;
import com.planit.mq.intake.AiJobIntakeGuard;
import com.planit.mq.job.AiJobType;
import com.planit.mq.message.AiRequestMessageWriter;
import com.planit.mq.outbox.OutboxService;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.schedule.domain.ScheduleGenerationJob;
import com.planit.schedule.domain.ScheduleGenerationReason;
import com.planit.schedule.repository.ScheduleGenerationJobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * 일정 생성 요청을 메시지 큐로 접수한다. 같은 Idempotency-Key로 이미 접수했으면 그 작업을 돌려주고,
 * 아니면 차단 플래그와 기존 사전 검증을 거쳐 한 트랜잭션에서 작업과 Outbox를 저장한다. 커밋되면 바로 발행한다.
 * 결과는 Result Worker가 반영하고 알림으로 전달한다.
 */
@Service
public class ScheduleGenerationIntakeService {

    /** AI가 처리를 포기하는 시각. Stale Job Checker의 일정 생성 기준 시간과 같아야 한다. */
    static final Duration DEADLINE_AFTER = Duration.ofMinutes(28);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ScheduleGenerationJobRepository jobRepository;
    private final ScheduleGenerationPreparationService preparationService;
    private final AiJobIntakeGuard intakeGuard;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final AiRequestMessageWriter messageWriter;
    private final OutboxService outboxService;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ScheduleGenerationIntakeService(
            ScheduleGenerationJobRepository jobRepository,
            ScheduleGenerationPreparationService preparationService,
            AiJobIntakeGuard intakeGuard,
            TripRepository tripRepository,
            TripMemberRepository tripMemberRepository,
            AiRequestMessageWriter messageWriter,
            OutboxService outboxService,
            @Qualifier(RabbitMqConfig.MQ_CLOCK) Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.jobRepository = jobRepository;
        this.preparationService = preparationService;
        this.intakeGuard = intakeGuard;
        this.tripRepository = tripRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.messageWriter = messageWriter;
        this.outboxService = outboxService;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * @param idempotencyKey 요청 헤더 Idempotency-Key. 없으면 서버가 만든다.
     * @return 접수한 작업 ID. 같은 키로 이미 접수했으면 그 작업 ID
     * @throws BusinessException 같은 키를 다른 여행에 썼으면 IDEMPOTENCY_KEY_REUSED,
     *                           같은 여행에 진행 중인 작업이 있으면 AI_JOB_ALREADY_IN_PROGRESS
     */
    public Long accept(String userPublicId, Long tripId, ScheduleGenerationReason reason, UUID idempotencyKey) {
        Optional<Long> accepted = acceptedJobId(idempotencyKey, tripId);
        if (accepted.isPresent()) {
            return accepted.get();
        }

        intakeGuard.requireAvailable(AiJobType.SCHEDULE_GENERATE);
        ScheduleGenerationPreparation preparation = preparationService.prepare(userPublicId, tripId, reason);
        UUID key = idempotencyKey != null ? idempotencyKey : UUID.randomUUID();
        try {
            return transaction.execute(status -> save(tripId, preparation, key));
        } catch (DataIntegrityViolationException e) {
            // 같은 키로 동시에 들어온 요청이 먼저 저장했으면 그 작업을 돌려주고, 아니면 같은 여행에 진행 중인 작업이 있는 것이다
            return acceptedJobId(idempotencyKey, tripId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.AI_JOB_ALREADY_IN_PROGRESS, e));
        }
    }

    private Long save(Long tripId, ScheduleGenerationPreparation preparation, UUID idempotencyKey) {
        LocalDateTime now = LocalDateTime.now(clock);
        ScheduleGenerationJob job = jobRepository.saveAndFlush(ScheduleGenerationJob.queued(
                tripRepository.getReferenceById(tripId),
                tripMemberRepository.getReferenceById(preparation.requestedByMemberId()),
                preparation.mode(),
                idempotencyKey,
                now
        ));
        outboxService.enqueue(
                AiJobType.SCHEDULE_GENERATE,
                job.getId(),
                messageWriter.write(AiJobType.SCHEDULE_GENERATE, job.getId(), deadlineOf(now), preparation.request())
        );
        return job.getId();
    }

    /** 같은 키로 접수한 작업이 있으면 그 ID. 다른 여행의 작업이면 키를 다시 쓴 것이므로 거부한다. */
    private Optional<Long> acceptedJobId(UUID idempotencyKey, Long tripId) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return jobRepository.findByIdempotencyKey(idempotencyKey)
                .map(job -> {
                    if (!job.getTrip().getId().equals(tripId)) {
                        throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
                    }
                    return job.getId();
                });
    }

    static OffsetDateTime deadlineOf(LocalDateTime createdAt) {
        return createdAt.plus(DEADLINE_AFTER).atZone(SEOUL).toOffsetDateTime();
    }
}
