package com.planit.schedule.domain;

import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.schedule.service.ScheduleGenerationMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 비동기로 처리하는 AI 일정 생성 작업.
 * 결과 반영과 Stale 처리가 같은 행을 동시에 끝낼 수 있으므로
 * 종료 처리는 엔티티 수정 대신 Repository의 `status = 'QUEUED'` 조건부 UPDATE로 한다.
 */
@Entity
@Table(
        name = "schedule_generation_jobs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_schedule_generation_jobs_active",
                        columnNames = {"trip_id", "active_slot"}
                ),
                @UniqueConstraint(
                        name = "uk_schedule_generation_jobs_idempotency",
                        columnNames = "idempotency_key"
                )
        }
)
public class ScheduleGenerationJob {

    /** 진행 중인 작업의 active_slot 값. 끝나면 NULL로 바꿔 다음 작업을 받을 수 있게 한다. */
    private static final byte ACTIVE_SLOT = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_member_id", nullable = false)
    private TripMember requestedByMember;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30)
    private ScheduleGenerationMode jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScheduleGenerationJobStatus status;

    @Column(name = "active_slot")
    private Byte activeSlot;

    @Column(name = "idempotency_key", nullable = false, columnDefinition = "BINARY(16)")
    private UUID idempotencyKey;

    @Column(name = "input_cutoff_at", nullable = false)
    private LocalDateTime inputCutoffAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ScheduleGenerationJob() {
    }

    /**
     * 접수한 일정 생성 작업을 만든다. 설문 입력 기준 시각과 접수 시각은 모두 요청 시각이다.
     *
     * @param idempotencyKey 요청 헤더 `Idempotency-Key` 값, 없으면 서버가 만든 값
     */
    public static ScheduleGenerationJob queued(
            Trip trip,
            TripMember requestedByMember,
            ScheduleGenerationMode jobType,
            UUID idempotencyKey,
            LocalDateTime requestedAt
    ) {
        ScheduleGenerationJob job = new ScheduleGenerationJob();
        job.trip = trip;
        job.requestedByMember = requestedByMember;
        job.jobType = jobType;
        job.status = ScheduleGenerationJobStatus.QUEUED;
        job.activeSlot = ACTIVE_SLOT;
        job.idempotencyKey = idempotencyKey;
        job.inputCutoffAt = requestedAt;
        job.createdAt = requestedAt;
        job.validate();
        return job;
    }

    private void validate() {
        requireValue(trip, "일정 생성 대상 여행은 필수입니다");
        requireValue(requestedByMember, "일정 생성 요청자는 필수입니다");
        requireValue(jobType, "일정 생성 방식은 필수입니다");
        requireValue(idempotencyKey, "멱등키는 필수입니다");
        requireValue(createdAt, "요청 시각은 필수입니다");
    }

    private static void requireValue(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    public Long getId() {
        return id;
    }

    public Trip getTrip() {
        return trip;
    }

    public TripMember getRequestedByMember() {
        return requestedByMember;
    }

    public ScheduleGenerationMode getJobType() {
        return jobType;
    }

    public ScheduleGenerationJobStatus getStatus() {
        return status;
    }

    public Byte getActiveSlot() {
        return activeSlot;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public LocalDateTime getInputCutoffAt() {
        return inputCutoffAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
