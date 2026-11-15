package com.planit.photomission.service;

import com.planit.domain.Trip;
import com.planit.mq.job.AiJobType;
import com.planit.mq.message.AiRequestMessageWriter;
import com.planit.mq.outbox.OutboxService;
import com.planit.photomission.ai.PhotoMissionGenerateRequest;
import com.planit.photomission.domain.MissionGenerationJob;
import com.planit.photomission.repository.MissionGenerationJobRepository;
import com.planit.repository.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 포토미션 생성을 메시지 큐로 접수한다. 여행 하나마다 한 트랜잭션에서 작업을 QUEUED로 저장하고 Outbox 행을 만들며,
 * 커밋되면 바로 발행한다. 결과는 Result Worker가 반영하고, 06:00까지 오지 않으면 Stale Job Checker가 실패로 끝낸다.
 */
@Service
public class PhotoMissionGenerationEnqueueService {

    /** AI가 처리를 포기하는 시각. Stale Job Checker의 포토미션 생성 기준 시각과 같아야 한다. */
    static final LocalTime DEADLINE = LocalTime.of(6, 0);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final TripRepository tripRepository;
    private final MissionGenerationJobRepository jobRepository;
    private final PhotoMissionGenerationTransactionService generationService;
    private final AiRequestMessageWriter messageWriter;
    private final OutboxService outboxService;

    public PhotoMissionGenerationEnqueueService(
            TripRepository tripRepository,
            MissionGenerationJobRepository jobRepository,
            PhotoMissionGenerationTransactionService generationService,
            AiRequestMessageWriter messageWriter,
            OutboxService outboxService
    ) {
        this.tripRepository = tripRepository;
        this.jobRepository = jobRepository;
        this.generationService = generationService;
        this.messageWriter = messageWriter;
        this.outboxService = outboxService;
    }

    /**
     * 처음이면 작업을 새로 만들고, 실패했거나 멈춘 작업이 있으면 다시 접수한다.
     * 요청 본문은 기존 동기 방식과 같이 확정 일정의 방문 장소로 만든다.
     *
     * @return 접수한 작업 ID
     * @throws IllegalArgumentException 여행이 없거나 삭제됐으면
     * @throws IllegalStateException 확정 일정이나 방문 장소가 없거나, 아직 진행 중인 작업이 있으면
     */
    @Transactional
    public Long enqueue(Long tripId, LocalDateTime attemptedAt) {
        Trip trip = tripRepository.findById(tripId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException(
                        "포토미션 생성 대상 여행을 찾을 수 없습니다"
                ));
        MissionGenerationJob job = jobRepository.findByTripIdForUpdate(tripId)
                .map(existing -> {
                    existing.requeue(attemptedAt);
                    return existing;
                })
                .orElseGet(() -> MissionGenerationJob.queued(trip, attemptedAt));
        Long jobId = jobRepository.saveAndFlush(job).getId();

        PhotoMissionGenerateRequest request = generationService.loadRequest(jobId);
        outboxService.enqueue(
                AiJobType.PHOTO_MISSION_GENERATE,
                jobId,
                messageWriter.write(AiJobType.PHOTO_MISSION_GENERATE, jobId, deadlineOf(attemptedAt), request)
        );
        return jobId;
    }

    /** 접수한 날의 06:00(서울). 00:00에 접수하므로 같은 날 아침이다. */
    static OffsetDateTime deadlineOf(LocalDateTime attemptedAt) {
        return attemptedAt.toLocalDate().atTime(DEADLINE).atZone(SEOUL).toOffsetDateTime();
    }
}
