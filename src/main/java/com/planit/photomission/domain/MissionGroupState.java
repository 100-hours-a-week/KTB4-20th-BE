package com.planit.photomission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "mission_group_states",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mission_group_states_mission",
                columnNames = "mission_id"
        )
)
public class MissionGroupState {

    public static final int MAX_RETRY_COUNT = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MissionCompletionStatus status;

    @Column(name = "retry_count", nullable = false)
    private byte retryCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_method", length = 20)
    private MissionCompletionMethod completionMethod;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected MissionGroupState() {
    }

    public static MissionGroupState create(
            Mission mission,
            LocalDateTime now
    ) {
        if (!mission.isGroup()) {
            throw new IllegalArgumentException(
                    "단체 미션만 공유 상태를 가질 수 있습니다"
            );
        }

        MissionGroupState state = new MissionGroupState();
        state.mission = mission;
        state.status = MissionCompletionStatus.PENDING;
        state.createdAt = now;
        state.updatedAt = now;
        return state;
    }

    public void recordUnsuccessfulEvaluation(LocalDateTime evaluatedAt) {
        requirePending();
        if (retryCount >= MAX_RETRY_COUNT) {
            throw new IllegalStateException("최대 판정 시도 횟수를 초과했습니다");
        }

        retryCount++;
        updatedAt = evaluatedAt;
    }

    public void completeByAi(LocalDateTime completedAt) {
        complete(MissionCompletionMethod.AI, completedAt);
    }

    public void completeManually(LocalDateTime completedAt) {
        requirePending();
        if (retryCount < MAX_RETRY_COUNT) {
            throw new IllegalStateException(
                    "판정 실패 3회 이후에 직접 완료할 수 있습니다"
            );
        }

        complete(MissionCompletionMethod.MANUAL, completedAt);
    }

    public void reflectPhotoDeletion(
            boolean hasRemainingSuccessPhoto,
            LocalDateTime updatedAt
    ) {
        if (status != MissionCompletionStatus.COMPLETED
                || completionMethod != MissionCompletionMethod.AI
                || hasRemainingSuccessPhoto) {
            return;
        }

        status = MissionCompletionStatus.PENDING;
        completionMethod = null;
        completedAt = null;
        this.updatedAt = updatedAt;
    }

    private void complete(
            MissionCompletionMethod method,
            LocalDateTime completedAt
    ) {
        requirePending();
        status = MissionCompletionStatus.COMPLETED;
        completionMethod = method;
        this.completedAt = completedAt;
        updatedAt = completedAt;
    }

    private void requirePending() {
        if (status != MissionCompletionStatus.PENDING) {
            throw new IllegalStateException("대기 중인 미션만 변경할 수 있습니다");
        }
    }

    public Long getId() {
        return id;
    }

    public Mission getMission() {
        return mission;
    }

    public MissionCompletionStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public MissionCompletionMethod getCompletionMethod() {
        return completionMethod;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
}
